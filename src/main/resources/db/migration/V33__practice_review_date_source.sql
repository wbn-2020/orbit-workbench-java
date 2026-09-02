-- C-03：记录错题复习日的写入来源，避免把用户手设与规则排期混为一谈。
-- 迁移前已有数据不回填：无法从历史 updated_at 判断真实写入来源时保持 NULL。
ALTER TABLE practice_item
    ADD COLUMN review_date_source VARCHAR(16) NULL AFTER next_review_date,
    ADD CONSTRAINT chk_practice_item_review_date_source CHECK (
        review_date_source IS NULL
            OR review_date_source IN ('MANUAL', 'RULE')
    );
