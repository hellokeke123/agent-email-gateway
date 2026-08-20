package com.agentgateway.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateTaskRequest {
    @NotBlank
    private String title;
    private String payload;
}
