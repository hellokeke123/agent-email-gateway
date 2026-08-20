package com.agentgateway.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("task_event")
public class TaskEvent {
    @TableId(type = IdType.AUTO) private Long id;
    private Long taskId;
    private Long workerId;
    private String eventType;
    private String payload;
    private String deliveryStatus;
    private String dispatchLeaseToken;
    private LocalDateTime dispatchLeaseExpiresAt;
    private Integer attempts;
    private LocalDateTime nextAttemptAt;
    private LocalDateTime deliveredAt;
    private String lastError;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updatedAt;
}
