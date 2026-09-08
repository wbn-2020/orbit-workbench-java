-- 同一用户的同一工作记录只能对应一张知识卡片。
-- source_log_id 为 NULL 的独立知识卡片不受该约束影响（MySQL UNIQUE 对 NULL 可重复）。
ALTER TABLE knowledge_card
    ADD UNIQUE KEY uk_knowledge_card_user_source_log (user_id, source_log_id);
