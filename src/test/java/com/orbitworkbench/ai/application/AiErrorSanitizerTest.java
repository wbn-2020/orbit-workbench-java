package com.orbitworkbench.ai.application;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AiErrorSanitizerTest {

    @Test
    void returnsNullForMissingMessages() {
        assertAll(
                () -> assertNull(AiErrorSanitizer.sanitize(null, "credential")),
                () -> assertNull(AiErrorSanitizer.sanitize("", "credential")),
                () -> assertNull(AiErrorSanitizer.sanitize(" \t ", "credential")));
    }

    @Test
    void redactsExplicitCredentialBearerTokensAndKnownKeyFormats() {
        String explicitCredential = "plain-credential-value";
        String raw = "request Bearer abc.DEF_123-+= failed for " + explicitCredential
                + " and sk-abcdefgh1234 and xai-ABCDEFGH_123";

        String sanitized = AiErrorSanitizer.sanitize(raw, explicitCredential);

        assertAll(
                () -> assertFalse(sanitized.contains(explicitCredential)),
                () -> assertFalse(sanitized.contains("abc.DEF_123")),
                () -> assertFalse(sanitized.contains("sk-abcdefgh1234")),
                () -> assertFalse(sanitized.contains("xai-ABCDEFGH_123")),
                () -> assertTrue(sanitized.contains("[REDACTED]")));
    }

    @Test
    void redactsSecretFieldsAndQueryParameters() {
        String raw = "api_key=\"alpha\", token=beta secret:gamma password='delta' "
                + "authorization=epsilon GET https://example.test/path?key=zeta&x=1";

        String sanitized = AiErrorSanitizer.sanitize(raw, null);

        assertAll(
                () -> assertFalse(sanitized.contains("alpha")),
                () -> assertFalse(sanitized.contains("beta")),
                () -> assertFalse(sanitized.contains("gamma")),
                () -> assertFalse(sanitized.contains("delta")),
                () -> assertFalse(sanitized.contains("epsilon")),
                () -> assertFalse(sanitized.contains("zeta")),
                () -> assertTrue(sanitized.contains("api_key=\"[REDACTED]\"")),
                () -> assertTrue(sanitized.contains("password='[REDACTED]'")));
    }

    @Test
    void flattensControlWhitespaceAndLimitsLength() {
        assertEquals("first second third fourth",
                AiErrorSanitizer.sanitize("first\r\nsecond\tthird\nfourth", null));

        String sanitized = AiErrorSanitizer.sanitize("x".repeat(600), null);

        assertEquals(512, sanitized.length());
    }
}
