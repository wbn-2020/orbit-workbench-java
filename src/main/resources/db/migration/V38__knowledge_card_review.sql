-- 知识卡片间隔复习（24 号设计）：复用错题本 C-03e 的 1/3/7/14 天阶梯。
-- review_stage 为 NULL 表示未排期（存量卡片不进到期队列）；
-- 本期复习日只由阶梯规则产生，无手工设定入口，故不引入 review_date_source。
ALTER TABLE knowledge_card
    ADD COLUMN review_stage INT NULL AFTER tags_json,
    ADD COLUMN next_review_date DATE NULL AFTER review_stage,
    ADD COLUMN last_reviewed_at DATETIME(6) NULL AFTER next_review_date;

CREATE INDEX idx_knowledge_card_user_review
    ON knowledge_card (user_id, next_review_date, id);
