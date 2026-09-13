package com.orbitworkbench.backup.application;

import com.orbitworkbench.backup.api.DataBackupDtos.BackupPayload;
import com.orbitworkbench.backup.api.DataBackupDtos.DataOperationSummary;
import com.orbitworkbench.backup.infrastructure.mapper.DataBackupMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数据出口：导出 / 覆盖式导入 / 清空。
 *
 * 范围是白名单里的用户维度表（当前产品的自有数据）；旧 Orbit 历史表按 ADR-0010 不纳入备份，
 * 因此「清空全部数据」清的是本产品写入的数据，不会动到冻结的历史表。
 * 表名与列名都做白名单校验，导入只接受本产品导出的载荷格式。
 */
@Service
public class DataBackupService {

    public static final String FORMAT = "orbit-workbench-backup";
    public static final int VERSION = 1;

    /** 清空操作的确认串，前端需要用户在弹窗里手动输入它。 */
    public static final String CLEAR_CONFIRM = "清空";

    private static final List<String> USER_TABLES = List.of(
            "project",
            "project_fact",
            "user_fact",
            "job_profile",
            "knowledge_chunk",
            "knowledge_card",
            "work_log",
            "learning_goal",
            "focus_session",
            "study_task",
            "practice_item",
            "capability_record",
            "interviewer_profile",
            "interview_session",
            "job_posting",
            "job_match_result",
            "job_application",
            "job_application_event",
            "resume_document",
            "resume_export_record",
            "schedule_event",
            "notification",
            "user_preference",
            "ai_scenario_route",
            "ai_call_audit");

    /**
     * 无 user_id 列、经父表间接归属的表：本表列 -> 父表（父表必有 user_id）。
     * 备份/清空/恢复都通过 JOIN 父表过滤。
     */
    private static final List<OwnedTable> OWNED_TABLES = List.of(
            new OwnedTable("interview_turn", "session_id", "interview_session"));

    /** 父表先于子表导出、后于子表清空/导入，保证外键顺序正确。 */
    private record OwnedTable(String table, String ownerColumn, String ownerTable) {}

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter MICROS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

    private final DataBackupMapper mapper;

