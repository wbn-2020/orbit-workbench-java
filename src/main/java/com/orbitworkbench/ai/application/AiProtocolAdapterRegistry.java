package com.orbitworkbench.ai.application;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AiProtocolAdapterRegistry {

    private final List<AiProtocolAdapter> adapters;

    public AiProtocolAdapterRegistry(List<AiProtocolAdapter> adapters) {
        this.adapters = List.copyOf(adapters);
    }

    public AiAdapterCall open(AiInvocation invocation) {
        return adapters.stream()
                .filter(adapter -> adapter.protocol().equalsIgnoreCase(invocation.connection().protocol()))
                .findFirst()
                .orElseThrow(() -> new AiProviderException(
                        com.orbitworkbench.shared.api.ErrorCode.UNSUPPORTED_CAPABILITY,
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        null,
                        "当前连接协议没有可用适配器",
                        null))
                .open(invocation);
    }
}
