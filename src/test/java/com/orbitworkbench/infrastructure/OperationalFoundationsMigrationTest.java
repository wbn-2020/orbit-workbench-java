package com.orbitworkbench.infrastructure;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OperationalFoundationsMigrationTest {

    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V27__operational_foundations.sql");

    @Test
    void migrationAddsKnowledgeBuildStateToProjectVersion() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("ADD COLUMN knowledge_build_status VARCHAR(16) NOT NULL DEFAULT 'PENDING'"));
        assertTrue(sql.contains("ADD COLUMN knowledge_chunk_count INT NOT NULL DEFAULT 0"));
        assertTrue(sql.contains("ADD COLUMN knowledge_build_attempts INT NOT NULL DEFAULT 0"));
        assertTrue(sql.contains("ADD COLUMN knowledge_build_error VARCHAR(512) NULL"));
        assertTrue(sql.contains("ADD COLUMN knowledge_built_at DATETIME(6) NULL"));
        assertTrue(sql.contains(
                "knowledge_build_status IN ('PENDING', 'BUILDING', 'READY', 'FAILED')"));
    }

    @Test
    void migrationCreatesInterviewerProfilesWithBuiltInSeeds() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("CREATE TABLE interviewer_profile"));
        assertTrue(sql.contains("UNIQUE KEY uk_interviewer_profile_code (code)"));
        assertTrue(sql.contains(
                "topic_mode IN ('ROTE', 'PROJECT_DEEP_DIVE', 'AI_TECH', 'CODE_REVIEW'"));
        assertTrue(sql.contains("'JAVA_FOUNDATION', 'Java 基础面试官'"));
        assertTrue(sql.contains("'PROJECT_DEEP_DIVE', '项目深挖面试官'"));
        assertTrue(sql.contains("'AI_APPLICATION', 'Java + AI 应用面试官'"));
        assertTrue(sql.contains("ADD COLUMN interviewer_snapshot_json JSON NULL"));
    }

    @Test
    void migrationCreatesNotificationScheduleAndPreferenceTables() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("CREATE TABLE notification"));
        assertTrue(sql.contains("UNIQUE KEY uk_notification_user_key (user_id, idempotency_key)"));
        assertTrue(sql.contains("CREATE TABLE schedule_event"));
        assertTrue(sql.contains("UNIQUE KEY uk_schedule_event_source (user_id, source_type, source_id)"));
        assertTrue(sql.contains(
                "source_type IN ('CUSTOM', 'STUDY_TASK', 'INTERVIEW', 'APPLICATION')"));
        assertTrue(sql.contains("CREATE TABLE user_preference"));
    }

    @Test
    void migrationCreatesAiScenarioRoutingAndAuditTables() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("CREATE TABLE ai_scenario_route"));
        assertTrue(sql.contains("UNIQUE KEY uk_ai_scenario_user_code (user_id, scenario_code)"));
        assertTrue(sql.contains(
                "scenario_code IN ('INTERVIEW_QUESTION', 'INTERVIEW_REPORT', 'PROJECT_FACT', 'KNOWLEDGE_ANSWER')"));
        assertTrue(sql.contains("CREATE TABLE ai_call_audit"));
        assertTrue(sql.contains("status IN ('RUNNING', 'SUCCEEDED', 'FAILED')"));
    }
}
