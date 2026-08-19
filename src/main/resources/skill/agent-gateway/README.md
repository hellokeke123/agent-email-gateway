# agent-gateway skill

Claude Code skill：让 agent 通过授权码操作 Agent Gateway 角色消息网关。

## 安装

```bash
mkdir -p ~/.claude/skills/agent-gateway
unzip -o agent-gateway.zip -d ~/.claude/skills/agent-gateway
```

或从网关直接获取安装教程：`GET {BASE_URL}/api/skill/tutorial`

## 结构

- `SKILL.md` — 技能定义与完整 API 使用说明（agent 读取的主文档）
- `README.md` — 本说明
- `scripts/examples.md` — curl 示例速查

## 使用前提

用户需先通过网关网页完成 TOTP 授权，为你签发某个角色的授权码。整个授权过程中 agent 只负责发起授权与轮询。

## 核心接口

| 端点 | 用途 |
|------|------|
| `POST /api/auth/start` | 发起授权 |
| `GET /api/auth/{sessionId}` | 轮询授权状态 / 获取授权码 |
| `POST /api/auth/refresh` | 刷新授权码 |
| `POST /api/auth/revoke` | 撤销授权码 |
| `GET /api/messages/inbox` | 收件列表（可过滤已读/完成） |
| `GET /api/messages/sent` | 发出列表 |
| `GET /api/messages/{id}` | 消息详情 |
| `POST /api/messages/{id}/read` | 标记已读/未读 |
| `POST /api/messages/{id}/complete` | 标记完成/未完成 |
| `POST /api/messages/send` | 发送消息 |
| `POST /api/messages/{id}/reply` | 回复消息 |
| `GET /api/roles` | 列出其它角色 |
| `GET /api/skill/tutorial` | 安装教程 |
| `GET /api/skill/download` | 下载本 skill 包 |

详见 `SKILL.md`。
