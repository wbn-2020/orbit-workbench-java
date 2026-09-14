package com.orbitworkbench.infrastructure;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** V50 迁移文本守门：只给 craft_note 加练习计数字段，不碰其它表。 */
class CraftPracticeWritebackMigrationTest {

    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V50__craft_practice_writeback.sql");

    @Test
    void migrationAddsPracticeColumnsOnlyToCraftNote() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("ALTER TABLE craft_note"));
        assertTrue(sql.contains("practice_count INT NOT NULL DEFAULT 0"));
        assertTrue(sql.contains("last_practiced_at DATETIME(6) NULL"));
        assertTrue(sql.contains("CHECK (practice_count >= 0)"));
        // 回写只动 craft_note：study_task 与历史表不碰，也没有新表
        assertTrue(!sql.contains("study_task"));
        assertTrue(!sql.contains("CREATE TABLE"));
        assertTrue(!sql.contains("DROP"));
    }
}
