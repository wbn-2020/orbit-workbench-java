package com.orbitworkbench.userfact.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.ai.application.AiOutputCleaner;
import com.orbitworkbench.ai.application.PromptCatalog;
import com.orbitworkbench.aiconnection.application.AiScenarioExecutionService;
import com.orbitworkbench.aiconnection.domain.AiScenario;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.userfact.api.UserFactDtos.DigestResponse;
import com.orbitworkbench.userfact.api.UserFactFreshness;
import com.orbitworkbench.userfact.domain.UserFactRecord;
import com.orbitworkbench.userfact.domain.UserProfileDigestRecord;
import com.orbitworkbench.userfact.infrastructure.mapper.UserFactMapper;
import com.orbitworkbench.userfact.infrastructure.mapper.UserProfileDigestMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 画像编译（V44，借鉴 EvoFlow 两阶段记忆固化）：把 CONFIRMED 事实集编译成一份
 * 高密度画像快照，注入链路优先用快照替代逐条罗列，控制记忆膨胀。
 *
 * <p>与蒸馏同一产品性格：手动触发、AI 产出、用户可见可删。快照不替代事实——
 * 事实本体不动，快照只是「编译产物」；快照与当前事实集不一致（有新增/归档）即过期，
 * 注入自动回退逐条模式，绝不拿过时快照喂模型。
 */
@Service
public class ProfileDigestService {

    /** 正文见 resources/prompts/profile-digest-system.txt。 */
    private static final String DIGEST_SYSTEM_PROMPT = PromptCatalog.load("profile-digest-system");

    private static final Duration DIGEST_TIMEOUT = Duration.ofSeconds(120);
    private static final int DIGEST_MAX_TOKENS = 1200;
    private static final int DIGEST_CHAR_MAX = 2000;
    /** 少于这个条数不值得编译：逐条注入本身就是紧凑的。 */
    private static final int MIN_FACTS_TO_DIGEST = 4;
    /** 编译输入上限与注入回退读取共用 listConfirmedForPrompt 的 60 条口径。 */
    private static final int MAX_SNAPSHOT_IDS = 60;

    private final UserFactMapper factMapper;
    private final UserProfileDigestMapper digestMapper;
    private final AiScenarioExecutionService aiScenarioExecution;
    private final ObjectMapper objectMapper;

    public ProfileDigestService(UserFactMapper factMapper,
                                UserProfileDigestMapper digestMapper,
                                AiScenarioExecutionService aiScenarioExecution,
                                ObjectMapper objectMapper) {
        this.factMapper = factMapper;
        this.digestMapper = digestMapper;
        this.aiScenarioExecution = aiScenarioExecution;
        this.objectMapper = objectMapper;
    }

