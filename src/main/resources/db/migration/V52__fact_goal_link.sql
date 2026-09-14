-- V52：画像事实 → 学习目标溯源链接（横向连接第三批）
-- 背景：画像事实（user_fact）沉淀了「我是谁」，学习目标（learning_goal）承接「我要学什么」，
-- 但两者此前互不相识：由一条技能缺口事实立的目标，看不出来源；事实侧也不知道自己已经转化过目标。
-- V49 给 study_task 加过同型链接（source_type + source_id），这里给 learning_goal 加单列即可：
-- 一条目标只来自一条事实，不需要枚举扩展。
--
-- 链接只记录来源，不级联删除：事实归档后目标照常存在（学习承诺不随记忆清理而消失）。

ALTER TABLE learning_goal
    ADD COLUMN source_fact_id BIGINT UNSIGNED NULL AFTER linked_skill,
    ADD KEY idx_learning_goal_source_fact (user_id, source_fact_id),
    ADD CONSTRAINT fk_learning_goal_source_fact
        FOREIGN KEY (source_fact_id) REFERENCES user_fact(id);
