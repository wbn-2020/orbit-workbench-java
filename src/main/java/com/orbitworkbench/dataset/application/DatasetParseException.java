package com.orbitworkbench.dataset.application;

import com.orbitworkbench.shared.api.ErrorCode;

public class DatasetParseException extends RuntimeException {

    private final ErrorCode errorCode;

    public DatasetParseException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public DatasetParseException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
