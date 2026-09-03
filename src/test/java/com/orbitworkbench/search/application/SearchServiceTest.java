package com.orbitworkbench.search.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.orbitworkbench.search.api.SearchDtos.SearchGroup;
import com.orbitworkbench.search.api.SearchDtos.SearchHit;
import com.orbitworkbench.search.api.SearchDtos.SearchResponse;
import com.orbitworkbench.search.domain.SearchHitRow;
import com.orbitworkbench.search.infrastructure.mapper.SearchMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SearchServiceTest {

    @Mock
    private SearchMapper searchMapper;

    private SearchService service;

    @BeforeEach
    void setUp() {
        service = new SearchService(searchMapper);
    }

    private SearchHitRow row(Long id, Long projectId, Long sessionId, String title, String body, String sub) {
        SearchHitRow row = new SearchHitRow();
        row.setId(id);
        row.setProjectId(projectId);
        row.setSessionId(sessionId);
        row.setTitle(title);
        row.setBody(body);
        row.setSub(sub);
        return row;
    }

    @Test
    void shortQueryReturnsEmptyWithoutHittingDatabase() {
        SearchResponse response = service.search(1L, "a");
        assertEquals(0, response.total());
        assertTrue(response.groups().isEmpty());
        verifyNoInteractions(searchMapper);
    }

    @Test
    void overlongQueryIsRejectedBeforeHittingDatabase() {
        org.junit.jupiter.api.Assertions.assertThrows(
                com.orbitworkbench.shared.api.ApiException.class,
                () -> service.search(1L, "a".repeat(SearchService.MAX_QUERY_LENGTH + 1)));
        verifyNoInteractions(searchMapper);
    }

    @Test
    void blankQueryReturnsEmpty() {
        SearchResponse response = service.search(1L, "   ");
        assertEquals(0, response.total());
        verifyNoInteractions(searchMapper);
    }

    @Test
    void aggregatesDomainsBuildsRoutesAndDropsEmptyGroups() {
        when(searchMapper.searchProjects(anyLong(), anyString(), anyInt()))
                .thenReturn(List.of(row(10L, 10L, null, "秒杀系统", null, null)));
        when(searchMapper.searchKnowledge(anyLong(), anyString(), anyInt()))
                .thenReturn(List.of(row(20L, 10L, null, "docs/秒杀.md", "这里是 秒杀 库存扣减的说明", null)));
        when(searchMapper.searchInterviewSessions(anyLong(), anyString(), anyInt()))
                .thenReturn(List.of(row(30L, null, 30L, "秒杀面试", "Java 后端", "面试官A")));
        when(searchMapper.searchReports(anyLong(), anyString(), anyInt()))
                .thenReturn(List.of(row(40L, null, 30L, "秒杀面试", "总分 76 · PASS", null)));
        when(searchMapper.searchJobApplications(anyLong(), anyString(), anyInt()))
                .thenReturn(List.of(row(50L, null, null, "美团 · 后端", "负责 秒杀 链路", "APPLIED")));

        SearchResponse response = service.search(1L, "秒杀");

        assertEquals(5, response.total());
        assertEquals(5, response.groups().size());
        SearchGroup project = response.groups().get(0);
        assertEquals("PROJECT", project.type());
        assertEquals("/projects/10", project.items().get(0).route());
        SearchGroup report = response.groups().stream()
                .filter(g -> g.type().equals("REPORT")).findFirst().orElseThrow();
        assertEquals("/interviews/30/report", report.items().get(0).route());
        SearchGroup application = response.groups().stream()
                .filter(g -> g.type().equals("APPLICATION")).findFirst().orElseThrow();
        assertEquals("/applications?focus=50", application.items().get(0).route());
        assertTrue(response.groups().stream().noneMatch(g -> g.items().isEmpty()));
    }

    @Test
    void projectVersionAndKnowledgeHitsKeepVersionInDeepLinks() {
        SearchHitRow version = row(23L, 10L, null, "项目版本", null, "V2");
        version.setProjectVersionId(23L);
        when(searchMapper.searchProjectVersions(anyLong(), anyString(), anyInt()))
                .thenReturn(List.of(version));
        SearchHitRow knowledge = row(24L, 10L, null, "docs/秒杀.md", "秒杀版本内容", null);
        knowledge.setProjectVersionId(23L);
        when(searchMapper.searchKnowledge(anyLong(), anyString(), anyInt()))
                .thenReturn(List.of(knowledge));

        SearchResponse response = service.search(1L, "秒杀");

        assertEquals("/projects/10?version=23",
                response.groups().stream()
                        .filter(group -> group.type().equals("PROJECT"))
                        .findFirst().orElseThrow().items().get(0).route());
        assertEquals("/projects/10?version=23",
                response.groups().stream()
                        .filter(group -> group.type().equals("KNOWLEDGE"))
                        .findFirst().orElseThrow().items().get(0).route());
    }

    @Test
    void searchesProjectFactsWithVersionAndFactDeepLink() {
        SearchHitRow fact = row(61L, 12L, null, "缓存策略", "秒杀项目使用缓存", "V3 · STRUCTURE");
        fact.setProjectVersionId(120L);
        when(searchMapper.searchProjectFacts(anyLong(), anyString(), anyInt())).thenReturn(List.of(fact));

        SearchResponse response = service.search(1L, "秒杀");

        SearchHit hit = response.groups().stream()
                .filter(g -> g.type().equals("PROJECT_FACT")).findFirst().orElseThrow()
                .items().get(0);
        assertEquals("/projects/12?version=120&fact=61", hit.route());
        assertTrue(hit.snippet().contains("秒杀"));
    }

    @Test
    void searchesOnlyVisibleActiveInterviewersAndRoutesToInterviewerPage() {
        when(searchMapper.searchInterviewers(anyLong(), anyString(), anyInt()))
                .thenReturn(List.of(row(62L, null, null, "项目深挖面试官", "基于项目事实追问", "内置")));

        SearchResponse response = service.search(1L, "项目");

        SearchHit hit = response.groups().stream()
                .filter(g -> g.type().equals("INTERVIEWER")).findFirst().orElseThrow()
                .items().get(0);
        assertEquals("/interviewers", hit.route());
    }

    @Test
    void searchesStudyTasksAndAddsFocusRoute() {
        when(searchMapper.searchStudyTasks(anyLong(), anyString(), anyInt()))
                .thenReturn(List.of(row(63L, null, null, "复盘缓存一致性", "秒杀", "PLANNED · REPORT")));

        SearchResponse response = service.search(1L, "秒杀");

        SearchHit hit = response.groups().stream()
                .filter(g -> g.type().equals("STUDY_TASK")).findFirst().orElseThrow()
                .items().get(0);
        assertEquals("/study-plan?focus=63", hit.route());
        assertTrue(hit.snippet().contains("秒杀"));
    }

    @Test
    void totalEqualsSumOfReturnedGroupTotalsIncludingExtendedDomains() {
        when(searchMapper.searchProjectFacts(anyLong(), anyString(), anyInt()))
                .thenReturn(List.of(row(64L, 13L, null, "事实", "关键词", null)));
        when(searchMapper.searchInterviewers(anyLong(), anyString(), anyInt()))
                .thenReturn(List.of(row(65L, null, null, "面试官", "关键词", null)));
        when(searchMapper.searchStudyTasks(anyLong(), anyString(), anyInt()))
                .thenReturn(List.of(row(66L, null, null, "复习任务", "关键词", null)));

        SearchResponse response = service.search(1L, "关键词");

        assertEquals(response.groups().stream().mapToInt(SearchGroup::total).sum(), response.total());
    }

    @Test
    void knowledgeFallsBackToLikeWhenFullTextMisses() {
        when(searchMapper.searchKnowledge(anyLong(), anyString(), anyInt())).thenReturn(List.of());
        when(searchMapper.searchKnowledgeLike(anyLong(), anyString(), anyInt()))
                .thenReturn(List.of(row(21L, 11L, null, "a.md", "秒杀 内容", null)));

        SearchResponse response = service.search(1L, "秒杀");

        SearchGroup knowledge = response.groups().stream()
                .filter(g -> g.type().equals("KNOWLEDGE")).findFirst().orElseThrow();
        assertEquals(1, knowledge.total());
        assertEquals("/projects/11", knowledge.items().get(0).route());
        verify(searchMapper).searchKnowledgeLike(anyLong(), anyString(), anyInt());
    }

    @Test
    void knowledgeSkipsLikeWhenFullTextHits() {
        when(searchMapper.searchKnowledge(anyLong(), anyString(), anyInt()))
                .thenReturn(List.of(row(22L, 12L, null, "b.md", "秒杀 正文", null)));

        service.search(1L, "秒杀");

        verify(searchMapper, never()).searchKnowledgeLike(anyLong(), anyString(), anyInt());
    }

    @Test
    void likeKeywordIsEscapedBeforeReachingMapper() {
        service.search(1L, "50%");
        verify(searchMapper).searchProjects(anyLong(), org.mockito.ArgumentMatchers.eq("50\\%"), anyInt());
    }

    @Test
    void snippetCentersWindowOnKeyword() {
        String body = "前缀".repeat(20) + "秒杀库存扣减" + "后缀".repeat(20);
        String snippet = SearchService.snippet(body, "秒杀");
        assertTrue(snippet.contains("秒杀库存扣减"));
        assertTrue(snippet.startsWith("…"));
    }

    @Test
    void snippetReturnsNullForBlankBody() {
        assertNull(SearchService.snippet("   ", "秒杀"));
        assertNull(SearchService.snippet(null, "秒杀"));
    }

    @Test
    void escapeLikeEscapesWildcards() {
        assertEquals("a\\%b\\_c\\\\d", SearchService.escapeLike("a%b_c\\d"));
    }

    @Test
    void matchedSnippetIsExposedOnHit() {
        when(searchMapper.searchInterviewTurns(anyLong(), anyString(), anyInt()))
                .thenReturn(List.of(row(60L, null, 31L, "面试X", "问：如何防超卖 答：用 秒杀 预扣", "第1题")));
        when(searchMapper.searchInterviewSessions(anyLong(), anyString(), anyInt())).thenReturn(List.of());

        SearchResponse response = service.search(1L, "秒杀");

        SearchHit hit = response.groups().stream()
                .filter(g -> g.type().equals("INTERVIEW")).findFirst().orElseThrow()
                .items().get(0);
        assertEquals("第1题", hit.sub());
        assertTrue(hit.snippet().contains("秒杀"));
        assertEquals("/interviews/31", hit.route());
    }
}
