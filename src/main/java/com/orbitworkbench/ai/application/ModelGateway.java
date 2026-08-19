package com.orbitworkbench.ai.application;

import reactor.core.publisher.Flux;

public interface ModelGateway {
    Flux<AiStreamEvent> stream(AiInvocation invocation);
}

