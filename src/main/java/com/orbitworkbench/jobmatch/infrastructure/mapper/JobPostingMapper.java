package com.orbitworkbench.jobmatch.infrastructure.mapper;

import com.orbitworkbench.jobmatch.domain.JobPostingListRow;
import com.orbitworkbench.jobmatch.domain.JobPostingRecord;
import com.orbitworkbench.jobmatch.domain.JobPostingVersionRecord;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface JobPostingMapper {

    void insert(JobPostingRecord record);

    JobPostingRecord findOwned(@Param("userId") Long userId, @Param("id") Long id);

    /** 不带用户条件：调用方必须先经 findOwned 确认归属，这条只用于已确认归属后的读取。 */
    JobPostingRecord findById(@Param("id") Long id);

    int updateMeta(@Param("id") Long id,
                   @Param("userId") Long userId,
                   @Param("applicationId") Long applicationId,
                   @Param("company") String company,
                   @Param("title") String title,
                   @Param("city") String city,
                   @Param("salaryNote") String salaryNote,
                   @Param("source") String source,
                   @Param("expectedUpdatedAt") Instant expectedUpdatedAt,
                   @Param("updatedAt") Instant updatedAt);

    /** 回填活动版本；新建岗位的第三步与后续切版本共用（`17` §6）。 */
    int updateActiveVersion(@Param("id") Long id,
                            @Param("userId") Long userId,
                            @Param("versionId") Long versionId,
                            @Param("updatedAt") Instant updatedAt);

    int updateArchived(@Param("id") Long id,
                       @Param("userId") Long userId,
                       @Param("archived") boolean archived,
                       @Param("updatedAt") Instant updatedAt);

    List<JobPostingListRow> listByUser(@Param("userId") Long userId,
                                       @Param("archived") Boolean archived,
                                       @Param("keyword") String keyword,
                                       @Param("limit") int limit,
                                       @Param("offset") int offset);

    long countByUser(@Param("userId") Long userId,
                     @Param("archived") Boolean archived,
                     @Param("keyword") String keyword);

    void insertVersion(JobPostingVersionRecord record);

    JobPostingVersionRecord findVersion(@Param("versionId") Long versionId);

    /** 版本必须属于指定岗位，跨岗位引用一律查不到（返回 null 由服务层转 404）。 */
    JobPostingVersionRecord findVersionOfPosting(@Param("postingId") Long postingId,
                                                 @Param("versionId") Long versionId);

    List<JobPostingVersionRecord> listVersions(@Param("postingId") Long postingId);

    Integer maxVersionNumber(@Param("postingId") Long postingId);
}
