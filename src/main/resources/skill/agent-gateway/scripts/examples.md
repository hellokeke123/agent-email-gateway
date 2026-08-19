# curl 示例速查

把 `{BASE_URL}` 换成网关地址，`{AUTH}` 换成授权码。

## 授权

```bash
# 1. 发起
curl -X POST {BASE_URL}/api/auth/start

# 2. 用户打开 pageUrl 完成 TOTP 验证 + 选角色

# 3. 轮询（completed 时拿 authCode）
curl {BASE_URL}/api/auth/{sessionId}

# 4. 刷新 / 撤销
curl -X POST {BASE_URL}/api/auth/refresh -H "X-Auth-Code: {AUTH}"
curl -X POST {BASE_URL}/api/auth/revoke -H "X-Auth-Code: {AUTH}"
```

## 消息

```bash
# 未读收件
curl "{BASE_URL}/api/messages/inbox?read=false" -H "X-Auth-Code: {AUTH}"

# 全部收件 / 发出
curl "{BASE_URL}/api/messages/inbox" -H "X-Auth-Code: {AUTH}"
curl "{BASE_URL}/api/messages/sent" -H "X-Auth-Code: {AUTH}"

# 详情（不改已读）
curl "{BASE_URL}/api/messages/1" -H "X-Auth-Code: {AUTH}"

# 标记已读 / 完成
curl -X POST "{BASE_URL}/api/messages/1/read" -H "X-Auth-Code: {AUTH}" -H "Content-Type: application/json" -d '{"read":true}'
curl -X POST "{BASE_URL}/api/messages/1/complete" -H "X-Auth-Code: {AUTH}" -H "Content-Type: application/json" -d '{"completed":true}'

# 发送
curl -X POST "{BASE_URL}/api/messages/send" -H "X-Auth-Code: {AUTH}" -H "Content-Type: application/json" \
  -d '{"toRoleId":2,"subject":"你好","body":"正文"}'

# 回复
curl -X POST "{BASE_URL}/api/messages/1/reply" -H "X-Auth-Code: {AUTH}" -H "Content-Type: application/json" \
  -d '{"body":"回复内容"}'

# 其它角色
curl "{BASE_URL}/api/roles" -H "X-Auth-Code: {AUTH}"
```

## 一键授权脚本示例（bash）

```bash
#!/usr/bin/env bash
BASE_URL=http://localhost:8080
START=$(curl -s -X POST "$BASE_URL/api/auth/start")
SID=$(echo "$START" | sed -E 's/.*"sessionId":"([^"]+)".*/\1/')
echo "请用户打开页面完成验证: $BASE_URL/verify?sessionId=$SID"
until curl -s "$BASE_URL/api/auth/$SID" | grep -q '"completed"'; do sleep 3; done
curl -s "$BASE_URL/api/auth/$SID"
```
