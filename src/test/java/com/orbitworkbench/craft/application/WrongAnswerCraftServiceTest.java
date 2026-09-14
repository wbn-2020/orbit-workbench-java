package com.orbitworkbench.craft.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.orbitworkbench.craft.api.CraftDtos.CraftForWrongAnswerResponse;
import com.orbitworkbench.craft.domain.CraftNoteRecord;
import com.orbitworkbench.craft.domain.CraftSource;
import com.orbitworkbench.craft.domain.CraftStatus;
import com.orbitworkbench.craft.infrastructure.mapper.CraftNoteMapper;
import com.orbitworkbench.practice.domain.PracticeItemRow;
import com.orbitworkbench.practice.infrastructure.mapper.PracticeItemMapper;
import com.orbitworkbench.shared.api.ApiException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WrongAnswerCraftServiceTest {

    @Mock private PracticeItemMapper itemMapper;
    @Mock private CraftNoteMapper craftMapper;

    private WrongAnswerCraftService service;

    @BeforeEach
    void setUp() {
        service = new WrongAnswerCraftService(itemMapper, craftMapper);
    }

    private static PracticeItemRow item(String topic, String question, String referenceAnswer) {
        PracticeItemRow row = new PracticeItemRow();
        row.setItemId(100L);
        row.setTopic(topic);
        row.setQuestion(question);
        row.setReferenceAnswer(referenceAnswer);
        return row;
    }

    private static CraftNoteRecord craft(long id, String title, String whenToUse, String content) {
        CraftNoteRecord r = new CraftNoteRecord();
        r.setId(id);
        r.setUserId(7L);
        r.setCategory("PLAYBOOK");
        r.setTitle(title);
        r.setWhenToUse(whenToUse);
        r.setContent(content);
        r.setTagsJson("[]");
        r.setSource(CraftSource.USER_ENTERED);
        r.setConfirmationStatus(CraftStatus.CONFIRMED);
        return r;
    }

    @Test
    void missingItemIsFourOhFour() {
        when(itemMapper.findOwned(7L, 100L)).thenReturn(null);

        ApiException error = assertThrows(ApiException.class, () -> service.forItem(7L, 100L));
        assertTrue(error.getMessage().contains("错题条目不存在"));
    }

    @Test
    void wrongAnswerTextMatchesDimensionCraft() {
        when(itemMapper.findOwned(7L, 100L)).thenReturn(
                item("并发", "线上服务偶发死锁，如何排查？", "先看线程栈定位持锁线程"));
        when(craftMapper.listConfirmedForPrompt(7L)).thenReturn(List.of(
                craft(1L, "并发问题五步排查法", "排查线上并发异常时", "1. 定位入口线程"),
                craft(2L, "架构选型三问法", "做技术选型取舍时", "1. 列约束 2. 对比方案")));

        CraftForWrongAnswerResponse response = service.forItem(7L, 100L);

        assertEquals(1, response.items().size());
        assertEquals(1L, response.items().get(0).craftId());
        assertEquals("排障与异常恢复", response.items().get(0).matchedDimension());
        assertTrue(response.note().contains("相关不等于因果"));
    }

    @Test
    void noKeywordHitMeansHonestEmpty() {
        when(itemMapper.findOwned(7L, 100L)).thenReturn(
                item("冷僻", "齐王为什么不想吃荔枝？", "因为季节不对"));
        when(craftMapper.listConfirmedForPrompt(7L)).thenReturn(List.of(
                craft(1L, "并发问题五步排查法", "排查线上并发异常时", "定位入口线程")));

        CraftForWrongAnswerResponse response = service.forItem(7L, 100L);

        assertTrue(response.items().isEmpty());
        assertTrue(response.note().contains("帮不上"));
    }

    @Test
    void dimensionHitButEmptyCraftPoolSuggestsDistill() {
        when(itemMapper.findOwned(7L, 100L)).thenReturn(
                item("缓存", "缓存击穿导致线上故障如何排查？", "加互斥锁恢复"));
        when(craftMapper.listConfirmedForPrompt(7L)).thenReturn(List.of());

        CraftForWrongAnswerResponse response = service.forItem(7L, 100L);

        assertTrue(response.items().isEmpty());
        assertTrue(response.note().contains("提炼套路"));
    }

    @Test
    void masteredCraftsAreStillSuggested() {
        when(itemMapper.findOwned(7L, 100L)).thenReturn(
                item("并发", "死锁怎么排查？", "jstack 看持锁线程"));
        CraftNoteRecord practiced = craft(1L, "并发问题五步排查法", "排查异常时", "排查步骤");
        practiced.setPracticeCount(2);
        when(craftMapper.listConfirmedForPrompt(7L)).thenReturn(List.of(practiced));

        CraftForWrongAnswerResponse response = service.forItem(7L, 100L);

        assertEquals(1, response.items().size());
        assertTrue(response.items().get(0).mastered());
    }

    @Test
    void oneCraftServesOnlyOneDimension() {
        when(itemMapper.findOwned(7L, 100L)).thenReturn(
                item("综合", "线上事故后如何排查分析并复盘表达？", "先排查再分析最后表达结论"));
        when(craftMapper.listConfirmedForPrompt(7L)).thenReturn(List.of(
                craft(9L, "线上事故定位与修复", "排查分析表达类问题", "先排查再分析最后验证表达")));

        CraftForWrongAnswerResponse response = service.forItem(7L, 100L);

        assertEquals(1, response.items().size()); // 同一条套路不重复占两个维度位
        assertEquals(9L, response.items().get(0).craftId());
    }

    @Test
    void referenceAnswerIsClippedOutOfHaystackBeyondLimit() {
        // 参考答案 200 字之后的「架构 选型 取舍」不应参与命中统计（避免噪声）
        String filler = "记".repeat(210);
        when(itemMapper.findOwned(7L, 100L)).thenReturn(
                item("并发", "偶发超时怎么办？", filler + "架构选型取舍"));
        when(craftMapper.listConfirmedForPrompt(7L)).thenReturn(List.of(
                craft(2L, "架构选型三问法", "做技术选型取舍时", "列约束 对比方案 定夺")));

        CraftForWrongAnswerResponse response = service.forItem(7L, 100L);

        assertTrue(response.items().stream().noneMatch(c -> c.craftId().equals(2L)));
    }

    @Test
    void blankItemTextMeansNoDimension() {
        when(itemMapper.findOwned(7L, 100L)).thenReturn(item(null, null, null));
        when(craftMapper.listConfirmedForPrompt(anyLong())).thenReturn(List.of());

        CraftForWrongAnswerResponse response = service.forItem(7L, 100L);

        assertTrue(response.items().isEmpty());
        assertTrue(response.note().contains("帮不上"));
    }
}
