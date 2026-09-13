package com.orbitworkbench.infrastructure;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UserFactMigrationTest {

    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V41__user_fact_memory.sql");

    @Test
    void migrationCreatesUserFactWithConstrainedVocabulary() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("CREATE TABLE user_fact"));
        assertTrue(sql.contains("fact_type IN ('PREFERENCE', 'KNOWLEDGE', 'CONTEXT', 'BEHAVIOR', 'GOAL', 'OTHER')"));
        assertTrue(sql.contains("source IN ('AI_SUGGESTED', 'USER_ENTERED')"));
        assertTrue(sql.contains("confirmation_status IN ('ANALYZED', 'CONFIRMED', 'ARCHIVED')"));
        assertTrue(sql.contains("confidence BETWEEN 0 AND 100"));
        assertTrue(sql.contains("archived_reason IN ('SUPERSEDED', 'MANUAL')"));
        assertTrue(sql.contains("fk_user_fact_user FOREIGN KEY (user_id) REFERENCES app_user(id)"));
    }

    @Test
    void migrationExtendsScenarioChecksWithUserFact() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("DROP CONSTRAINT chk_ai_scenario_code"));
        assertTrue(sql.contains("'KNOWLEDGE_ANSWER', 'USER_FACT'"));
        // ai_call_audit 的 scenario_code 没有 CHECK（V27 只约束 status），迁移不得引用不存在的约束
        assertTrue(!sql.contains("chk_ai_call_audit_scenario"));
    }
}
