package com.orbitworkbench.search.infrastructure.mapper;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SearchMapperContractTest {

    private static final Path MAPPER_XML = Path.of(
            "src", "main", "resources", "sqlmapper", "search", "SearchMapper.xml");

    @Test
    void projectFactSearchIsOwnedNonArchivedAndReturnsDeepLinkColumns() throws IOException {
        String sql = statement("searchProjectFacts");

        assertAll(
                () -> assertTrue(sql.contains("FROM project_fact f")),
                () -> assertTrue(sql.contains("f.user_id = #{userId}")),
                () -> assertTrue(sql.contains("p.user_id = #{userId}")),
                () -> assertTrue(sql.contains("f.confirmation_status &lt;&gt; 'ARCHIVED'")),
                () -> assertTrue(sql.contains("p.id AS projectId")),
                () -> assertTrue(sql.contains("f.project_version_id AS projectVersionId")),
                () -> assertTrue(sql.contains("f.title LIKE CONCAT('%', #{keyword}, '%')")),
                () -> assertTrue(sql.contains("LIMIT #{limit}")));
    }

    @Test
    void interviewerSearchUsesCurrentVisibleActiveScope() throws IOException {
        String sql = statement("searchInterviewers");

        assertAll(
                () -> assertTrue(sql.contains("FROM interviewer_profile ip")),
                () -> assertTrue(sql.contains("(ip.user_id IS NULL OR ip.user_id = #{userId})")),
                () -> assertTrue(sql.contains("ip.archived = 0")),
                () -> assertTrue(sql.contains("ip.name LIKE CONCAT('%', #{keyword}, '%')")),
                () -> assertTrue(sql.contains("LIMIT #{limit}")));
    }

    @Test
    void studyTaskSearchIsOwnedAndSearchesTitleAndTopic() throws IOException {
        String sql = statement("searchStudyTasks");

        assertAll(
                () -> assertTrue(sql.contains("FROM study_task t")),
                () -> assertTrue(sql.contains("t.user_id = #{userId}")),
                () -> assertTrue(sql.contains("t.title LIKE CONCAT('%', #{keyword}, '%')")),
                () -> assertTrue(sql.contains("t.topic LIKE CONCAT('%', #{keyword}, '%')")),
                () -> assertTrue(sql.contains("LIMIT #{limit}")));
    }

    private String statement(String id) throws IOException {
        String xml = Files.readString(MAPPER_XML);
        String startTag = "<select id=\"" + id + "\"";
        int start = xml.indexOf(startTag);
        int end = start < 0 ? -1 : xml.indexOf("</select>", start);
        assertTrue(start >= 0 && end > start, "Missing select statement: " + id);
        return xml.substring(start, end).replaceAll("\\s+", " ");
    }
}
