# Agent Gateway — 角色消息网关

自托管服务：让 AI 工具（Agent）通过**授权 API** 访问角色之间的消息。本文档既讲清**为什么这么设计**，也给出**怎么部署使用**。

## 核心设计原则

> **Agent 永远拿不到任何明文凭据。**

这是一切设计的前提。我们解决的是一组真实矛盾：

- Agent 需要读写消息，但我们**不能让 agent 持有任何密码、TOTP 密钥、账号凭据**——那等于把钥匙交给可能被提示词注入的实体。
- 授权必须由**人类**在可控的网页上完成，agent 只扮演"发起 → 轮询 → 领到一把一次性钥匙"的角色。
- 这把钥匙必须是**作用域受限**的：绑定某一个角色、有有效期、可随时作废、每次操作都可追溯。

我们最初评估过真实邮箱方案（IMAP/SMTP + 邮箱别名），最终**放弃邮箱，改为本地消息**：收发双方是「角色」，消息存在本地 MySQL，这样既不需要维护任何真实邮箱账号的凭据，又能获得完整的审计与生命周期控制。这是"零凭据"原则能落到实处的关键一步。

---

## 1. 架构总览

```
┌──────────┐   ① POST /api/auth/start          ┌──────────────────────────┐
│  Agent   │ ─────────────────────────────────► │                          │
│ (AI工具) │   ② GET /api/auth/{sessionId} 轮询  │     Agent Gateway        │
│          │ ◄───────────────────────────────── │     (Spring Boot:8080)   │
│          │   ⑦ X-Auth-Code: <uuid> 调消息 API  │                          │
│          │ ─────────────────────────────────► │   ├ Web 页面 (Thymeleaf) │
└──────────┘                                   │   │   /setup /verify      │
    ▲                                         │   │   /select /roles     │
    │ ③ 打开 pageUrl，浏览器完成人类操作          │   ├ API  (/api/**)      │
    │                                         │   │   auth / messages /  │
┌──────────┐   ④ TOTP 码                     │   │   roles / skill      │
│ 人类用户  │ ─────────────────────────────────► │   └ 定时器: 过期清扫    │
│ 浏览器    │   ⑤ 选角色                        │                          │
│ +        │                                  └──────────┬───────────────┘
│ Authenti-│                                             │ JDBC
│ cator App│                                             ▼
└──────────┘                                    ┌────────────────────────┐
                                                │  MySQL: agent_gateway  │
                                                │  totp_config           │
                                                │  app_role              │
                                                │  auth_code / auth_ssn  │
                                                │  message               │
                                                └────────────────────────┘
```

**角色**（`app_role`）是消息的收发实体，一个虚拟身份。**人类**通过 TOTP 证明"我是这台服务器的主人"，然后为某个角色签发授权码；**Agent** 用该授权码以这个角色的名义收发消息。人类与 Agent 的操作面完全分离。

### 两条独立的信任链

