package com.orbitworkbench.infrastructure;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PracticeReviewDateSourceMigrationTest {

    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration",
            "V33__practice_review_date_source.sql");

    @Test
    void migrationPreservesUnknownHistoryAndRestrictsKnownSources() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("ADD COLUMN review_date_source VARCHAR(16) NULL"));
        assertTrue(sql.contains("review_date_source IS NULL"));
        assertTrue(sql.contains("review_date_source IN ('MANUAL', 'RULE')"));
        assertTrue(sql.contains("不回填"));
    }
}
