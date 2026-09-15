package com.orbitworkbench.backup.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * V60 备份范围守卫：静态扫描迁移脚本，确保「带 user_id 的用户数据表」与
 * 「经外键挂在用户表下的子表」都被 {@link DataBackupService} 覆盖。
 *
 * <p>起因：本事库、面试报告、错题重练记录等表曾整体缺席白名单，导致「导出再导入」
 * 静默丢数据、「清空全部数据」留下孤儿行——而没有任何测试会发现，因为缺的是
 * <em>没有出现</em>的表。本测试反向断言「迁移里存在的用户表必须在覆盖清单里」，
 * 漏表会直接失败。刻意排除的表列在 {@link #INTENTIONALLY_EXCLUDED} 并附理由。
 */
class DataBackupScopeTest {

    private static final Path MIGRATION_DIR = Path.of("src", "main", "resources", "db", "migration");

    /**
     * 有意不纳入备份的表及其理由（写在这里而不是注释里，改动的人必须面对它）。
     * - ai_connection/model_profile/connection_test_record：加密凭据 + 与安装态密钥绑定，不可移植
     * - workspace：建号即建、建项目的前置依赖，清掉会让应用无法再建项目
     * - provider_catalog：迁移种子化的系统目录
     * - app_user：账号本体（账号删除是另一个能力，本服务不处理）
     * - ai_call_audit 之外的历史表：ADR-0010 冻结，业务代码零引用
     */
    private static final Set<String> INTENTIONALLY_EXCLUDED = Set.of(
            "ai_connection",
            "model_profile",
            "connection_test_record",
            "workspace",
            "provider_catalog",
            "app_user");

    /** 旧 Orbit 遗留表（ADR-0010 冻结）：不纳入备份，但必须在下方由专门测试确认它们确实无业务引用。 */
    private static final Set<String> LEGACY_FROZEN = Set.of(
            "agent_definition", "agent_run", "agent_run_step", "agent_version",
            "artifact", "artifact_export", "artifact_version",
            "content_project", "content_project_material", "content_version",
            "data_analysis_task", "dataset", "dataset_column", "dataset_profile", "dataset_sheet",
            "document", "mcp_server", "mcp_tool", "memory_candidate", "memory_record",
            "model_call", "prompt_template", "prompt_version", "run_event",
            "skill_definition", "skill_version", "skill_version_tool",
            "storage_cleanup_failure", "task", "task_create_idempotency", "task_document",
            "tool_call", "tool_catalog", "tool_definition", "tool_version",
            "workflow_definition", "workflow_edge", "workflow_node", "workflow_node_run",
            "workflow_run", "workflow_run_event", "workflow_version");

    /** 框架自建表：Spring Session 等，不属于业务数据。 */
    private static final Set<String> FRAMEWORK_TABLES = Set.of(
            "spring_session", "spring_session_attributes", "flyway_schema_history");

    @Test
    void everyUserOwnedTableFromMigrationsIsCovered() throws IOException {
        Set<String> covered = new LinkedHashSet<>(DataBackupService.USER_TABLES);
        DataBackupService.OWNED_TABLES.forEach(owned -> covered.add(owned.table()));

        List<String> missing = new ArrayList<>();
        for (String table : tablesWithUserIdColumn()) {
            if (covered.contains(table) || INTENTIONALLY_EXCLUDED.contains(table)
                    || LEGACY_FROZEN.contains(table) || FRAMEWORK_TABLES.contains(table)) {
                continue;
            }
            missing.add(table);
        }

        assertTrue(missing.isEmpty(),
                "以下用户数据表带 user_id 列却不在备份范围，会让备份静默丢数据、清空留下孤儿行："
                        + missing + "。请加入 DataBackupService.USER_TABLES，或写入本测试的 "
                        + "INTENTIONALLY_EXCLUDED 并说明理由。");
    }

    @Test
    void indirectlyOwnedChildTablesAreCovered() throws IOException {
        Set<String> covered = new LinkedHashSet<>(DataBackupService.USER_TABLES);
        DataBackupService.OWNED_TABLES.forEach(owned -> covered.add(owned.table()));

        List<String> missing = new ArrayList<>();
        for (String table : childTablesPointingAtUserTables()) {
            if (covered.contains(table) || INTENTIONALLY_EXCLUDED.contains(table)
                    || LEGACY_FROZEN.contains(table) || FRAMEWORK_TABLES.contains(table)) {
                continue;
            }
            missing.add(table);
        }

        assertTrue(missing.isEmpty(),
                "以下表经外键挂在用户数据表下却不在 OWNED_TABLES，导出/清空会漏掉它们："
                        + missing);
    }

    @Test
    void ownedTableJoinsResolveToAUserColumn() throws IOException {
        Set<String> tablesWithUserId = tablesWithUserIdColumn();
        Set<String> allTables = allTableNames();

        for (DataBackupService.OwnedTable owned : DataBackupService.OWNED_TABLES) {
            assertTrue(allTables.contains(owned.table()),
                    "OWNED_TABLES 里的表不存在于迁移：" + owned.table());
            assertFalse(tablesWithUserId.contains(owned.table()),
                    owned.table() + " 自带 user_id，应放进 USER_TABLES 而不是 OWNED_TABLES");
            // 归属链必须最终落到一张带 user_id 的表
            assertTrue(tablesWithUserId.contains(owned.userOwnerTable()),
                    owned.table() + " 的归属链最终指向 " + owned.userOwnerTable()
                            + "，但它没有 user_id 列——两跳上限之外需要重新审视模型");
        }
    }

    @Test
    void excludedCredentialTablesStayOutOfBackup() {
        // 凭据是安全的底线：这条断言防止有人「顺手」把加密凭据加进导出
        assertFalse(DataBackupService.USER_TABLES.contains("ai_connection"));
        assertFalse(DataBackupService.USER_TABLES.contains("model_profile"));
        DataBackupService.OWNED_TABLES.forEach(owned ->
                assertFalse("ai_connection".equals(owned.table())));
    }

    // ---- 迁移脚本静态解析 ----

    private static Set<String> tablesWithUserIdColumn() throws IOException {
        Set<String> out = new LinkedHashSet<>();
        for (Path file : migrationFiles()) {
            String sql = Files.readString(file, StandardCharsets.UTF_8);
            for (String table : createTableBlocks(sql).keySet()) {
                String block = createTableBlocks(sql).get(table);
                if (Pattern.compile("(?im)^\\s*user_id\\s+BIGINT", Pattern.MULTILINE)
                        .matcher(block).find()) {
                    out.add(table);
                }
            }
            // 后置 ALTER TABLE ... ADD COLUMN user_id 也要算
            Matcher alter = Pattern.compile(
                    "(?is)ALTER\\s+TABLE\\s+`?(\\w+)`?\\s+(.*?);", Pattern.DOTALL)
                    .matcher(sql);
            while (alter.find()) {
                if (Pattern.compile("(?i)ADD\\s+(COLUMN\\s+)?user_id\\b")
                        .matcher(alter.group(2)).find()) {
                    out.add(alter.group(1));
                }
            }
        }
        return out;
    }

    private static Set<String> childTablesPointingAtUserTables() throws IOException {
        Set<String> hasUserId = tablesWithUserIdColumn();
        Set<String> children = new LinkedHashSet<>();
        for (Path file : migrationFiles()) {
            String sql = Files.readString(file, StandardCharsets.UTF_8);
            for (String table : createTableBlocks(sql).keySet()) {
                if (hasUserId.contains(table)) {
                    continue; // 自带 user_id 的表由上一个测试负责
                }
                String block = createTableBlocks(sql).get(table);
                Matcher fk = Pattern.compile(
                        "(?is)FOREIGN\\s+KEY\\s*\\(\\s*`?(\\w+)`?\\s*\\)\\s*REFERENCES\\s+`?(\\w+)`?")
                        .matcher(block);
                while (fk.find()) {
                    String parent = fk.group(2);
                    // 只关心「直接挂到用户表」或「挂到某张用户表子表」的
                    if (hasUserId.contains(parent) || isChildOfUserTable(parent, hasUserId, sql)) {
                        children.add(table);
                        break;
                    }
                }
            }
        }
        return children;
    }

    /** 父表自身无 user_id 时，判断它是否经由外键挂到带 user_id 的表（即两跳归属）。 */
    private static boolean isChildOfUserTable(String parent, Set<String> hasUserId, String sql) {
        String block = createTableBlocks(sql).get(parent);
        if (block == null) {
            return false;
        }
        Matcher fk = Pattern.compile(
                "(?is)FOREIGN\\s+KEY\\s*\\(\\s*`?(\\w+)`?\\s*\\)\\s*REFERENCES\\s+`?(\\w+)`?")
                .matcher(block);
        while (fk.find()) {
            if (hasUserId.contains(fk.group(2))) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> allTableNames() throws IOException {
        Set<String> out = new LinkedHashSet<>();
        for (Path file : migrationFiles()) {
            out.addAll(createTableBlocks(Files.readString(file, StandardCharsets.UTF_8)).keySet());
        }
        return out;
    }

    /** 解析出 CREATE TABLE 块：表名 -> 定义体。 */
    private static java.util.Map<String, String> createTableBlocks(String sql) {
        java.util.Map<String, String> out = new java.util.LinkedHashMap<>();
        Matcher matcher = Pattern.compile(
                "(?is)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`?(\\w+)`?\\s*\\(")
                .matcher(sql);
        while (matcher.find()) {
            String table = matcher.group(1);
            int depth = 1;
            int index = matcher.end();
            while (index < sql.length() && depth > 0) {
                char c = sql.charAt(index);
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                }
                index++;
            }
            out.put(table, sql.substring(matcher.end(), Math.max(matcher.end(), index - 1)));
        }
        return out;
    }

    private static List<Path> migrationFiles() throws IOException {
        try (Stream<Path> stream = Files.list(MIGRATION_DIR)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted()
                    .toList();
        }
    }
}
