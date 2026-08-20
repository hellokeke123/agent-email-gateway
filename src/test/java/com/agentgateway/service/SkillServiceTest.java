package com.agentgateway.service;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillServiceTest {

    private final SkillService skillService = new SkillService();

    @Test
    void buildZip_rendersOnlyBaseUrlTokensAndPreservesEntries() throws Exception {
        String baseUrl = "https://gateway.example.com/proxy";
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(skillService.buildZip(baseUrl)))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }

        assertEquals(java.util.Set.of(
                "agent-gateway/SKILL.md",
                "agent-gateway/README.md",
                "agent-gateway/scripts/examples.md"), entries.keySet());
        assertTrue(entries.values().stream().allMatch(content -> !content.contains("{BASE_URL}")));
        assertTrue(entries.values().stream().anyMatch(content -> content.contains(baseUrl)));

        String skill = entries.get("agent-gateway/SKILL.md");
        String examples = entries.get("agent-gateway/scripts/examples.md");

        // 授权流程
        assertTrue(skill.contains(baseUrl + "/api/auth/start"));
        assertTrue(skill.contains("waiting_totp"));
        assertTrue(skill.contains("verified"));
        assertTrue(skill.contains("completed"));
        assertTrue(skill.contains("SESSION_EXPIRED"));

        // 角色上下文
        assertTrue(skill.contains("role.name"));
        assertTrue(skill.contains("role.description"));

        // 核心决策协议：原始用户请求优先
        assertTrue(skill.contains("原始用户请求"));
        assertTrue(skill.contains("核心决策协议"));

        // 收件箱是按需工具，不是自动任务来源
        assertTrue(skill.contains("inbox?read=false&completed=false"));
        assertTrue(skill.contains("inbox?completed=false") || skill.contains("inbox?read=false&completed=false"));
        assertFalse(skill.contains("立即查询未读且未完成的收件任务"),
                "不得出现强制收件箱优先的指令");

        // 环境中立后台能力
        assertTrue(skill.contains("后台") || skill.contains("异步"));
        assertFalse(skill.contains("agent_runtime.start_background_task"),
                "不得出现 Claude 专用后台 API");

        // 安全约束
        assertTrue(skill.contains("不得索取、接收或处理"));
        assertTrue(skill.contains("INVALID_JSON"));
        assertTrue(skill.contains("UTF-8"));

        // 回复 API 保留
        assertTrue(skill.contains("reply"));

        // Worker Task 是独立可选能力，不是主流程
        assertTrue(skill.contains("createTask"));
        assertFalse(skill.contains("worker-runtime"), "不得出现 worker-runtime 部署要求");

        // 无硬编码 localhost
        assertFalse(skill.contains("localhost:8080"));

        // examples 包含协作示例
        assertTrue(examples.contains("pageUrl") || examples.contains("sessionId"));
        assertTrue(examples.contains("SESSION_EXPIRED"));
        assertTrue(examples.contains("--data-binary"));
        assertTrue(examples.contains("Content-Type: application/json; charset=UTF-8"));
        assertTrue(examples.contains("X-Auth-Code: {AUTH}") || examples.contains("X-Auth-Code:"));
        assertTrue(examples.contains("{id}"));
        assertTrue(examples.contains("后台") || examples.contains("异步"));
        assertFalse(examples.contains("agent_runtime.start_background_task"),
                "不得出现 Claude 专用后台 API");
    }

    @Test
    void tutorialMarkdown_originalRequestPriorityAndEnvironmentNeutralPolling() {
        String tutorial = skillService.tutorialMarkdown("https://gateway.example.com/proxy");

        // 下载与安装
        assertTrue(tutorial.contains("https://gateway.example.com/proxy/api/skill/download"));
        assertTrue(tutorial.contains("https://gateway.example.com/proxy/api/auth/start"));

        // 原始用户请求优先
        assertTrue(tutorial.contains("原始用户请求"));

        // 授权状态
        assertTrue(tutorial.contains("waiting_totp"));
        assertTrue(tutorial.contains("verified") || tutorial.contains("completed"));
        assertTrue(tutorial.contains("SESSION_EXPIRED"));

        // TOTP 安全约束
        assertTrue(tutorial.contains("不得索取、接收或处理"));

        // 环境中立后台能力描述，不强制 Claude 专用 API
        assertTrue(tutorial.contains("后台") || tutorial.contains("异步"));
        assertFalse(tutorial.contains("agent_runtime.start_background_task"),
                "不得出现 Claude 专用后台 API");

        // 明确说明不支持后台时的限制
        assertTrue(tutorial.contains("不支持") || tutorial.contains("不要声称"));

        // 不强制收件箱优先
        assertFalse(tutorial.contains("授权完成后必须立即执行收件箱优先的工作流"),
                "不得出现强制收件箱优先的指令");

        // 角色上下文
        assertTrue(tutorial.contains("role.name") || tutorial.contains("role.description"));
    }
}
