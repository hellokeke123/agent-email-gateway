package com.agentgateway.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode errorCode;

    public ApiException(HttpStatus status, ErrorCode errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public static ApiException of(HttpStatus status, ErrorCode code, String message) {
        return new ApiException(status, code, message);
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, message);
    }

    public static ApiException invalidTotp(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_TOTP, message);
    }

    public static ApiException totpLocked(String message) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.TOTP_LOCKED, message);
    }

    public static ApiException sessionNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.SESSION_NOT_FOUND, "授权会话不存在");
    }

    public static ApiException sessionExpired() {
        return new ApiException(HttpStatus.GONE, ErrorCode.SESSION_EXPIRED, "授权会话已过期，请让 Agent 重新发起授权");
    }

    public static ApiException authCodeInvalid() {
        return new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_CODE_INVALID, "授权码无效或已被撤销");
    }

    public static ApiException authCodeExpired() {
        return new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_CODE_EXPIRED, "授权码已过期");
    }
}
