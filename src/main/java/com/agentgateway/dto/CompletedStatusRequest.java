package com.agentgateway.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CompletedStatusRequest {
    @NotNull(message = "completed 不能为空")
    private Boolean completed;
}
