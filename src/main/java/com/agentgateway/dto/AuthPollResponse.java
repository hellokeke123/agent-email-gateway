package com.agentgateway.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AuthPollResponse {
    private String sessionId;
    /** waiting_totp / verified / completed / expired */
    private String state;
    /** completed 时返回 */
    private String authCode;
    private LocalDateTime authCodeExpiresAt;
    private RoleDto role;
}
