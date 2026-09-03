package com.orbitworkbench.search.api;

import java.util.List;

/** 全局搜索传输对象。 */
public final class SearchDtos {

    private SearchDtos() {
    }

    /**
     * 单条命中。
     *
     * @param type    所属域（PROJECT/KNOWLEDGE/INTERVIEW/REPORT/APPLICATION/PROJECT_FACT/INTERVIEWER/STUDY_TASK）
     * @param id      命中行主键（用于前端 key）
     * @param title   展示标题
     * @param snippet 命中摘要（可能为 null，表示该域无正文可摘）
     * @param sub     次级标签（可能为 null，如“V3”“第 2 题”“阶段”）
     * @param route   前端跳转路由
     */
    public record SearchHit(
            String type,
            Long id,
            String title,
            String snippet,
            String sub,
            String route) {
    }

    public record SearchGroup(String type, String label, int total, List<SearchHit> items) {
    }

    public record SearchResponse(String query, int total, List<SearchGroup> groups) {
    }
}
