package com.orbitworkbench.ai.application;

import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 本系统在**发出请求之前**就拒绝了一次调用（能力/形状不满足、声明与协议不符）。
 *
 * <p>需要单独成一个类型，是因为"我们没发这个请求"和"上游把它办砸了"对用户的后果完全不同：
 * 前者内容一点没丢、也不存在"失败可重试"的语义，若一并记成报告失败并推送通知，
 * 用户会收到一条"报告生成失败"，而实际上请求从未离开本机。
 * {@code AiCallFailures.toApiException} 对 {@code ApiException} 原样透传，所以这个类型
 * 能穿过调用链活着到达报告服务。
 */
public class RequestRejectedException extends ApiException {

    public RequestRejectedException(HttpStatus status, ErrorCode errorCode, String message) {
        super(status, errorCode, message);
    }
}
