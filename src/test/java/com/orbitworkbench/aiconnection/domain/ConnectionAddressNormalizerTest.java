package com.orbitworkbench.aiconnection.domain;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

class ConnectionAddressNormalizerTest {

    private final ConnectionAddressNormalizer normalizer = new ConnectionAddressNormalizer();

    @Test
    void normalizesBasePathAndUsesChatCompletionsByDefault() {
        ConnectionAddressNormalizer.NormalizedAddress result = normalizer.normalize(
                " https://api.example.com//v1/ ",
                null,
                AiProtocol.CHAT_COMPLETIONS);

        assertAll(
                () -> assertEquals("https://api.example.com/v1", result.baseUrl()),
                () -> assertEquals("/chat/completions", result.endpointPath()));
    }

    @Test
    void usesResponsesEndpointForResponsesProtocol() {
        ConnectionAddressNormalizer.NormalizedAddress result = normalizer.normalize(
                "https://api.example.com/v1",
                " ",
                AiProtocol.RESPONSES);

        assertAll(
                () -> assertEquals("https://api.example.com/v1", result.baseUrl()),
                () -> assertEquals("/responses", result.endpointPath()));
    }

    @Test
    void extractsEndpointAlreadyIncludedInBaseUrl() {
        ConnectionAddressNormalizer.NormalizedAddress result = normalizer.normalize(
                "https://api.example.com/v1/chat/completions/",
                null,
                AiProtocol.CHAT_COMPLETIONS);

        assertAll(
                () -> assertEquals("https://api.example.com/v1", result.baseUrl()),
                () -> assertEquals("/chat/completions", result.endpointPath()));
    }

    @Test
    void acceptsAbsoluteEndpointOnSameOrigin() {
        ConnectionAddressNormalizer.NormalizedAddress result = normalizer.normalize(
                "https://api.example.com:8443/v1",
                "https://api.example.com:8443/v1/responses",
                AiProtocol.RESPONSES);

        assertAll(
                () -> assertEquals("https://api.example.com:8443/v1", result.baseUrl()),
                () -> assertEquals("/responses", result.endpointPath()));
    }

    @Test
    void rejectsAbsoluteEndpointOnDifferentOrigin() {
        assertValidationFailure(() -> normalizer.normalize(
                "https://api.example.com/v1",
                "https://other.example.com/v1/responses",
                AiProtocol.RESPONSES));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            " ",
            "ftp://api.example.com/v1",
            "https://user:secret@api.example.com/v1",
            "https://api.example.com/v1?token=value",
            "https://api.example.com/v1#fragment"
    })
    void rejectsInvalidBaseUrls(String baseUrl) {
        assertValidationFailure(() -> normalizer.normalize(
                baseUrl,
                null,
                AiProtocol.CHAT_COMPLETIONS));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/responses?token=value",
            "/responses#fragment",
            "/chat completions",
            "/chat\tcompletions"
    })
    void rejectsInvalidEndpointPaths(String endpointPath) {
        assertValidationFailure(() -> normalizer.normalize(
                "https://api.example.com/v1",
                endpointPath,
                AiProtocol.CHAT_COMPLETIONS));
    }

    @Test
    void buildsRequestUriWithExactlyOneJoiningSlash() {
        URI result = normalizer.requestUri(
                "https://api.example.com/v1/",
                "responses");

        assertEquals(URI.create("https://api.example.com/v1/responses"), result);
    }

    private void assertValidationFailure(Executable executable) {
        ApiException exception = assertThrows(ApiException.class, executable);
        assertAll(
                () -> assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus()),
                () -> assertEquals(ErrorCode.VALIDATION_FAILED, exception.getErrorCode()));
    }
}
