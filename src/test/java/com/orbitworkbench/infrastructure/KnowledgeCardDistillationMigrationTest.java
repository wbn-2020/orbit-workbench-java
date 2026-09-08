package com.orbitworkbench.infrastructure;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class KnowledgeCardDistillationMigrationTest {

    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration",
            "V35__knowledge_card_source_idempotency.sql");

    @Test
    void migrationAddsPerUserSourceLogUniqueKey() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("ALTER TABLE knowledge_card"));
        assertTrue(sql.contains(
                "ADD UNIQUE KEY uk_knowledge_card_user_source_log (user_id, source_log_id)"));
    }
}
