package com.orbitworkbench.infrastructure;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class JobProfileMigrationTest {

    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V21__job_profile.sql");

    @Test
    void migrationCreatesUserScopedJobProfile() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("CREATE TABLE job_profile"));
        assertTrue(sql.contains("UNIQUE KEY uk_job_profile_user (user_id)"));
        assertTrue(sql.contains("FOREIGN KEY (user_id) REFERENCES app_user(id)"));
        assertTrue(sql.contains("target_experience_band IN"));
    }
}
