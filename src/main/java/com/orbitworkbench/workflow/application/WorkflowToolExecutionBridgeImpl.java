package com.orbitworkbench.workflow.application;

import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.tool.application.WorkflowToolExecutionBridge;
import com.orbitworkbench.workflow.infrastructure.mapper.WorkflowNodeRunMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class WorkflowToolExecutionBridgeImpl implements WorkflowToolExecutionBridge {

    private final WorkflowNodeRunMapper nodeRunMapper;
    private final WorkflowRunEventService eventService;

    public WorkflowToolExecutionBridgeImpl(WorkflowNodeRunMapper nodeRunMapper,
                                           WorkflowRunEventService eventService) {
        this.nodeRunMapper = nodeRunMapper;
        this.eventService = eventService;
    }

    @Override
    @Transactional
    public void attachToolCall(Long workflowNodeRunId, Long toolCallId) {
        if (nodeRunMapper.attachToolCall(
                workflowNodeRunId, toolCallId, java.time.Instant.now()) != 1) {
            throw new ApiException(HttpStatus.CONFLICT,
                    ErrorCode.STATE_CONFLICT,
                    "Workflow 节点运行状态已变化");
        }
    }

    @Override
    @Transactional
    public void publish(Long workflowRunId,
                        String eventType,
                        String summary,
                        Object payload) {
        eventService.append(workflowRunId, eventType, summary, payload);
    }
}
