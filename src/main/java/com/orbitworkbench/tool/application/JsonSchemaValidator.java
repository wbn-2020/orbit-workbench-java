package com.orbitworkbench.tool.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class JsonSchemaValidator {

    public void validate(JsonNode schema, JsonNode value) {
        validate(schema, value, "$");
    }

    private void validate(JsonNode schema, JsonNode value, String path) {
        if (schema == null || !schema.isObject()) {
            invalid(path + " 的 Schema 无效");
        }
        String type = text(schema, "type");
        if (type != null && !matches(type, value)) {
            invalid(path + " 类型必须为 " + type);
        }
        validateEnum(schema, value, path);
        if (value != null && value.isObject()) {
            validateObject(schema, value, path);
        } else if (value != null && value.isArray()) {
            validateArray(schema, value, path);
        } else if (value != null && value.isTextual()) {
            validateString(schema, value, path);
        } else if (value != null && value.isNumber()) {
            validateNumber(schema, value, path);
        }
    }

    private void validateObject(JsonNode schema, JsonNode value, String path) {
        Set<String> required = new HashSet<>();
        JsonNode requiredNode = schema.get("required");
        if (requiredNode != null && requiredNode.isArray()) {
            requiredNode.forEach(item -> required.add(item.asText()));
        }
        for (String name : required) {
            if (!value.has(name) || value.get(name).isNull()) {
                invalid(path + "." + name + " 不能为空");
            }
        }
        JsonNode properties = schema.get("properties");
        boolean allowAdditional = !schema.has("additionalProperties")
                || schema.get("additionalProperties").asBoolean(true);
        value.fieldNames().forEachRemaining(name -> {
            JsonNode propertySchema = properties == null ? null : properties.get(name);
            if (propertySchema == null) {
                if (!allowAdditional) {
                    invalid(path + "." + name + " 不是允许的参数");
                }
                return;
            }
            validate(propertySchema, value.get(name), path + "." + name);
        });
    }

    private void validateArray(JsonNode schema, JsonNode value, String path) {
        Integer maxItems = integer(schema, "maxItems");
        Integer minItems = integer(schema, "minItems");
        if (maxItems != null && value.size() > maxItems) {
            invalid(path + " 元素数量超过上限");
        }
        if (minItems != null && value.size() < minItems) {
            invalid(path + " 元素数量不足");
        }
        JsonNode itemSchema = schema.get("items");
        if (itemSchema != null) {
            for (int index = 0; index < value.size(); index++) {
                validate(itemSchema, value.get(index), path + "[" + index + "]");
            }
        }
    }

    private void validateString(JsonNode schema, JsonNode value, String path) {
        Integer maxLength = integer(schema, "maxLength");
        Integer minLength = integer(schema, "minLength");
        int length = value.asText().length();
        if (maxLength != null && length > maxLength) {
            invalid(path + " 长度超过上限");
        }
        if (minLength != null && length < minLength) {
            invalid(path + " 长度不足");
        }
    }

    private void validateNumber(JsonNode schema, JsonNode value, String path) {
        BigDecimal number = value.decimalValue();
        JsonNode minimum = schema.get("minimum");
        JsonNode maximum = schema.get("maximum");
        if (minimum != null && minimum.isNumber()
                && number.compareTo(minimum.decimalValue()) < 0) {
            invalid(path + " 小于最小值");
        }
        if (maximum != null && maximum.isNumber()
                && number.compareTo(maximum.decimalValue()) > 0) {
            invalid(path + " 大于最大值");
        }
    }

    private void validateEnum(JsonNode schema, JsonNode value, String path) {
        JsonNode allowed = schema.get("enum");
        if (allowed == null || !allowed.isArray()) {
            return;
        }
        for (JsonNode candidate : allowed) {
            if (candidate.equals(value)) {
                return;
            }
        }
        invalid(path + " 不在允许值范围内");
    }

    private boolean matches(String type, JsonNode value) {
        if (value == null || value.isNull()) {
            return "null".equals(type);
        }
        return switch (type) {
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "string" -> value.isTextual();
            case "integer" -> value.isIntegralNumber();
            case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            case "null" -> value.isNull();
            default -> false;
        };
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isTextual() ? null : value.asText();
    }

    private Integer integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isIntegralNumber() ? null : value.asInt();
    }

    private void invalid(String message) {
        throw new ApiException(
                HttpStatus.BAD_REQUEST,
                ErrorCode.TOOL_CALL_INVALID,
                message);
    }
}
