package com.agentgateway.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 角色间消息（本地存储，替代真实邮件） */
@Data
@TableName("message")
public class Message {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 生成的消息 ID（如 <uuid@gateway>），用于回复线程引用 */
    private String messageId;

    /** 谁发送的 */
    private Long fromRoleId;

    /** 发送给谁的（单收件人） */
    private Long toRoleId;

    /** ★ 发送时使用的授权码（审计追溯链） */
    private Long authCodeId;

    private String subject;

    private String bodyText;

    private String bodyHtml;

    /** 是否已读（接收方视角） */
    private Boolean isRead = false;

    private LocalDateTime readAt;

    /** 是否完成（接收方视角） */
    private Boolean isCompleted = false;

    private LocalDateTime completedAt;

    /** 回复的原始消息 message_id */
    private String inReplyTo;

    /** 线程引用链（字段名避开 MySQL 保留字 references；列名自动映射为 references_chain） */
    private String referencesChain;

    /** 软删除 */
    private Boolean deleted = false;

    private LocalDateTime deletedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
