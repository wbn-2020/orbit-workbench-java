package com.orbitworkbench.ai.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.ai.application.AiErrorSanitizer;
import com.orbitworkbench.ai.application.AiProviderException;
import com.orbitworkbench.ai.application.AiUsage;
import com.orbitworkbench.shared.api.ErrorCode;
import java.util.Iterator;
import java.util.Map;
import org.springframework.http.HttpStatus;

final class AiJsonSupport {

    private AiJsonSupport() {
    }

    static JsonNode read(ObjectMapper objectMapper, String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root == null || root.isMissingNode()) {
                throw new IllegalArgumentException("empty JSON");
            }
            return root;
        } catch (Exception exception) {
            throw new AiProviderException(
                    ErrorCode.UNKNOWN_PROVIDER_ERROR,
                    HttpStatus.BAD_GATEWAY,
                    null,
                    "上游返回了无法解析的 JSON",
                    null,
                    exception);
        }
    }

    static String text(JsonNode node, String... path) {
        JsonNode current = navigate(node, path);
        return current == null || current.isMissingNode() || current.isNull()
                ? null : current.asText(null);
    }

    static JsonNode node(JsonNode node, String... path) {
        JsonNode current = navigate(node, path);
        return current == null || current.isMissingNode() || current.isNull() ? null : current;
    }

    private static JsonNode navigate(JsonNode node, String... path) {
        JsonNode current = node;
        for (String part : path) {
            if (current == null) {
                return null;
            }
            if (current.isArray() && part.chars().allMatch(Character::isDigit)) {
                current = current.path(Integer.parseInt(part));
            } else {
                current = current.path(part);
            }
        }
        return current;
    }

    static String providerRequestId(JsonNode node) {
        String id = text(node, "id");
        return id != null ? id : text(node, "response", "id");
    }

    static AiUsage usage(JsonNode root) {
        JsonNode usage = node(root, "usage");
        if (usage == null) {
            usage = node(root, "response", "usage");
        }
        if (usage == null) {
            return null;
        }
        Integer input = integer(usage, "prompt_tokens");
        if (input == null) {
            input = integer(usage, "input_tokens");
        }
        Integer output = integer(usage, "completion_tokens");
        if (output == null) {
            output = integer(usage, "output_tokens");
        }
        Integer total = integer(usage, "total_tokens");
        // 明细两套命名：chat 风格 prompt_/completion_，responses 风格 input_/output_；都没有则保持 null。
        Integer cached = detailInteger(usage, "prompt_tokens_details", "cached_tokens");
        if (cached == null) {
            cached = detailInteger(usage, "input_tokens_details", "cached_tokens");
        }
        Integer reasoning = detailInteger(usage, "completion_tokens_details", "reasoning_tokens");
        if (reasoning == null) {
            reasoning = detailInteger(usage, "output_tokens_details", "reasoning_tokens");
        }
        return new AiUsage(input, output, total, cached, reasoning);
    }

    private static Integer detailInteger(JsonNode usage, String detailsField, String valueField) {
        JsonNode details = node(usage, detailsField);
        return integer(details, valueField);
    }

    static Integer integer(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || !value.isNumber() ? null : value.asInt();
    }

    static String errorSummary(ObjectMapper objectMapper, String body, String credential) {
        String summary = body;
        try {
            JsonNode root = objectMapper.readTree(body);
            String message = text(root, "error", "message");
            if (message == null) {
                message = text(root, "message");
            }
            if (message != null) {
                summary = message;
            }
        } catch (Exception ignored) {
            // Keep the raw body only as an input to the sanitizer.
        }
        return AiErrorSanitizer.sanitize(summary, credential);
    }

    static String firstText(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                String value = firstText(item);
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        if (node.isObject()) {
            for (String key : new String[]{"text", "value", "content", "delta"}) {
                String value = firstText(node.get(key));
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }
}
