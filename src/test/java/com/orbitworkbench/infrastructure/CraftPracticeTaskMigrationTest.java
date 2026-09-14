package com.orbitworkbench.infrastructure;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** V49 迁移文本守门：任务来源扩展为 CRAFT，且不引入其它结构改动。 */
class CraftPracticeTaskMigrationTest {

    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V49__craft_practice_task.sql");

    @Test
    void migrationExtendsStudyTaskSourceWithCraft() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("DROP CONSTRAINT chk_study_task_source"));
        assertTrue(sql.contains("'MANUAL', 'REPORT', 'WORKBENCH', 'CRAFT'"));
        // 只扩枚举，不新增表或列
        assertTrue(!sql.contains("CREATE TABLE"));
        assertTrue(!sql.contains("ADD COLUMN"));
    }
}
