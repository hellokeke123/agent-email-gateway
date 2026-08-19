package com.agentgateway.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    TOTP_NOT_CONFIGURED("TOTP_NOT_CONFIGURED"),
    INVALID_TOTP("INVALID_TOTP"),
    TOTP_LOCKED("TOTP_LOCKED"),
    SESSION_NOT_FOUND("SESSION_NOT_FOUND"),
    SESSION_EXPIRED("SESSION_EXPIRED"),
    AUTH_CODE_INVALID("AUTH_CODE_INVALID"),
    AUTH_CODE_EXPIRED("AUTH_CODE_EXPIRED"),
    NOT_FOUND("NOT_FOUND"),
    ROLE_INVALID("ROLE_INVALID"),
    VALIDATION_ERROR("VALIDATION_ERROR"),
    INTERNAL_ERROR("INTERNAL_ERROR");

    private final String code;

    ErrorCode(String code) {
        this.code = code;
    }
}
