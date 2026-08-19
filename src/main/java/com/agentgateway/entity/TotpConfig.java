package com.agentgateway.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 管理员 TOTP 配置（单行，id 恒为 1） */
@Data
@TableName("totp_config")
public class TotpConfig {

    @TableId(value = "id", type = IdType.INPUT)
    private Integer id = 1;

    /** Base32 编码的 TOTP 密钥 */
    private String secretB32;

    /** 是否已启用（首次部署验证通过后置 true） */
    private Boolean enabled = false;

    private Integer failedAttempts = 0;

    /** 锁定截止时间 */
    private LocalDateTime lockedUntil;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
