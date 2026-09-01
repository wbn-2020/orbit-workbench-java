package com.orbitworkbench.shared.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApiException(ApiException exception, HttpServletRequest request) {
        return response(exception.getStatus(), exception.getErrorCode(), exception.getMessage(), request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception,
                                                    HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return response(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                "请求参数校验失败", request, fieldErrors);
    }

    @ExceptionHandler({
            ConstraintViolationException.class,
            MissingServletRequestParameterException.class
    })
    ResponseEntity<ProblemDetail> handleBadRequest(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                exception.getMessage(), request, null);
    }

    /**
     * 内容类型不支持属于客户端请求形态问题，必须返回 415：落到兜底分支会变成
     * 500 {@code UNKNOWN_PROVIDER_ERROR}，把「你的 Content-Type 不对」伪装成服务器故障
     * （2026-09-01 用一个不带 Content-Type 的 POST 真实撞出来）。
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ProblemDetail> handleMediaType(HttpMediaTypeNotSupportedException exception,
                                                  HttpServletRequest request) {
        return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                "该接口只接受 application/json 请求体，收到的内容类型为 "
                        + (exception.getContentType() == null ? "未提供" : exception.getContentType()),
                request, null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ProblemDetail> handleUploadLimit(MaxUploadSizeExceededException exception,
                                                    HttpServletRequest request) {
        return response(HttpStatus.PAYLOAD_TOO_LARGE, ErrorCode.FILE_TOO_LARGE,
                "上传文件超过大小限制", request, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException exception,
                                                     HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED,
                "没有权限执行此操作", request, null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("未预期异常：{} {}", request.getMethod(), request.getRequestURI(), exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.UNKNOWN_PROVIDER_ERROR,
                "服务器处理请求失败", request, null);
    }

    private ResponseEntity<ProblemDetail> response(HttpStatus status,
                                                   ErrorCode errorCode,
                                                   String detail,
                                                   HttpServletRequest request,
                                                   Map<String, String> fieldErrors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title(status));
        problem.setInstance(java.net.URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", errorCode.name());
        problem.setProperty("traceId", MDC.get("traceId"));
        if (fieldErrors != null && !fieldErrors.isEmpty()) {
            problem.setProperty("fieldErrors", fieldErrors);
        }
        return ResponseEntity.status(status).body(problem);
    }

    private String title(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "请求参数错误";
            case UNAUTHORIZED -> "未登录";
            case FORBIDDEN -> "禁止访问";
            case NOT_FOUND -> "资源不存在";
            case CONFLICT -> "状态冲突";
            default -> "请求处理失败";
        };
    }
}

