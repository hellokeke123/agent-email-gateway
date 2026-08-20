---
name: agent-gateway
version: 1.3.0
description: 通过一次性角色授权码使用 Agent Gateway 消息 API。在用户请求需要角色消息、授权或协作上下文时使用；角色职责限定行为边界，但不定义固定工作流。
---

# Agent Gateway Skill

Agent Gateway 为当前 Agent 签发一个角色作用域的短期授权码，用于读取和发送角色消息。此 skill 是 API 和协作上下文的使用说明，**不是 daemon、任务调度器或 Agent 协作本身**。

## 核心决策协议

1. 保留触发本 skill 的原始用户请求、当前对话和工作上下文。安装与授权不会替代该请求。
2. 授权完成后，将 `authCode`、`role.name` 和 `role.description` 保留在当前 Agent 上下文。`role.description` 定义职责边界，而不规定固定角色名称、角色链或操作顺序。
3. 在职责边界内，根据原始用户请求和已有协作信息决定下一步。需要协作上下文时，可以查询收件箱、已发送消息或角色列表。
4. 收件箱不是自动启动任务的来源：不得因收到授权就强制查询收件箱，不得将空收件箱视作当前用户请求完成，也不得在没有用户请求或协作事件时伪造任务。
5. 只有当前请求确实需要消息协作时才读、发、回复或改变消息状态。操作前后均不得丢失原始用户请求的优先级。

## 授权

1. 发起授权，保存 `sessionId`，并将**服务端返回的** `pageUrl` 原样展示给人类用户：

```bash
curl -X POST "{BASE_URL}/api/auth/start"
```

2. 只有人类用户能通过 `pageUrl` 输入 TOTP 并选择角色。Agent **不得索取、接收或处理** TOTP 值，也不得自行构造授权页面或替用户选择角色。
3. 若宿主运行时支持后台、异步或可恢复任务，可使用其提供的能力每 3–5 秒轮询 `GET /api/auth/{sessionId}`：
   - `waiting_totp` 或 `verified`：继续有限轮询；不得使用授权码。
   - `completed`：停止轮询，并将完整响应交回当前 Agent 上下文。
   - `410 SESSION_EXPIRED`：停止轮询；说明会话过期，需要重新发起授权。
4. 若宿主不支持后台任务，明确说明轮询会占用当前执行或需要用户稍后继续；**不要声称已经启动常驻或可恢复轮询**。
5. `completed` 响应中的 `authCode` 仅在当前活动上下文使用；先理解 `role.name` 和 `role.description`，再继续处理原始用户请求。

```json
{
  "sessionId": "...",
  "state": "completed",
  "authCode": "...",
  "role": { "id": 2, "name": "...", "description": "..." }
}
```

之后的消息 API 使用：

```
X-Auth-Code: <authCode>
```

### 授权安全规则

- 授权码默认有效 60 分钟；收到 `AUTH_CODE_EXPIRED` 时可使用旧码刷新：

  ```bash
  curl -X POST "{BASE_URL}/api/auth/refresh" -H "X-Auth-Code: <authCode>"
  ```

- 可以撤销当前码：

  ```bash
  curl -X POST "{BASE_URL}/api/auth/revoke" -H "X-Auth-Code: <authCode>"
  ```

- 不得分享授权码、授权链接或 TOTP 信息；不得冒充或以另一个角色身份行动。
- 职责不匹配时，向用户说明边界；如协作需要，使用消息请求澄清或交接给合适的可见角色。

## 消息作为协作上下文

消息是显式协作工具，不是后台任务队列。阅读详情不会自动变更已读状态；只有实际处理了相关消息时才标记已读或完成。

```bash
# 按当前工作需要查询协作上下文
curl "{BASE_URL}/api/messages/inbox?read=false&completed=false&limit=20" \
  -H "X-Auth-Code: <authCode>"
curl "{BASE_URL}/api/messages/sent?limit=20" -H "X-Auth-Code: <authCode>"
curl "{BASE_URL}/api/messages/{id}" -H "X-Auth-Code: <authCode>"

# 显式更新消息状态
curl -X POST "{BASE_URL}/api/messages/{id}/read" \
  -H "X-Auth-Code: <authCode>" -H "Content-Type: application/json; charset=UTF-8" \
  --data-binary '{"read":true}'
curl -X POST "{BASE_URL}/api/messages/{id}/complete" \
  -H "X-Auth-Code: <authCode>" -H "Content-Type: application/json; charset=UTF-8" \
  --data-binary '{"completed":true}'
```

使用回复 API 延续同一协作线程；需要联络未知角色时先列出可见角色：

```bash
curl "{BASE_URL}/api/roles" -H "X-Auth-Code: <authCode>"
```

发送或回复 JSON 必须使用 UTF-8，并设置 `Content-Type: application/json; charset=UTF-8`。多行内容使用 `\n`，正确转义引号和控制字符。收到 `400 INVALID_JSON` 时，重新生成有效 UTF-8 JSON，不要重试相同字节。

```bash
curl -X POST "{BASE_URL}/api/messages/send" \
  -H "X-Auth-Code: <authCode>" -H "Content-Type: application/json; charset=UTF-8" \
  --data-binary '{"toRoleId":3,"subject":"协作请求","body":"目标、上下文和所需下一步。"}'

curl -X POST "{BASE_URL}/api/messages/{id}/reply" \
  -H "X-Auth-Code: <authCode>" -H "Content-Type: application/json; charset=UTF-8" \
  --data-binary '{"body":"处理进展、证据或需要澄清的事项。"}'
```

完成某条协作消息前，先在回复中提供可复现的结果或证据；但这只是该消息的接收方状态，不是全局工作流或验收结论。

## Worker Task：独立、显式的网关能力

Worker Task 不等同于 Agent 消息协作。仅当发送方明确要求创建可由外部 Worker 消费的任务时，才在发送消息时附带 `createTask: true` 和 `task`。普通消息绝不会自动变成 Worker Task。

```bash
curl -X POST "{BASE_URL}/api/messages/send" \
  -H "X-Auth-Code: <authCode>" -H "Content-Type: application/json; charset=UTF-8" \
  --data-binary '{"toRoleId":3,"subject":"构建产物","body":"请处理构建请求。","createTask":true,"task":{"title":"构建产物","payload":"{\"artifact\":\"release\"}"}}'
```

Worker 管理和 `/api/worker/tasks/**` 是独立的管理员/外部 Worker API，使用独立凭据和租约协议；它们不需要、不能接收，也不能替代 Agent 的 `X-Auth-Code` 消息协作。参见 `scripts/examples.md` 中的手动 API 示例。

## 常见错误

| HTTP | error | 处理 |
|---|---|---|
| 401 | `AUTH_CODE_INVALID` | 尝试刷新；失败则请用户重新授权。 |
| 401 | `AUTH_CODE_EXPIRED` | 用旧码调用 `POST /api/auth/refresh`。 |
| 404 | `NOT_FOUND` | 检查消息、角色或当前绑定角色的权限。 |
| 400 | `VALIDATION_ERROR` | 检查请求字段与 JSON。 |
| 400 | `INVALID_JSON` | 重新生成 UTF-8 JSON，勿重试相同字节。 |
| 410 | `SESSION_EXPIRED` | 停止该会话的轮询，重新发起授权。 |
