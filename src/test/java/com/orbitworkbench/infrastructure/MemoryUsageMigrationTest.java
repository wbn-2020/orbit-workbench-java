package com.orbitworkbench.infrastructure;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** V46 迁移文本守门：用量列与近期关注表的形状。 */
class MemoryUsageMigrationTest {

    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V46__memory_usage_and_focus.sql");

    @Test
    void migrationAddsUsageColumnsWithHonestDefault() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("ADD COLUMN injection_count INT NOT NULL DEFAULT 0"));
        assertTrue(sql.contains("ADD COLUMN last_injected_at DATETIME(6) NULL"));
    }

    @Test
    void migrationCreatesSingleRowFocusNote() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("CREATE TABLE user_focus_note"));
        // 每用户至多一条：唯一键是「保存即覆盖」的前提
        assertTrue(sql.contains("UNIQUE KEY uk_user_focus_note_user (user_id)"));
        assertTrue(sql.contains("expires_at DATETIME(6) NULL"));
        assertTrue(sql.contains("fk_user_focus_note_user FOREIGN KEY (user_id) REFERENCES app_user(id)"));
    }
}
