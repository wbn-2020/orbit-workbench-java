package com.orbitworkbench.infrastructure;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class InterviewKnowledgeBindingsMigrationTest {

    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration",
            "V37__interview_session_knowledge_bindings.sql");

    @Test
    void migrationAddsKnowledgeBindingsSnapshotColumn() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("ALTER TABLE interview_session"));
        assertTrue(sql.contains("ADD COLUMN knowledge_bindings_json MEDIUMTEXT NULL"));
    }
}
