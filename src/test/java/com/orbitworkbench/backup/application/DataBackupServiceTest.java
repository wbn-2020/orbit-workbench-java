package com.orbitworkbench.backup.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orbitworkbench.backup.api.DataBackupDtos.BackupPayload;
import com.orbitworkbench.backup.infrastructure.mapper.DataBackupMapper;
import com.orbitworkbench.shared.api.ApiException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
// 本类多数用例只关心「调了哪些删除」，其余表用宽松桩即可
@MockitoSettings(strictness = Strictness.LENIENT)
class DataBackupServiceTest {

    @Mock
    private DataBackupMapper mapper;

    @Test
    void exportRendersTemporalColumnsAsMysqlFriendlyStrings() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 7L);
        row.put("started_at", Timestamp.valueOf("2026-09-10 18:55:45"));
        row.put("created_at", Timestamp.valueOf("2026-09-10 18:55:45.123456"));
        when(mapper.selectByUser(anyString(), eq(1L))).thenReturn(List.of(row));

        BackupPayload payload = new DataBackupService(mapper).export(1L);

        Map<String, Object> rendered = payload.data().get("focus_session").get(0);
        assertEquals("2026-09-10 18:55:45", rendered.get("started_at"));
        assertEquals("2026-09-10 18:55:45.123456", rendered.get("created_at"));
        assertEquals(DataBackupService.FORMAT, payload.format());
        assertTrue(payload.tables().contains("focus_session"));
        assertTrue(payload.tables().contains("work_log"));
    }

    @Test
    void exportIncludesIndirectlyOwnedTablesViaJoin() {
        // interview_turn 无 user_id 列：走 owner-join 变体查询，并出现在 tables 清单里
        Map<String, Object> turn = new LinkedHashMap<>();
        turn.put("id", 11L);
        turn.put("session_id", 21L);
        turn.put("question", "秒杀如何防超卖？");
        when(mapper.selectByOwner("interview_turn", "session_id", "interview_session",
                null, null, 1L)).thenReturn(List.of(turn));

        BackupPayload payload = new DataBackupService(mapper).export(1L);

        verify(mapper).selectByOwner("interview_turn", "session_id", "interview_session",
                null, null, 1L);
        assertTrue(payload.tables().contains("interview_turn"));
        assertEquals("秒杀如何防超卖？", payload.data().get("interview_turn").get(0).get("question"));
    }

    @Test
    void exportCoversPreviouslyMissingUserTables() {
        // V60：这几张表曾整体缺席白名单——导出不含它们会让「导入恢复」静默丢数据
        when(mapper.selectByUser(anyString(), eq(1L))).thenReturn(List.of());

        BackupPayload payload = new DataBackupService(mapper).export(1L);

        assertTrue(payload.tables().contains("craft_note"), "本事库必须可导出");
        assertTrue(payload.tables().contains("interview_report"), "面试报告必须可导出");
        assertTrue(payload.tables().contains("practice_attempt"), "重练记录必须可导出");
        assertTrue(payload.tables().contains("user_focus_note"));
        assertTrue(payload.tables().contains("user_profile_digest"));
        assertTrue(payload.tables().contains("project_version"));
        assertTrue(payload.tables().contains("project_file"));
        assertTrue(payload.tables().contains("resume_version"));
        assertTrue(payload.tables().contains("job_posting_version"));
    }

    @Test
    void twoHopChildTableJoinsThroughBridgeOwner() {
        // project_file 的归属是 project_version -> project（两跳），SQL 需经桥表再落到 user_id
        when(mapper.selectByUser(anyString(), eq(1L))).thenReturn(List.of());
        when(mapper.selectByOwner(anyString(), anyString(), anyString(),
                any(), any(), eq(1L))).thenReturn(List.of());

        new DataBackupService(mapper).export(1L);

        verify(mapper).selectByOwner("project_file", "project_version_id", "project_version",
                "project_id", "project", 1L);
    }

    @Test
    void clearRemovesIndirectlyOwnedTables() {
        when(mapper.deleteByUser(anyString(), eq(1L))).thenReturn(1);
        when(mapper.deleteByOwner(eq("interview_turn"), eq("session_id"),
                eq("interview_session"), eq(null), eq(null), eq(1L))).thenReturn(4);

        var summary = new DataBackupService(mapper).clear(1L, DataBackupService.CLEAR_CONFIRM);

        verify(mapper).deleteByOwner("interview_turn", "session_id", "interview_session",
                null, null, 1L);
        // 表位数 = USER_TABLES 张数 + OWNED_TABLES 张数，跟着白名单一起长
        int expected = DataBackupService.USER_TABLES.size() + DataBackupService.OWNED_TABLES.size();
        assertEquals(expected, summary.tables());
        assertTrue(summary.rows() >= 4);
    }

    @Test
    void childTablesAreDeletedBeforeTheirParents() {
        // V60 真库验收抓到的 bug：先删父表的话，子表的 JOIN 定位不到父行，
        // 子表数据会整批残留成孤儿。顺序在这里锁死：所有子表删完，才轮到直删父表。
        when(mapper.deleteByUser(anyString(), eq(1L))).thenReturn(0);
        when(mapper.deleteByOwner(anyString(), anyString(), anyString(), any(), any(), eq(1L)))
                .thenReturn(0);

        new DataBackupService(mapper).clear(1L, DataBackupService.CLEAR_CONFIRM);

        var inOrder = org.mockito.Mockito.inOrder(mapper);
        // project_file 依赖 project_version（两跳），必须先于它删
        inOrder.verify(mapper).deleteByOwner("project_file", "project_version_id", "project_version",
                "project_id", "project", 1L);
        inOrder.verify(mapper).deleteByOwner("project_version", "project_id", "project",
                null, null, 1L);
        // interview_turn 依赖 interview_session
        inOrder.verify(mapper).deleteByOwner("interview_turn", "session_id", "interview_session",
                null, null, 1L);
        // 所有子表删完之后，才直删父表（按 USER_TABLES 声明序）
        inOrder.verify(mapper).deleteByUser("project", 1L);
        inOrder.verify(mapper).deleteByUser("interview_session", 1L);
    }

    @Test
    void restoreRejectsUnknownTable() {
        BackupPayload payload = new BackupPayload(DataBackupService.FORMAT, 1, Instant.now(),
                List.of(), Map.of("legacy_orbit_table", List.of(Map.of("id", 1))));

        ApiException error = assertThrows(ApiException.class,
                () -> new DataBackupService(mapper).restore(1L, payload));

        assertTrue(error.getMessage().contains("未知数据表"));
        verify(mapper, never()).insertRow(any(), any(), any());
    }

    @Test
    void restoreRejectsUnknownColumn() {
        BackupPayload payload = new BackupPayload(DataBackupService.FORMAT, 1, Instant.now(),
                List.of(), Map.of("work_log", List.of(Map.of("id", 1, "not_a_column", "x"))));
        when(mapper.columnsOf("work_log")).thenReturn(List.of("id", "user_id", "title"));

        ApiException error = assertThrows(ApiException.class,
                () -> new DataBackupService(mapper).restore(1L, payload));

        assertTrue(error.getMessage().contains("未知字段"));
    }

    @Test
    void restoreRequiresMatchingFormatAndVersion() {
        ApiException wrongFormat = assertThrows(ApiException.class, () -> new DataBackupService(mapper)
                .restore(1L, new BackupPayload("something-else", 1, Instant.now(), List.of(), Map.of())));
        assertTrue(wrongFormat.getMessage().contains("不是本产品导出的格式"));

        ApiException futureVersion = assertThrows(ApiException.class, () -> new DataBackupService(mapper)
                .restore(1L, new BackupPayload(DataBackupService.FORMAT, 99, Instant.now(), List.of(), Map.of())));
        assertTrue(futureVersion.getMessage().contains("版本"));
    }

    @Test
    void clearRequiresExplicitConfirmationString() {
        ApiException error = assertThrows(ApiException.class,
                () -> new DataBackupService(mapper).clear(1L, "clear"));
        assertTrue(error.getMessage().contains("确认串"));

        new DataBackupService(mapper).clear(1L, DataBackupService.CLEAR_CONFIRM);
        verify(mapper).deleteByUser("work_log", 1L);
    }
}