| 路径 | 验证方式 | 保护对象 | 状态保持 |
|------|----------|----------|----------|
| **Web 管理面**（/roles、/select 等） | TOTP 一次性码 | 管理操作：角色 CRUD、签发授权码 | HttpSession 30 分钟 |
| **Agent API 面**（/api/messages/** 等） | `X-Auth-Code` 请求头 | 每个授权码绑定的单个角色 | 无状态，每次请求校验 |

两条链互不混淆：TOTP 只回答"操作者是不是管理员"，授权码只回答"这次请求以哪个角色身份、用哪把钥匙"。**TOTP 密钥与角色无关**，这就是为什么单个管理员密钥足够。

---

## 2. 安全设计

### 2.1 零明文凭据

- 服务器保存的所有秘密：TOTP 密钥（Base32，仅管理员知道）与数据库口令。**没有任何账号密码被下发给 agent。**
- 授权码是 UUID，在服务器侧绑定 `role_id`，agent 侧只是一串随机字符——即使被截获，也只能操作那一个角色，且可被撤销。
- Web 页面的 TOTP 验证在服务端进行，验证码不进入 agent 的上下文。

### 2.2 TOTP 门禁（RFC 6238）

- 手写实现 HMAC-SHA1，30 秒步长，6 位数字，**±1 步窗口**容忍时钟漂移。
- 首次部署 `/setup` 生成 160-bit 随机密钥并渲染 ZXing 二维码；输入正确验证码后 `enabled=1`。
- **失败锁定**：连续 5 次错误锁定 1 分钟（阈值/时长可配置），防止离线暴力破解。
- TOTP 验证通过后，`HttpSession` 打上 `totp_verified` 标记，30 分钟无操作过期；`TotpVerifiedInterceptor` 对 `/roles/**`、`/select` 等统一做门禁，未验证一律重定向 `/verify`。

### 2.3 授权码作用域（最小权限）

- 一个授权码**只绑定一个角色**，只能以该角色身份：收发其参与的消息、把消息标记已读/完成。
- `GET /api/roles` 仅返回**其它**角色的名称，只读、不可操作——agent 可以"看到有哪些邻居"，但不能改任何角色。
- 消息详情接口校验：消息必须与绑定角色相关（发送方或接收方），否则 404，不泄漏他人消息。

---

## 3. 数据模型（5 张表）

```
app_role ─┬─< auth_code      （一个角色多条授权码，但同一时刻至多一条有效）
          └─< message.from_role_id
             < message.to_role_id

auth_code ──< auth_session.auth_code_id
          ──< message.auth_code_id   （★ 审计链）

totp_config：单行（id=1），管理员密钥
```

### 3.1 totp_config（管理员密钥，单行）

| 字段 | 说明 |
|------|------|
| secret_b32 | Base32 密钥（首次部署生成，仅此一次展示给管理员扫码） |
| enabled | 是否已启用（/setup 验证通过后置 1） |
| failed_attempts / locked_until | 失败锁定状态 |

### 3.2 app_role（角色）

| 字段 | 说明 |
|------|------|
| name | 角色名，**UNIQUE** |
| description | 描述 |
| enabled / deleted / deleted_at | 启用状态 + **软删除**（删除保留审计链，见 §4.4） |

### 3.3 auth_code（授权码，核心审计对象）

| 字段 | 说明 |
|------|------|
| code | UUID，**UNIQUE** |
| role_id | 绑定的角色（FK） |
| is_active | 当前是否有效 |
| active_role_key | **生成列**：`CASE WHEN is_active=1 THEN role_id ELSE NULL END`，其上是唯一索引（见 §4.3） |
| issued_at / expires_at | 签发/过期时间（默认 60 分钟） |
| revoked / revoked_at / revoked_reason | 撤销标记 + 原因：`REISSUED`（被新码顶替）/ `EXPIRED`（清扫）/ `MANUAL`（手动撤销） |

**有效性定义**：`revoked=0 AND is_active=1 AND expires_at > now()`。

### 3.4 auth_session（Agent 授权会话）

| 字段 | 说明 |
|------|------|
| session_id | UUID，**UNIQUE** |
| state | `waiting_totp → verified → completed` |
| role_id / auth_code_id | 绑定完成后回填 |
| expires_at | 15 分钟；**已完成的会话不过期**（Agent 晚些轮询仍能拿到授权码） |

### 3.5 message（角色间消息，审计主表）

| 字段 | 设计意图 |
|------|----------|
| message_id | 生成的线程 ID（`<uuid@gateway>`），供回复引用 |
| from_role_id / to_role_id | 发送方 / **单收件人** |
| **auth_code_id** | ★ **发送时使用的授权码**（FK→auth_code），审计追溯链核心 |
| subject / body_text / body_html | 正文（纯文本 + 可选 HTML） |
| is_read / read_at | 已读状态 + 时间（独立标记，见 §4.2） |
| is_completed / completed_at | **完成状态 + 时间**（接收方视角的"这件事办完了"） |
| in_reply_to / references_chain | 线程链（见 §4.5） |
| deleted / deleted_at | 软删除 |
| 索引 | `(to_role_id, deleted, is_read)` 覆盖收件箱按已读过滤；`(from_role_id, deleted)` 覆盖发件箱 |

---

## 4. 关键设计决策与理由

### 4.1 授权状态机与授权码生命周期

```
                    ┌─────────────────────────┐
POST /api/auth/start│ auth_session            │
───────────────────►│ state = waiting_totp     │
                    │ expires_at = now + 15min │
                    └───────────┬─────────────┘
                                │ 人类开 pageUrl，输 TOTP 通过
                                ▼
                    ┌─────────────────────────┐
                    │ state = verified        │
                    └───────────┬─────────────┘
                                │ 人类选角色 roleId
                                ▼
                    ┌─────────────────────────┐   Agent 轮询 GET /api/auth/{sid}
                    │ state = completed       │ ─► { authCode, expiresAt, role }
                    │ bindRole: 签发授权码     │     完成态会话永不过期
                    └─────────────────────────┘
```

授权码自身的生命周期：

```
签发 (issueCode)
  │
  ├─► 手动撤销 (revoke)          ──► revoked=MANUAL，立即失效，不可恢复
  │
  ├─► 被新码顶替 (REISSUED)      ──► 同角色再次授权时旧码作废，不可恢复（防旧码复活）
  │
  ├─► 自然过期                   ──► 请求时报 401 AUTH_CODE_EXPIRED
  │      └─ 60s 内清扫           ──► revoked=EXPIRED
  │              └─ 过期仍可 refresh ──► 签新码（恢复路径，不丢失角色身份）
  │
  └─► 过期后 60s 内刷新 (refresh) ──► 直接签新码
```

设计要点：

- **过期≠判死刑**。授权码过期是"自然现象"（60 分钟会话结束），不是安全事件，所以可刷新恢复；而被顶替、被手动撤销则是明确的操作，**拒绝刷新**，防止旧钥匙复活。这通过 `revoked_reason` 区分。
- **刷新是"换新钥匙"，不是"续期"**：总是签发新 UUID、作废旧码，而不是延长旧码——即使旧码被截获，刷新后也立即失效。

### 4.2 为什么"读详情不改已读状态"

真实邮箱的"标记已读"是副作用，这里拆成**独立的显式标记接口**。理由：

1. Agent 可能只是"扫一眼"消息内容（搜索、摘要），不该污染已读语义。
2. 已读是**接收方工作流**的状态：agent 处理完一条才能标记，未处理的一直是未读。
3. 同样的思路延伸到**完成状态**：`is_completed` + `completed_at` 是接收方对"这件事办完没有"的答复，与已读解耦——可以已读未完成，也可以未读但已完成。

### 4.3 一角色一有效码：为什么用生成列，而不是 `UNIQUE(role_id, is_active)`

需求是"**同一角色同一时刻最多一条有效码**"。最直觉的写法是 `UNIQUE(role_id, is_active)`，但这是错的：

- `is_active` 只有 0/1 两个值，该索引要求每个 `role_id` 在"非活跃"档位**至多一条**记录。
- 而历史上每签发一条码，旧码就会变成 `is_active=0`。多次签发/过期/撤销后，同一角色会积累多条非活跃记录，**必然撞唯一索引**——发布 1 分钟后就会炸（本项目在端到端测试中真实踩到这个坑）。

MySQL 不支持部分/过滤唯一索引（PG/SQLServer 才有），所以用**生成列 + 唯一索引**实现：

```sql
active_role_key BIGINT GENERATED ALWAYS AS
  (CASE WHEN is_active = 1 THEN role_id ELSE NULL END) STORED,
UNIQUE KEY uk_role_active (active_role_key)
```

- 只有 `is_active=1` 时键值非 `NULL`；MySQL 唯一索引允许多个 `NULL`，历史失效码彼此不冲突。
- 并发兜底：`issueCode` 在**同一事务**内先 `UPDATE ... SET is_active=0 WHERE role_id=? AND is_active=1`（作废旧码），再 INSERT 新码；两个并发签发时，后者会撞唯一索引 → 事务回滚 → 请求失败，绝不产生两条有效码。DB 层面硬保证，不依赖应用逻辑的时序。

### 4.4 为什么软删除

消息删除只做 `deleted=1` + `deleted_at`，不物理删除。理由：

1. **审计链必须完整**：`message → auth_code → role` 的追溯不允许出现断链。
2. Agent 拿到的是"某角色身份"，误删/批量删除的代价很高，软删除可恢复。
3. 索引 `(to_role_id, deleted, is_read)` 使软删数据对收件箱查询零成本。

角色的软删除同理：删角色不删它的消息，历史记录保留，只是不再出现在可交互列表中。

### 4.5 线程链：in_reply_to + references

回复消息时：

- `in_reply_to` = 被回复消息的 `message_id`（直接父消息）。
- `references_chain` = 整条线程的所有祖先 `message_id`，空格分隔（RFC 5322 References 头风格，用列名避开 MySQL 保留字 `references`）。
- 主题自动加 `Re: ` 前缀，并去除已有前缀避免 `Re: Re: Re:` 叠加。

这样 agent 可以从任意一条消息向上重建整条对话，而不需要递归查询。

### 4.6 TOTP 失败锁定

每次验证失败 `failed_attempts+1`，达到阈值（默认 5）置 `locked_until = now + 1min`，锁定期内**即使输入正确验证码也拒绝**并提示稍后再试。防止对 6 位码的离线枚举。

### 4.7 清扫定时器

`@Scheduled(fixedDelay=60s)` 每分钟执行两次清理：

- `auth_session`：把 `expires_at < now` 且未完成的会话置为 `expired`。
- `auth_code`：把已过期未撤销的码标为 `revoked=EXPIRED`。

注意清扫**不删除任何行**——它是状态维护，不是垃圾回收；历史记录永远保留作审计。

### 4.8 Skill 为什么是三件套

Agent 是"一次性"的，它每次冷启动都不知道你的网关协议，所以需要一套自举机制：

1. **教程接口** `GET /api/skill/tutorial`：一段直接给 agent 的提示词，告诉它"去下载、解压、读这个 skill"。这是**让 agent 自己学会安装**的入口。
2. **Skill 包**：`SKILL.md`（完整的 API 文档 + 授权流程 + 错误码表，agent 读的主文档）+ `README.md` + `scripts/examples.md`（可直接复制的 curl 示例）。
3. **下载接口** `GET /api/skill/download`：把包打成 zip 返回。

三者分工：教程是"怎么装"，包是"装什么"，下载是"从哪拿"。教程把安装指令文本化，agent 照着执行即可，不需要任何人工干预。

---

## 5. API 总表

### 授权（无需凭据，或需 X-Auth-Code）

| 端点 | 认证 | 说明 |
|------|------|------|
| `POST /api/auth/start` | 无 | 创建会话 → `{sessionId, pageUrl}` |
| `GET /api/auth/{sessionId}` | 无 | 轮询；`completed` 时返回 `{authCode, authCodeExpiresAt, role}` |
| `POST /api/auth/refresh` | X-Auth-Code | 过期可刷新的码换新码 |
| `POST /api/auth/revoke` | X-Auth-Code | 手动撤销当前码 |

### 消息（均需 `X-Auth-Code`，作用于绑定角色）

| 端点 | 说明 |
|------|------|
| `GET /api/messages/inbox?read=&completed=&limit=` | 收件列表（可按已读/完成过滤） |
| `GET /api/messages/sent?limit=` | 发出列表 |
| `GET /api/messages/{id}` | 详情（不改已读） |
| `POST /api/messages/{id}/read` | `{read:true/false}` 独立标记已读 |
| `POST /api/messages/{id}/complete` | `{completed:true/false}` 独立标记完成 |
| `POST /api/messages/send` | `{toRoleId, subject, body}` 发送，**记录 auth_code_id** |
| `POST /api/messages/{id}/reply` | `{body}` 回复，构建线程链 |
| `GET /api/roles` | 列其它角色（只读） |

### Skill 与健康

| 端点 | 认证 | 说明 |
|------|------|------|
| `GET /api/skill/tutorial` | 无 | 教 agent 安装 skill 的提示词 |
| `GET /api/skill/download` | 无 | 下载 skill 包（zip） |
| `GET /api/health` | 无 | 健康检查 |

### 错误码

| HTTP | code | 含义 |
|------|------|------|
| 400 | `VALIDATION_ERROR` | 参数校验失败 |
| 401 | `AUTH_CODE_INVALID` | 授权码无效或已撤销 |
| 401 | `AUTH_CODE_EXPIRED` | 授权码已过期（可 refresh 恢复） |
| 401 | `INVALID_TOTP` | TOTP 码错误 |
| 404 | `NOT_FOUND` | 消息/角色/授权会话不存在，或消息与本角色无关 |
| 409 | `TOTP_NOT_CONFIGURED` | TOTP 尚未完成首次配置 |
| 410 | `SESSION_EXPIRED` | 授权会话已过期 |
| 429 | `TOTP_LOCKED` | 连续失败被锁定，稍后再试 |

统一 JSON：`{timestamp, status, error, message, path}`。

---

## 6. 快速开始

```bash
# 1. 启动 MySQL 8（本机或 Docker；连接串带 createDatabaseIfNotExist，自动建库）
docker run --name aeg-mysql -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=agent_gateway -p 3306:3306 mysql:8

# 2. 构建并启动（Java 17）
JAVA_HOME=D:/java_home/jdk-17.0.18+8 mvn clean package -DskipTests
java -jar target/agent-gateway-0.1.0.jar

# 3. 首次部署：打开 http://localhost:8080/setup
#    Authenticator app 扫二维码 → 输入 6 位码 → TOTP 启用
# 4. http://localhost:8080/roles 添加角色（如「角色A」「角色B」）
```

启动时 `spring.sql.init` 自动执行幂等 `schema.sql` 建表（MyBatis-Plus 不自动建表）。数据库不存在会自动创建。

### 配置项（application.yml）

| 配置 | 默认 | 说明 |
|------|------|------|
| `app.auth-code-ttl-minutes` | 60 | 授权码有效期 |
| `app.session-ttl-minutes` | 15 | Agent 授权会话有效期 |
| `app.web-session-ttl-minutes` | 30 | Web 管理面 TOTP 后会话保持 |
| `app.totp-window` | 1 | TOTP ±窗口步数 |
| `app.totp-lock-threshold` | 5 | 失败锁定阈值 |
| `app.totp-lock-minutes` | 1 | 锁定时长 |
| `app.base-url` | "" | 对外地址（skill 下载链接；留空自动推导） |
| `app.issuer` | AgentGateway | TOTP otpauth URI issuer |

---

## 7. Skill 使用（Agent 侧）

```bash
# 网关先把教程交给 agent，或直接：
curl -L -o agent-gateway.zip http://localhost:8080/api/skill/download
mkdir -p ~/.claude/skills/agent-gateway && unzip -o agent-gateway.zip -d ~/.claude/skills/agent-gateway
```

安装后 agent 读 `SKILL.md` 即可了解全部协议。授权流程示例（agent 只负责发起与轮询）：

```bash
START=$(curl -s -X POST http://localhost:8080/api/auth/start)
SID=$(echo "$START" | sed -E 's/.*"sessionId":"([^"]+)".*/\1/')
# 请用户打开 $START 里的 pageUrl 完成验证并选角色
until curl -s "http://localhost:8080/api/auth/$SID" | grep -q '"completed"'; do sleep 3; done
AUTH=$(curl -s "http://localhost:8080/api/auth/$SID" | sed -E 's/.*"authCode":"([^"]+)".*/\1/')
curl -s "http://localhost:8080/api/messages/inbox" -H "X-Auth-Code: $AUTH"
```

---

## 8. 测试

```bash
JAVA_HOME=D:/java_home/jdk-17.0.18+8 mvn test
```

- **TotpUtilTest** — 对照 RFC 6238 官方测试向量（6 组 SHA1 向量），验证手写实现正确性。
- **AuthServiceTest** — 授权码全生命周期：一角色一有效码、签发/撤销/过期判定、过期可刷新、REISSUED/MANUAL 拒刷、`active_role_key` 部分唯一索引回归（多次签发不冲突）。
- **MessageServiceTest** — 发送记录授权码（审计链）、收件/过滤、独立已读/完成、回复线程链。

集成测试用 H2（`MODE=MySQL`）内存库，`@Transactional` 每用例回滚，互不污染。

---

## 9. 项目结构

```
src/main/java/com/agentgateway/
  controller/   API（auth/messages/roles/skill/health）+ Thymeleaf 页面路由
  service/      TotpService / AuthService / RoleService / MessageService / SkillService
  mapper/       MyBatis-Plus Mapper（selectByCode/revokeActiveCodes/expireAllPast 等 @Update 生命周期 SQL）
  entity/       TotpConfig / Role / AuthSession / AuthCode / Message
  interceptor/  AuthCodeInterceptor（X-Auth-Code → 绑定角色）
                TotpVerifiedInterceptor（Web 管理面 TOTP 门禁）
  totp/         TotpUtil（手写 RFC 6238）+ TotpService 的失败锁定
  util/         QrCodeUtil（data URI 二维码）/ BaseUrlResolver
  config/       WebConfig（拦截器注册）/ SchedulerConfig（60s 清扫）
  exception/    ApiException / ErrorCode / GlobalExceptionHandler（统一错误 JSON）
src/main/resources/
  schema.sql                   幂等建表 DDL（含 active_role_key 生成列）
  application.yml              配置
  templates/                   Thymeleaf 页面（fragments 布局复用）
  skill/agent-gateway/         Skill 包（SKILL.md / README.md / scripts/examples.md）
```
