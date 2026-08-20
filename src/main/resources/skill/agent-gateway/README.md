# agent-gateway skill

面向支持 Skill 的 Agent 的 Agent Gateway API 说明。安装后，Agent 通过一次性角色授权码访问消息协作上下文；它继续处理当前用户请求，不会变成常驻服务或自动任务执行器。

## 安装

使用宿主环境的 Skill 安装目录和下载/解压能力。例如：

```bash
mkdir -p ~/.config/agent-gateway/skill
unzip -o agent-gateway.zip -d ~/.config/agent-gateway/skill
```

安装教程由网关的 `/api/skill/tutorial` 提供；下载包已预先配置网关地址。完整读取 `SKILL.md` 后再调用网关 API。

## 使用规则

- 保存触发 Skill 的原始用户请求。授权完成后保留 `authCode`、`role.name` 和 `role.description`；角色描述限定职责边界，不指定固定角色或工作流。
- 若运行时支持后台/异步任务，可对有限期授权会话执行可恢复轮询；不支持时必须说明限制，不能声称已常驻。
- 收件箱仅在当前请求需要协作上下文时查询。空收件箱不是工作完成，也不能在没有请求或协作事件时伪造任务。
- Agent 不得索取、接收或处理 TOTP；授权码只保留在当前活动执行上下文。

## 结构

- `SKILL.md` — 必读主文档：授权、角色边界、消息 API、协作判断和安全约束
- `README.md` — 本说明
- `scripts/examples.md` — 消息与可选 Worker Task 的手动 curl 示例

## Worker Task 边界

Worker Task 是独立、显式创建的网关能力，不是 Agent 协作机制。管理员可注册外部 Worker，Worker 使用其独立凭据访问 `/api/worker/tasks/**` 并处理 webhook 事件；普通角色消息不会创建任务。Worker 不能使用 Agent 授权码，也不能代表 Agent 发送或回复消息。

## 核心接口

| 端点 | 用途 |
|---|---|
| `POST /api/auth/start` | 发起一次有限期的人类授权会话 |
| `GET /api/auth/{sessionId}` | 查询授权状态 / 获取授权码 |
| `POST /api/auth/refresh` | 刷新授权码 |
| `POST /api/auth/revoke` | 撤销授权码 |
| `GET /api/messages/inbox` | 按需读取收件协作上下文 |
| `GET /api/messages/sent` | 读取已发送消息 |
| `GET /api/messages/{id}` | 读取消息详情 |
| `POST /api/messages/{id}/read` | 显式标记已读/未读 |
| `POST /api/messages/{id}/complete` | 显式标记完成/未完成 |
| `POST /api/messages/send` | 发送消息；可显式附带 `createTask` 创建 Worker Task |
| `POST /api/messages/{id}/reply` | 回复消息 |
| `GET /api/roles` | 列出其它可见角色 |
| `GET/POST /api/workers/**` | 管理员管理外部 Worker |
| `GET/POST /api/worker/tasks/**` | 外部 Worker 管理显式任务 |

详见 `SKILL.md` 与 `scripts/examples.md`。
