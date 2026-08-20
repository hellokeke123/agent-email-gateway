# curl 示例速查

> JSON 请求必须使用 UTF-8，并设置 `Content-Type: application/json; charset=UTF-8`；多行内容使用 `\n`，正确转义引号和控制字符。若收到 `400 INVALID_JSON`，请重新生成有效 UTF-8 JSON，不要重试相同字节。

下载的 skill 包已预先配置网关地址。`{BASE_URL}` 仅在 ZIP 生成时替换；将 `{AUTH}`、`{sessionId}`、`{id}` 替换为运行时值。

## 有限授权轮询

`/api/auth/start` + `/api/auth/{sessionId}` 是有限的人类授权会话。保存触发授权的原始用户请求，并将服务端返回的 `pageUrl` 原样交给人类用户完成 TOTP 和角色选择；Agent 不得索取或处理 TOTP。

若宿主运行时支持后台、异步或可恢复任务，可用其提供的能力每 3–5 秒轮询。`completed` 后停止轮询，并将 `authCode`、`role.name`、`role.description` 交回当前 Agent 上下文；`SESSION_EXPIRED` 后停止。若不支持后台能力，应说明轮询会占用当前执行或请用户稍后继续，不能声称已启动常驻任务。

```bash
curl -X POST "{BASE_URL}/api/auth/start"
curl "{BASE_URL}/api/auth/{sessionId}"
# 仅在 state=completed 后使用 authCode；角色描述定义职责边界。
curl -X POST "{BASE_URL}/api/auth/refresh" -H "X-Auth-Code: {AUTH}"
curl -X POST "{BASE_URL}/api/auth/revoke" -H "X-Auth-Code: {AUTH}"
```

授权后继续处理原始用户请求。收件箱仅是按需获取的协作上下文：空收件箱不表示当前请求结束，也不能在没有用户请求或协作事件时伪造任务。

## 消息协作上下文

```bash
# 仅在当前请求需要协作上下文时查询收件箱
curl "{BASE_URL}/api/messages/inbox?read=false&completed=false&limit=20" \
  -H "X-Auth-Code: {AUTH}"
curl "{BASE_URL}/api/messages/sent?limit=20" -H "X-Auth-Code: {AUTH}"

# 读取详情不会自动改变已读状态
curl "{BASE_URL}/api/messages/{id}" -H "X-Auth-Code: {AUTH}"

# 发送消息
curl -X POST "{BASE_URL}/api/messages/send" \
  -H "X-Auth-Code: {AUTH}" -H "Content-Type: application/json; charset=UTF-8" \
  --data-binary '{"toRoleId":2,"subject":"协作请求","body":"目标、上下文和所需下一步。"}'

# 在原消息线程中回复进度、结果或澄清请求
curl -X POST "{BASE_URL}/api/messages/{id}/reply" \
  -H "X-Auth-Code: {AUTH}" -H "Content-Type: application/json; charset=UTF-8" \
  --data-binary '{"body":"处理进展、可复现证据或需要澄清的事项。"}'

# 只有实际处理了该消息时才更新接收方状态
curl -X POST "{BASE_URL}/api/messages/{id}/read" \
  -H "X-Auth-Code: {AUTH}" -H "Content-Type: application/json; charset=UTF-8" \
  --data-binary '{"read":true}'
curl -X POST "{BASE_URL}/api/messages/{id}/complete" \
  -H "X-Auth-Code: {AUTH}" -H "Content-Type: application/json; charset=UTF-8" \
  --data-binary '{"completed":true}'
```

## 显式 Worker Task API

Worker Task 是可选的独立网关能力，不是 Agent 消息协作机制。普通消息不会自动创建任务；发送者必须显式设置 `createTask` 和 `task`：

```bash
curl -X POST "{BASE_URL}/api/messages/send" \
  -H "X-Auth-Code: {AUTH}" -H "Content-Type: application/json; charset=UTF-8" \
  --data-binary '{"toRoleId":2,"subject":"构建产物","body":"请处理构建请求。","createTask":true,"task":{"title":"构建产物","payload":"{\"artifact\":\"release\"}"}}'
```

外部 Worker 使用独立的 Worker 凭据管理任务，不使用 Agent 的 `X-Auth-Code`，也不能发送或回复角色消息。以下示例供已注册的外部 Worker 手动诊断其任务 API：

```bash
curl "{BASE_URL}/api/worker/tasks" -H "X-Worker-Token: {WORKER_TOKEN}"
curl "{BASE_URL}/api/worker/tasks/{publicId}" -H "X-Worker-Token: {WORKER_TOKEN}"

curl -X POST "{BASE_URL}/api/worker/tasks/{publicId}/claim" \
  -H "X-Worker-Token: {WORKER_TOKEN}" -H "Content-Type: application/json; charset=UTF-8" \
  --data-binary '{"version":1,"leaseSeconds":300}'

curl -X POST "{BASE_URL}/api/worker/tasks/{publicId}/progress" \
  -H "X-Worker-Token: {WORKER_TOKEN}" -H "Content-Type: application/json; charset=UTF-8" \
  --data-binary '{"version":2,"leaseToken":"{LEASE_TOKEN}","progress":50}'

curl -X POST "{BASE_URL}/api/worker/tasks/{publicId}/complete" \
  -H "X-Worker-Token: {WORKER_TOKEN}" -H "Content-Type: application/json; charset=UTF-8" \
  --data-binary '{"version":3,"leaseToken":"{LEASE_TOKEN}","result":"完成结果"}'
```

管理员注册、轮换或启停外部 Worker 的接口和 webhook 验签协议属于部署与运维边界；详情请参阅根项目 README。Worker 任务状态以 API 返回为准，发生版本或租约冲突时重新读取任务。
