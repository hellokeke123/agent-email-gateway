package com.agentgateway.exception;

import com.agentgateway.controller.ApiMessageController;
import com.agentgateway.service.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MessageService messageService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        messageService = mock(MessageService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ApiMessageController(messageService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void send_withMalformedJson_returnsInvalidJsonContract() throws Exception {
        mockMvc.perform(post("/api/messages/send")
                        .contentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
                        .content("{\"toRoleId\":1,\"subject\":\"test\",\"body\":\"body\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_JSON"))
                .andExpect(jsonPath("$.path").value("/api/messages/send"))
                .andExpect(jsonPath("$.message").value("请发送有效的 UTF-8 JSON，设置 Content-Type: application/json; charset=UTF-8，并检查 JSON 的引号、逗号和转义。"));

        verifyNoInteractions(messageService);
    }

    @Test
    void send_withInvalidUtf8_returnsInvalidJsonContract() throws Exception {
        byte[] prefix = "{\"toRoleId\":1,\"subject\":\"".getBytes(StandardCharsets.UTF_8);
        byte[] suffix = "\",\"body\":\"body\"}".getBytes(StandardCharsets.UTF_8);
        byte[] content = new byte[prefix.length + 2 + suffix.length];
        System.arraycopy(prefix, 0, content, 0, prefix.length);
        content[prefix.length] = (byte) 0xC3;
        content[prefix.length + 1] = (byte) 0x28;
        System.arraycopy(suffix, 0, content, prefix.length + 2, suffix.length);

        mockMvc.perform(post("/api/messages/send")
                        .contentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
                        .content(content))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_JSON"))
                .andExpect(jsonPath("$.path").value("/api/messages/send"))
                .andExpect(jsonPath("$.message").value("请发送有效的 UTF-8 JSON，设置 Content-Type: application/json; charset=UTF-8，并检查 JSON 的引号、逗号和转义。"));

        verifyNoInteractions(messageService);
    }

    @Test
    void send_withValidJsonButConstraintViolation_returnsValidationError() throws Exception {
        mockMvc.perform(post("/api/messages/send")
                        .contentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
                        .content("{\"toRoleId\":1,\"subject\":\"\",\"body\":\"body\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.path").value("/api/messages/send"));

        verifyNoInteractions(messageService);
    }
}
