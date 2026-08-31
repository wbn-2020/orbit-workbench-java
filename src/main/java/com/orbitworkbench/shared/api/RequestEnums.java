package com.orbitworkbench.shared.api;

import org.springframework.http.HttpStatus;

/**
 * 把请求里的枚举字符串安全解析成枚举，避免非法取值以未捕获异常形式变成 500。
 */
public final class RequestEnums {

    private RequestEnums() {
    }

    public static <E extends Enum<E>> E parse(Class<E> type, String raw, String field) {
        String value = raw == null ? "" : raw.trim();
        for (E candidate : type.getEnumConstants()) {
            if (candidate.name().equals(value)) {
                return candidate;
            }
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST,
                field + " 取值不合法");
    }
}
