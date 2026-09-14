package com.orbitworkbench.craft.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.craft.api.CraftDtos.CraftEffectResponse;
import com.orbitworkbench.craft.domain.CraftNoteRecord;
import com.orbitworkbench.craft.infrastructure.mapper.CraftNoteMapper;
import com.orbitworkbench.interview.domain.ReportCenterRow;
import com.orbitworkbench.interview.infrastructure.mapper.InterviewReportMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V54：练熟证据链——「练熟这条套路后，关联维度的面试分数变了没有」。
 *
 * <p>纯派生，零迁移、无 AI、无落库：套路文本经 {@link CraftLexicon} 关联到维度
 * （与 V51 推荐同一张表，两方向判定不漂移），再拿练熟时刻把历史报告切成前后两组，
 * 对比该维度平均分。V50 只回答「练没练」，V54 回答「练了有没有用」——
 * 且如实声明这是<b>相关不是因果</b>：样本少、维度多、状态有噪声，文案永不写成「涨分靠它」。
 */
@Service
public class CraftEffectService {

    /** 判定「有变化」的最小差值：小于 5 分视为波动内（11 维评分的人工噪声级别）。 */
    private static final int MIN_DELTA = 5;
    /** 对比用最近报告数；再多则前组样本被稀释、意义不大。 */
    private static final int REPORT_SCAN_LIMIT = 30;

    private final CraftNoteMapper craftMapper;
    private final InterviewReportMapper reportMapper;
    private final ObjectMapper objectMapper;

    public CraftEffectService(CraftNoteMapper craftMapper,
                              InterviewReportMapper reportMapper,
                              ObjectMapper objectMapper) {
        this.craftMapper = craftMapper;
        this.reportMapper = reportMapper;
        this.objectMapper = objectMapper;
    }

    /** 所有已确认且有真实练熟时间戳的套路的证据卡；未练熟的套路无条目（响应键缺席，前端不显示）。 */
    @Transactional(readOnly = true)
    public List<CraftEffectResponse> effects(Long userId) {
        List<ReportCenterRow> reports = readyReports(userId);
        List<CraftEffectResponse> out = new ArrayList<>();
        for (CraftNoteRecord craft : craftMapper.listByUser(userId)) {
            // V50 的写路径保证只有 CONFIRMED 会被计练熟；读路径再挡一次，防历史脏数据
            if (craft.getConfirmationStatus()
                    != com.orbitworkbench.craft.domain.CraftStatus.CONFIRMED
                    || craft.getPracticeCount() < 1 || craft.getLastPracticedAt() == null) {
                continue;
            }
            CraftEffectResponse effect = evaluate(craft, reports);
            if (effect != null) {
                out.add(effect);
            }
        }
        return List.copyOf(out);
    }

    private List<ReportCenterRow> readyReports(Long userId) {
        return reportMapper.listByUser(userId, null, null, null, null, null, REPORT_SCAN_LIMIT, 0)
                .stream()
                .filter(row -> "REPORT_READY".equals(row.getReportStatus()))
                .filter(row -> row.getGeneratedAt() != null)
                .toList();
    }

    /** 主关联维度（命中数最高）的前后对比；该维度没有任何带分报告时返回 null。 */
    private CraftEffectResponse evaluate(CraftNoteRecord craft, List<ReportCenterRow> reports) {
        List<String> dimensions = CraftLexicon.dimensionsFor(craft);
        if (dimensions.isEmpty()) {
            return null;
        }
        String dimension = dimensions.get(0);
        Instant cut = craft.getLastPracticedAt();
        List<Integer> before = new ArrayList<>();
        List<Integer> after = new ArrayList<>();
        for (ReportCenterRow row : reports) {
            Integer score = CraftLexicon.parseScores(objectMapper, row.getDimensionScoresJson())
                    .get(dimension);
            if (score == null) {
                continue;
            }
            if (row.getGeneratedAt().isBefore(cut)) {
                before.add(score);
            } else {
                after.add(score);
            }
        }
        if (before.isEmpty() && after.isEmpty()) {
            return null;
        }
        Integer beforeAvg = average(before);
        Integer afterAvg = average(after);
        String status;
        if (beforeAvg == null || afterAvg == null) {
            status = "INSUFFICIENT";
        } else if (afterAvg - beforeAvg >= MIN_DELTA) {
            status = "IMPROVED";
        } else if (beforeAvg - afterAvg >= MIN_DELTA) {
            status = "DECLINED";
        } else {
            status = "FLAT";
        }
        return new CraftEffectResponse(craft.getId(), dimension, before.size(), beforeAvg,
                after.size(), afterAvg, status);
    }

    private Integer average(List<Integer> values) {
        return values.isEmpty() ? null
                : (int) Math.round(values.stream().mapToInt(Integer::intValue).average().orElse(0));
    }
}
