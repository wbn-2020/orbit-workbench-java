package com.orbitworkbench.ai.application;

import java.util.regex.Pattern;

public final class AiErrorSanitizer {

    private static final int MAX_LENGTH = 512;
    private static final Pattern AUTHORIZATION = Pattern.compile(
            "(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern SECRET_FIELD = Pattern.compile(
            "(?i)(api[_-]?key|authorization|token|secret|password)\\s*[:=]\\s*([\"']?)[^\\s,\"'}]+");
    private static final Pattern QUERY_SECRET = Pattern.compile(
            "(?i)([?&](?:api[_-]?key|token|secret|key)=)[^&#\\s]+");
    private static final Pattern KNOWN_KEY = Pattern.compile(
            "(?i)\\b(?:sk|xai|dsk)-[A-Za-z0-9_-]{8,}\\b");

    private AiErrorSanitizer() {
    }

    public static String sanitize(String raw, String credential) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw;
        if (credential != null && !credential.isBlank()) {
            value = value.replace(credential, "[REDACTED]");
        }
        value = AUTHORIZATION.matcher(value).replaceAll("Bearer [REDACTED]");
        value = SECRET_FIELD.matcher(value).replaceAll("$1=$2[REDACTED]");
        value = QUERY_SECRET.matcher(value).replaceAll("$1[REDACTED]");
        value = KNOWN_KEY.matcher(value).replaceAll("[REDACTED]");
        value = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return value.length() <= MAX_LENGTH ? value : value.substring(0, MAX_LENGTH);
    }
}
