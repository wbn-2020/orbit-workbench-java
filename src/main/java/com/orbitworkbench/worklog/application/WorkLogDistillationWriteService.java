package com.orbitworkbench.worklog.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.worklog.domain.KnowledgeCardRecord;
import com.orbitworkbench.worklog.infrastructure.mapper.KnowledgeCardMapper;
import com.orbitworkbench.worklog.infrastructure.mapper.WorkLogMapper;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 知识蒸馏结果的短事务写回。
 *
 * <p>AI 调用发生在事务外；卡片写入与来源记录标记必须由 Spring 代理包在同一事务中。
 */
@Service
public class WorkLogDistillationWriteService {

    private final WorkLogMapper workLogMapper;
    private final KnowledgeCardMapper knowledgeCardMapper;
    private final ObjectMapper objectMapper;

    public WorkLogDistillationWriteService(WorkLogMapper workLogMapper,
                                           KnowledgeCardMapper knowledgeCardMapper,
                                           ObjectMapper objectMapper) {
        this.workLogMapper = workLogMapper;
        this.knowledgeCardMapper = knowledgeCardMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public KnowledgeCardRecord persist(Long userId, Long sourceLogId, String title,
                                       String summary, List<String> tags) {
        Instant now = Instant.now();
        KnowledgeCardRecord card = new KnowledgeCardRecord();
        card.setUserId(userId);
        card.setSourceLogId(sourceLogId);
        card.setTitle(title);
        card.setSummary(summary);
        card.setTagsJson(KnowledgeCardService.serializeTags(objectMapper, tags));
        card.setVersion(1);
        card.setCreatedAt(now);
        card.setUpdatedAt(now);
        knowledgeCardMapper.insert(card);
        workLogMapper.markDistilled(sourceLogId, userId, now);
        return card;
    }
}
