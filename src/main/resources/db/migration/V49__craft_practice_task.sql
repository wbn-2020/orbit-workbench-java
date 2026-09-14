-- V49：横向联动——方法论 → 练习任务（借鉴 EvoFlow craft 可复用 + 我们既有 REPORT→复习任务 模式）
-- 背景：V47 有了本事库，但套路沉淀完就躺着——它本该有出口。面试报告揭示弱项（感知），
-- 报告生成复习任务（学习），而「怎么练」这一环此前只能靠人自己想办法。
-- 本迁移让已确认方法论能一键转成练习任务（source_type='CRAFT'，source_id=craft_note.id），
-- 与既有 REPORT 来源同一套幂等口径（来源 + 标题去重），并让工作台据此提示闭环缺口。
--
-- 只扩枚举，不动既有数据：存量任务的 source_type 仍是 MANUAL/REPORT/WORKBENCH。

ALTER TABLE study_task
    DROP CONSTRAINT chk_study_task_source,
    ADD CONSTRAINT chk_study_task_source CHECK (
        source_type IN ('MANUAL', 'REPORT', 'WORKBENCH', 'CRAFT')
    );
