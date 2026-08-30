package com.orbitworkbench.interviewer.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orbitworkbench.interview.infrastructure.mapper.InterviewSessionMapper;
import com.orbitworkbench.interviewer.api.InterviewerDtos.CopyInterviewerRequest;
import com.orbitworkbench.interviewer.api.InterviewerDtos.CreateInterviewerRequest;
import com.orbitworkbench.interviewer.api.InterviewerDtos.InterviewerResponse;
import com.orbitworkbench.interviewer.api.InterviewerDtos.UpdateInterviewerRequest;
import com.orbitworkbench.interviewer.domain.InterviewerProfileRecord;
import com.orbitworkbench.interviewer.infrastructure.mapper.InterviewerProfileMapper;
import com.orbitworkbench.shared.api.ApiException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InterviewerServiceTest {

    @Mock
    private InterviewerProfileMapper profileMapper;

    @Mock
    private InterviewSessionMapper sessionMapper;

    private InterviewerService service;

    @BeforeEach
    void setUp() {
        service = new InterviewerService(profileMapper, sessionMapper,
                new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @Test
    void createPersistsCustomProfileWithTags() {
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.<InterviewerProfileRecord>getArgument(0).setId(31L);
            return null;
        }).when(profileMapper).insert(any(InterviewerProfileRecord.class));
        InterviewerProfileRecord saved = custom(31L, "秒杀项目深挖官");
        saved.setFocusTagsJson("[\"秒杀\",\"高并发\"]");
        when(profileMapper.findById(31L)).thenReturn(saved);

        InterviewerResponse response = service.create(7L, new CreateInterviewerRequest(
                "秒杀项目深挖官", "专攻秒杀项目", "请围绕秒杀链路提问", "PROJECT_DEEP_DIVE",
                List.of("秒杀", "高并发"), 6, 4));

        ArgumentCaptor<InterviewerProfileRecord> captor =
                ArgumentCaptor.forClass(InterviewerProfileRecord.class);
        verify(profileMapper).insert(captor.capture());
        assertEquals(7L, captor.getValue().getUserId());
        assertEquals(false, captor.getValue().getBuiltIn());
        assertEquals(false, captor.getValue().getArchived());
        assertEquals("[\"秒杀\",\"高并发\"]", captor.getValue().getFocusTagsJson());
        assertEquals(List.of("秒杀", "高并发"), response.focusTags());
        assertEquals(31L, response.id());
    }

    @Test
    void copyBuiltInCreatesCustomCopy() {
        InterviewerProfileRecord builtIn = builtIn(1L, "项目深挖面试官");
        when(profileMapper.findById(1L)).thenReturn(builtIn);
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.<InterviewerProfileRecord>getArgument(0).setId(32L);
            return null;
        }).when(profileMapper).insert(any(InterviewerProfileRecord.class));
        InterviewerProfileRecord copy = custom(32L, "项目深挖面试官（我的副本）");
        when(profileMapper.findById(32L)).thenReturn(copy);

        InterviewerResponse response = service.copy(7L, 1L, new CopyInterviewerRequest(null));

        ArgumentCaptor<InterviewerProfileRecord> captor =
                ArgumentCaptor.forClass(InterviewerProfileRecord.class);
        verify(profileMapper).insert(captor.capture());
        assertEquals(7L, captor.getValue().getUserId());
        assertEquals(false, captor.getValue().getBuiltIn());
        assertEquals("项目深挖面试官（我的副本）", captor.getValue().getName());
        assertEquals(builtIn.getSystemPrompt(), captor.getValue().getSystemPrompt());
        assertEquals(32L, response.id());
    }

    @Test
    void updateRejectsBuiltInProfile() {
        when(profileMapper.findById(1L)).thenReturn(builtIn(1L, "项目深挖面试官"));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.update(7L, 1L, new UpdateInterviewerRequest(
                        "改名", "描述", "提示词", "ROTE", List.of(), 5, 2)));
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, exception.getStatus());
        verify(profileMapper, never()).updateProfile(any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any());
    }

    @Test
    void archiveRejectsBuiltInProfile() {
        when(profileMapper.findById(1L)).thenReturn(builtIn(1L, "项目深挖面试官"));

        assertThrows(ApiException.class, () -> service.setArchived(7L, 1L, true));
        verify(profileMapper, never()).setArchived(any(), any(), org.mockito.ArgumentMatchers.anyBoolean(), any());
    }

    @Test
    void deleteRejectsProfileReferencedBySessions() {
        when(profileMapper.findById(31L)).thenReturn(custom(31L, "秒杀项目深挖官"));
        when(sessionMapper.countByInterviewer(31L)).thenReturn(2);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.delete(7L, 31L));
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, exception.getStatus());
        verify(profileMapper, never()).deleteByIdAndUser(any(), any());
    }

    @Test
    void deleteSucceedsForUnreferencedCustomProfile() {
        when(profileMapper.findById(31L)).thenReturn(custom(31L, "秒杀项目深挖官"));
        when(sessionMapper.countByInterviewer(31L)).thenReturn(0);

        service.delete(7L, 31L);

        verify(profileMapper).deleteByIdAndUser(31L, 7L);
    }

    @Test
    void requireUsableRejectsArchivedProfile() {
        InterviewerProfileRecord archived = custom(31L, "秒杀项目深挖官");
        archived.setArchived(true);
        when(profileMapper.findById(31L)).thenReturn(archived);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.requireUsable(7L, 31L));
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void requireUsableRejectsAnotherUsersProfile() {
        InterviewerProfileRecord other = custom(31L, "别人的面试官");
        other.setUserId(8L);
        when(profileMapper.findById(31L)).thenReturn(other);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.requireUsable(7L, 31L));
        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, exception.getStatus());
    }

    private InterviewerProfileRecord builtIn(Long id, String name) {
        InterviewerProfileRecord record = new InterviewerProfileRecord();
        record.setId(id);
        record.setUserId(null);
        record.setName(name);
        record.setDescription("内置");
        record.setSystemPrompt("内置提示词");
        record.setTopicMode("PROJECT_DEEP_DIVE");
        record.setFocusTagsJson(null);
        record.setDefaultQuestionLimit(6);
        record.setDefaultFollowUpLimit(5);
        record.setBuiltIn(true);
        record.setArchived(false);
        record.setVersion(1);
        return record;
    }

    private InterviewerProfileRecord custom(Long id, String name) {
        InterviewerProfileRecord record = builtIn(id, name);
        record.setUserId(7L);
        record.setBuiltIn(false);
        return record;
    }
}
