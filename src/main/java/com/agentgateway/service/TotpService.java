package com.agentgateway.service;

import com.agentgateway.config.AppProperties;
import com.agentgateway.entity.TotpConfig;
import com.agentgateway.exception.ApiException;
import com.agentgateway.exception.ErrorCode;
import com.agentgateway.mapper.TotpConfigMapper;
import com.agentgateway.totp.TotpUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;

/** 管理员 TOTP 密钥管理：首次部署生成、二维码、验证（失败锁定） */
@Service
@RequiredArgsConstructor
public class TotpService {

    private final TotpConfigMapper mapper;
    private final AppProperties props;

    public boolean isConfigured() {
        TotpConfig c = mapper.selectById(1);
        return c != null && Boolean.TRUE.equals(c.getEnabled());
    }

    /** 首次部署：确保存在密钥（enabled=false），返回当前配置 */
    public TotpConfig bootstrap() {
        TotpConfig c = mapper.selectById(1);
        if (c == null) {
            c = new TotpConfig();
            c.setId(1);
            c.setSecretB32(TotpUtil.generateSecret());
            c.setEnabled(false);
            c.setFailedAttempts(0);
            mapper.insert(c);
        }
        return c;
    }

    public String provisioningUri() {
        TotpConfig c = bootstrap();
        return TotpUtil.provisioningUri(c.getSecretB32(), props.getIssuer(), "admin");
    }

    /** 首次部署页：验证一次性代码后启用 */
    public void enable(String code) {
        TotpConfig c = bootstrap();
        if (Boolean.TRUE.equals(c.getEnabled())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        checkLock(c, now);
        if (!TotpUtil.verify(c.getSecretB32(), code, props.getTotpWindow(), Instant.now())) {
            registerFailure(c, now);
            throw ApiException.invalidTotp("一次性代码不正确");
        }
        c.setEnabled(true);
        c.setFailedAttempts(0);
        c.setLockedUntil(null);
        c.setUpdatedAt(now);
        mapper.updateById(c);
    }

    /** 常规 TOTP 登录验证 */
    public void verify(String code) {
        if (!isConfigured()) {
            throw ApiException.of(HttpStatus.CONFLICT, ErrorCode.TOTP_NOT_CONFIGURED, "TOTP 尚未配置");
        }
        TotpConfig c = mapper.selectById(1);
        LocalDateTime now = LocalDateTime.now();
        checkLock(c, now);
        if (!TotpUtil.verify(c.getSecretB32(), code, props.getTotpWindow(), Instant.now())) {
            registerFailure(c, now);
            throw ApiException.invalidTotp("一次性代码不正确");
        }
        c.setFailedAttempts(0);
        c.setLockedUntil(null);
        c.setUpdatedAt(now);
        mapper.updateById(c);
    }

    /** 当前是否处于锁定状态（页面提示用） */
    public boolean isLocked() {
        TotpConfig c = mapper.selectById(1);
        return c != null && c.getLockedUntil() != null && LocalDateTime.now().isBefore(c.getLockedUntil());
    }

    private void checkLock(TotpConfig c, LocalDateTime now) {
        if (c.getLockedUntil() != null && now.isBefore(c.getLockedUntil())) {
            throw ApiException.totpLocked("连续失败次数过多，请稍后再试");
        }
    }

    private void registerFailure(TotpConfig c, LocalDateTime now) {
        int fails = (c.getFailedAttempts() == null ? 0 : c.getFailedAttempts()) + 1;
        c.setFailedAttempts(fails);
        if (fails >= props.getTotpLockThreshold()) {
            c.setLockedUntil(now.plusMinutes(props.getTotpLockMinutes()));
            c.setFailedAttempts(0);
        }
        c.setUpdatedAt(now);
        mapper.updateById(c);
    }
}
