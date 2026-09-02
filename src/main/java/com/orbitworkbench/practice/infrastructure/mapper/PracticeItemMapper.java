package com.orbitworkbench.practice.infrastructure.mapper;

import com.orbitworkbench.practice.domain.MasteryStatus;
import com.orbitworkbench.practice.domain.PracticeItemRecord;
import com.orbitworkbench.practice.domain.PracticeItemRow;
import com.orbitworkbench.practice.domain.PracticeSource;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface PracticeItemMapper {

    void insert(PracticeItemRecord record);

    PracticeItemRecord findById(@Param("id") Long id);

    PracticeItemRow findOwned(@Param("userId") Long userId, @Param("itemId") Long itemId);

    List<PracticeItemRow> listByUser(
            @Param("userId") Long userId,
            @Param("mastery") MasteryStatus mastery,
            @Param("sourceType") PracticeSource sourceType,
            @Param("topic") String topic,
            @Param("archived") Boolean archived,
            @Param("limit") int limit,
            @Param("offset") int offset);

    long countByUser(
            @Param("userId") Long userId,
            @Param("mastery") MasteryStatus mastery,
            @Param("sourceType") PracticeSource sourceType,
            @Param("topic") String topic,
            @Param("archived") Boolean archived);

    List<String> listTopics(@Param("userId") Long userId, @Param("archived") Boolean archived);

    /** 入队幂等：同一来源的同一文本只应有一条（`15` §4，沿用复习任务 from-report 的做法）。 */
    int countSameSourceText(
            @Param("userId") Long userId,
            @Param("sourceType") PracticeSource sourceType,
            @Param("sourceId") Long sourceId,
            @Param("question") String question);

    /**
     * 一次重练后的两个派生结果一起落：掌握状态与按阶梯推进的复习日。
     * 复习日无条件覆盖原值（含用户手设值），口径见 `15` §11 第二条。
     */
    int updateAfterAttempt(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("mastery") MasteryStatus mastery,
            @Param("nextReviewDate") LocalDate nextReviewDate,
            @Param("updatedAt") Instant updatedAt);

    int updateArchived(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("archived") boolean archived,
            @Param("updatedAt") Instant updatedAt);

    int updateClassification(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("topic") String topic,
            @Param("referenceAnswer") String referenceAnswer,
            @Param("nextReviewDate") LocalDate nextReviewDate,
            @Param("expectedUpdatedAt") Instant expectedUpdatedAt,
            @Param("updatedAt") Instant updatedAt);
}
