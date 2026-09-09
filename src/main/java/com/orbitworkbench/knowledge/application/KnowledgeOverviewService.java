package com.orbitworkbench.knowledge.application;

import com.orbitworkbench.knowledge.infrastructure.mapper.KnowledgeChunkMapper;
import com.orbitworkbench.knowledge.infrastructure.mapper.ProjectFactMapper;
import com.orbitworkbench.worklog.infrastructure.mapper.KnowledgeCardMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 知识总览聚合（V2-B1）。只读：把项目事实、知识卡片、项目知识块三类资产的
 * 计数与最近条目聚成一份响应，不写任何表。三套体系各自保留原入口，
 * 这里只做「我积累了什么」的一屏回答。
 */
@Service
public class KnowledgeOverviewService {

    /** 最近条目各取 5 条：总览页一屏可读，不滚动。 */
    private static final int RECENT_LIMIT = 5;

    private final ProjectFactMapper factMapper;
    private final KnowledgeCardMapper cardMapper;
    private final KnowledgeChunkMapper chunkMapper;

    public KnowledgeOverviewService(ProjectFactMapper factMapper,
                                    KnowledgeCardMapper cardMapper,
                                    KnowledgeChunkMapper chunkMapper) {
        this.factMapper = factMapper;
        this.cardMapper = cardMapper;
        this.chunkMapper = chunkMapper;
    }

    @Transactional(readOnly = true)
    public KnowledgeOverviewResponse overview(Long userId) {
        long confirmedFacts = factMapper.countByStatus(userId, "CONFIRMED");
        long analyzedFacts = factMapper.countByStatus(userId, "ANALYZED");
        long archivedFacts = factMapper.countByStatus(userId, "ARCHIVED");

        List<FactItem> facts = factMapper.recentByStatus(userId, "CONFIRMED", RECENT_LIMIT).stream()
                .map(fact -> new FactItem(fact.getId(), fact.getTitle(), fact.getContent(), fact.getCreatedAt()))
                .toList();
        List<CardItem> cards = cardMapper.listByUser(userId, RECENT_LIMIT, 0).stream()
                .map(card -> new CardItem(card.getId(), card.getTitle(), card.getSummary(), card.getCreatedAt()))
                .toList();

        long chunkCount = chunkMapper.countByUser(userId);

        return new KnowledgeOverviewResponse(
                new FactSection(confirmedFacts, analyzedFacts, archivedFacts, facts),
                new CardSection(cardMapper.countByUser(userId), cards),
                new ChunkSection(chunkCount));
    }

    // ---------- DTO ----------

    public record FactItem(Long id, String title, String content, java.time.Instant createdAt) {}

    public record CardItem(Long id, String title, String summary, java.time.Instant createdAt) {}

    public record FactSection(long confirmed, long analyzed, long archived, List<FactItem> recent) {}

    public record CardSection(long total, List<CardItem> recent) {}

    /** 知识块只有计数：块是检索切片，单块没有独立阅读价值，入口留在项目详情与问答页。 */
    public record ChunkSection(long total) {}

    public record KnowledgeOverviewResponse(
            FactSection projectFacts,
            CardSection knowledgeCards,
            ChunkSection projectChunks) {}
}
