package com.orbitworkbench.tool.application;

public interface WorkflowToolExecutionBridge {

    void attachToolCall(Long workflowNodeRunId, Long toolCallId);

    void publish(Long workflowRunId,
                 String eventType,
                 String summary,
                 Object payload);
}
