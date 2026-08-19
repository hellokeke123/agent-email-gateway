package com.agentgateway.controller;

import com.agentgateway.dto.CompletedStatusRequest;
import com.agentgateway.dto.MessageDetailDto;
import com.agentgateway.dto.MessageDto;
import com.agentgateway.dto.MessageListResponse;
import com.agentgateway.dto.ReadStatusRequest;
import com.agentgateway.dto.ReplyMessageRequest;
import com.agentgateway.dto.SendMessageRequest;
import com.agentgateway.entity.AuthCode;
import com.agentgateway.entity.Role;
import com.agentgateway.service.MessageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class ApiMessageController {

    private final MessageService messageService;

    /** 收件列表，可按 read / completed 过滤 */
    @GetMapping("/inbox")
    public MessageListResponse inbox(HttpServletRequest request,
                                     @RequestParam(required = false) Boolean read,
                                     @RequestParam(required = false) Boolean completed,
                                     @RequestParam(required = false) Integer limit) {
        Role role = (Role) request.getAttribute("boundRole");
        List<MessageDto> list = messageService.inbox(role, read, completed, limit);
        return MessageListResponse.builder().count(list.size()).messages(list).build();
    }

    /** 绑定角色发出的消息 */
    @GetMapping("/sent")
    public MessageListResponse sent(HttpServletRequest request,
                                    @RequestParam(required = false) Integer limit) {
        Role role = (Role) request.getAttribute("boundRole");
        List<MessageDto> list = messageService.sent(role, limit);
        return MessageListResponse.builder().count(list.size()).messages(list).build();
    }

    /** 读消息详情（不改已读状态） */
    @GetMapping("/{id}")
    public MessageDetailDto detail(@PathVariable Long id, HttpServletRequest request) {
        Role role = (Role) request.getAttribute("boundRole");
        return messageService.detail(role, id);
    }

    /** 独立标记已读/未读 */
    @PostMapping("/{id}/read")
    public MessageDetailDto markRead(@PathVariable Long id,
                                     @RequestBody @Valid ReadStatusRequest req,
                                     HttpServletRequest request) {
        Role role = (Role) request.getAttribute("boundRole");
        messageService.markRead(role, id, req.getRead());
        return messageService.detail(role, id);
    }

    /** 独立标记完成/未完成 */
    @PostMapping("/{id}/complete")
    public MessageDetailDto markCompleted(@PathVariable Long id,
                                          @RequestBody @Valid CompletedStatusRequest req,
                                          HttpServletRequest request) {
        Role role = (Role) request.getAttribute("boundRole");
        messageService.markCompleted(role, id, req.getCompleted());
        return messageService.detail(role, id);
    }

    /** 从绑定角色发送给其它角色（记录授权码） */
    @PostMapping("/send")
    public MessageDetailDto send(@RequestBody @Valid SendMessageRequest req, HttpServletRequest request) {
        Role role = (Role) request.getAttribute("boundRole");
        AuthCode authCode = (AuthCode) request.getAttribute("boundAuthCode");
        MessageDto sent = messageService.send(role, req, authCode);
        return messageService.detail(role, sent.getId());
    }

    /** 回复消息（线程链，记录授权码） */
    @PostMapping("/{id}/reply")
    public MessageDetailDto reply(@PathVariable Long id,
                                  @RequestBody @Valid ReplyMessageRequest req,
                                  HttpServletRequest request) {
        Role role = (Role) request.getAttribute("boundRole");
        AuthCode authCode = (AuthCode) request.getAttribute("boundAuthCode");
        MessageDto sent = messageService.reply(role, id, req, authCode);
        return messageService.detail(role, sent.getId());
    }
}
