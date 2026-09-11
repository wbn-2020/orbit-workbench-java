package com.orbitworkbench.backup.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 数据备份（导出 / 导入 / 清空）的请求与响应结构。 */
public final class DataBackupDtos {

    private DataBackupDtos() {
    }

    /**
     * 备份载荷。tables 是本次实际导出的表名，data 为「表名 -> 行数组」。
     * 行是列名到值的映射，便于跨版本容忍缺列。
     */
    public record BackupPayload(
            String format,
            int version,
            Instant exportedAt,
            List<String> tables,
            Map<String, List<Map<String, Object>>> data
    ) {}

    /** 导入 / 清空的结果摘要。 */
    public record DataOperationSummary(
            int tables,
            long rows
    ) {}

    /** 清空全部数据必须显式带上确认串，避免误触发。 */
    public record ClearDataRequest(String confirm) {}
}
