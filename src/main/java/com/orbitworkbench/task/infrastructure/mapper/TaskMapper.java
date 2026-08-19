package com.orbitworkbench.task.infrastructure.mapper;

import com.orbitworkbench.task.domain.TaskRecord;
import com.orbitworkbench.task.domain.TaskCreateIdempotencyRecord;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface TaskMapper {

    int insert(TaskRecord task);

    int insertCreateReservation(@Param("idempotencyKey") String idempotencyKey,
                                @Param("requestFingerprint") String requestFingerprint,
                                @Param("now") java.time.Instant now);

    TaskCreateIdempotencyRecord findCreateReservationForUpdate(
            @Param("idempotencyKey") String idempotencyKey);

    int attachCreatedTask(@Param("idempotencyKey") String idempotencyKey,
                          @Param("requestFingerprint") String requestFingerprint,
                          @Param("taskId") Long taskId,
                          @Param("now") java.time.Instant now);

    TaskRecord findById(@Param("id") Long id);

    TaskRecord findByIdForUpdate(@Param("id") Long id);

    List<TaskRecord> findPage(@Param("workspaceId") Long workspaceId,
                              @Param("status") String status,
                              @Param("moduleType") String moduleType,
                              @Param("offset") int offset,
                              @Param("limit") int limit);

    long countPage(@Param("workspaceId") Long workspaceId,
                   @Param("status") String status,
                   @Param("moduleType") String moduleType);

    int updateEditable(TaskRecord task);

    int updateRunState(@Param("id") Long id,
                       @Param("currentRunId") Long currentRunId,
                       @Param("status") String status,
                       @Param("fromStatus") String fromStatus);

    int updateStatusForCurrentRun(@Param("id") Long id,
                                  @Param("currentRunId") Long currentRunId,
                                  @Param("status") String status);

    Long lockActiveWorkspace(@Param("workspaceId") Long workspaceId);

    Long lockUsableConnection(@Param("connectionId") Long connectionId);

    List<Long> lockDocuments(@Param("workspaceId") Long workspaceId,
                             @Param("documentIds") List<Long> documentIds);

    void insertTaskDocument(@Param("taskId") Long taskId,
                            @Param("documentId") Long documentId,
                            @Param("relationType") String relationType);

    void deleteTaskDocuments(@Param("taskId") Long taskId);

    List<Long> findDocumentIds(@Param("taskId") Long taskId);
}
