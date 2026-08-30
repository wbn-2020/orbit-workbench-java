package com.orbitworkbench.project.application;

/**
 * 项目版本导入成功后发布的集成事件。
 * 监听方应在导入事务提交后再执行后续动作（如异步知识块构建）。
 */
public record ProjectVersionImportedEvent(Long userId, Long projectId, Long versionId) {
}
