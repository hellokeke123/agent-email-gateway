package com.agentgateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateWorkerRequest {
    @NotNull
    private Long roleId;
    @NotBlank
    private String name;
    private String webhookUrl;
}
