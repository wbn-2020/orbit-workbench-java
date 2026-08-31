package com.orbitworkbench.workspace.application;

import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.workspace.api.WorkspaceDtos.WorkspaceRequest;
import com.orbitworkbench.workspace.api.WorkspaceDtos.WorkspaceResponse;
import com.orbitworkbench.workspace.domain.WorkspaceRecord;
import com.orbitworkbench.workspace.infrastructure.mapper.WorkspaceMapper;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceService {
    public static final String DEFAULT_WORKSPACE_NAME = "我的工作台";

    private final WorkspaceMapper mapper;

    public WorkspaceService(WorkspaceMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<WorkspaceResponse> list() {
        return mapper.findAll().stream().map(WorkspaceResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse get(Long id) {
        return WorkspaceResponse.from(require(id));
    }

    @Transactional
    public WorkspaceResponse create(WorkspaceRequest request) {
        WorkspaceRecord record = new WorkspaceRecord();
        record.setName(request.name().trim());
        record.setDescription(normalize(request.description()));
        record.setDefaultKey(null);
        record.setStatus("ACTIVE");
        record.setCreatedAt(Instant.now());
        record.setUpdatedAt(record.getCreatedAt());
        mapper.insert(record);
        return WorkspaceResponse.from(record);
    }

    @Transactional
    public WorkspaceResponse update(Long id, WorkspaceRequest request) {
        WorkspaceRecord record = require(id);
        record.setName(request.name().trim());
        record.setDescription(normalize(request.description()));
        record.setUpdatedAt(Instant.now());
        if (mapper.update(record) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT, "工作空间状态已变化");
        }
        return WorkspaceResponse.from(require(id));
    }

    @Transactional
    public void delete(Long id) {
        WorkspaceRecord record = mapper.findByIdForUpdate(id);
        if (record == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "工作空间不存在");
        }
        if (record.isDefaultWorkspace()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "默认工作空间不能删除");
        }
        if (mapper.countAll() <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "不能删除唯一的工作空间");
        }
        if (mapper.countProjects(id) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "存在项目资料的工作空间不能删除");
        }
        if (mapper.softDelete(id) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "工作空间状态已变化，请刷新后重试");
        }
    }

    @Transactional
    public WorkspaceRecord createDefault() {
        WorkspaceRecord record = new WorkspaceRecord();
        record.setName(DEFAULT_WORKSPACE_NAME);
        record.setDescription("Orbit Workbench 默认工作空间");
        record.setDefaultKey(1);
        record.setStatus("ACTIVE");
        record.setCreatedAt(Instant.now());
        record.setUpdatedAt(record.getCreatedAt());
        mapper.insert(record);
        return record;
    }

    @Transactional(readOnly = true)
    public WorkspaceRecord require(Long id) {
        WorkspaceRecord record = mapper.findById(id);
        if (record == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "工作空间不存在");
        }
        return record;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
