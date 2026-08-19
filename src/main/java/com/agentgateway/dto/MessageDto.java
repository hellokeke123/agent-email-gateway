package com.agentgateway.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MessageDto {
    private Long id;
    private String messageId;
    private Long fromRoleId;
    private String fromRoleName;
    private Long toRoleId;
    private String toRoleName;
    private String subject;
    private Boolean isRead;
    private LocalDateTime readAt;
    private Boolean isCompleted;
    private LocalDateTime completedAt;
    private String inReplyTo;
    private LocalDateTime createdAt;
}
