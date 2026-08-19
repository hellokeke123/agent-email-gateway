package com.agentgateway.controller;

import com.agentgateway.dto.AuthPollResponse;
import com.agentgateway.dto.AuthRefreshResponse;
import com.agentgateway.dto.AuthStartResponse;
import com.agentgateway.dto.RoleDto;
import com.agentgateway.entity.AuthCode;
import com.agentgateway.entity.Role;
import com.agentgateway.service.AuthService;
import com.agentgateway.service.RoleService;
import com.agentgateway.util.BaseUrlResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class ApiAuthController {

    private final AuthService authService;
    private final RoleService roleService;
    private final BaseUrlResolver baseUrlResolver;

    /** 1) Agent 发起授权 */
    @PostMapping("/start")
    public ResponseEntity<AuthStartResponse> start(HttpServletRequest request) {
        var session = authService.createSession();
        String pageUrl = baseUrlResolver.resolve(request) + "/verify?sessionId=" + session.getSessionId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AuthStartResponse.builder()
                        .sessionId(session.getSessionId())
                        .pageUrl(pageUrl)
                        .build());
    }

    /** 2) Agent 轮询授权状态，completed 时返回授权码 */
    @GetMapping("/{sessionId}")
    public AuthPollResponse poll(@PathVariable String sessionId) {
        return authService.buildPoll(sessionId);
    }

    /** 3) 刷新授权码（可过期但未撤销） */
    @PostMapping("/refresh")
    public AuthRefreshResponse refresh(@RequestHeader("X-Auth-Code") String code) {
        AuthCode newCode = authService.refresh(code.trim());
        Role role = roleService.getEnabledOrThrow(newCode.getRoleId());
        return AuthRefreshResponse.builder()
                .authCode(newCode.getCode())
                .expiresAt(newCode.getExpiresAt())
                .role(RoleDto.from(role))
                .build();
    }

    /** 4) 撤销授权码 */
    @PostMapping("/revoke")
    public Map<String, String> revoke(@RequestHeader("X-Auth-Code") String code) {
        authService.revoke(code.trim());
        return Map.of("status", "revoked");
    }
}
