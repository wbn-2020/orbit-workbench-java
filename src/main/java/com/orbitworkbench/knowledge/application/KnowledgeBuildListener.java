package com.orbitworkbench.knowledge.application;

import com.orbitworkbench.project.application.ProjectVersionImportedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 导入事务提交后才触发知识块自动构建，避免构建读到未提交的文件清单；
 * 实际构建经 KnowledgeBuildService 的异步代理执行，不阻塞导入请求。
 */
@Component
public class KnowledgeBuildListener {

    private final KnowledgeBuildService buildService;

    public KnowledgeBuildListener(KnowledgeBuildService buildService) {
        this.buildService = buildService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVersionImported(ProjectVersionImportedEvent event) {
        buildService.buildAfterImport(event);
    }
}
