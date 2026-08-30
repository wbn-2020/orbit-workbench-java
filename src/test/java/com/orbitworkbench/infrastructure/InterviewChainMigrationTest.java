package com.orbitworkbench.infrastructure;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class InterviewChainMigrationTest {

    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V23__interview_session_report_study_task.sql");

    @Test
    void migrationCreatesInterviewSessionWithStateMachine() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("CREATE TABLE interview_session"));
        assertTrue(sql.contains("FOREIGN KEY (user_id) REFERENCES app_user(id)"));
        assertTrue(sql.contains("status IN ('READY', 'RUNNING', 'PAUSED', 'USER_ENDED', 'COMPLETING',"));
        assertTrue(sql.contains("form IN ('TRAINING', 'FORMAL')"));
    }

    @Test
    void migrationCreatesImmutableTurnNumberingAndAnswerSource() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("UNIQUE KEY uk_interview_turn_no (session_id, turn_no)"));
        assertTrue(sql.contains("turn_type IN ('MAIN', 'FOLLOW_UP')"));
        assertTrue(sql.contains("answer_source IN"));
        assertTrue(sql.contains("'INDEPENDENT', 'PROMPTED', 'AI_ASSISTED', 'AI_GENERATED', 'HISTORY_IMPORT'"));
    }

    @Test
    void migrationCreatesOneReportPerSessionWithPendingStates() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("UNIQUE KEY uk_interview_report_session (session_id)"));
        assertTrue(sql.contains("status IN ('REPORT_PENDING', 'REPORT_FAILED', 'REPORT_READY')"));
        assertTrue(sql.contains("hiring_recommendation IN ('STRONG_PASS', 'PASS', 'HOLD', 'FAIL')"));
    }

    @Test
    void migrationCreatesUserScopedStudyTaskWithLifecycle() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("CREATE TABLE study_task"));
        assertTrue(sql.contains("FOREIGN KEY (user_id) REFERENCES app_user(id)"));
        assertTrue(sql.contains("status IN ('PLANNED', 'IN_PROGRESS', 'COMPLETED', 'POSTPONED', 'SKIPPED')"));
        assertTrue(sql.contains("source_type IN ('MANUAL', 'REPORT', 'WORKBENCH')"));
    }

    @Test
    void snapshotMigrationAddsSessionSnapshotColumns() throws Exception {
        String sql = Files.readString(Path.of(
                "src", "main", "resources", "db", "migration", "V24__interview_session_snapshots.sql"));

        assertTrue(sql.contains("ALTER TABLE interview_session"));
        assertTrue(sql.contains("interviewer_name_snapshot VARCHAR(64)"));
        assertTrue(sql.contains("ai_connection_id_snapshot BIGINT UNSIGNED"));
        assertTrue(sql.contains("ai_model_snapshot VARCHAR(128)"));
        assertTrue(sql.contains("project_bindings_json JSON"));
        assertTrue(sql.contains("web_search_policy IN ('DISABLED', 'ON_DEMAND', 'AUTO')"));
    }
}
