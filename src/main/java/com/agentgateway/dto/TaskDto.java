package com.agentgateway.dto;

import lombok.Builder;
import lombok.Value;
import java.time.LocalDateTime;

@Value
@Builder
public class TaskDto {
    String publicId;
    Long sourceMessageId;
    Long initiatorRoleId;
    Long targetRoleId;
    String status;
    String title;
    String payload;
    Integer progress;
    String blockReason;
    String result;
    Integer version;
    String leaseToken;
    LocalDateTime leaseExpiresAt;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
