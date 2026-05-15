package com.agenticrag.infra.ai.service;

import org.springframework.http.HttpStatus;

public class AiProviderException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public AiProviderException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public static AiProviderException insufficientBalance(Throwable cause) {
        return new AiProviderException(
                HttpStatus.PAYMENT_REQUIRED,
                "ai_insufficient_balance",
                "AI 提供商账户余额不足，当前无法完成问答或评测。请充值，或切换到可用的模型/API Key 后重试。",
                cause);
    }

    public static AiProviderException providerCallFailed(String message, Throwable cause) {
        return new AiProviderException(
                HttpStatus.BAD_GATEWAY,
                "ai_provider_call_failed",
                message,
                cause);
    }
}
