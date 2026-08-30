package com.orbitworkbench.jobprofile.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orbitworkbench.jobprofile.api.JobProfileDtos.JobProfileRequest;
import com.orbitworkbench.jobprofile.domain.JobProfileRecord;
import com.orbitworkbench.jobprofile.infrastructure.mapper.JobProfileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobProfileServiceTest {

    @Mock
    private JobProfileMapper mapper;

    private JobProfileService service;

    @BeforeEach
    void setUp() {
        service = new JobProfileService(mapper);
    }

    @Test
    void saveCreatesProfileForCurrentUser() {
        when(mapper.findByUserId(7L)).thenReturn(null);
        doAnswer(invocation -> {
            invocation.<JobProfileRecord>getArgument(0).setId(18L);
            return null;
        }).when(mapper).insert(any(JobProfileRecord.class));

        var response = service.save(7L, request());

        ArgumentCaptor<JobProfileRecord> captor = ArgumentCaptor.forClass(JobProfileRecord.class);
        verify(mapper).insert(captor.capture());
        assertEquals(18L, response.id());
        assertEquals(7L, captor.getValue().getUserId());
        assertEquals("Java + AI 应用开发", captor.getValue().getTargetRole());
        assertEquals("THREE_TO_FIVE_YEARS", captor.getValue().getTargetExperienceBand());
    }

    @Test
    void saveUpdatesExistingProfile() {
        JobProfileRecord profile = new JobProfileRecord();
        profile.setId(18L);
        profile.setUserId(7L);
        when(mapper.findByUserId(7L)).thenReturn(profile);
        when(mapper.update(profile)).thenReturn(1);

        var response = service.save(7L, request());

        verify(mapper).update(profile);
        assertEquals(18L, response.id());
        assertEquals("PRACTICAL", profile.getJavaSkillLevel());
    }

    private JobProfileRequest request() {
        return new JobProfileRequest(
                " Java + AI 应用开发 ",
                "THREE_TO_FIVE_YEARS",
                "CAREER_TRANSITION",
                "MIDDLE",
                "示例公司",
                "PRACTICAL",
                "WORKING_KNOWLEDGE",
                null);
    }
}
