package com.orbitworkbench.focus.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orbitworkbench.focus.infrastructure.mapper.FocusSessionMapper;
import com.orbitworkbench.focus.domain.FocusSessionRow;
import com.orbitworkbench.preference.application.PreferenceService;
import com.orbitworkbench.shared.api.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FocusSessionServiceTest {

    private static final ZoneId LOS_ANGELES = ZoneId.of("America/Los_Angeles");
    private static final Instant FIXED_NOW = Instant.parse("2026-09-07T00:30:00Z");

    @Mock
    private FocusSessionMapper mapper;
    @Mock
    private PreferenceService preferenceService;

    @Test
    void statsUsesUserTimezoneForDateWindowAndSqlAggregation() {
        when(preferenceService.timezone(1L)).thenReturn(LOS_ANGELES);
        FocusSessionRow session = new FocusSessionRow();
        session.setStartedAt(Instant.parse("2026-09-06T08:00:00Z"));
        session.setDurationMinutes(25);
        when(mapper.listFocusSince(eq(1L), any())).thenReturn(List.of(session));

        FocusSessionService service = new FocusSessionService(
                mapper, preferenceService, Clock.fixed(FIXED_NOW, ZoneId.of("UTC")));

        List<?> result = service.stats(1L, "2");

        verify(mapper).listFocusSince(eq(1L), eq(Instant.parse("2026-09-05T07:00:00Z")));
        assertEquals(2, result.size());
        assertEquals("2026-09-05", ((com.orbitworkbench.focus.api.FocusDtos.FocusStatResponse)
                result.get(0)).date());
        assertEquals(0, ((com.orbitworkbench.focus.api.FocusDtos.FocusStatResponse)
                result.get(0)).focusMinutes());
        assertEquals("2026-09-06", ((com.orbitworkbench.focus.api.FocusDtos.FocusStatResponse)
                result.get(1)).date());
        assertEquals(25, ((com.orbitworkbench.focus.api.FocusDtos.FocusStatResponse)
                result.get(1)).focusMinutes());
    }

    @Test
    void saveRejectsFutureOrNotYetCompletedSession() {
        Instant now = Instant.parse("2026-09-07T00:30:00Z");
        FocusSessionService service = new FocusSessionService(
                mapper, preferenceService, Clock.fixed(now, ZoneId.of("UTC")));

        assertThrows(ApiException.class, () -> service.save(1L,
                new com.orbitworkbench.focus.api.FocusDtos.SaveFocusSessionRequest(
                        "2026-09-07T00:31:00Z", 25, "focus", null),
                "future"));
        assertThrows(ApiException.class, () -> service.save(1L,
                new com.orbitworkbench.focus.api.FocusDtos.SaveFocusSessionRequest(
                        "2026-09-07T00:10:00Z", 25, "focus", null),
                "in-progress"));
    }

    @Test
    void saveReplaysExistingRowForSameIdempotencyKeyAndContent() {
        Instant now = Instant.parse("2026-09-07T00:30:00Z");
        FocusSessionRow existing = new FocusSessionRow();
        existing.setId(11L);
        existing.setStartedAt(Instant.parse("2026-09-07T00:05:00Z"));
        existing.setDurationMinutes(25);
        existing.setMode(com.orbitworkbench.focus.domain.FocusMode.FOCUS);
        existing.setLabel(null);
        when(mapper.findByIdempotencyKey(1L, "key-1")).thenReturn(existing);
        FocusSessionService service = new FocusSessionService(
                mapper, preferenceService, Clock.fixed(now, ZoneId.of("UTC")));

        var response = service.save(1L,
                new com.orbitworkbench.focus.api.FocusDtos.SaveFocusSessionRequest(
                        "2026-09-07T00:05:00Z", 25, "focus", null),
                "key-1");

        assertEquals("11", response.id());
        verify(mapper, org.mockito.Mockito.never()).insert(any());
    }

    @Test
    void saveConflictsWhenIdempotencyKeyReusedWithDifferentContent() {
        Instant now = Instant.parse("2026-09-07T00:30:00Z");
        FocusSessionRow existing = new FocusSessionRow();
        existing.setId(11L);
        existing.setStartedAt(Instant.parse("2026-09-07T00:05:00Z"));
        existing.setDurationMinutes(25);
        existing.setMode(com.orbitworkbench.focus.domain.FocusMode.FOCUS);
        existing.setLabel(null);
        when(mapper.findByIdempotencyKey(1L, "key-1")).thenReturn(existing);
        FocusSessionService service = new FocusSessionService(
                mapper, preferenceService, Clock.fixed(now, ZoneId.of("UTC")));

        // 同键、起点相同但时长不同（00:05+20min=00:25 未越过允许上界 00:31，能穿过时间窗校验）
        ApiException exception = assertThrows(ApiException.class, () -> service.save(1L,
                new com.orbitworkbench.focus.api.FocusDtos.SaveFocusSessionRequest(
                        "2026-09-07T00:05:00Z", 20, "focus", null),
                "key-1"));

        assertEquals(org.springframework.http.HttpStatus.CONFLICT, exception.getStatus());
        verify(mapper, org.mockito.Mockito.never()).insert(any());
    }
}
