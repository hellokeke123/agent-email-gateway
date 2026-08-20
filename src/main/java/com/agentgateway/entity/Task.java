package com.agentgateway.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("worker_task")
public class Task {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String publicId;
    private Long sourceMessageId;
    private Long initiatorRoleId;
    private Long targetRoleId;
    private String status;
    private String title;
    private String payload;
    private Long claimedByWorkerId;
    private String leaseToken;
    private LocalDateTime leaseExpiresAt;
    private Integer version;
    private Integer progress;
    private String blockReason;
    private String result;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
