package com.agentgateway.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("worker_credential")
public class WorkerCredential {
    @TableId(type = IdType.AUTO) private Long id;
    private Long workerId;
    private String tokenHash;
    private String webhookSigningSecretHash;
    private String tokenPrefix;
    private Boolean active = true;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updatedAt;
}
