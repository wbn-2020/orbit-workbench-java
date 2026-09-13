-- token 明细与缓存计费（用户请求：按官方的来，含缓存价与 token 明细）。
-- qwen 系列 usage 会报 prompt_tokens_details.cached_tokens（命中隐式缓存的输入 token，
-- 计费单价远低于常规输入价）和 completion_tokens_details.reasoning_tokens（推理输出）。
-- 明细列同样全部可空：上游没报就是不知道，不用 0 冒充（V40 口径的延续）。
ALTER TABLE ai_call_audit
    ADD COLUMN cached_input_tokens INT NULL AFTER output_tokens,
    ADD COLUMN reasoning_output_tokens INT NULL AFTER cached_input_tokens;

-- 缓存命中输入价：与 V40 单价同口径——用户自己的合同价、每百万 token、不内置价格表。
-- 不配也不报错：该部分按常规输入价计费（宁可高估不低估，见 AiCallAuditRecorder.cost 注释）。
ALTER TABLE ai_connection
    ADD COLUMN cached_input_price_per_million DECIMAL(14,6) NULL AFTER output_price_per_million;
