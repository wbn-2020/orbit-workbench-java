package com.orbitworkbench.infrastructure;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProjectMaterialsMigrationTest {

    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V22__project_materials.sql");

    @Test
    void migrationCreatesProjectVersionsAndFileInventory() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS project ("));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS project_version ("));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS project_file ("));
        assertTrue(sql.contains("UNIQUE KEY uk_project_version_number"));
        assertTrue(sql.contains("UNIQUE KEY uk_project_file_version_path"));
        assertTrue(sql.contains("relative_path_hash CHAR(64) NOT NULL"));
    }
}
