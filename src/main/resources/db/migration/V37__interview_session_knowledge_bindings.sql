-- 知识卡片进面试上下文（23 号设计 A 案）：创建会话时把选中的知识卡片全文
-- 写入不可变快照（仅 INSERT 路径，无 UPDATE），出题与评分消费同一份信息条件。
-- NULL 兼容历史会话；MEDIUMTEXT 与 project_bindings_json 同规格。
ALTER TABLE interview_session
    ADD COLUMN knowledge_bindings_json MEDIUMTEXT NULL AFTER project_bindings_json;
