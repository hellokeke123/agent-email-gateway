package com.agentgateway.service;

import com.agentgateway.entity.AuthCode;
import com.agentgateway.entity.Role;
import com.agentgateway.exception.ApiException;
import com.agentgateway.mapper.AuthCodeMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthServiceTest {

    @Autowired
    AuthService authService;
    @Autowired
    RoleService roleService;
    @Autowired
    AuthCodeMapper codeMapper;

    @Test
    void fullFlow_bindsRoleAndPollReturnsCode() {
        roleService.create("角色A", null);
        Role role = roleService.listAll().get(0);

        var session = authService.createSession();
        assertNotNull(session.getSessionId());
        assertEquals("waiting_totp", session.getState());

        authService.markVerified(session.getSessionId());
        var bound = authService.bindRole(session.getSessionId(), role.getId());
        assertNotNull(bound.getAuthCodeId());

        var poll = authService.buildPoll(session.getSessionId());
        assertEquals("completed", poll.getState());
        assertNotNull(poll.getAuthCode());
        assertEquals(role.getName(), poll.getRole().getName());
    }

    @Test
    void issueCode_onlyOneActiveCodePerRole() {
        roleService.create("角色X", null);
        Role role = roleService.listAll().get(0);

        AuthCode c1 = authService.issueCode(role);
        AuthCode c2 = authService.issueCode(role);

        AuthCode old = codeMapper.selectById(c1.getId());
        assertTrue(old.getRevoked());
        assertFalse(old.getIsActive());
        assertTrue(codeMapper.selectById(c2.getId()).getIsActive());

        long activeCount = codeMapper.selectCount(new LambdaQueryWrapper<AuthCode>()
                .eq(AuthCode::getRoleId, role.getId())
                .eq(AuthCode::getIsActive, true));
        assertEquals(1, activeCount);
    }

    @Test
    void repeatedIssuesAndRevoke_keepSingleActiveCode() {
        // 回归：多次签发/撤销后同角色积累多条失效码，仍能继续签发与撤销（active_role_key 部分唯一）
        roleService.create("角色M", null);
        Role role = roleService.listAll().get(0);
        AuthCode last = null;
        for (int i = 0; i < 3; i++) {
            last = authService.issueCode(role);
        }
        long activeCount = codeMapper.selectCount(new LambdaQueryWrapper<AuthCode>()
                .eq(AuthCode::getRoleId, role.getId())
                .eq(AuthCode::getIsActive, true));
        assertEquals(1, activeCount);
        assertTrue(codeMapper.selectById(last.getId()).getIsActive());

        // 撤销最后一个有效码：应成功（不再与历史失效码撞唯一索引）
        authService.revoke(last.getCode());
        assertTrue(codeMapper.selectById(last.getId()).getRevoked());
        // 撤销后再签发
        AuthCode again = authService.issueCode(role);
        assertTrue(codeMapper.selectById(again.getId()).getIsActive());
    }

    @Test
    void validateOperationCode_rejectsRevokedAndExpired() {
        roleService.create("角色Y", null);
        Role role = roleService.listAll().get(0);

        AuthCode c1 = authService.issueCode(role);
        authService.issueCode(role); // 签发新码作废 c1
        assertThrows(ApiException.class, () -> authService.validateOperationCode(c1.getCode()));

        // 过期但未撤销的码：用全新角色，避免撞 uk_role_active（该角色不能已有有效码）
        roleService.create("角色Y2", null);
        Role role2 = roleService.listAll().get(1);
        AuthCode expired = makeExpiredActiveCode(role2.getId());

        ApiException ex = assertThrows(ApiException.class,
                () -> authService.validateOperationCode(expired.getCode()));
        assertEquals("AUTH_CODE_EXPIRED", ex.getErrorCode().getCode());
    }

    @Test
    void refresh_worksForExpiredButNotRevoked() {
        roleService.create("角色Z", null);
        Role role = roleService.listAll().get(0);
        AuthCode expired = makeExpiredActiveCode(role.getId());

        AuthCode fresh = authService.refresh(expired.getCode());
        assertNotNull(fresh.getCode());
        assertTrue(fresh.getExpiresAt().isAfter(LocalDateTime.now()));
        assertTrue(codeMapper.selectById(expired.getId()).getRevoked());
    }

    @Test
    void refresh_worksForSweptExpired() {
        roleService.create("角色V", null);
        Role role = roleService.listAll().get(0);
        AuthCode swept = makeExpiredActiveCode(role.getId());
        // 模拟 @Scheduled 清扫把过期码标为 revoked_reason=EXPIRED
        swept.setRevoked(true);
        swept.setRevokedAt(LocalDateTime.now().minusMinutes(5));
        swept.setIsActive(false);
        swept.setRevokedReason("EXPIRED");
        codeMapper.updateById(swept);

        AuthCode fresh = authService.refresh(swept.getCode());
        assertNotNull(fresh.getCode());
        assertFalse(codeMapper.selectById(fresh.getId()).getRevoked());
    }

    /** 构造「已过期但未撤销、仍标记有效」的授权码（仅用于没有其它有效码的角色） */
    private AuthCode makeExpiredActiveCode(Long roleId) {
        AuthCode c = new AuthCode();
        c.setCode(UUID.randomUUID().toString());
        c.setRoleId(roleId);
        c.setIsActive(true);
        c.setIssuedAt(LocalDateTime.now().minusHours(2));
        c.setExpiresAt(LocalDateTime.now().minusHours(1));
        c.setRevoked(false);
        codeMapper.insert(c);
        return c;
    }

    @Test
    void refresh_rejectsRevokedCode() {
        roleService.create("角色W", null);
        Role role = roleService.listAll().get(0);
        AuthCode c1 = authService.issueCode(role);
        authService.revoke(c1.getCode());
        assertThrows(ApiException.class, () -> authService.refresh(c1.getCode()));
    }
}
