package com.orbitworkbench.capability.infrastructure.mapper;

import com.orbitworkbench.capability.domain.CapabilityReportRow;
import com.orbitworkbench.capability.domain.RuleVersionCount;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 技能图谱的只读查询（`16` §8）。本模块没有任何写语句：能力值不落地，每次请求现算。
 */
public interface CapabilityMapper {

    /** 某规则版本下已就绪报告的维度分与总分，按生成时间升序（趋势与均分共用这一份样本）。 */
    List<CapabilityReportRow> readyReports(
            @Param("userId") Long userId,
            @Param("days") Integer days,
            @Param("ruleVersion") String ruleVersion);

    /** 已就绪报告按规则版本分组计数；{@code scoring_rule_version IS NULL} 单独成组。 */
    List<RuleVersionCount> readyVersionCounts(
            @Param("userId") Long userId,
            @Param("days") Integer days);

    /** 用户自己确认过的项目事实条数（`16` §6.3：只计数，不折算成分数）。 */
    long countConfirmedProjectFacts(@Param("userId") Long userId);
}
