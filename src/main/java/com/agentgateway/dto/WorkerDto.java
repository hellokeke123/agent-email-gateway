package com.agentgateway.dto;

import lombok.Builder;
import lombok.Value;
import java.time.LocalDateTime;

@Value
@Builder
public class WorkerDto {
    Long id;
    Long roleId;
    String name;
    String status;
    String webhookUrl;
    LocalDateTime createdAt;
}
