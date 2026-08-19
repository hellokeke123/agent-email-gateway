package com.agentgateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SendMessageRequest {
    @NotNull(message = "toRoleId 不能为空")
    private Long toRoleId;
    @NotBlank(message = "subject 不能为空")
    private String subject;
    @NotBlank(message = "body 不能为空")
    private String body;
    private String html;
}
