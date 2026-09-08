package com.orbitworkbench.worklog.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.worklog.api.KnowledgeCardDtos.KnowledgeCardResponse;
import com.orbitworkbench.worklog.api.KnowledgeCardDtos.UpdateKnowledgeCardRequest;
import com.orbitworkbench.worklog.infrastructure.mapper.KnowledgeCardMapper;
import com.orbitworkbench.worklog.domain.KnowledgeCardRow;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 知识卡片读取、人工编辑与标签序列化。
 *
 * <p>标签以 JSON 数组字符串落库，反序列化失败一律返回空列表而不是猜测补值，
 * 避免把脏数据伪装成正常标签。
 */
@Service
public class KnowledgeCardService {

    static final int MAX_TAGS = 8;
    static final int MAX_TAG_CHARS = 64;

    private final KnowledgeCardMapper mapper;
    private final ObjectMapper objectMapper;

    public KnowledgeCardService(KnowledgeCardMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<KnowledgeCardResponse> list(Long userId) {
        return mapper.listByUser(userId, 500, 0).stream()
                .map(row -> KnowledgeCardResponse.from(row, parseTags(objectMapper, row.getTagsJson())))
                .toList();
    }

    /**
     * 人工编辑卡片（标题/摘要/标签）。蒸馏来源（sourceLogId）不可改：
     * 卡片与工作记录的追溯关系是事实，编辑只修正表达，不重写来源。
     */
    @Transactional
    public KnowledgeCardResponse update(Long userId, Long id, UpdateKnowledgeCardRequest request) {
        requireOwned(userId, id);
        String title = requireText(request.title(), 255, "title");
        String summary = requireText(request.summary(), 2000, "summary");
        List<String> tags = request.tags() == null ? List.of() : request.tags();
        mapper.updateContent(id, userId, title, summary,
                serializeTags(objectMapper, tags), Instant.now());
        KnowledgeCardRow row = requireOwned(userId, id);
        return KnowledgeCardResponse.from(row, parseTags(objectMapper, row.getTagsJson()));
    }

    private KnowledgeCardRow requireOwned(Long userId, Long id) {
        KnowledgeCardRow row = mapper.findOwned(userId, id);
        if (row == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "知识卡片不存在");
        }
        return row;
    }

    private static String requireText(String raw, int maxChars, String field) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw invalid(field + " 不能为空");
        }
        if (value.length() > maxChars) {
            throw invalid(field + " 长度不得超过 " + maxChars + " 字");
        }
        return value;
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, message);
    }

    /** tags_json 反序列化为列表；空或解析失败都返回空列表，不猜测补值。 */
    static List<String> parseTags(ObjectMapper objectMapper, String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isArray()) {
                return List.of();
            }
            List<String> tags = new ArrayList<>();
            for (JsonNode item : node) {
                String value = item.asText();
                if (value != null && !value.isBlank()) {
                    tags.add(value.length() > MAX_TAG_CHARS ? value.substring(0, MAX_TAG_CHARS) : value);
                }
            }
            return List.copyOf(tags);
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    /** 标签序列化为 JSON 数组字符串；截断并限长，确保不超列宽。 */
    static String serializeTags(ObjectMapper objectMapper, List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "[]";
        }
        List<String> cleaned = tags.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(t -> t.length() > MAX_TAG_CHARS ? t.substring(0, MAX_TAG_CHARS) : t)
                .distinct()
                .limit(MAX_TAGS)
                .toList();
        try {
            return objectMapper.writeValueAsString(cleaned);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("知识卡片标签序列化失败", exception);
        }
    }
}
