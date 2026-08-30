package com.orbitworkbench.interviewer.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.interview.infrastructure.mapper.InterviewSessionMapper;
import com.orbitworkbench.interviewer.api.InterviewerDtos.CopyInterviewerRequest;
import com.orbitworkbench.interviewer.api.InterviewerDtos.CreateInterviewerRequest;
import com.orbitworkbench.interviewer.api.InterviewerDtos.InterviewerResponse;
import com.orbitworkbench.interviewer.api.InterviewerDtos.UpdateInterviewerRequest;
import com.orbitworkbench.interviewer.domain.InterviewerProfileRecord;
import com.orbitworkbench.interviewer.infrastructure.mapper.InterviewerProfileMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 面试官档案：内置模板（user_id=NULL，built_in=1）只读，用户可复制为专属后编辑、
 * 归档或删除；删除受历史面试会话引用保护，会话侧始终保留创建时的快照。
 */
@Service
public class InterviewerService {

    private static final TypeReference<List<String>> TAGS_TYPE = new TypeReference<>() {};
    private static final int COPY_NAME_SUFFIX_LIMIT = 64;

    private final InterviewerProfileMapper profileMapper;
    private final InterviewSessionMapper sessionMapper;
    private final ObjectMapper objectMapper;

    public InterviewerService(InterviewerProfileMapper profileMapper,
                              InterviewSessionMapper sessionMapper,
                              ObjectMapper objectMapper) {
        this.profileMapper = profileMapper;
        this.sessionMapper = sessionMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<InterviewerResponse> list(Long userId, boolean includeArchived) {
        return profileMapper.listVisible(userId, includeArchived).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public InterviewerResponse get(Long userId, Long id) {
        return toResponse(requireVisible(userId, id));
    }

    @Transactional
    public InterviewerResponse create(Long userId, CreateInterviewerRequest request) {
        Instant now = Instant.now();
        InterviewerProfileRecord record = new InterviewerProfileRecord();
        record.setUserId(userId);
        record.setName(request.name().trim());
        record.setDescription(normalize(request.description()));
        record.setSystemPrompt(request.systemPrompt().trim());
        record.setTopicMode(request.topicMode());
        record.setFocusTagsJson(writeTags(request.focusTags()));
        record.setDefaultQuestionLimit(request.defaultQuestionLimit());
        record.setDefaultFollowUpLimit(request.defaultFollowUpLimit());
        record.setBuiltIn(false);
        record.setArchived(false);
        record.setVersion(1);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        profileMapper.insert(record);
        return toResponse(profileMapper.findById(record.getId()));
    }

    @Transactional
    public InterviewerResponse copy(Long userId, Long id, CopyInterviewerRequest request) {
        InterviewerProfileRecord source = requireVisible(userId, id);
        String name = request != null && request.name() != null && !request.name().isBlank()
                ? request.name().trim()
                : copyName(source.getName());
        Instant now = Instant.now();
        InterviewerProfileRecord record = new InterviewerProfileRecord();
        record.setUserId(userId);
        record.setName(name);
        record.setDescription(source.getDescription());
        record.setSystemPrompt(source.getSystemPrompt());
        record.setTopicMode(source.getTopicMode());
        record.setFocusTagsJson(source.getFocusTagsJson());
        record.setDefaultQuestionLimit(source.getDefaultQuestionLimit());
        record.setDefaultFollowUpLimit(source.getDefaultFollowUpLimit());
        record.setBuiltIn(false);
        record.setArchived(false);
        record.setVersion(1);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        profileMapper.insert(record);
        return toResponse(profileMapper.findById(record.getId()));
    }

    @Transactional
    public InterviewerResponse update(Long userId, Long id, UpdateInterviewerRequest request) {
        requireCustom(userId, id);
        int updated = profileMapper.updateProfile(id, userId,
                request.name().trim(),
                normalize(request.description()),
                request.systemPrompt().trim(),
                request.topicMode(),
                writeTags(request.focusTags()),
                request.defaultQuestionLimit(),
                request.defaultFollowUpLimit(),
                Instant.now());
        if (updated != 1) {
            throw conflict();
        }
        return toResponse(profileMapper.findById(id));
    }

    @Transactional
    public InterviewerResponse setArchived(Long userId, Long id, boolean archived) {
        requireCustom(userId, id);
        int updated = profileMapper.setArchived(id, userId, archived, Instant.now());
        if (updated != 1) {
            throw conflict();
        }
        return toResponse(profileMapper.findById(id));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        requireCustom(userId, id);
        if (sessionMapper.countByInterviewer(id) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "该面试官已被历史面试会话引用，无法删除，请改用归档");
        }
        profileMapper.deleteByIdAndUser(id, userId);
    }

    /**
     * 会话创建时取可用面试官：内置或本人可见，且未归档。
     */
    @Transactional(readOnly = true)
    public InterviewerProfileRecord requireUsable(Long userId, Long interviewerId) {
        InterviewerProfileRecord record = requireVisible(userId, interviewerId);
        if (Boolean.TRUE.equals(record.getArchived())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST,
                    "面试官已归档，请改用其他面试官或先取消归档");
        }
        return record;
    }

    public List<String> parseTags(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> tags = objectMapper.readValue(json, TAGS_TYPE);
            return tags == null ? List.of() : List.copyOf(tags);
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private InterviewerProfileRecord requireVisible(Long userId, Long id) {
        InterviewerProfileRecord record = profileMapper.findById(id);
        if (record == null || (record.getUserId() != null && !record.getUserId().equals(userId))) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "面试官不存在");
        }
        return record;
    }

    private InterviewerProfileRecord requireCustom(Long userId, Long id) {
        InterviewerProfileRecord record = requireVisible(userId, id);
        if (Boolean.TRUE.equals(record.getBuiltIn())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST,
                    "内置面试官不可修改，请复制为专属面试官后再调整");
        }
        return record;
    }

    private ApiException conflict() {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                "面试官状态已变化，请刷新后重试");
    }

    private String copyName(String sourceName) {
        String suffix = "（我的副本）";
        String base = sourceName.length() + suffix.length() <= COPY_NAME_SUFFIX_LIMIT
                ? sourceName
                : sourceName.substring(0, COPY_NAME_SUFFIX_LIMIT - suffix.length());
        return base + suffix;
    }

    private String writeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        List<String> trimmed = tags.stream().map(String::trim).filter(tag -> !tag.isEmpty()).toList();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(trimmed);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("面试官关注方向序列化失败", exception);
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private InterviewerResponse toResponse(InterviewerProfileRecord record) {
        return new InterviewerResponse(
                record.getId(),
                record.getCode(),
                record.getName(),
                record.getDescription(),
                record.getSystemPrompt(),
                record.getTopicMode(),
                parseTags(record.getFocusTagsJson()),
                record.getDefaultQuestionLimit(),
                record.getDefaultFollowUpLimit(),
                Boolean.TRUE.equals(record.getBuiltIn()),
                Boolean.TRUE.equals(record.getArchived()),
                record.getVersion(),
                record.getCreatedAt(),
                record.getUpdatedAt());
    }
}
