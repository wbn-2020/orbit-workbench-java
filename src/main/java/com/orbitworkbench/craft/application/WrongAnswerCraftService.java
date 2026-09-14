package com.orbitworkbench.craft.application;

import com.orbitworkbench.craft.api.CraftDtos.CraftForWrongAnswerResponse;
import com.orbitworkbench.craft.domain.CraftNoteRecord;
import com.orbitworkbench.craft.infrastructure.mapper.CraftNoteMapper;
import com.orbitworkbench.practice.domain.PracticeItemRow;
import com.orbitworkbench.practice.infrastructure.mapper.PracticeItemMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V57：错题本 → 本事库（EvoFlow 二轮对照⑤）——「这类错该用哪条套路治」。
 *
 * <p>与 V51 完全同一纪律与机器：纯关键词派生、零 AI、零落库、零迁移。
 * 错题文本（topic + question + 参考答案摘要）先经 {@link CraftLexicon} 归到它命中的
 * 能力维度（与弱项推荐同一张表），再找治该维度命中最多的已确认套路。
 * 匹配不上就如实返回空——错题五花八门，本事库没有对应套路是常态，不硬凑。
 */
@Service
public class WrongAnswerCraftService {

    /** 参考答案只取前若干字参与匹配：太长会把无关词带进命中统计。 */
    private static final int ANSWER_CLIP_CHARS = 200;
    /** 一道错题最多挂几条候选套路（命中维度去重后通常 1–2 条）。 */
    private static final int MAX_CANDIDATES = 3;

    private final PracticeItemMapper itemMapper;
    private final CraftNoteMapper craftMapper;

    public WrongAnswerCraftService(PracticeItemMapper itemMapper, CraftNoteMapper craftMapper) {
        this.itemMapper = itemMapper;
        this.craftMapper = craftMapper;
    }

    @Transactional(readOnly = true)
    public CraftForWrongAnswerResponse forItem(Long userId, Long itemId) {
        PracticeItemRow item = itemMapper.findOwned(userId, itemId);
        if (item == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "错题条目不存在");
        }
        String haystack = (nullToEmpty(item.getTopic()) + " " + nullToEmpty(item.getQuestion()) + " "
                + clip(nullToEmpty(item.getReferenceAnswer()), ANSWER_CLIP_CHARS)).trim();
        List<CraftNoteRecord> pool = craftMapper.listConfirmedForPrompt(userId);
        List<CraftForWrongAnswerResponse.CraftCandidate> candidates = new ArrayList<>();
        Set<Long> used = new java.util.HashSet<>();
        // 维度命中数降序：错题文本最像哪个维度，先给那个维度的最佳套路
        List<Map.Entry<String, Integer>> dimensions = scoredDimensions(haystack);
        for (Map.Entry<String, Integer> dimension : dimensions) {
            if (candidates.size() >= MAX_CANDIDATES) {
                break;
            }
            CraftNoteRecord best = bestCraft(pool, used, dimension.getKey());
            if (best == null) {
                continue;
            }
            used.add(best.getId());
            candidates.add(new CraftForWrongAnswerResponse.CraftCandidate(
                    best.getId(), best.getTitle(), best.getCategory(), best.getWhenToUse(),
                    dimension.getKey(), best.getPracticeCount() >= 1));
        }
        String note = candidates.isEmpty()
                ? (dimensions.isEmpty()
                        ? "这条错题的文本没命中任何能力维度关键词——本事库帮不上，先按错题本自己的节奏练。"
                        : "错题指向「" + dimensions.get(0).getKey() + "」，但本事库里还没有对得上的套路——"
                                + "可以在本事库点「提炼套路」长一条，或手动录入。")
                : "按错题文本命中的能力维度匹配；相关不等于因果，练完记得回错题本验证掌握度。";
        return new CraftForWrongAnswerResponse(candidates, note);
    }

    /** 错题文本对各维度的命中数（>0 才入列），按命中数降序、同分按维度名定序。 */
    private List<Map.Entry<String, Integer>> scoredDimensions(String haystack) {
        List<Map.Entry<String, Integer>> scored = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : CraftLexicon.DIMENSION_KEYWORDS.entrySet()) {
            int hits = CraftLexicon.hits(entry.getValue(), haystack);
            if (hits > 0) {
                scored.add(Map.entry(entry.getKey(), hits));
            }
        }
        scored.sort(java.util.Comparator
                .<Map.Entry<String, Integer>, Integer>comparing(Map.Entry::getValue).reversed()
                .thenComparing(Map.Entry::getKey));
        return scored;
    }

    /** 该维度命中最多的未使用套路（已练熟的仍可推荐——错题语境下「再练一遍」合理）。 */
    private CraftNoteRecord bestCraft(List<CraftNoteRecord> pool, Set<Long> used, String dimension) {
        Set<String> keywords = CraftLexicon.DIMENSION_KEYWORDS.get(dimension);
        if (keywords == null || keywords.isEmpty()) {
            return null;
        }
        CraftNoteRecord best = null;
        int bestHits = 0;
        for (CraftNoteRecord craft : pool) {
            if (used.contains(craft.getId())) {
                continue;
            }
            int hits = CraftLexicon.hits(keywords, craft);
            if (hits > bestHits) {
                best = craft;
                bestHits = hits;
            }
        }
        return best;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String clip(String value, int maxChars) {
        return value.length() <= maxChars ? value : value.substring(0, maxChars);
    }
}
