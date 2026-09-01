-- C-05 岗位与 JD 匹配（设计见 项目文档/求职成长工作台/产品报告/17_岗位与JD匹配设计.md §7）
--
-- 只做三件事，均不改任何 CHECK 的取值集合：
-- 1) 岗位与投递记录的关联承载：`11` §5.4 与 `06` §13 都要求「关联现有 JobApplication」，但 V25 与 V28 两侧
--    都没有对方主键列。ON DELETE SET NULL 是刻意的——删掉投递记录不得连带销毁用户手工粘贴的 JD。
-- 2) 匹配分数列改为可空：V28 把四个分数列写成 NOT NULL + CHECK 0-100，而本期没有任何诚实口径能填它们
--    （job_profile 无年限数值、简历工作/项目区块是自由文本、project_fact 无分数）。NULL 的语义固定为
--    「未评分」。CHECK 对 NULL 求值为 UNKNOWN，MySQL 允许，因此不需要重建约束。
-- 3) 补按 JD 版本查匹配历史的索引：V28 只有 idx_job_match_user_created，按岗位回看历史只能扫全表。

ALTER TABLE job_posting
    ADD COLUMN application_id BIGINT UNSIGNED NULL AFTER user_id;

ALTER TABLE job_posting
    ADD CONSTRAINT fk_job_posting_application
        FOREIGN KEY (application_id) REFERENCES job_application(id) ON DELETE SET NULL,
    ADD KEY idx_job_posting_application (application_id);

ALTER TABLE job_match_result
    MODIFY COLUMN total_score INT NULL,
    MODIFY COLUMN skill_score INT NULL,
    MODIFY COLUMN experience_score INT NULL,
    MODIFY COLUMN project_score INT NULL,
    ADD KEY idx_job_match_posting_version (job_posting_version_id, created_at, id);
