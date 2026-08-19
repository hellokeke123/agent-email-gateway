package com.agentgateway.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReplyMessageRequest {
    @NotBlank(message = "body 不能为空")
    private String body;
    private String html;
}
