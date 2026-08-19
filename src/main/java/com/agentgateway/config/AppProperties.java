package com.agentgateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** 授权码有效期（分钟） */
    private long authCodeTtlMinutes = 60;
    /** Agent 授权会话有效期（分钟） */
    private long sessionTtlMinutes = 15;
    /** Web 管理页 TOTP 验证后 session 保持时长（分钟） */
    private long webSessionTtlMinutes = 30;
    /** TOTP 校验窗口（±步数） */
    private int totpWindow = 1;
    /** 失败锁定阈值 */
    private int totpLockThreshold = 5;
    /** 锁定分钟数 */
    private long totpLockMinutes = 1;
    /** TOTP 发行方名称（二维码） */
    private String issuer = "AgentGateway";
    /** 对外公开地址（用于 skill 下载链接；空则按请求自动推导） */
    private String baseUrl = "";
}
