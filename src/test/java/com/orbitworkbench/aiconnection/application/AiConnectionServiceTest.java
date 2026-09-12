package com.orbitworkbench.aiconnection.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orbitworkbench.ai.application.AiInvocation;
import com.orbitworkbench.ai.application.AiStreamEvent;
import com.orbitworkbench.ai.application.ModelGateway;
import com.orbitworkbench.aiconnection.api.AiConnectionDtos.ConnectionResponse;
import com.orbitworkbench.aiconnection.api.AiConnectionDtos.ConnectionTestResponse;
import com.orbitworkbench.aiconnection.api.AiConnectionDtos.DraftTestRequest;
import com.orbitworkbench.aiconnection.api.AiConnectionDtos.SavedTestRequest;
import com.orbitworkbench.aiconnection.api.AiConnectionDtos.UpdateConnectionRequest;
import com.orbitworkbench.aiconnection.domain.AiConnectionRecord;
import com.orbitworkbench.aiconnection.infrastructure.mapper.AiConnectionMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionStatus;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class AiConnectionServiceTest {

    @Mock
    private AiConnectionMapper mapper;
    @Mock
    private CredentialCipher cipher;
    @Mock
    private ModelGateway gateway;
    @Mock
    private TransactionTemplate transactionTemplate;

    private AiConnectionService service;

    @BeforeEach
    void setUp() {
        service = new AiConnectionService(mapper, cipher, gateway, transactionTemplate);
    }

    @Test
    void draftConnectionTestUsesSmallOutputLimit() {
        stubDraftRecord();
        when(gateway.stream(any(AiInvocation.class))).thenReturn(Flux.just(
                AiStreamEvent.started(),
                AiStreamEvent.delta("OK"),
                new AiStreamEvent(
                        "run.completed", null, null, null, null,
                        true, null, null)));

        ConnectionTestResponse result = service.testDraft(request());

        ArgumentCaptor<AiInvocation> invocationCaptor =
                ArgumentCaptor.forClass(AiInvocation.class);
        verify(gateway).stream(invocationCaptor.capture());
        assertEquals(128, invocationCaptor.getValue().maxOutputTokens());
        assertEquals("SUCCESS", result.status());
    }

    @Test
    void draftConnectionTestPreservesCompleteRequestUrl() {
        stubDraftRecord();
        when(gateway.stream(any(AiInvocation.class))).thenReturn(Flux.just(
                AiStreamEvent.started(),
                AiStreamEvent.delta("OK"),
                new AiStreamEvent(
                        "run.completed", null, null, null, null,
                        true, null, null)));
        DraftTestRequest request = new DraftTestRequest(
                "test",
                "CUSTOM_OPENAI_COMPATIBLE",
                "https://chatapi.weixin.qq.com/openai/v1/chat/completions",
                "/chat/completions",
                "CHAT_COMPLETIONS",
                "test-model",
                "secret",
                30000,
                true,
                "Reply with OK only.");

        service.testDraft(request);

        ArgumentCaptor<AiInvocation> invocationCaptor =
                ArgumentCaptor.forClass(AiInvocation.class);
        verify(gateway).stream(invocationCaptor.capture());
        assertEquals(
                "https://chatapi.weixin.qq.com/openai/v1/chat/completions",
                invocationCaptor.getValue().connection().baseUrl());
        assertEquals(
                "/chat/completions",
                invocationCaptor.getValue().connection().endpointPath());
    }

    @Test
    void connectionTestRejectsCompletionWithoutText() {
        stubDraftRecord();
        when(gateway.stream(any(AiInvocation.class))).thenReturn(Flux.just(
                AiStreamEvent.started(),
                new AiStreamEvent(
                        "run.completed", null, null, null, null,
                        true, null, null)));

        ConnectionTestResponse result = service.testDraft(request());

        assertEquals("FAILED", result.status());
        assertEquals(ErrorCode.INVALID_STRUCTURED_OUTPUT.name(), result.errorCode());
    }

    @Test
    void connectionTestStopsWhenEventCountExceedsLimit() {
        stubDraftRecord();
        service = serviceWithLimits(2, 1000, 1000, Duration.ofSeconds(1));
        when(gateway.stream(any(AiInvocation.class))).thenReturn(Flux.just(
                AiStreamEvent.started(),
                AiStreamEvent.delta("OK"),
                AiStreamEvent.completed()));

        ConnectionTestResponse result = service.testDraft(request());

        assertEquals("FAILED", result.status());
        assertEquals(ErrorCode.OUTPUT_LIMIT_EXCEEDED.name(), result.errorCode());
    }

    @Test
    void connectionTestStopsWhenCharacterCountExceedsLimit() {
        stubDraftRecord();
        service = serviceWithLimits(100, 30, 1000, Duration.ofSeconds(1));
        when(gateway.stream(any(AiInvocation.class))).thenReturn(Flux.just(
                AiStreamEvent.started(),
                AiStreamEvent.delta("OK"),
                AiStreamEvent.completed()));

        ConnectionTestResponse result = service.testDraft(request());

        assertEquals("FAILED", result.status());
        assertEquals(ErrorCode.OUTPUT_LIMIT_EXCEEDED.name(), result.errorCode());
    }

    @Test
    void connectionTestCountsUtf8BytesSeparatelyFromCharacters() {
        stubDraftRecord();
        service = serviceWithLimits(100, 1000, 40, Duration.ofSeconds(1));
        when(gateway.stream(any(AiInvocation.class))).thenReturn(Flux.just(
                AiStreamEvent.started(),
                AiStreamEvent.delta("你"),
                AiStreamEvent.completed()));

        ConnectionTestResponse result = service.testDraft(request());

        assertEquals("FAILED", result.status());
        assertEquals(ErrorCode.OUTPUT_LIMIT_EXCEEDED.name(), result.errorCode());
    }

    @Test
    void connectionTestCancelsUpstreamAtAbsoluteTotalTimeout() {
        stubDraftRecord();
        service = serviceWithLimits(100, 1000, 1000, Duration.ofMillis(60));
        AtomicBoolean cancelled = new AtomicBoolean();
        when(gateway.stream(any(AiInvocation.class))).thenReturn(
                Flux.<AiStreamEvent>never().doOnCancel(() -> cancelled.set(true)));

        long start = System.nanoTime();
        ConnectionTestResponse result = service.testDraft(request());
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;

        assertEquals("FAILED", result.status());
        assertEquals(ErrorCode.REQUEST_TIMEOUT.name(), result.errorCode());
        assertTrue(cancelled.get());
        assertTrue(elapsedMillis < 500, "连接测试超时没有遵守绝对时长上限");
    }

    @Test
    void updateWithoutApiKeyDoesNotCarryForwardCredentialColumns() {
        AiConnectionRecord current = connection(7);
        current.setEnabled(false);
        AiConnectionRecord refreshed = connection(8);
        refreshed.setName("renamed");
        refreshed.setEnabled(false);
        when(mapper.findById(1L)).thenReturn(current, refreshed);
        when(mapper.findProviderId("CUSTOM_OPENAI_COMPATIBLE")).thenReturn(1L);
        when(mapper.updateConnection(any(AiConnectionRecord.class))).thenReturn(1);
        when(mapper.updateModelProfile(any(AiConnectionRecord.class))).thenReturn(1);

        ConnectionResponse response = service.update(1L, updateRequest(7L, "renamed", null));

        ArgumentCaptor<AiConnectionRecord> captor =
                ArgumentCaptor.forClass(AiConnectionRecord.class);
        verify(mapper).updateConnection(captor.capture());
        assertNull(captor.getValue().getCredentialCiphertext());
        assertNull(captor.getValue().getCredentialIv());
        assertNull(captor.getValue().getCredentialKeyVersion());
        assertEquals(false, captor.getValue().isEnabled());
        assertEquals(8L, response.version());
        verify(cipher, never()).encrypt(anyString());
    }

    @Test
    void concurrentConnectionUpdateReturnsConflict() {
        when(mapper.findById(1L)).thenReturn(connection(7));
        when(mapper.findProviderId("CUSTOM_OPENAI_COMPATIBLE")).thenReturn(1L);
        when(mapper.updateConnection(any(AiConnectionRecord.class))).thenReturn(0);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.update(1L, updateRequest(7L, "renamed", null)));

        assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode());
        verify(mapper, never()).updateModelProfile(any(AiConnectionRecord.class));
    }

    @Test
    void savedTestSummaryUsesConfigurationVersionFromTestStart() {
        AiConnectionRecord current = connection(7);
        when(mapper.findById(1L)).thenReturn(current);
        when(cipher.decrypt(current.getCredentialCiphertext(),
                current.getCredentialIv(), current.getCredentialKeyVersion()))
                .thenReturn("secret");
        when(gateway.stream(any(AiInvocation.class))).thenReturn(Flux.just(
                AiStreamEvent.started(),
                AiStreamEvent.delta("OK"),
                new AiStreamEvent(
                        "run.completed", null, null, null, null,
                        true, null, null)));
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        service.testSaved(1L, new SavedTestRequest(true, "OK"));

        verify(mapper).updateTestSummary(
                eq(1L), eq(7L), eq("SUCCESS"), any(Instant.class),
                any(Integer.class), eq(null), eq(null));
        ArgumentCaptor<com.orbitworkbench.aiconnection.domain.ConnectionTestRecord> captor =
                ArgumentCaptor.forClass(
                        com.orbitworkbench.aiconnection.domain.ConnectionTestRecord.class);
        verify(mapper).insertTestRecord(captor.capture());
        assertEquals(7L, captor.getValue().getConfigurationVersion());
    }

    private DraftTestRequest request() {
        return new DraftTestRequest(
                "test",
                "CUSTOM_OPENAI_COMPATIBLE",
                "https://example.invalid/v1",
                "/chat/completions",
                "CHAT_COMPLETIONS",
                "test-model",
                "secret",
                30000,
                true,
                "Reply with OK only.");
    }

    private UpdateConnectionRequest updateRequest(
            Long expectedVersion,
            String name,
            String apiKey) {
        return new UpdateConnectionRequest(
                expectedVersion,
                name,
                "CUSTOM_OPENAI_COMPATIBLE",
                "https://example.invalid/v1",
                "/chat/completions",
                "CHAT_COMPLETIONS",
                "test-model",
                apiKey,
                30000,
                null,
                null,
                null,
                null);
    }

    private AiConnectionRecord connection(long version) {
        AiConnectionRecord record = new AiConnectionRecord();
        record.setId(1L);
        record.setProviderCatalogId(1L);
        record.setProviderType("CUSTOM_OPENAI_COMPATIBLE");
        record.setName("test");
        record.setBaseUrl("https://example.invalid/v1");
        record.setEndpointPath("/chat/completions");
        record.setProtocol("CHAT_COMPLETIONS");
        record.setCredentialCiphertext(new byte[]{1});
        record.setCredentialIv(new byte[12]);
        record.setCredentialKeyVersion(1);
        record.setCredentialMasked("****");
        record.setEnabled(true);
        record.setTimeoutMs(30000);
        record.setLastTestStatus("SUCCESS");
        record.setConfigurationVersion(version);
        record.setModelProfileId(2L);
        record.setModelName("test-model");
        record.setModelDisplayName("test-model");
        record.setSupportedProtocols("[\"CHAT_COMPLETIONS\"]");
        record.setCapabilitiesJson("{\"supportsStreaming\":true}");
        record.setDefaultParametersJson("{}");
        record.setCreatedAt(Instant.parse("2026-08-19T00:00:00Z"));
        record.setUpdatedAt(Instant.parse("2026-08-19T00:00:00Z"));
        return record;
    }

    private void stubDraftRecord() {
        when(mapper.findProviderId("CUSTOM_OPENAI_COMPATIBLE")).thenReturn(1L);
        when(cipher.encrypt("secret")).thenReturn(
                new CredentialCipher.EncryptedCredential(
                        new byte[]{1}, new byte[12], 1, "****"));
    }

    private AiConnectionService serviceWithLimits(int maxEvents,
                                                  int maxCharacters,
                                                  int maxBytes,
                                                  Duration absoluteTotalDuration) {
        return new AiConnectionService(
                mapper,
                cipher,
                gateway,
                transactionTemplate,
                new AiConnectionService.ConnectionTestLimits(
                        maxEvents,
                        maxCharacters,
                        maxBytes,
                        absoluteTotalDuration,
                        Duration.ZERO));
    }
}
