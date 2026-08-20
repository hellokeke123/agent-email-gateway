package com.agentgateway.service;

import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Skill 三件套：教程提示词 + skill 包（zip 打包） */
@Service
public class SkillService {

    private static final String PREFIX = "/skill/agent-gateway/";
    private static final List<String> ENTRIES = List.of("SKILL.md", "README.md", "scripts/examples.md");

    /** 教程接口：返回教 agent 安装 skill 的提示词 */
    public String tutorialMarkdown(String baseUrl) {
        String downloadUrl = baseUrl + "/api/skill/download";
        String authStartUrl = baseUrl + "/api/auth/start";
        return """
                # 安装 Agent Gateway Skill（必读）

                你是一个 AI 助手。用户环境中有名为 **agent-gateway** 的服务，它通过一次性角色授权码提供角色消息 API。
                请安装并阅读 skill，然后继续处理触发本教程的**原始用户请求**；不要把安装或收件箱查询当成用户工作的替代品。

                ## 下载、解压和读取

                ```bash
                curl -L -o agent-gateway.zip "%s"
                mkdir -p ~/.config/agent-gateway/skill
                unzip -o agent-gateway.zip -d ~/.config/agent-gateway/skill
                # 也可以使用宿主环境等价的下载、解压和 skill 安装能力
                ```

                完成后完整读取 `SKILL.md`，确认本地版本与下载包一致。它描述真实 API、安全约束、授权流程和协作判断规则。

                ## 授权与继续工作

                1. 保存当前用户的原始请求和相关上下文。调用 `POST %s`，将响应中的 `pageUrl` 原样交给用户；只有用户能在该页面完成 TOTP 和角色选择。你不得索取、接收或处理 TOTP。
                2. 如果你的运行时支持后台或异步任务，将 `GET /api/auth/{sessionId}` 的有限轮询交给该能力，每 3–5 秒检查一次。`waiting_totp` / `verified` 继续轮询，`completed` 停止并将结果通知或恢复到当前 Agent 上下文，`410 SESSION_EXPIRED` 停止并说明需要重新授权。
                3. 如果运行时不支持后台或异步任务，明确告知用户授权轮询会占用当前执行，或请用户稍后让你继续；不要声称已经启动常驻任务，也不要伪造后台能力。
                4. 轮询完成后，在当前上下文保留 `authCode`，并保存返回的 `role.name` 与 `role.description`。`role.description` 只定义职责边界；根据原始用户请求和已有协作信息自主决定下一步。收件箱是协作上下文工具，不是自动启动任务的来源；空收件箱不是当前请求结束的理由。没有请求或协作事件时，不伪造任务。
                5. 使用授权身份继续处理原始用户请求。只有在当前请求需要时才查询收件箱、发送或回复消息，并遵守 `SKILL.md` 的安全和验证规则。

                未取得授权码前不要调用消息 API。若环境没有 curl/unzip，使用等价的宿主能力完成下载和解压。现在开始安装并读取 skill，然后回到原始用户请求。
                """.formatted(downloadUrl, authStartUrl);
    }

    /** 下载接口：把 skill 文件打包成 zip */
    public byte[] buildZip(String baseUrl) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (String entry : ENTRIES) {
                try (InputStream in = getClass().getResourceAsStream(PREFIX + entry)) {
                    if (in == null) {
                        throw new IllegalStateException("skill 资源缺失: " + entry);
                    }
                    String content = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                            .replace("{BASE_URL}", baseUrl);
                    zos.putNextEntry(new ZipEntry("agent-gateway/" + entry));
                    zos.write(content.getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                }
            }
            zos.finish();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("skill 打包失败", e);
        }
    }
}
