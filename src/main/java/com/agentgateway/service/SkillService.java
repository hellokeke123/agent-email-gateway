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
    private static final List<String> ENTRIES = List.of(
            "SKILL.md", "README.md", "scripts/examples.md",
            "scripts/auth.py", "scripts/inbox.py", "scripts/gateway.py");

    /** 读取 SKILL.md 当前版本号（frontmatter 的 version 字段） */
    public String currentVersion() {
        try (InputStream in = getClass().getResourceAsStream(PREFIX + "SKILL.md")) {
            if (in == null) {
                throw new IllegalStateException("SKILL.md 资源缺失");
            }
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return java.util.regex.Pattern.compile("(?m)^version:\\s*(.+)\\s*$")
                    .matcher(content)
                    .results()
                    .findFirst()
                    .map(m -> m.group(1).trim())
                    .orElseThrow(() -> new IllegalStateException("SKILL.md 缺少 version 字段"));
        } catch (IOException e) {
            throw new IllegalStateException("读取 SKILL.md 版本失败", e);
        }
    }

    /** 教程接口：返回教 agent 安装 skill 的提示词 */
    public String tutorialMarkdown(String baseUrl) {
        String version = currentVersion();
        String downloadUrl = baseUrl + "/api/skill/download";
        String authStartUrl = baseUrl + "/api/auth/start";
        return """
                # 安装 Agent Gateway Skill（必读）

                你是一个 AI 助手。用户环境中有名为 **agent-gateway** 的服务，它通过一次性角色授权码提供角色消息 API。
                请安装并阅读 skill，然后按照 SKILL.md 的授权流程开始工作。**本教程对应 skill 版本：%s**。

                ## 检查版本、下载、解压和读取

                先检查本地是否已安装此 skill：若 `~/.config/agent-gateway/skill/agent-gateway/SKILL.md` 存在且其 version 与 %s 一致，说明已安装最新版，跳过下载，直接进入"授权与继续工作"。否则执行：

                ```bash
                curl -L -o agent-gateway.zip "%s"
                mkdir -p ~/.config/agent-gateway/skill
                unzip -o agent-gateway.zip -d ~/.config/agent-gateway/skill
                # 也可以使用宿主环境等价的下载、解压和 skill 安装能力
                ```

                完成后完整读取 `SKILL.md`，确认本地 version 与 %s 一致。它描述真实 API、安全约束、授权流程和协作判断规则。

                ## 授权与继续工作

                现在按顺序执行以下步骤，每步完成后立即执行下一步，无需等待用户回复。

                **步骤 1：执行此脚本发起授权**

                ```python
                import urllib.request, json
                resp = json.loads(urllib.request.urlopen(
                    urllib.request.Request("%s", method="POST")
                ).read())
                session_id = resp["sessionId"]
                print("pageUrl:", resp["pageUrl"])
                # 将 pageUrl 原样展示给用户，告知其在浏览器完成 TOTP 和角色选择，不得索取或处理 TOTP
                ```

                **步骤 2：立即执行此轮询脚本，不等用户回复**

                ```python
                import urllib.request, json, time
                while True:
                    resp = json.loads(urllib.request.urlopen(
                        "%s/api/auth/" + session_id
                    ).read())
                    state = resp.get("state")
                    if state == "completed":
                        auth_code = resp["authCode"]
                        role_name = resp["role"]["name"]
                        role_desc = resp["role"]["description"]
                        print("授权完成, role:", role_name)
                        break
                    elif state in ("waiting_totp", "verified"):
                        time.sleep(4)
                    else:
                        print("SESSION_EXPIRED，需重新授权"); raise SystemExit(1)
                ```

                **步骤 3：轮询完成后立即查询收件箱，在职责范围内处理消息**

                ```python
                import urllib.request, json
                req = urllib.request.Request(
                    "%s/api/messages/inbox?read=false&completed=false&limit=20",
                    headers={"X-Auth-Code": auth_code}
                )
                inbox = json.loads(urllib.request.urlopen(req).read())
                print(json.dumps(inbox, ensure_ascii=False, indent=2))
                # 根据 role_desc 定义的职责边界处理收到的消息
                ```

                发送或回复消息时始终用 `json.dumps` 构造 body，不要手拼 JSON 字符串。收到 `400 INVALID_JSON` 时改用 `json.dumps` 后立即重试，不得停止。完整 API 用法见 SKILL.md。
                """.formatted(version, version, downloadUrl, version, authStartUrl, baseUrl, baseUrl);
    }

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
