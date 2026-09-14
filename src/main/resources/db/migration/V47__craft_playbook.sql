-- V47：可复用「本事」库（craft，借鉴 EvoFlow 资产中心 craft / 晋升为技能）
-- 背景：知识块（knowledge_card）回答「这个技术点是什么」，面试报告回答「我表现如何」，
-- 但「我怎么做这类事」——项目讲述结构、STAR 话术、并发排查套路、复盘方法——没有归宿。
-- 这类可复用指令既不是事实（不该进画像注入），也不是可检索的技术资料，
-- 需要独立一层，供准备与练习时查阅。
--
-- 与 user_fact 同一产物纪律：AI 只能给建议（ANALYZED 候选池），用户确认后才进库；
-- 手动写的方法论直接 CONFIRMED。归档保留痕迹，不物理删除。
-- 刻意「不注入 AI 对话」：套路是候选人自己的底牌，不该告诉面试官（与画像注入的边界区分）。

ALTER TABLE ai_scenario_route
    DROP CONSTRAINT chk_ai_scenario_code,
    ADD CONSTRAINT chk_ai_scenario_code CHECK (
        scenario_code IN ('INTERVIEW_QUESTION', 'INTERVIEW_REPORT', 'PROJECT_FACT',
            'KNOWLEDGE_ANSWER', 'USER_FACT', 'PROFILE_DIGEST', 'CRAFT_DISTILL')
    );

CREATE TABLE craft_note (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    category VARCHAR(24) NOT NULL,
    title VARCHAR(120) NOT NULL,
    -- 「什么时候用」：没有适用场景的套路等于无效套路，创建时强制
    when_to_use VARCHAR(255) NOT NULL,
    -- 套路正文（步骤 / 模板 / 检查清单），Markdown 风格纯文本
    content VARCHAR(2000) NOT NULL,
    tags_json VARCHAR(1024) NULL,
    source VARCHAR(16) NOT NULL,
    confirmation_status VARCHAR(16) NOT NULL,
    confidence INT NULL,
    -- 置顶：常用的几条排在前面
    pinned TINYINT(1) NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_craft_note_user_status (user_id, confirmation_status, id),
    CONSTRAINT fk_craft_note_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT chk_craft_note_category CHECK (
        category IN ('STORY', 'SCRIPT', 'PLAYBOOK', 'REVIEW', 'OTHER')
    ),
    CONSTRAINT chk_craft_note_source CHECK (source IN ('AI_SUGGESTED', 'USER_ENTERED')),
    CONSTRAINT chk_craft_note_status CHECK (
        confirmation_status IN ('ANALYZED', 'CONFIRMED', 'ARCHIVED')
    ),
    CONSTRAINT chk_craft_note_confidence CHECK (
        confidence IS NULL OR (confidence BETWEEN 0 AND 100)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
