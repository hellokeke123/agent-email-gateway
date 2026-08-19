---
name: agent-gateway
description: 通过角色授权码操作 Agent Gateway 网关，可查询未读消息、读取、发送、回复、标记已读/完成、列出其它角色。当用户提到"网关消息"、"角色消息"、"agent-gateway"、"未读消息"、"发消息"时激活。
---

# Agent Gateway Skill

Agent Gateway 是一个角色消息网关服务。你可以通过**授权码**代表某个**角色**收发消息。授权码由人类用户通过 TOTP（Authenticator）在网页上签发，**你永远拿不到任何明文凭据**，只能使用授权码。

## 一、授权流程（必须先完成才能调用任何消息 API）

1. 发起授权：

```bash
curl -X POST {BASE_URL}/api/auth/start
```

返回：

```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "pageUrl": "http://localhost:8080/verify?sessionId=550e8400-e29b-41d4-a716-446655440000"
}
```

2. **请人类用户**在浏览器打开 `pageUrl`，完成 TOTP 一次性代码验证，并选择要授权的角色（如"角色A"）。页面会展示授权码。

3. 你轮询授权状态（约每 3~5 秒一次）：

```bash
curl {BASE_URL}/api/auth/{sessionId}
```

- `state` 为 `waiting_totp` / `verified` 时继续等待
- `state` 为 `completed` 时返回：

```json
{
  "sessionId": "...",
  "state": "completed",
  "authCode": "9f8f6f2c-....-000000000001",
  "authCodeExpiresAt": "2026-08-19T11:00:00Z",
  "role": { "id": 2, "name": "角色A", "description": "..." }
}
```

4. 拿到 `authCode` 后，以下所有消息 API 都通过请求头携带：

```
X-Auth-Code: <authCode>
```

### 授权码生命周期

- 有效期默认 **60 分钟**，过期后可用旧码刷新（刷新也适用于过期但未撤销的码）：

```bash
curl -X POST {BASE_URL}/api/auth/refresh -H "X-Auth-Code: <authCode>"
```

- 撤销（调用后该码立即失效）：

```bash
curl -X POST {BASE_URL}/api/auth/revoke -H "X-Auth-Code: <authCode>"
```

- ⚠️ 一个角色同时只能有一个有效授权码。若用户对同一角色再次授权，旧的授权码立即失效。收到 `401 AUTH_CODE_INVALID` 时，请先尝试 `refresh`；若仍失败，需请用户重新走授权流程。
- 授权码绑定的是**单个角色**：所有操作都作用于该角色。你不能操作其它角色，只能查询其它角色的名称。

## 二、消息操作 API

所有请求带 `X-Auth-Code` 头。`{BASE_URL}` 指网关地址。

### 1. 查询收件（可按 已读 / 完成 过滤）

```bash
curl "{BASE_URL}/api/messages/inbox?read=false&limit=20" -H "X-Auth-Code: <authCode>"
```

参数：`read`(true/false)、`completed`(true/false)、`limit`(默认 20，最大 100)。全部省略即返回全部收件。

### 2. 查询发出（sent）

```bash
curl "{BASE_URL}/api/messages/sent?limit=20" -H "X-Auth-Code: <authCode>"
```

### 3. 读取消息详情（**不会**改变已读状态）

```bash
curl "{BASE_URL}/api/messages/{id}" -H "X-Auth-Code: <authCode>"
```

### 4. 标记已读 / 未读

```bash
curl -X POST "{BASE_URL}/api/messages/{id}/read" \
  -H "X-Auth-Code: <authCode>" -H "Content-Type: application/json" \
  -d '{"read": true}'
```

### 5. 标记完成 / 未完成

```bash
curl -X POST "{BASE_URL}/api/messages/{id}/complete" \
  -H "X-Auth-Code: <authCode>" -H "Content-Type: application/json" \
  -d '{"completed": true}'
```

### 6. 发送消息（从绑定角色发给另一个角色）

```bash
curl -X POST "{BASE_URL}/api/messages/send" \
  -H "X-Auth-Code: <authCode>" -H "Content-Type: application/json" \
  -d '{"toRoleId": 3, "subject": "你好", "body": "正文内容", "html": "<p>正文内容</p>"}'
```

`html` 可选。每条发送消息都会记录发送时使用的授权码（`auth_code_id`），可完整追溯。

### 7. 回复消息（自动带线程链 In-Reply-To / References，主题加 Re: 前缀）

```bash
curl -X POST "{BASE_URL}/api/messages/{id}/reply" \
  -H "X-Auth-Code: <authCode>" -H "Content-Type: application/json" \
  -d '{"body": "回复内容"}'
```

### 8. 列出其它角色（只读名称）

```bash
curl "{BASE_URL}/api/roles" -H "X-Auth-Code: <authCode>"
```

## 三、常见错误

| HTTP | error | 含义与处理 |
|------|-------|-----------|
| 401 | `AUTH_CODE_INVALID` | 授权码无效/已撤销。尝试 `refresh`；失败则请用户重新授权 |
| 401 | `AUTH_CODE_EXPIRED` | 授权码过期。用旧码 `POST /api/auth/refresh` 换新码 |
| 404 | `NOT_FOUND` | 消息/角色不存在（也可能消息不属于你的绑定角色） |
| 400 | `VALIDATION_ERROR` | 请求参数校验失败，检查字段 |

## 四、工作准则

1. 未取得有效授权码前，**不要**调用任何消息 API。
2. 所有操作都默认作用于你绑定角色收到的/发出的消息。
3. 用户提到的"对方"角色名未知时，先 `GET /api/roles` 查询。
4. 发送消息时用绑定角色作为发件人；回复消息会自动回给原发件人角色。
