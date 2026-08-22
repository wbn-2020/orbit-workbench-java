package com.orbitworkbench.search.application;

import com.orbitworkbench.search.api.SearchDtos.ResourceType;
import com.orbitworkbench.search.api.SearchDtos.SearchResultResponse;
import com.orbitworkbench.search.infrastructure.mapper.SearchMapper;
import com.orbitworkbench.search.infrastructure.mapper.SearchResultRow;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchService {

    private static final int MAX_QUERY_LENGTH = 100;
    private static final int MAX_LIMIT_PER_TYPE = 50;
    private static final int MAX_SCAN_PER_TYPE = 1000;
    private static final List<ResourceType> ALL_TYPES = List.of(ResourceType.values());
    private static final Comparator<Instant> UPDATED_AT_ORDER =
            Comparator.nullsLast(Comparator.reverseOrder());

    private final SearchMapper searchMapper;

    public SearchService(SearchMapper searchMapper) {
        this.searchMapper = searchMapper;
    }

    @Transactional(readOnly = true)
    public List<SearchResultResponse> search(String q,
                                             Long workspaceId,
                                             String types,
                                             int limit) {
        String normalizedQuery = normalizeQuery(q);
        validateWorkspaceId(workspaceId);
        List<ResourceType> selectedTypes = parseTypes(types);
        int normalizedLimit = Math.min(Math.max(limit, 1), MAX_LIMIT_PER_TYPE);
        String escapedQuery = escapeLike(normalizedQuery);

        List<SearchResultRow> rows = new ArrayList<>(selectedTypes.size() * normalizedLimit);
        for (ResourceType type : selectedTypes) {
            rows.addAll(findByType(type, workspaceId, escapedQuery, normalizedLimit));
        }

        return rows.stream()
                .map(this::toResponse)
                .sorted(this::compareResults)
                .toList();
    }

    private List<SearchResultRow> findByType(ResourceType type,
                                             Long workspaceId,
                                             String escapedQuery,
                                             int limit) {
        return switch (type) {
            case TASK -> searchMapper.searchTasks(
                    workspaceId, escapedQuery, MAX_SCAN_PER_TYPE, limit);
            case DATASET -> searchMapper.searchDatasets(
                    workspaceId, escapedQuery, MAX_SCAN_PER_TYPE, limit);
            case DOCUMENT -> searchMapper.searchDocuments(
                    workspaceId, escapedQuery, MAX_SCAN_PER_TYPE, limit);
            case ARTIFACT -> searchMapper.searchArtifacts(
                    workspaceId, escapedQuery, MAX_SCAN_PER_TYPE, limit);
            case RUN -> searchMapper.searchRuns(
                    workspaceId, escapedQuery, MAX_SCAN_PER_TYPE, limit);
        };
    }

    private SearchResultResponse toResponse(SearchResultRow row) {
        return new SearchResultResponse(
                ResourceType.valueOf(row.getResourceType()),
                row.getResourceId(),
                row.getTitle(),
                row.getSummary(),
                row.getUpdatedAt(),
                row.getRoute()
        );
    }

    private int compareResults(SearchResultResponse left, SearchResultResponse right) {
        int byUpdatedAt = UPDATED_AT_ORDER.compare(left.updatedAt(), right.updatedAt());
        if (byUpdatedAt != 0) {
            return byUpdatedAt;
        }
        int byType = Integer.compare(
                left.resourceType().ordinal(), right.resourceType().ordinal());
        if (byType != 0) {
            return byType;
        }
        return Comparator.<Long>reverseOrder()
                .compare(left.resourceId(), right.resourceId());
    }

    private String normalizeQuery(String q) {
        if (q == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "q 不能为空");
        }
        String normalized = q.trim();
        if (normalized.length() < 2 || normalized.length() > MAX_QUERY_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "q 长度必须为 2-100 个字符");
        }
        return normalized;
    }

    private void validateWorkspaceId(Long workspaceId) {
        if (workspaceId == null || workspaceId <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "workspaceId 必须为正整数");
        }
    }

    private List<ResourceType> parseTypes(String types) {
        if (types == null || types.isBlank()) {
            return ALL_TYPES;
        }

        Set<ResourceType> selected = EnumSet.noneOf(ResourceType.class);
        for (String value : types.split(",", -1)) {
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                throw invalidTypes();
            }
            try {
                selected.add(ResourceType.valueOf(normalized));
            } catch (IllegalArgumentException exception) {
                throw invalidTypes();
            }
        }
        return ALL_TYPES.stream().filter(selected::contains).toList();
    }

    private ApiException invalidTypes() {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                "types 仅支持 TASK、DATASET、DOCUMENT、ARTIFACT、RUN");
    }

    private String escapeLike(String value) {
        return value
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }
}
