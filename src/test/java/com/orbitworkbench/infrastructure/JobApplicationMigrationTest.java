package com.orbitworkbench.infrastructure;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class JobApplicationMigrationTest {

    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V25__job_application.sql");

    @Test
    void migrationCreatesUserScopedApplicationWithStages() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("CREATE TABLE job_application"));
        assertTrue(sql.contains("FOREIGN KEY (user_id) REFERENCES app_user(id)"));
        assertTrue(sql.contains("stage IN ('WATCHING', 'APPLIED', 'WRITTEN_TEST', 'INTERVIEWING', 'HR', 'OFFER', 'CLOSED')"));
        assertTrue(sql.contains("result IN ('PENDING', 'PASSED', 'REJECTED', 'WITHDRAWN')"));
    }

    @Test
    void migrationCreatesStageEventTrail() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("CREATE TABLE job_application_event"));
        assertTrue(sql.contains("FOREIGN KEY (application_id) REFERENCES job_application(id)"));
        assertTrue(sql.contains("event_type IN ('CREATED', 'STAGE_CHANGED', 'ARCHIVED', 'NOTE')"));
    }
}
