package com.agentgateway.service;

import com.agentgateway.dto.MessageDto;
import com.agentgateway.dto.ReplyMessageRequest;
import com.agentgateway.dto.SendMessageRequest;
import com.agentgateway.entity.AuthCode;
import com.agentgateway.entity.Message;
import com.agentgateway.entity.Role;
import com.agentgateway.mapper.MessageMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MessageServiceTest {

    @Autowired
    MessageService messageService;
    @Autowired
    RoleService roleService;
    @Autowired
    AuthService authService;
    @Autowired
    MessageMapper messageMapper;

    private List<Role> twoRoles() {
        roleService.create("角色A", null);
        roleService.create("角色B", null);
        return roleService.listAll();
    }

    @Test
    void send_recordsAuthCode_andAppearsInRecipientInbox() {
        List<Role> roles = twoRoles();
        Role a = roles.get(0), b = roles.get(1);
        AuthCode code = authService.issueCode(a);

        SendMessageRequest req = new SendMessageRequest();
        req.setToRoleId(b.getId());
        req.setSubject("你好");
        req.setBody("正文");
        MessageDto dto = messageService.send(a, req, code);

        Message m = messageMapper.selectById(dto.getId());
        assertEquals(a.getId(), m.getFromRoleId());
        assertEquals(b.getId(), m.getToRoleId());
        assertEquals(code.getId(), m.getAuthCodeId()); // ★ 审计：记录发送时授权码
        assertEquals("你好", m.getSubject());

        List<MessageDto> inbox = messageService.inbox(b, null, null, 20);
        assertEquals(1, inbox.size());
        assertEquals(a.getName(), inbox.get(0).getFromRoleName());

        // unread filter
        assertEquals(1, messageService.inbox(b, false, null, 20).size());
    }

    @Test
    void markReadAndComplete_areIndependent() {
        List<Role> roles = twoRoles();
        Role a = roles.get(0), b = roles.get(1);
        AuthCode code = authService.issueCode(a);
        SendMessageRequest req = new SendMessageRequest();
        req.setToRoleId(b.getId());
        req.setSubject("s");
        req.setBody("b");
        MessageDto dto = messageService.send(a, req, code);

        messageService.markRead(b, dto.getId(), true);
        Message m = messageMapper.selectById(dto.getId());
        assertTrue(m.getIsRead());
        assertNotNull(m.getReadAt());
        assertFalse(m.getIsCompleted()); // 未完成

        messageService.markCompleted(b, dto.getId(), true);
        Message m2 = messageMapper.selectById(dto.getId());
        assertTrue(m2.getIsCompleted());
        assertNotNull(m2.getCompletedAt());

        // 已读过滤生效
        assertEquals(1, messageService.inbox(b, true, null, 20).size());
    }

    @Test
    void reply_buildsThreadChain() {
        List<Role> roles = twoRoles();
        Role a = roles.get(0), b = roles.get(1);
        AuthCode codeA = authService.issueCode(a);
        AuthCode codeB = authService.issueCode(b);

        SendMessageRequest req = new SendMessageRequest();
        req.setToRoleId(b.getId());
        req.setSubject("项目进度");
        req.setBody("第一阶段完成");
        MessageDto original = messageService.send(a, req, codeA);

        ReplyMessageRequest replyReq = new ReplyMessageRequest();
        replyReq.setBody("收到，辛苦了");
        MessageDto reply = messageService.reply(b, original.getId(), replyReq, codeB);

        Message rm = messageMapper.selectById(reply.getId());
        assertEquals(b.getId(), rm.getFromRoleId());
        assertEquals(a.getId(), rm.getToRoleId());
        assertEquals(codeB.getId(), rm.getAuthCodeId());
        assertEquals(original.getMessageId(), rm.getInReplyTo());
        assertEquals("Re: 项目进度", rm.getSubject());
        assertTrue(rm.getReferencesChain().contains(original.getMessageId()));
    }
}
