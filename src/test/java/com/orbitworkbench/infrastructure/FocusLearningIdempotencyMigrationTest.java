package com.orbitworkbench.infrastructure;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FocusLearningIdempotencyMigrationTest {

    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration",
            "V36__focus_learning_idempotency.sql");

    @Test
    void migrationAddsUserScopedIdempotencyKeys() throws Exception {
        String sql = Files.readString(MIGRATION);
        assertTrue(sql.contains("learning_goal"));
        assertTrue(sql.contains("focus_session"));
        assertTrue(sql.contains("idempotency_key"));
        assertTrue(sql.contains("uk_learning_goal_user_idempotency"));
        assertTrue(sql.contains("uk_focus_session_user_idempotency"));
    }
}
