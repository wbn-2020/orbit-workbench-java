-- V50：练习完成回写——本事库记录熟练度（横向连接第一批的收尾）
-- 背景：V49 打通了「方法论 → 练习任务」的出口，但闭环只到「安排」为止：
-- 练习任务完成后，套路本身没有变化，用户看不出哪条练过、哪条练熟了。
-- 本迁移给 craft_note 增加练习计数与最近练习时间，完成 CRAFT 来源任务时回写。
--
-- 刻意不加独立状态列：熟练度从 practice_count 派生（0 未练熟 / ≥1 已练熟），
-- 与 V49 practiced（已安排练习任务）形成「安排 → 练熟」两步递进。
-- V49 的按标题幂等保证同一套路只有一个练习任务，故完成一次即练熟，阈值取 1。
-- 状态永远从事实数据算出来，不出现「标记了但数据没动」的漂移。

ALTER TABLE craft_note
    ADD COLUMN practice_count INT NOT NULL DEFAULT 0 AFTER pinned,
    ADD COLUMN last_practiced_at DATETIME(6) NULL AFTER practice_count,
    ADD CONSTRAINT chk_craft_note_practice_count CHECK (practice_count >= 0);
