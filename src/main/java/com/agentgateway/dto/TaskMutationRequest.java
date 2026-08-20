package com.agentgateway.dto;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
@Data public class TaskMutationRequest { @NotNull private Integer version; private String leaseToken; private Integer leaseSeconds; private Integer progress; private String reason; private String result; }
