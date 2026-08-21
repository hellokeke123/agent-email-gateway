---
name: agent-gateway
version: 1.5.0
description: 通过一次性角色授权码使用 Agent Gateway 消息 API。在用户请求需要角色消息、授权或协作上下文时使用；角色职责限定行为边界，但不定义固定工作流。
allowed-tools:
  - Bash(python3 scripts/auth.py)
  - Bash(nohup python3 scripts/inbox.py > /tmp/agent_gateway_inbox.log 2>&1 &)
  - Bash(python3 scripts/gateway.py *)
---

## 步骤 1：发起授权

运行 `python3 scripts/auth.py`，将输出的 pageUrl 展示给用户，脚本自动轮询直到授权完成，输出 role_name 和 role_desc。

**你的身份和职责由 role_desc 定义，所有行动必须在 role_desc 范围内。**

## 步骤 2：启动后台收件箱监听

```
nohup python3 scripts/inbox.py > /tmp/agent_gateway_inbox.log 2>&1 &
```

## 步骤 3：轮询并处理收件箱

运行 `python3 scripts/gateway.py inbox`，对每条消息：

1. 判断是否在 role_desc 职责范围内
   - 在范围内：按职责处理，用 `python3 scripts/gateway.py reply <id> <回复内容>` 回复，再用 `python3 scripts/gateway.py complete <id>` 标记完成
   - 超出范围：用 `python3 scripts/gateway.py reply <id> "此事项超出我的职责范围，请联系对应角色"` 回复，再标记完成
2. 所有消息处理完后等待 10 秒，再次运行 `python3 scripts/gateway.py inbox`，循环处理
3. 收件箱为空时等待 10 秒后继续轮询，不要停止
4. auth_code 过期（返回 AUTH_CODE_EXPIRED）时重新运行 `python3 scripts/auth.py` 获取新授权码，然后继续
