-- 14_报告中心设计.md §4：给「评分规则版本」一个真实承载。
-- 只追加一个可空列，不回填历史行：历史报告当时用的是哪版规则无法由今天反推成事实，
-- 保持 NULL 并由界面显示「未记录规则版本」，不参与比较与趋势。
ALTER TABLE interview_report
    ADD COLUMN scoring_rule_version VARCHAR(40) NULL AFTER hiring_recommendation;

-- 报告中心的列表与趋势都按「用户 + 状态 + 生成时间」取数；interview_report 自身没有 user_id，
-- 查询必须 JOIN interview_session 过滤归属，因此时间索引建在生成时间上即可。
ALTER TABLE interview_report
    ADD KEY idx_interview_report_generated (generated_at, id);
