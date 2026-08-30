package com.orbitworkbench.infrastructure;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class KnowledgeMigrationTest {

    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V26__knowledge_chunk_project_fact.sql");

    @Test
    void migrationCreatesChunkTableWithNgramFulltext() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("CREATE TABLE knowledge_chunk"));
        assertTrue(sql.contains("FOREIGN KEY (user_id) REFERENCES app_user(id)"));
        assertTrue(sql.contains("FULLTEXT KEY ft_knowledge_chunk_content (content) WITH PARSER ngram"));
        assertTrue(sql.contains("KEY idx_knowledge_chunk_version (project_version_id, chunk_no)"));
    }

    @Test
    void migrationCreatesTwoLayerProjectFacts() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("CREATE TABLE project_fact"));
        assertTrue(sql.contains("source IN ('AI_ANALYZED', 'USER_CONFIRMED')"));
        assertTrue(sql.contains("confirmation_status IN ('ANALYZED', 'CONFIRMED', 'ARCHIVED')"));
        assertTrue(sql.contains("fact_type IN ('BUSINESS', 'STRUCTURE', 'RISK', 'RESPONSIBILITY', 'TECH_STACK', 'OTHER')"));
        assertTrue(sql.contains("confidence BETWEEN 0 AND 100"));
    }
}
