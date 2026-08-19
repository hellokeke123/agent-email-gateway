package com.agentgateway.service;

import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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

                你是一个 AI 助手。用户环境中有名为 **agent-gateway** 的服务，它通过角色授权码让你代为收发角色之间的消息。
                你需要安装其 skill 才能知道如何使用它。请严格按以下步骤执行：

                ## 第一步：下载 skill 包

                ```bash
                curl -L -o agent-gateway.zip "%s"
                ```

                ## 第二步：解压安装到 skill 目录

                ```bash
                mkdir -p ~/.claude/skills/agent-gateway
                unzip -o agent-gateway.zip -d ~/.claude/skills/agent-gateway
                # 若安装到项目本地：解压到 .claude/skills/agent-gateway/
                ```

                ## 第三步：阅读并加载 skill

                解压后根目录有 `SKILL.md`，请完整读取它，它会告诉你：
                - 授权流程：调用 `POST %s` 发起授权 → 请用户打开返回的页面完成 TOTP 验证并选择一个角色 → 你轮询直到拿到授权码
                - 所有消息操作 API 的用法与 curl 示例（未读查询、发送、回复、已读/完成标记等）

                ## 安装完成后的自检

                - 确认 `SKILL.md` 已读取，且你知道如何发起授权
                - 未取得授权码前，不要尝试调用任何消息 API
                - 若你没有 curl/unzip，可用你环境等价的下载与解压方式完成同样步骤

                现在开始执行以上步骤。若已安装过，直接读取 skill 即可。
                """.formatted(downloadUrl, authStartUrl);
    }

    /** 下载接口：把 skill 文件打包成 zip */
    public byte[] buildZip() {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (String entry : ENTRIES) {
                try (InputStream in = getClass().getResourceAsStream(PREFIX + entry)) {
                    if (in == null) {
                        throw new IllegalStateException("skill 资源缺失: " + entry);
                    }
                    zos.putNextEntry(new ZipEntry("agent-gateway/" + entry));
                    in.transferTo(zos);
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
