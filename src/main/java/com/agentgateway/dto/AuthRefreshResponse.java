package com.agentgateway.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AuthRefreshResponse {
    private String authCode;
    private LocalDateTime expiresAt;
    private RoleDto role;
}
