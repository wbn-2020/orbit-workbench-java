package com.orbitworkbench.interviewer.infrastructure.mapper;

import com.orbitworkbench.interviewer.domain.InterviewerProfileRecord;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface InterviewerProfileMapper {

    void insert(InterviewerProfileRecord record);

    InterviewerProfileRecord findById(@Param("id") Long id);

    List<InterviewerProfileRecord> listVisible(@Param("userId") Long userId,
                                               @Param("includeArchived") boolean includeArchived);

    int updateProfile(@Param("id") Long id,
                      @Param("userId") Long userId,
                      @Param("name") String name,
                      @Param("description") String description,
                      @Param("systemPrompt") String systemPrompt,
                      @Param("topicMode") String topicMode,
                      @Param("focusTagsJson") String focusTagsJson,
                      @Param("defaultQuestionLimit") Integer defaultQuestionLimit,
                      @Param("defaultFollowUpLimit") Integer defaultFollowUpLimit,
                      @Param("updatedAt") Instant updatedAt);

    int setArchived(@Param("id") Long id,
                    @Param("userId") Long userId,
                    @Param("archived") boolean archived,
                    @Param("updatedAt") Instant updatedAt);

    int deleteByIdAndUser(@Param("id") Long id,
                          @Param("userId") Long userId);
}
