package com.orbitworkbench.ai.application;

import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Flux;

@Service
@Primary
public class DefaultModelGateway implements ModelGateway {

    private final AiProtocolAdapterRegistry adapterRegistry;

    public DefaultModelGateway(AiProtocolAdapterRegistry adapterRegistry) {
        this.adapterRegistry = adapterRegistry;
    }

    @Override
    public Flux<AiStreamEvent> stream(AiInvocation invocation) {
        if (invocation == null || invocation.connection() == null) {
            return Flux.error(new AiProviderException(
                    com.orbitworkbench.shared.api.ErrorCode.INVALID_REQUEST,
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    null,
                    "模型调用配置不能为空",
                    null));
        }
        return adapterRegistry.open(invocation).events();
    }
}
