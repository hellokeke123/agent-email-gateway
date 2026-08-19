package com.agentgateway.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 授权码（UUID），绑定一个角色；一角色同时只能有一个有效码（is_active=1） */
@Data
@TableName("auth_code")
public class AuthCode {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** UUID 授权码 */
    private String code;

    private Long roleId;

    /** 唯一有效位：同一 role_id 下最多一条 is_active=1 */
    private Boolean isActive = true;

    private LocalDateTime issuedAt;

    private LocalDateTime expiresAt;

    private Boolean revoked = false;

    private LocalDateTime revokedAt;

    /** REISSUED / EXPIRED / MANUAL */
    private String revokedReason;
}
