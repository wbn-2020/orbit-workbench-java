package com.orbitworkbench.backup.infrastructure.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

/**
 * 备份用的通用 mapper。
 * 表名一律来自 {@code DataBackupService} 内的固定白名单常量，不接受任何外部输入，
 * 因此这里使用 {@code ${table}} 拼接是可控的；列名同样先经 {@link #columnsOf} 校验后再拼。
 */
public interface DataBackupMapper {

    /** 表的真实列名（含生成列），用于校验导入数据里的列是否合法。 */
    List<String> columnsOf(@Param("table") String table);

    List<Map<String, Object>> selectByUser(@Param("table") String table, @Param("userId") Long userId);

    int deleteByUser(@Param("table") String table, @Param("userId") Long userId);

    /**
     * 无 user_id 列的表（如 interview_turn，经 session 间接归属）用此变体：
     * {@code ownerColumn} 指定本表指向父表的外键列，{@code ownerTable} 为父表。
     * 父表本身也无 user_id 时传 {@code bridgeColumn}(父表指向祖父表的外键列) 与
     * {@code bridgeTable}(祖父表，持有 user_id)；两者为 null 表示一跳。
     */
    List<Map<String, Object>> selectByOwner(@Param("table") String table,
                                            @Param("ownerColumn") String ownerColumn,
                                            @Param("ownerTable") String ownerTable,
                                            @Param("bridgeColumn") String bridgeColumn,
                                            @Param("bridgeTable") String bridgeTable,
                                            @Param("userId") Long userId);

    int deleteByOwner(@Param("table") String table,
                      @Param("ownerColumn") String ownerColumn,
                      @Param("ownerTable") String ownerTable,
                      @Param("bridgeColumn") String bridgeColumn,
                      @Param("bridgeTable") String bridgeTable,
                      @Param("userId") Long userId);

    int insertRow(@Param("table") String table,
                  @Param("columns") List<String> columns,
                  @Param("values") List<Object> values);

    void disableForeignKeyChecks();

    void enableForeignKeyChecks();
}
