package com.orbitworkbench.search.application;

import com.orbitworkbench.search.api.SearchDtos.SearchGroup;
import com.orbitworkbench.search.api.SearchDtos.SearchHit;
import com.orbitworkbench.search.api.SearchDtos.SearchResponse;
import com.orbitworkbench.search.domain.SearchHitRow;
import com.orbitworkbench.search.infrastructure.mapper.SearchMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 全局搜索：按用户隔离聚合项目资料、知识块、面试记录、报告、投递记录、
 * 项目事实、面试官档案与复习任务八个域。
 *
 * <p>全部为只读检索；知识块优先 FULLTEXT ngram，未命中再用 LIKE 兜底，其余域用 LIKE。
 * 查询过短（去空白后少于 {@value #MIN_QUERY_LENGTH} 字）直接返回空结果，不打数据库。
 * 每条命中带可跳转路由；摘要在 Java 侧按原始查询截取窗口，避免把整段正文回给前端。
 * SQL 侧关键词经 {@link #escapeLike} 转义，防止 % / _ 被当作通配符。</p>
 */
@Service
public class SearchService {

    static final int MIN_QUERY_LENGTH = 2;
    static final int MAX_QUERY_LENGTH = 100;
    static final int PER_DOMAIN_LIMIT = 6;
    private static final int SNIPPET_RADIUS_BEFORE = 24;
    private static final int SNIPPET_RADIUS_AFTER = 72;
    private static final int SNIPPET_MAX = 120;

    private final SearchMapper searchMapper;

    public SearchService(SearchMapper searchMapper) {
        this.searchMapper = searchMapper;
    }

    @Transactional(readOnly = true)
    public SearchResponse search(Long userId, String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.length() < MIN_QUERY_LENGTH) {
            return new SearchResponse(query, 0, List.of());
        }
        if (query.length() > MAX_QUERY_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "搜索关键词不能超过" + MAX_QUERY_LENGTH + "个字符");
        }
        String keyword = escapeLike(query);

        List<SearchGroup> groups = new ArrayList<>();
        groups.add(projectGroup(userId, keyword, query));
        groups.add(group("KNOWLEDGE", "知识块", knowledgeHits(userId, keyword, query)));
        groups.add(interviewGroup(userId, keyword, query));
        groups.add(group("REPORT", "面试报告",
                searchMapper.searchReports(userId, keyword, PER_DOMAIN_LIMIT).stream()
                        .map(row -> hit("REPORT", row.getId(), row.getTitle(),
                                snippet(row.getBody(), query), row.getSub(),
                                "/interviews/" + row.getSessionId() + "/report"))
                        .toList()));
        groups.add(group("APPLICATION", "投递记录",
                searchMapper.searchJobApplications(userId, keyword, PER_DOMAIN_LIMIT).stream()
                        .map(row -> hit("APPLICATION", row.getId(), row.getTitle(),
                                snippet(row.getBody(), query), row.getSub(),
                                "/applications?focus=" + row.getId()))
                        .toList()));
        groups.add(group("PROJECT_FACT", "项目事实",
                searchMapper.searchProjectFacts(userId, keyword, PER_DOMAIN_LIMIT).stream()
                        .map(row -> hit("PROJECT_FACT", row.getId(), row.getTitle(),
                                snippet(row.getBody(), query), row.getSub(),
                                "/projects/" + row.getProjectId()
                                        + "?version=" + row.getProjectVersionId()
                                        + "&fact=" + row.getId()))
                        .toList()));
        groups.add(group("INTERVIEWER", "面试官",
                searchMapper.searchInterviewers(userId, keyword, PER_DOMAIN_LIMIT).stream()
                        .map(row -> hit("INTERVIEWER", row.getId(), row.getTitle(),
                                snippet(row.getBody(), query), row.getSub(), "/interviewers"))
                        .toList()));
        groups.add(group("STUDY_TASK", "复习任务",
                searchMapper.searchStudyTasks(userId, keyword, PER_DOMAIN_LIMIT).stream()
                        .map(row -> hit("STUDY_TASK", row.getId(), row.getTitle(),
                                snippet(row.getBody(), query), row.getSub(),
                                "/study-plan?focus=" + row.getId()))
                        .toList()));
        groups.removeIf(g -> g.items().isEmpty());

        int total = groups.stream().mapToInt(SearchGroup::total).sum();
        return new SearchResponse(query, total, groups);
    }

    private SearchGroup projectGroup(Long userId, String keyword, String query) {
        List<SearchHit> items = new ArrayList<>();
        searchMapper.searchProjects(userId, keyword, PER_DOMAIN_LIMIT).forEach(row ->
                items.add(hit("PROJECT", row.getId(), row.getTitle(), null, row.getSub(),
                        "/projects/" + row.getProjectId())));
        searchMapper.searchProjectVersions(userId, keyword, PER_DOMAIN_LIMIT).forEach(row ->
                items.add(hit("PROJECT", row.getId(), row.getTitle(), null, row.getSub(),
                        projectRoute(row))));
        return group("PROJECT", "项目资料", items.stream().limit(PER_DOMAIN_LIMIT).toList());
    }

    private SearchGroup interviewGroup(Long userId, String keyword, String query) {
        List<SearchHit> items = new ArrayList<>();
        searchMapper.searchInterviewSessions(userId, keyword, PER_DOMAIN_LIMIT).forEach(row ->
                items.add(hit("INTERVIEW", row.getId(), row.getTitle(),
                        snippet(row.getBody(), query), row.getSub(),
                        "/interviews/" + row.getSessionId())));
        searchMapper.searchInterviewTurns(userId, keyword, PER_DOMAIN_LIMIT).forEach(row ->
                items.add(hit("INTERVIEW", row.getId(), row.getTitle(),
                        snippet(row.getBody(), query), row.getSub(),
                        "/interviews/" + row.getSessionId())));
        return group("INTERVIEW", "面试记录", items.stream().limit(PER_DOMAIN_LIMIT).toList());
    }

    private List<SearchHit> knowledgeHits(Long userId, String keyword, String query) {
        List<SearchHitRow> rows = searchMapper.searchKnowledge(userId, keyword, PER_DOMAIN_LIMIT);
        if (rows.isEmpty()) {
            rows = searchMapper.searchKnowledgeLike(userId, keyword, PER_DOMAIN_LIMIT);
        }
        return rows.stream()
                .map(row -> hit("KNOWLEDGE", row.getId(), row.getTitle(),
                        snippet(row.getBody(), query), row.getSub(),
                        projectRoute(row)))
                .toList();
    }

    private SearchGroup group(String type, String label, List<SearchHit> items) {
        return new SearchGroup(type, label, items.size(), items);
    }

    private SearchHit hit(String type, Long id, String title, String snippet, String sub, String route) {
        return new SearchHit(type, id, title, snippet, sub, route);
    }

    private String projectRoute(SearchHitRow row) {
        String route = "/projects/" + row.getProjectId();
        return row.getProjectVersionId() == null
                ? route
                : route + "?version=" + row.getProjectVersionId();
    }

    /** 按关键词截取上下文窗口，命中不到就取开头一段。 */
    static String snippet(String body, String keyword) {
        if (body == null || body.isBlank()) {
            return null;
        }
        String text = body.replaceAll("\\s+", " ").trim();
        int idx = keyword == null || keyword.isEmpty()
                ? -1 : text.toLowerCase(Locale.ROOT).indexOf(keyword.toLowerCase(Locale.ROOT));
        int start = idx < 0 ? 0 : Math.max(0, idx - SNIPPET_RADIUS_BEFORE);
        int end = idx < 0
                ? Math.min(text.length(), SNIPPET_MAX)
                : Math.min(text.length(), idx + keyword.length() + SNIPPET_RADIUS_AFTER);
        String window = text.substring(start, end);
        StringBuilder result = new StringBuilder();
        if (start > 0) {
            result.append('…');
        }
        result.append(window);
        if (end < text.length()) {
            result.append('…');
        }
        return result.toString();
    }

    /** 转义 LIKE 通配符，避免用户输入 % / _ 变成模式匹配。 */
    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
