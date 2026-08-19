package com.orbitworkbench.ai.application;

public interface AiProtocolAdapter {

    String protocol();

    AiAdapterCall open(AiInvocation invocation);
}
