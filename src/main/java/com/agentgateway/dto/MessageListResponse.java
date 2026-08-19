package com.agentgateway.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MessageListResponse {
    private int count;
    private List<MessageDto> messages;
}
