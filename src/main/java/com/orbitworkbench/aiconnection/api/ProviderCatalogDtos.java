package com.orbitworkbench.aiconnection.api;

import java.util.List;
import java.util.Map;

public final class ProviderCatalogDtos {

    private ProviderCatalogDtos() {
    }

    public record ProviderCatalogResponse(
            Long id,
            String providerCode,
            String displayName,
            String adapterType,
            List<String> defaultProtocols,
            Map<String, Object> capabilities,
            boolean enabled
    ) {
    }
}
