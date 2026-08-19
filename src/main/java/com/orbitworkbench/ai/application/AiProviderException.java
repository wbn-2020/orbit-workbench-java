package com.orbitworkbench.ai.application;

import com.orbitworkbench.shared.api.ErrorCode;
import org.springframework.http.HttpStatus;

public class AiProviderException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus status;
    private final Integer httpStatus;
    private final String providerRequestId;

    public AiProviderException(ErrorCode errorCode,
                               HttpStatus status,
                               Integer httpStatus,
                               String message,
                               String providerRequestId) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
        this.httpStatus = httpStatus;
        this.providerRequestId = providerRequestId;
    }

    public AiProviderException(ErrorCode errorCode,
                               HttpStatus status,
                               Integer httpStatus,
                               String message,
                               String providerRequestId,
                               Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.status = status;
        this.httpStatus = httpStatus;
        this.providerRequestId = providerRequestId;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public String getProviderRequestId() {
        return providerRequestId;
    }
}
