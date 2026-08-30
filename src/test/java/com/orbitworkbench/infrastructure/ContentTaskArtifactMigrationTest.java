package com.orbitworkbench.infrastructure;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ContentTaskArtifactMigrationTest {

    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration",
            "V20__content_task_and_artifact_constraints.sql");

    @Test
    void migrationAllowsContentTasksAndArtifacts() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("'TECH_LEARNING', 'DATA_ANALYSIS', 'CONTENT_CREATION'"));
        assertTrue(sql.contains("'CONTENT_DRAFT', 'CONTENT_REVIEW'"));
        assertTrue(sql.contains("DROP CHECK chk_task_module_type"));
        assertTrue(sql.contains("DROP CHECK chk_task_expected_artifact_type"));
        assertTrue(sql.contains("DROP CHECK chk_artifact_type"));
    }
}
