package com.orbitworkbench.infrastructure;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RunEventSequenceMigrationTest {

    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration",
            "V6__agent_run_event_sequence.sql");

    @Test
    void migrationAddsAndBackfillsAgentRunEventSequence() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("ADD COLUMN event_sequence BIGINT NOT NULL DEFAULT 0"));
        assertTrue(sql.contains("MAX(sequence) AS max_sequence"));
        assertTrue(sql.contains("SET ar.event_sequence = COALESCE(existing_events.max_sequence, 0)"));
    }
}
