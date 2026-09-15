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
 * <p>范围是白名单里的用户维度表（当前产品的自有数据）。旧 Orbit 历史表按 ADR-0010 不纳入备份，
 * 因此「清空全部数据」清的是本产品写入的数据，不会动到冻结的历史表。
 *
 * <p><b>完整性约束（由 {@code DataBackupScopeTest} 守卫）</b>：任何带 {@code user_id} 列的表
 * 都必须出现在 {@link #USER_TABLES} 里；没有 {@code user_id}、但经外键挂在用户表下的表
 * 必须出现在 {@link #OWNED_TABLES} 里。漏表会让「导出再导入」静默丢数据、让「清空」留下
 * 孤儿行——V60 修的就是这类漏网（本事库、面试报告、错题重练记录等曾整体缺席）。
 *
 * <p>刻意不在范围内（有明确理由，不是遗漏）：
 * <ul>
 *   <li>{@code ai_connection} / {@code model_profile} / {@code connection_test_record}：
 *       保存加密凭据，密文与安装态的密钥绑定（{@code CredentialCipher}），导出到文件既不可移植
 *       也可能泄露密钥材料；属于安装配置而非成长数据。</li>
 *   <li>{@code workspace}：建号时创建、项目创建的前置依赖（{@code IdentityService} 调
 *       {@code createDefault}），清掉它会让应用无法再建项目，属环境必要数据。</li>
 *   <li>{@code provider_catalog}：随迁移种子化的系统目录。</li>
 *   <li>{@code app_user}：账号本身。本服务只处理「本账号写入的数据」，
 *       账号删除是另一个能力（当前未提供）。</li>
 * </ul>
 *
 * <p>表名与列名都做白名单校验，导入只接受本产品导出的载荷格式。
 */
@Service
public class DataBackupService {

    public static final String FORMAT = "orbit-workbench-backup";
    public static final int VERSION = 1;

    /** 清空操作的确认串，前端需要用户在弹窗里手动输入它。 */
    public static final String CLEAR_CONFIRM = "清空";

    /** 直接带 user_id 的用户数据表（包可见以便范围守卫测试直接断言）。 */
    static final List<String> USER_TABLES = List.of(
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
            "ai_call_audit",
            // V60 补齐：以下三张是早期版本新增、当时漏进白名单的用户数据表
            "craft_note",
            "user_focus_note",
            "user_profile_digest");

    /**
     * 无 user_id 列、经父表间接归属的表：本表外键列 -> 父表（父表必须有 user_id）。
     * 备份/清空/恢复都通过 JOIN 父表过滤。
     *
     * <p>父表本身也无 user_id 时，用 {@code bridgeColumn}/{@code bridgeTable} 再上溯一层
     * （如 {@code project_file → project_version → project}）。只支持两跳：
     * 当前产品最深的归属链就是两级，再深应先重新审视模型而不是继续加 JOIN。
     *
     * <p>顺序即插入顺序：父表必须排在子表之前（{@code interview_session} 等父表在
     * {@link #USER_TABLES} 里、会先于本列表写入）。
     */
    static final List<OwnedTable> OWNED_TABLES = List.of(
            new OwnedTable("interview_turn", "session_id", "interview_session", null, null),
            new OwnedTable("interview_report", "session_id", "interview_session", null, null),
            new OwnedTable("practice_attempt", "practice_item_id", "practice_item", null, null),
            new OwnedTable("resume_version", "resume_id", "resume_document", null, null),
            new OwnedTable("job_posting_version", "job_posting_id", "job_posting", null, null),
            new OwnedTable("project_version", "project_id", "project", null, null),
            new OwnedTable("project_file", "project_version_id", "project_version",
                    "project_id", "project"));

    /** 父表先于子表导出、后于子表清空/导入，保证外键顺序正确。 */
    record OwnedTable(String table, String ownerColumn, String ownerTable,
                      String bridgeColumn, String bridgeTable) {

        /** 跳到最终持有 user_id 的那张表：两跳时是 bridge 表，否则是直接父表。 */
        String userOwnerTable() {
            return bridgeTable != null ? bridgeTable : ownerTable;
        }
    }

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
                    owned.table(), owned.ownerColumn(), owned.ownerTable(),
                    owned.bridgeColumn(), owned.bridgeTable(), userId);
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
            deleteAllRowsForUser(userId);
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

    /**
     * 删除本用户的全部产品数据（恢复与清空共用），返回删除行数。
     *
     * <p><b>顺序是硬约束</b>：间接归属表走 {@code JOIN 父表} 定位归属，所以必须
     * <em>先删子表、后删父表</em>。反过来做，父行已被删掉、JOIN 匹配为空，
     * 子表数据会整批留下成为孤儿——V60 运行验收抓到的正是这个 bug（单测用 mock
     * 不会暴露它，只有真库 JOIN 才会）。
     */
    private long deleteAllRowsForUser(Long userId) {
        long rows = 0;
        List<OwnedTable> reverse = new ArrayList<>(OWNED_TABLES);
        java.util.Collections.reverse(reverse);
        for (OwnedTable owned : reverse) {
            rows += mapper.deleteByOwner(owned.table(), owned.ownerColumn(),
                    owned.ownerTable(), owned.bridgeColumn(), owned.bridgeTable(), userId);
        }
        for (String table : USER_TABLES) {
            rows += mapper.deleteByUser(table, userId);
        }
        return rows;
    }

    @Transactional
    public DataOperationSummary clear(Long userId, String confirm) {
        if (!CLEAR_CONFIRM.equals(confirm)) {
            throw invalid("清空全部数据需要输入确认串「" + CLEAR_CONFIRM + "」");
        }
        long rows = 0;
        mapper.disableForeignKeyChecks();
        try {
            rows += deleteAllRowsForUser(userId);
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
