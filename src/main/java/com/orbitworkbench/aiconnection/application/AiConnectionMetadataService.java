package com.orbitworkbench.aiconnection.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.aiconnection.api.AiConnectionMetadataDtos.ConnectionTestResponse;
import com.orbitworkbench.aiconnection.api.AiConnectionMetadataDtos.ModelProfileResponse;
import com.orbitworkbench.aiconnection.domain.ConnectionTestRecord;
import com.orbitworkbench.aiconnection.domain.ModelProfileRecord;
import com.orbitworkbench.aiconnection.infrastructure.mapper.ConnectionTestRecordMapper;
import com.orbitworkbench.aiconnection.infrastructure.mapper.ModelProfileMapper;
import com.orbitworkbench.shared.api.PageResult;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiConnectionMetadataService {

    private final AiConnectionService connectionService;
    private final ModelProfileMapper modelProfileMapper;
    private final ConnectionTestRecordMapper testRecordMapper;
    private final ObjectMapper objectMapper;

    public AiConnectionMetadataService(AiConnectionService connectionService,
                                       ModelProfileMapper modelProfileMapper,
                                       ConnectionTestRecordMapper testRecordMapper,
                                       ObjectMapper objectMapper) {
        this.connectionService = connectionService;
        this.modelProfileMapper = modelProfileMapper;
        this.testRecordMapper = testRecordMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ModelProfileResponse> modelProfiles(Long connectionId) {
        connectionService.get(connectionId);
        return modelProfileMapper.findEnabledByConnection(connectionId).stream()
                .map(this::toModelProfile)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResult<ConnectionTestResponse> testHistory(Long connectionId, int page, int size) {
        connectionService.get(connectionId);
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        int offset = (normalizedPage - 1) * normalizedSize;
        List<ConnectionTestResponse> items = testRecordMapper
                .findPage(connectionId, offset, normalizedSize).stream()
                .map(this::toTest)
                .toList();
        return new PageResult<>(items, normalizedPage, normalizedSize,
                testRecordMapper.countPage(connectionId));
    }

    private ModelProfileResponse toModelProfile(ModelProfileRecord record) {
        return new ModelProfileResponse(
                record.getId(),
                record.getConnectionId(),
                record.getModelName(),
                record.getDisplayName(),
                readList(record.getSupportedProtocolsJson()),
                readMap(record.getCapabilitiesJson()),
                readMap(record.getDefaultParametersJson()),
                record.isEnabled(),
                record.getCreatedAt(),
                record.getUpdatedAt());
    }

    private ConnectionTestResponse toTest(ConnectionTestRecord record) {
        return new ConnectionTestResponse(
                record.getId(),
                record.getConnectionId(),
                record.getProtocol(),
                record.getModelName(),
                record.isStreaming(),
                record.getStatus(),
                record.getHttpStatus(),
                record.getLatencyMs(),
                record.getEventCount(),
                record.isDoneMarkerReceived(),
                record.getErrorCode(),
                record.getErrorSummary(),
                record.getConfigurationVersion(),
                record.getTestedAt());
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