    /** 编译入口：AI 在事务外调用，落库在短事务里（与蒸馏同一模式）。 */
    public DigestResponse digest(Long userId, Long connectionId) {
        List<UserFactRecord> facts = factMapper.listConfirmedForPrompt(userId);
        if (facts.size() < MIN_FACTS_TO_DIGEST) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "已确认的画像事实少于 " + MIN_FACTS_TO_DIGEST + " 条，逐条注入已经足够紧凑，暂不需要编译");
        }
        String material = buildMaterial(facts);
        String output = aiScenarioExecution.executeText(AiScenario.PROFILE_DIGEST, userId,
                connectionId, DIGEST_SYSTEM_PROMPT, material, DIGEST_MAX_TOKENS, DIGEST_TIMEOUT);
        return persist(userId, output, facts);
    }

    @Transactional(readOnly = true)
    public DigestResponse current(Long userId) {
        UserProfileDigestRecord record = digestMapper.findByUser(userId);
        if (record == null) {
            return null;
        }
        return toResponse(record, currentConfirmedIds(userId), Instant.now());
    }

    @Transactional
    public void clear(Long userId) {
        digestMapper.deleteByUser(userId);
    }

    /**
     * 注入链路读取口（UserFactService.confirmedContext 调用）：
     * 快照存在且与当前 CONFIRMED 集合一致时返回快照记录（正文 + 来源事实 id，V45 溯源），
     * 否则返回 null 走逐条模式。
     */
    @Transactional(readOnly = true)
    public UserProfileDigestRecord freshDigestForInjection(Long userId) {
        UserProfileDigestRecord record = digestMapper.findByUser(userId);
        if (record == null) {
            return null;
        }
        Set<Long> snapshotIds = parseIds(record.getSourceFactIds());
        Set<Long> nowIds = currentConfirmedIds(userId);
        return snapshotIds.equals(nowIds) ? record : null;
    }

    private DigestResponse persist(Long userId, String output, List<UserFactRecord> facts) {
        String text = cleanOutput(output);
        if (text.length() < 30) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, ErrorCode.UPSTREAM_UNAVAILABLE,
                    "画像编译失败：模型输出过短，未形成有效快照");
        }
        Instant now = Instant.now();
        UserProfileDigestRecord record = new UserProfileDigestRecord();
        record.setUserId(userId);
        record.setDigest(AiOutputCleaner.truncate(text, DIGEST_CHAR_MAX));
        record.setSourceFactIds(serializeIds(facts.stream().map(UserFactRecord::getId).toList()));
        record.setSourceCount(facts.size());
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        digestMapper.upsert(record);
        Set<Long> ids = new LinkedHashSet<>();
        for (UserFactRecord fact : facts) {
            ids.add(fact.getId());
        }
        return toResponse(record, ids, now);
    }

    /** 剥掉模型偶发的代码围栏与前后空白；快照正文本身按纪律不带围栏。 */
    private static String cleanOutput(String output) {
        String text = output == null ? "" : output.trim();
        if (text.startsWith("```")) {
            int firstBreak = text.indexOf('\n');
            int fenceEnd = text.lastIndexOf("```");
            if (firstBreak > 0 && fenceEnd > firstBreak) {
                text = text.substring(firstBreak + 1, fenceEnd).trim();
            }
        }
        return text;
    }

    private String buildMaterial(List<UserFactRecord> facts) {
        StringBuilder material = new StringBuilder("用户已确认的画像事实清单：\n");
        for (UserFactRecord fact : facts) {
            material.append("- [").append(fact.getFactType()).append("] ")
                    .append(fact.getTitle()).append('：').append(fact.getContent());
            String note = UserFactFreshness.injectNote(fact);
            if (!note.isEmpty()) {
                material.append(note);
            }
            material.append('\n');
        }
        return material.toString();
    }

    private Set<Long> currentConfirmedIds(Long userId) {
        Set<Long> ids = new LinkedHashSet<>();
        for (UserFactRecord fact : factMapper.listConfirmedForPrompt(userId)) {
            ids.add(fact.getId());
        }
        return ids;
    }

    private DigestResponse toResponse(UserProfileDigestRecord record, Set<Long> nowIds, Instant now) {
        Set<Long> snapshotIds = parseIds(record.getSourceFactIds());
        int added = 0;
        for (Long id : nowIds) {
            if (!snapshotIds.contains(id)) {
                added++;
            }
        }
        int removed = 0;
        for (Long id : snapshotIds) {
            if (!nowIds.contains(id)) {
                removed++;
            }
        }
        return new DigestResponse(record.getId(), record.getDigest(), record.getSourceCount(),
                record.getModel(), record.getUpdatedAt(), added + removed > 0, added, removed);
    }

    private Set<Long> parseIds(String json) {
        Set<Long> ids = new LinkedHashSet<>();
        if (json == null || json.isBlank()) {
            return ids;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.isArray()) {
                for (JsonNode item : node) {
                    if (item.isNumber()) {
                        ids.add(item.asLong());
                    }
                }
            }
        } catch (Exception exception) {
            // 脏数据按「与当前集合不一致」处理即可（过期回退逐条），不抛出
        }
        return ids;
    }

    private String serializeIds(List<Long> ids) {
        List<Long> capped = ids.size() > MAX_SNAPSHOT_IDS ? ids.subList(0, MAX_SNAPSHOT_IDS) : ids;
        StringBuilder text = new StringBuilder("[");
        for (int i = 0; i < capped.size(); i++) {
            if (i > 0) {
                text.append(',');
            }
            text.append(capped.get(i));
        }
        return text.append(']').toString();
    }
}
