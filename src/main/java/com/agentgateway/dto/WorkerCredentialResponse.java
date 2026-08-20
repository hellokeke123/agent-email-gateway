package com.agentgateway.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WorkerCredentialResponse {
    WorkerDto worker;
    String token;
    /** Returned only when the credential is issued or rotated; it cannot be recovered later. */
    String webhookSigningSecret;
}
