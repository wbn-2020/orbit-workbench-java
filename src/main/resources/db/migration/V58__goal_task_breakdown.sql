-- V58：学习目标 → 任务拆解（EvoFlow 三轮对照：主链最后半环）
-- 背景：V52 打通了「事实→目标」，但目标立完就悬着——progress 靠人肉 PUT、DONE 靠手动点，
-- 是 v2 三模式里唯一要人维护状态的角落；EvoFlow 任务中心的「目标可拆可追」我们缺前半截。
-- 本迁移让目标能拆出带来源的执行任务（source_type='GOAL'，source_id=learning_goal.id），
-- 与 V49 的 CRAFT 来源同一套幂等口径（来源 + 目标 id + 标题去重）；
-- 进度改为读取时从任务派生（照 V50 mastered 从 practice_count 派生的纪律——不加派生列、不双写）。
--
-- 只扩枚举，不动既有数据：存量任务的 source_type 仍是 MANUAL/REPORT/WORKBENCH/CRAFT。

ALTER TABLE study_task
    DROP CONSTRAINT chk_study_task_source,
    ADD CONSTRAINT chk_study_task_source CHECK (
        source_type IN ('MANUAL', 'REPORT', 'WORKBENCH', 'CRAFT', 'GOAL')
    );
