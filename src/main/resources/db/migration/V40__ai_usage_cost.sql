-- AI 用量与费用账本（22 号诊断 P0-1）。
-- 三列都允许 NULL：旧审计行没有用量数据，供应商也可能真报不出 token——
-- 不知道就说不知道，不用 0 冒充（同 product_fact 的「不伪造」口径）。
-- cost_amount 是**快照**：单价以后会变，历史成本不该被改写（同 V30 scoring_rule_version、V32 web_search_outcome）。
ALTER TABLE ai_call_audit
    ADD COLUMN input_tokens INT NULL AFTER response_chars,
    ADD COLUMN output_tokens INT NULL AFTER input_tokens,
    ADD COLUMN cost_amount DECIMAL(14,6) NULL AFTER output_tokens;

-- 单价配在连接上：这是用户自己的合同价，产品不内置价格表（内置表必然过时且会误导）。
-- 单位「每百万 token」，币种由用户自行统一，产品不做汇率换算。
ALTER TABLE ai_connection
    ADD COLUMN input_price_per_million DECIMAL(14,6) NULL AFTER timeout_ms,
    ADD COLUMN output_price_per_million DECIMAL(14,6) NULL AFTER input_price_per_million;

-- 用量页按「用户 + 时间窗」聚合，现有 idx_ai_call_audit_user_created 已覆盖；
-- 再补一个场景维度索引，供按场景拆分。
CREATE INDEX idx_ai_call_audit_user_scenario
    ON ai_call_audit (user_id, scenario_code, created_at);
