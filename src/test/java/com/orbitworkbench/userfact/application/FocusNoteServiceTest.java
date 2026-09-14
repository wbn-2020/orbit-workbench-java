package com.orbitworkbench.userfact.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orbitworkbench.userfact.api.UserFactDtos.FocusNoteResponse;
import com.orbitworkbench.userfact.domain.UserFocusNoteRecord;
import com.orbitworkbench.userfact.infrastructure.mapper.UserFocusNoteMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FocusNoteServiceTest {

    @Mock
    private UserFocusNoteMapper focusMapper;

    private FocusNoteService service;

    @BeforeEach
    void setUp() {
        service = new FocusNoteService(focusMapper);
    }

    private static UserFocusNoteRecord record(String content, Instant expiresAt) {
        UserFocusNoteRecord r = new UserFocusNoteRecord();
        r.setId(1L);
        r.setUserId(1L);
        r.setContent(content);
        r.setExpiresAt(expiresAt);
        r.setCreatedAt(Instant.now());
        r.setUpdatedAt(Instant.now());
        return r;
    }

    @Test
    void freshFocusIsInjectedWithDeadlineHint() {
        Instant expiry = Instant.now().plus(3, ChronoUnit.DAYS);
        when(focusMapper.findByUser(1L)).thenReturn(record("准备字节二面", expiry));

        String text = service.freshFocusForInjection(1L);

        assertNotNull(text);
        assertTrue(text.contains("准备字节二面"));
        assertTrue(text.contains("关注截止"), text);
        assertTrue(text.contains("短期信号"), text);
    }

    @Test
    void expiredFocusIsNotInjected() {
        when(focusMapper.findByUser(1L))
                .thenReturn(record("过期关注", Instant.now().minus(1, ChronoUnit.DAYS)));

        assertNull(service.freshFocusForInjection(1L));
    }

    @Test
    void focusWithoutExpiryNeverExpires() {
        when(focusMapper.findByUser(1L)).thenReturn(record("无截止关注", null));

        String text = service.freshFocusForInjection(1L);

        assertNotNull(text);
        assertTrue(text.contains("无截止关注"));
        assertFalse(text.contains("关注截止"), "无失效时刻不应显示截止提示：" + text);
    }

    @Test
    void missingFocusYieldsNullCurrentAndNullInjection() {
        when(focusMapper.findByUser(1L)).thenReturn(null);

        assertNull(service.current(1L));
        assertNull(service.freshFocusForInjection(1L));
    }

    @Test
    void currentReportsExpiredFlag() {
        when(focusMapper.findByUser(1L))
                .thenReturn(record("陈关注", Instant.now().minus(1, ChronoUnit.DAYS)));

        FocusNoteResponse response = service.current(1L);

        assertTrue(response.expired());
        assertEquals("陈关注", response.content());
    }

    @Test
    void saveConvertsDaysToExpiryTime() {
        when(focusMapper.findByUser(1L)).thenReturn(record("新关注", Instant.now().plus(7, ChronoUnit.DAYS)));

        service.save(1L, "  新关注  ", 7);

        // 保存即覆盖，且内容去首尾空白
        verify(focusMapper).upsert(any(UserFocusNoteRecord.class));
    }

    @Test
    void saveWithoutDaysLeavesNoExpiry() {
        when(focusMapper.findByUser(1L)).thenReturn(record("长期关注", null));

        service.save(1L, "长期关注", null);

        verify(focusMapper).upsert(any(UserFocusNoteRecord.class));
    }
}
