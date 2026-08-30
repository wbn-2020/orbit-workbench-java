package com.orbitworkbench.ai.application;

import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpStatus;

/**
 * 把模型调用抛出的异常映射为对外业务异常，保留 Adapter 已经分类好的错误码。
 *
 * <p>阻塞与流式两条路径共用同一映射，避免"同一个上游故障在两条路径上给出不同错误语义"；
 * 消息只带错误码，不带模型或上游返回的原文，防止上游响应体被写进日志与数据库。
 */
public final class AiCallFailures {

    private AiCallFailures() {
    }

    public static ApiException toApiException(String subject, Throwable failure) {
        if (failure instanceof ApiException apiException) {
            return apiException;
        }
        if (failure instanceof AiProviderException provider) {
            return new ApiException(provider.getStatus(), provider.getErrorCode(),
                    subject + "调用失败：" + provider.getErrorCode().name());
        }
        if (isTimeout(failure)) {
            return new ApiException(HttpStatus.GATEWAY_TIMEOUT, ErrorCode.REQUEST_TIMEOUT,
                    subject + "调用失败：" + ErrorCode.REQUEST_TIMEOUT.name());
        }
        return new ApiException(HttpStatus.BAD_GATEWAY, ErrorCode.UPSTREAM_UNAVAILABLE,
                subject + "调用未完成：" + failure.getClass().getSimpleName());
    }

    private static boolean isTimeout(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof TimeoutException) {
                return true;
            }
            if (current instanceof IllegalStateException illegal && illegal.getMessage() != null
                    && illegal.getMessage().contains("Timeout on blocking read")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
