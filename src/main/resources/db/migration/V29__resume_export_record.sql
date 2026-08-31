-- C-01 简历工作台：导出留痕（13_简历工作台设计.md §4.2）
-- V28 的 pdf_storage_ref 只能表达「当前可下载文件」，无法满足「导出记录可追溯」，
-- 因此新增只追加表；V28 文本已被 CareerArtifactsMigrationTest 锁定，不在此处改动。
CREATE TABLE resume_export_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    resume_version_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    storage_ref VARCHAR(512) NULL,
    content_hash CHAR(64) NULL,
    size_bytes BIGINT UNSIGNED NULL,
    font_name VARCHAR(128) NULL,
    status VARCHAR(16) NOT NULL,
    failure_reason VARCHAR(512) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_resume_export_version FOREIGN KEY (resume_version_id) REFERENCES resume_version(id),
    CONSTRAINT fk_resume_export_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    KEY idx_resume_export_version_created (resume_version_id, created_at, id),
    CONSTRAINT chk_resume_export_status CHECK (status IN ('SUCCEEDED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
