package com.orbitworkbench.agent.application;

final class AgentRunControlException extends RuntimeException {

    enum Type {
        CANCELLED,
        PAUSED
    }

    private final Type type;

    AgentRunControlException(Type type) {
        this.type = type;
    }

    Type type() {
        return type;
    }
}
