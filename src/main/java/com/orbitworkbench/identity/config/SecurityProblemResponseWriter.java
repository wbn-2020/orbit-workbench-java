package com.orbitworkbench.identity.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.shared.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class SecurityProblemResponseWriter {

    private final ObjectMapper objectMapper;

    public SecurityProblemResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletRequest request,
                      HttpServletResponse response,
                      HttpStatus status,
                      String errorCode,
                      String detail) throws IOException {
        String traceId = MDC.get("traceId");
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
            response.setHeader(TraceIdFilter.TRACE_ID_HEADER, traceId);
        }

        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "about:blank");
        problem.put("title", status == HttpStatus.UNAUTHORIZED ? "未登录" : "禁止访问");
        problem.put("status", status.value());
        problem.put("detail", detail);
        problem.put("instance", request.getRequestURI());
        problem.put("errorCode", errorCode);
        problem.put("traceId", traceId);

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
