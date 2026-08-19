package com.orbitworkbench.agent.application;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class RecoveryCoordinator {

    private final AgentRunService agentRunService;

    public RecoveryCoordinator(AgentRunService agentRunService) {
        this.agentRunService = agentRunService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        agentRunService.recoverInterruptedRuns();
    }
}

