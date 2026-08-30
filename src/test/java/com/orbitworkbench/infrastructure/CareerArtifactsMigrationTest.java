package com.orbitworkbench.infrastructure;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CareerArtifactsMigrationTest {

    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V28__career_artifacts.sql");

    @Test
    void migrationCreatesResumeDocumentWithVersionedSections() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("CREATE TABLE resume_document"));
        assertTrue(sql.contains("UNIQUE KEY uk_resume_document_user (user_id)"));
        assertTrue(sql.contains("CREATE TABLE resume_version"));
        assertTrue(sql.contains("UNIQUE KEY uk_resume_version_number (resume_id, version_number)"));
        assertTrue(sql.contains("status IN ('DRAFT', 'FINAL')"));
    }

    @Test
    void migrationCreatesPracticeItemsAndAttempts() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("CREATE TABLE practice_item"));
        assertTrue(sql.contains("source_type IN ('REPORT', 'INTERVIEW_TURN', 'MANUAL')"));
        assertTrue(sql.contains("mastery_status IN ('NEW', 'LEARNING', 'MASTERED')"));
        assertTrue(sql.contains("CREATE TABLE practice_attempt"));
        assertTrue(sql.contains("result IN ('RETRY', 'PARTIAL', 'PASSED')"));
        assertTrue(sql.contains("self_score IS NULL OR self_score BETWEEN 0 AND 100"));
    }

    @Test
    void migrationCreatesJobPostingMatchingAndCapabilityTables() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("CREATE TABLE job_posting"));
        assertTrue(sql.contains("CREATE TABLE job_posting_version"));
        assertTrue(sql.contains("UNIQUE KEY uk_job_posting_version_number (job_posting_id, version_number)"));
        assertTrue(sql.contains("CREATE TABLE job_match_result"));
        assertTrue(sql.contains(
                "confirmation_status IN ('PENDING', 'CONFIRMED', 'REJECTED')"));
        assertTrue(sql.contains("CREATE TABLE capability_record"));
        assertTrue(sql.contains("UNIQUE KEY uk_capability_user_code (user_id, capability_code)"));
    }
}
