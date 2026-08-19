package com.agentgateway.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReadStatusRequest {
    @NotNull(message = "read 不能为空")
    private Boolean read;
}
