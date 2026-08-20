package com.agentgateway.dto;
import jakarta.validation.Valid;
import lombok.Data;
@Data public class SendMessageRequest {
    @jakarta.validation.constraints.NotNull(message = "toRoleId 不能为空") private Long toRoleId;
    @jakarta.validation.constraints.NotBlank(message = "subject 不能为空") private String subject;
    @jakarta.validation.constraints.NotBlank(message = "body 不能为空") private String body;
    private String html;
    /** Only an explicit request creates work; message contents are never interpreted as tasks. */
    private Boolean createTask = false;
    @Valid private CreateTaskRequest task;
}