    public DataBackupService(DataBackupMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public BackupPayload export(Long userId) {
        Map<String, List<Map<String, Object>>> data = new LinkedHashMap<>();
        for (String table : USER_TABLES) {
            List<Map<String, Object>> rows = mapper.selectByUser(table, userId);
            List<Map<String, Object>> rendered = new ArrayList<>(rows.size());
            for (Map<String, Object> row : rows) {
                Map<String, Object> out = new LinkedHashMap<>();
                row.forEach((key, value) -> out.put(key, render(value)));
                rendered.add(out);
            }
            data.put(table, rendered);
        }
        for (OwnedTable owned : OWNED_TABLES) {
            List<Map<String, Object>> rows = mapper.selectByOwner(
                    owned.table(), owned.ownerColumn(), owned.ownerTable(), userId);
            List<Map<String, Object>> rendered = new ArrayList<>(rows.size());
            for (Map<String, Object> row : rows) {
                Map<String, Object> out = new LinkedHashMap<>();
                row.forEach((key, value) -> out.put(key, render(value)));
                rendered.add(out);
            }
            data.put(owned.table(), rendered);
        }
        List<String> allTables = new ArrayList<>(USER_TABLES);
        OWNED_TABLES.forEach(owned -> allTables.add(owned.table()));
        return new BackupPayload(FORMAT, VERSION, Instant.now(), allTables, data);
    }

    /** 覆盖式恢复：先清空当前用户的同名表数据，再按载荷写回。整段在同一事务内，失败全量回滚。 */
    @Transactional
    public DataOperationSummary restore(Long userId, BackupPayload payload) {
        if (payload == null || !FORMAT.equals(payload.format())) {
            throw invalid("备份文件不是本产品导出的格式");
        }
        if (payload.version() > VERSION) {
            throw invalid("备份文件版本高于当前程序支持版本，请升级后再导入");
        }
        Map<String, List<Map<String, Object>>> data = payload.data() == null ? Map.of() : payload.data();
        Set<String> known = new LinkedHashSet<>(USER_TABLES);
        OWNED_TABLES.forEach(owned -> known.add(owned.table()));
        Set<String> unknown = new LinkedHashSet<>(data.keySet());
        unknown.removeAll(known);
        if (!unknown.isEmpty()) {
            throw invalid("备份文件包含未知数据表：" + String.join(", ", unknown));
        }

        long rows = 0;
        mapper.disableForeignKeyChecks();
        try {
            for (String table : USER_TABLES) {
                mapper.deleteByUser(table, userId);
            }
            // 子表在父表（interview_session）清空之后删除，避免外键悬挂
            for (OwnedTable owned : OWNED_TABLES) {
                rows += mapper.deleteByOwner(owned.table(), owned.ownerColumn(),
                        owned.ownerTable(), userId);
            }
            for (String table : USER_TABLES) {
                List<Map<String, Object>> tableRows = data.get(table);
                if (tableRows == null || tableRows.isEmpty()) {
                    continue;
                }
                List<String> actual = mapper.columnsOf(table);
                if (actual.isEmpty()) {
                    throw invalid("数据表不存在：" + table);
                }
                for (Map<String, Object> row : tableRows) {
                    List<String> columns = new ArrayList<>();
                    List<Object> values = new ArrayList<>();
                    for (Map.Entry<String, Object> entry : row.entrySet()) {
                        if (!actual.contains(entry.getKey())) {
                            throw invalid("备份文件包含未知字段：" + table + "." + entry.getKey());
                        }
                        columns.add(entry.getKey());
                        values.add(entry.getValue());
                    }
                    if (columns.isEmpty()) {
                        continue;
                    }
                    rows += mapper.insertRow(table, columns, values);
                }
            }
            // 子表（interview_turn）在父表回填之后导入，外键才有所指
            for (OwnedTable owned : OWNED_TABLES) {
                List<Map<String, Object>> ownedRows = data.get(owned.table());
                if (ownedRows == null || ownedRows.isEmpty()) {
                    continue;
                }
                List<String> ownedActual = mapper.columnsOf(owned.table());
                if (ownedActual.isEmpty()) {
                    throw invalid("数据表不存在：" + owned.table());
                }
                for (Map<String, Object> row : ownedRows) {
                    List<String> columns = new ArrayList<>();
                    List<Object> values = new ArrayList<>();
                    for (Map.Entry<String, Object> entry : row.entrySet()) {
                        if (!ownedActual.contains(entry.getKey())) {
                            throw invalid("备份文件包含未知字段：" + owned.table() + "." + entry.getKey());
                        }
                        columns.add(entry.getKey());
                        values.add(entry.getValue());
                    }
                    if (columns.isEmpty()) {
                        continue;
                    }
                    rows += mapper.insertRow(owned.table(), columns, values);
                }
            }
        } finally {
            mapper.enableForeignKeyChecks();
        }
        return new DataOperationSummary(countNonEmpty(data), rows);
    }

    @Transactional
    public DataOperationSummary clear(Long userId, String confirm) {
        if (!CLEAR_CONFIRM.equals(confirm)) {
            throw invalid("清空全部数据需要输入确认串「" + CLEAR_CONFIRM + "」");
        }
        long rows = 0;
        mapper.disableForeignKeyChecks();
        try {
            for (String table : USER_TABLES) {
                rows += mapper.deleteByUser(table, userId);
            }
            for (OwnedTable owned : OWNED_TABLES) {
                rows += mapper.deleteByOwner(owned.table(), owned.ownerColumn(),
                        owned.ownerTable(), userId);
            }
        } finally {
            mapper.enableForeignKeyChecks();
        }
        List<String> allTables = new ArrayList<>(USER_TABLES);
        OWNED_TABLES.forEach(owned -> allTables.add(owned.table()));
        return new DataOperationSummary(allTables.size(), rows);
    }

    private static int countNonEmpty(Map<String, List<Map<String, Object>>> data) {
        int n = 0;
        for (List<Map<String, Object>> rows : data.values()) {
            if (rows != null && !rows.isEmpty()) {
                n++;
            }
        }
        return n;
    }

    /** 时间类型统一渲染成 MySQL 可回填的字符串，避免导入时因 ISO「T」分隔符被拒。 */
    static Object render(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp timestamp) {
            LocalDateTime local = timestamp.toLocalDateTime();
            return timestamp.getNanos() % 1_000_000_000 == 0 ? DATE_TIME.format(local) : MICROS.format(local);
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().toString();
        }
        if (value instanceof java.sql.Time time) {
            return time.toLocalTime().toString();
        }
        if (value instanceof LocalDateTime local) {
            return DATE_TIME.format(local);
        }
        if (value instanceof LocalDate local) {
            return local.toString();
        }
        if (value instanceof LocalTime local) {
            return local.toString();
        }
        if (value instanceof OffsetDateTime offset) {
            return DATE_TIME.format(offset.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime());
        }
        if (value instanceof java.util.Date date) {
            return DATE_TIME.format(LocalDateTime.ofInstant(date.toInstant(), ZoneOffset.UTC));
        }
        return value;
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, message);
    }
}
