package com.orbitworkbench.shared.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    /**
     * 路径变量绑不上目标类型（如 {@code /job-postings/abc} 要绑 {@code Long id}）是客户端请求形态问题，
     * 必须返回 400：原先没有任何分支接住，落到兜底分支变成 500 {@code UNKNOWN_PROVIDER_ERROR}，
     * 把「你给的路径参数不是数字」伪装成服务器故障（2026-09-02 C-05b 实测）。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException exception,
                                                     HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        fieldErrors.put(exception.getName(), "取值形态不正确，需要" + typeName(exception.getRequiredType()));
        return response(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                "路径参数 " + exception.getName() + " 不是合法取值", request, fieldErrors);
    }

    private static String typeName(Class<?> requiredType) {
        if (requiredType == null) {
            return "合法值";
        }
        if (Long.class.equals(requiredType) || Integer.class.equals(requiredType)) {
            return "整数";
        }
        if (Boolean.class.equals(requiredType)) {
            return "true 或 false";
        }
        return requiredType.getSimpleName();
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

    /**
     * 未映射的 API 路径（如前端先于后端上线调新端点）在 Spring Boot 3.2+ 会以
     * {@link NoResourceFoundException} 抛出。此前没有任何分支接住，落到兜底分支变成
     * 500 {@code UNKNOWN_PROVIDER_ERROR}，把「这个接口不存在」伪装成服务器故障——
     * 2026-09-09 知识总览联调时被旧后端进程真实撞出：B1 新端点在旧 jar 上呈现为 500，
     * 掩盖了「后端版本落后」的真实原因。必须显式 404，让版本错位一眼可辨。
     */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    ResponseEntity<ProblemDetail> handleNoResource(org.springframework.web.servlet.resource.NoResourceFoundException exception,
                                                   HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "接口或资源不存在：若前端已更新而后端未重新部署，请重启后端到当前版本", request, null);
    }

    /**
     * 请求方法不被支持（某端点只提供 POST 却收到 GET）是客户端调用形态问题，
     * 必须返回 405 并按 RFC 9110 带上 {@code Allow} 头列出可用方法：
     * 此前没有任何分支接住，落到兜底分支变成 500 {@code UNKNOWN_PROVIDER_ERROR}，
     * 把「你用错了 HTTP 方法」伪装成服务器故障 —— 2026-09-11 验收 ZIP 导入时
     * 用 {@code GET /projects/{id}/versions} 真实撞出，日志实锤
     * {@code HttpRequestMethodNotSupportedException}（traceId 772f475f）。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception,
                                                            HttpServletRequest request) {
        ResponseEntity<ProblemDetail> body =
                response(HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED,
                        "该接口不支持 " + exception.getMethod() + " 方法", request, null);
        HttpHeaders headers = new HttpHeaders();
        if (exception.getSupportedHttpMethods() != null && !exception.getSupportedHttpMethods().isEmpty()) {
            headers.setAllow(exception.getSupportedHttpMethods());
        }
        return new ResponseEntity<>(body.getBody(), headers, HttpStatus.METHOD_NOT_ALLOWED);
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
            case METHOD_NOT_ALLOWED -> "方法不被允许";
            case CONFLICT -> "状态冲突";
            default -> "请求处理失败";
        };
    }
}

