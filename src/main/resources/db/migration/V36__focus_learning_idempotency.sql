-- 客户端重试必须复用同一个幂等键，避免请求已落库但响应丢失时重复创建。
ALTER TABLE learning_goal
    ADD COLUMN idempotency_key VARCHAR(128) NULL AFTER linked_skill,
    ADD UNIQUE KEY uk_learning_goal_user_idempotency (user_id, idempotency_key);

ALTER TABLE focus_session
    ADD COLUMN idempotency_key VARCHAR(128) NULL AFTER label,
    ADD UNIQUE KEY uk_focus_session_user_idempotency (user_id, idempotency_key);
