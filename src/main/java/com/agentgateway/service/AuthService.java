package com.agentgateway.service;

import com.agentgateway.config.AppProperties;
import com.agentgateway.dto.AuthPollResponse;
import com.agentgateway.dto.RoleDto;
import com.agentgateway.entity.AuthCode;
import com.agentgateway.entity.AuthSession;
import com.agentgateway.entity.Role;
import com.agentgateway.exception.ApiException;
import com.agentgateway.mapper.AuthCodeMapper;
import com.agentgateway.mapper.AuthSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/** 授权会话 + 授权码生命周期：创建、TOTP 标记、绑定角色签发码、刷新、撤销、操作校验 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthSessionMapper sessionMapper;
    private final AuthCodeMapper codeMapper;
    private final RoleService roleService;
    private final AppProperties props;

    /** 1) Agent 发起授权：创建 waiting_totp 会话 */
    public AuthSession createSession() {
        AuthSession s = new AuthSession();
        s.setSessionId(UUID.randomUUID().toString());
        s.setState(AuthSession.STATE_WAITING_TOTP);
        s.setExpiresAt(LocalDateTime.now().plusMinutes(props.getSessionTtlMinutes()));
        sessionMapper.insert(s);
        return s;
    }

    /** 查询会话；已完成会话不过期，其余过期抛 410 */
    public AuthSession getSessionOrThrow(String sessionId) {
        AuthSession s = sessionMapper.selectBySessionId(sessionId);
        if (s == null) {
            throw ApiException.sessionNotFound();
        }
        if (LocalDateTime.now().isAfter(s.getExpiresAt())
                && !AuthSession.STATE_COMPLETED.equals(s.getState())) {
            throw ApiException.sessionExpired();
        }
        return s;
    }

    /** 2) 用户通过 TOTP 后标记 verified */
    public void markVerified(String sessionId) {
        AuthSession s = getSessionOrThrow(sessionId);
        if (AuthSession.STATE_COMPLETED.equals(s.getState())) {
            return;
        }
        sessionMapper.updateState(s.getId(), AuthSession.STATE_VERIFIED, LocalDateTime.now());
    }

    /** 3) 用户选择角色后绑定并签发授权码（幂等：已 completed 返回原码，不重复签发） */
    @Transactional
    public AuthSession bindRole(String sessionId, Long roleId) {
        AuthSession s = getSessionOrThrow(sessionId);
        Role role = roleService.getEnabledOrThrow(roleId);
        if (AuthSession.STATE_COMPLETED.equals(s.getState()) && s.getAuthCodeId() != null) {
            return s;
        }
        AuthCode code = issueCode(role);
        sessionMapper.bind(s.getId(), AuthSession.STATE_COMPLETED, role.getId(), code.getId(), LocalDateTime.now());
        s.setState(AuthSession.STATE_COMPLETED);
        s.setRoleId(role.getId());
        s.setAuthCodeId(code.getId());
        return s;
    }

    /**
     * 签发新授权码：先作废旧码，再插入新码。
     * 一角色一有效码由 DB 唯一索引 uk_role_active(role_id, is_active) 兜底：
     * 并发签发时第二个 INSERT 触发 DuplicateKeyException → 回滚。
     */
    @Transactional
    public AuthCode issueCode(Role role) {
        LocalDateTime now = LocalDateTime.now();
        codeMapper.revokeActiveCodes(role.getId(), now, "REISSUED");
        AuthCode c = new AuthCode();
        c.setCode(UUID.randomUUID().toString());
        c.setRoleId(role.getId());
        c.setIsActive(true);
        c.setIssuedAt(now);
        c.setExpiresAt(now.plusMinutes(props.getAuthCodeTtlMinutes()));
        codeMapper.insert(c);
        return c;
    }

    /** 刷新：用当前码（有效或自然过期）换新码。旧码作废。 */
    @Transactional
    public AuthCode refresh(String presentedCode) {
        AuthCode c = codeMapper.selectByCode(presentedCode);
        if (c == null || !isRefreshable(c)) {
            throw ApiException.authCodeInvalid();
        }
        Role role = roleService.getEnabledOrThrow(c.getRoleId());
        return issueCode(role);
    }

    /**
     * 可刷新条件：码未被作废（有效或已自然过期未清扫），或仅被清扫标记 EXPIRED（恢复路径）。
     * REISSUED（被新码顶替）与 MANUAL（人工撤销）拒绝刷新，防止旧码复活。
     */
    private boolean isRefreshable(AuthCode c) {
        if (Boolean.FALSE.equals(c.getRevoked())) {
            return true;
        }
        return "EXPIRED".equalsIgnoreCase(c.getRevokedReason());
    }

    /** 手动撤销 */
    public void revoke(String presentedCode) {
        AuthCode c = codeMapper.selectByCode(presentedCode);
        if (c == null) {
            throw ApiException.authCodeInvalid();
        }
        LocalDateTime now = LocalDateTime.now();
        c.setRevoked(true);
        c.setRevokedAt(now);
        c.setIsActive(false);
        c.setRevokedReason("MANUAL");
        codeMapper.updateById(c);
    }

    /** 组装 Agent 轮询响应 */
    public AuthPollResponse buildPoll(String sessionId) {
        AuthSession s = getSessionOrThrow(sessionId);
        if (AuthSession.STATE_COMPLETED.equals(s.getState()) && s.getAuthCodeId() != null) {
            AuthCode code = codeMapper.selectById(s.getAuthCodeId());
            Role role = roleService.getEnabledOrThrow(code.getRoleId());
            return AuthPollResponse.builder()
                    .sessionId(sessionId)
                    .state(AuthSession.STATE_COMPLETED)
                    .authCode(code.getCode())
                    .authCodeExpiresAt(code.getExpiresAt())
                    .role(RoleDto.from(role))
                    .build();
        }
        return AuthPollResponse.builder()
                .sessionId(sessionId)
                .state(s.getState())
                .build();
    }

    /** 校验用于操作的有效授权码（未撤销、有效、未过期）；返回该码绑定的角色 */
    public AuthCode validateOperationCode(String presentedCode) {
        AuthCode c = codeMapper.selectByCode(presentedCode);
        if (c == null || Boolean.TRUE.equals(c.getRevoked()) || !Boolean.TRUE.equals(c.getIsActive())) {
            throw ApiException.authCodeInvalid();
        }
        if (LocalDateTime.now().isAfter(c.getExpiresAt())) {
            throw ApiException.authCodeExpired();
        }
        return c;
    }
}
