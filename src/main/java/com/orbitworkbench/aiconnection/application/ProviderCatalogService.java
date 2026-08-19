package com.orbitworkbench.aiconnection.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.aiconnection.api.ProviderCatalogDtos.ProviderCatalogResponse;
import com.orbitworkbench.aiconnection.domain.ProviderCatalogRecord;
import com.orbitworkbench.aiconnection.infrastructure.mapper.ProviderCatalogMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProviderCatalogService {

    private final ProviderCatalogMapper mapper;
    private final ObjectMapper objectMapper;

    public ProviderCatalogService(ProviderCatalogMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ProviderCatalogResponse> listEnabled() {
        return mapper.findEnabled().stream().map(this::toResponse).toList();
    }

    private ProviderCatalogResponse toResponse(ProviderCatalogRecord record) {
        return new ProviderCatalogResponse(
                record.getId(),
                record.getProviderCode(),
                record.getDisplayName(),
                record.getAdapterType(),
                readList(record.getDefaultProtocolsJson()),
                readMap(record.getCapabilitiesJson()),
                record.isEnabled());
    }

    private List<String> readList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception exception) {
            return List.of();
        }
    }

    private Map<String, Object> readMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception exception) {
            return Map.of();
        }
    }
}
