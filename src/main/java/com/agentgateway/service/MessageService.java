package com.agentgateway.service;

import com.agentgateway.dto.MessageDetailDto;
import com.agentgateway.dto.MessageDto;
import com.agentgateway.entity.AuthCode;
import com.agentgateway.entity.Message;
import com.agentgateway.entity.Role;
import com.agentgateway.exception.ApiException;
import com.agentgateway.mapper.MessageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 角色间消息：收件/发件/详情/已读/完成/发送/回复（发送记录授权码） */
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageMapper messageMapper;
    private final RoleService roleService;
    private final TaskService taskService;

    public List<MessageDto> inbox(Role boundRole, Boolean read, Boolean completed, Integer limit) {
        int lim = normalizeLimit(limit);
        LambdaQueryWrapper<Message> qw = new LambdaQueryWrapper<>();
        qw.eq(Message::getToRoleId, boundRole.getId())
                .eq(Message::getDeleted, false);
        if (read != null) {
            qw.eq(Message::getIsRead, read);
        }
        if (completed != null) {
            qw.eq(Message::getIsCompleted, completed);
        }
        qw.orderByDesc(Message::getId).last("LIMIT " + lim);
        return toDtoList(messageMapper.selectList(qw));
    }

    public List<MessageDto> sent(Role boundRole, Integer limit) {
        int lim = normalizeLimit(limit);
        List<Message> list = messageMapper.selectList(new LambdaQueryWrapper<Message>()
                .eq(Message::getFromRoleId, boundRole.getId())
                .eq(Message::getDeleted, false)
                .orderByDesc(Message::getId)
                .last("LIMIT " + lim));
        return toDtoList(list);
    }

    /** 读详情：消息必须与绑定角色相关（发送方或接收方） */
    public MessageDetailDto detail(Role boundRole, Long id) {
        Message m = getInvolvingOrThrow(boundRole, id);
        return toDetailDto(m);
    }

    /** 独立标记已读/未读（仅接收方） */
    public void markRead(Role boundRole, Long id, boolean read) {
        Message m = getReceivedOrThrow(boundRole, id);
        m.setIsRead(read);
        m.setReadAt(read ? LocalDateTime.now() : null);
        messageMapper.updateById(m);
    }

    /** 独立标记完成/未完成（仅接收方） */
    public void markCompleted(Role boundRole, Long id, boolean completed) {
        Message m = getReceivedOrThrow(boundRole, id);
        m.setIsCompleted(completed);
        m.setCompletedAt(completed ? LocalDateTime.now() : null);
        messageMapper.updateById(m);
    }

    /** 从绑定角色发送给另一角色；★记录发送时使用的授权码 */
    @Transactional
    public MessageDto send(Role boundRole, com.agentgateway.dto.SendMessageRequest req, AuthCode authCode) {
        Role toRole = roleService.getEnabledOrThrow(req.getToRoleId());
        if (Boolean.TRUE.equals(req.getCreateTask())) {
            if (req.getTask() == null) {
                throw ApiException.badRequest("task is required when createTask is true");
            }
            // Validate before persisting so message, task, and its outbox event commit atomically.
            taskService.validateForCreate(req.getTask());
        }
        Message m = new Message();
        m.setMessageId("<" + UUID.randomUUID() + "@gateway>");
        m.setFromRoleId(boundRole.getId());
        m.setToRoleId(toRole.getId());
        m.setAuthCodeId(authCode.getId());
        m.setSubject(req.getSubject());
        m.setBodyText(req.getBody());
        m.setBodyHtml(req.getHtml());
        messageMapper.insert(m);
        if (Boolean.TRUE.equals(req.getCreateTask())) {
            taskService.create(m.getId(), boundRole.getId(), toRole.getId(), req.getTask());
        }
        return toDto(m, roleName(m.getFromRoleId()), roleName(m.getToRoleId()));
    }

    /** 回复消息（线程链 in_reply_to + references；★记录授权码） */
    public MessageDto reply(Role boundRole, Long originalId, com.agentgateway.dto.ReplyMessageRequest req, AuthCode authCode) {
        Message original = getReceivedOrThrow(boundRole, originalId);
        Role toRole = roleService.getEnabledOrThrow(original.getFromRoleId());
        Message m = new Message();
        m.setMessageId("<" + UUID.randomUUID() + "@gateway>");
        m.setFromRoleId(boundRole.getId());
        m.setToRoleId(toRole.getId());
        m.setAuthCodeId(authCode.getId());
        m.setSubject("Re: " + stripRe(original.getSubject()));
        m.setBodyText(req.getBody());
        m.setBodyHtml(req.getHtml());
        m.setInReplyTo(original.getMessageId());
        String refs = (original.getReferencesChain() == null || original.getReferencesChain().isBlank())
                ? original.getMessageId()
                : original.getReferencesChain().trim() + " " + original.getMessageId();
        m.setReferencesChain(refs);
        messageMapper.insert(m);
        return toDto(m, roleName(m.getFromRoleId()), roleName(m.getToRoleId()));
    }

    // ---------- helpers ----------

    private Message getInvolvingOrThrow(Role boundRole, Long id) {
        Message m = messageMapper.selectById(id);
        if (m == null || Boolean.TRUE.equals(m.getDeleted())) {
            throw ApiException.notFound("消息不存在");
        }
        if (!boundRole.getId().equals(m.getFromRoleId()) && !boundRole.getId().equals(m.getToRoleId())) {
            throw ApiException.notFound("消息不存在");
        }
        return m;
    }

    private Message getReceivedOrThrow(Role boundRole, Long id) {
        Message m = messageMapper.selectById(id);
        if (m == null || Boolean.TRUE.equals(m.getDeleted())) {
            throw ApiException.notFound("消息不存在");
        }
        if (!boundRole.getId().equals(m.getToRoleId())) {
            throw ApiException.notFound("消息不存在");
        }
        return m;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return 20;
        }
        return Math.min(limit, 100);
    }

    private String stripRe(String subject) {
        if (subject == null) {
            return "";
        }
        String s = subject.trim();
        while (s.toLowerCase().startsWith("re:")) {
            s = s.substring(3).trim();
        }
        return s;
    }

    private Map<Long, String> roleNameMap() {
        return roleService.listAll().stream()
                .collect(Collectors.toMap(Role::getId, Role::getName, (a, b) -> a));
    }

    private String roleName(Long id) {
        return roleNameMap().getOrDefault(id, String.valueOf(id));
    }

    private List<MessageDto> toDtoList(List<Message> list) {
        Map<Long, String> names = roleNameMap();
        return list.stream().map(m -> toDto(m, names.getOrDefault(m.getFromRoleId(), String.valueOf(m.getFromRoleId())),
                names.getOrDefault(m.getToRoleId(), String.valueOf(m.getToRoleId())))).toList();
    }

    private MessageDto toDto(Message m, String fromName, String toName) {
        return MessageDto.builder()
                .id(m.getId())
                .messageId(m.getMessageId())
                .fromRoleId(m.getFromRoleId())
                .fromRoleName(fromName)
                .toRoleId(m.getToRoleId())
                .toRoleName(toName)
                .subject(m.getSubject())
                .isRead(m.getIsRead())
                .readAt(m.getReadAt())
                .isCompleted(m.getIsCompleted())
                .completedAt(m.getCompletedAt())
                .inReplyTo(m.getInReplyTo())
                .createdAt(m.getCreatedAt())
                .build();
    }

    private MessageDetailDto toDetailDto(Message m) {
        return MessageDetailDto.builder()
                .id(m.getId())
                .messageId(m.getMessageId())
                .fromRoleId(m.getFromRoleId())
                .fromRoleName(roleName(m.getFromRoleId()))
                .toRoleId(m.getToRoleId())
                .toRoleName(roleName(m.getToRoleId()))
                .subject(m.getSubject())
                .bodyText(m.getBodyText())
                .bodyHtml(m.getBodyHtml())
                .isRead(m.getIsRead())
                .readAt(m.getReadAt())
                .isCompleted(m.getIsCompleted())
                .completedAt(m.getCompletedAt())
                .inReplyTo(m.getInReplyTo())
                .references(m.getReferencesChain())
                .createdAt(m.getCreatedAt())
                .build();
    }
}
