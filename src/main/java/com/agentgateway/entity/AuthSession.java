package com.agentgateway.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Agent 授权会话：waiting_totp -> verified -> completed / expired */
@Data
@TableName("auth_session")
public class AuthSession {

    public static final String STATE_WAITING_TOTP = "waiting_totp";
    public static final String STATE_VERIFIED = "verified";
    public static final String STATE_COMPLETED = "completed";
    public static final String STATE_EXPIRED = "expired";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 对外暴露的会话 ID（UUID） */
    private String sessionId;

    private String state = STATE_WAITING_TOTP;

    /** 用户选定的角色 */
    private Long roleId;

    /** 绑定签发的授权码 */
    private Long authCodeId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 会话过期时间（人工完成授权的窗口） */
    private LocalDateTime expiresAt;
}
