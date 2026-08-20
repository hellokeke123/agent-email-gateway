-- Agent Gateway 建表 DDL（MySQL 8，幂等）
-- 启动时由 spring.sql.init 自动执行。请先手动创建数据库：
--   CREATE DATABASE IF NOT EXISTS agent_gateway DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS totp_config (
  id              TINYINT       NOT NULL DEFAULT 1,
  secret_b32      VARCHAR(255)  NOT NULL,
  enabled         TINYINT(1)    NOT NULL DEFAULT 0,
  failed_attempts INT           NOT NULL DEFAULT 0,
  locked_until    DATETIME      NULL,
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS app_config (
  id              TINYINT       NOT NULL DEFAULT 1,
  base_url        VARCHAR(2048) NULL,
  allow_private_webhook_urls TINYINT(1) NOT NULL DEFAULT 0,
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB;

ALTER TABLE app_config ADD COLUMN IF NOT EXISTS allow_private_webhook_urls TINYINT(1) NOT NULL DEFAULT 0 AFTER base_url;

CREATE TABLE IF NOT EXISTS app_role (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  name        VARCHAR(100) NOT NULL,
  description TEXT         NULL,
  enabled     TINYINT(1)   NOT NULL DEFAULT 1,
  deleted     TINYINT(1)   NOT NULL DEFAULT 0,
  deleted_at  DATETIME     NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_name (name)
) ENGINE=InnoDB;

ALTER TABLE app_role MODIFY COLUMN description TEXT NULL;

CREATE TABLE IF NOT EXISTS auth_code (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  code           VARCHAR(36)  NOT NULL,
  role_id        BIGINT       NOT NULL,
  is_active      TINYINT(1)   NOT NULL DEFAULT 1,
  -- 生成列实现「部分唯一」：仅 is_active=1 时非 NULL（=role_id），非活跃行为 NULL（可多条）
  active_role_key BIGINT     GENERATED ALWAYS AS (CASE WHEN is_active = 1 THEN role_id ELSE NULL END) STORED,
  issued_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at     DATETIME     NOT NULL,
  revoked        TINYINT(1)   NOT NULL DEFAULT 0,
  revoked_at     DATETIME     NULL,
  revoked_reason VARCHAR(50)  NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_code (code),
  -- 一角色一有效码：active_role_key 唯一，NULL 不冲突，允许多条历史失效码共存
  UNIQUE KEY uk_role_active (active_role_key),
  KEY idx_code_expires (expires_at),
  CONSTRAINT fk_code_role FOREIGN KEY (role_id) REFERENCES app_role (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS auth_session (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  session_id    VARCHAR(36)  NOT NULL,
  state         VARCHAR(20)  NOT NULL DEFAULT 'waiting_totp',
  role_id       BIGINT       NULL,
  auth_code_id  BIGINT       NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  expires_at    DATETIME     NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_session_id (session_id),
  KEY idx_session_state_expires (state, expires_at),
  CONSTRAINT fk_session_role FOREIGN KEY (role_id) REFERENCES app_role (id),
  CONSTRAINT fk_session_code FOREIGN KEY (auth_code_id) REFERENCES auth_code (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS message (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  message_id     VARCHAR(100) NOT NULL,
  from_role_id   BIGINT       NOT NULL,
  to_role_id     BIGINT       NOT NULL,
  auth_code_id   BIGINT       NULL,
  subject        VARCHAR(500) NULL,
  body_text      TEXT         NULL,
  body_html      TEXT         NULL,
  is_read        TINYINT(1)   NOT NULL DEFAULT 0,
  read_at        DATETIME     NULL,
  is_completed   TINYINT(1)   NOT NULL DEFAULT 0,
  completed_at   DATETIME     NULL,
  in_reply_to    VARCHAR(100) NULL,
  references_chain TEXT       NULL,
  deleted        TINYINT(1)   NOT NULL DEFAULT 0,
  deleted_at     DATETIME     NULL,
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_message_to (to_role_id, deleted, is_read),
  KEY idx_message_from (from_role_id, deleted),
  CONSTRAINT fk_message_from_role FOREIGN KEY (from_role_id) REFERENCES app_role (id),
  CONSTRAINT fk_message_to_role FOREIGN KEY (to_role_id) REFERENCES app_role (id),
  CONSTRAINT fk_message_code FOREIGN KEY (auth_code_id) REFERENCES auth_code (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS worker (
  id BIGINT NOT NULL AUTO_INCREMENT, role_id BIGINT NOT NULL, name VARCHAR(100) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', webhook_url VARCHAR(2048) NULL, disabled_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_worker_role (role_id), CONSTRAINT fk_worker_role FOREIGN KEY (role_id) REFERENCES app_role(id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS worker_credential (
  id BIGINT NOT NULL AUTO_INCREMENT, worker_id BIGINT NOT NULL, token_hash VARCHAR(64) NOT NULL, webhook_signing_secret_hash VARCHAR(64) NOT NULL, token_prefix VARCHAR(32) NOT NULL,
  active TINYINT(1) NOT NULL DEFAULT 1, expires_at DATETIME NULL, revoked_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_worker_token_hash (token_hash), KEY idx_worker_credential_active (worker_id, active), CONSTRAINT fk_credential_worker FOREIGN KEY (worker_id) REFERENCES worker(id)
) ENGINE=InnoDB;
ALTER TABLE worker_credential ADD COLUMN IF NOT EXISTS webhook_signing_secret VARCHAR(255) NULL AFTER webhook_signing_secret_hash;
CREATE TABLE IF NOT EXISTS worker_task (
  id BIGINT NOT NULL AUTO_INCREMENT, public_id VARCHAR(36) NOT NULL, source_message_id BIGINT NOT NULL, initiator_role_id BIGINT NOT NULL, target_role_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL, title VARCHAR(500) NOT NULL, payload TEXT NULL, claimed_by_worker_id BIGINT NULL, lease_token VARCHAR(64) NULL, lease_expires_at DATETIME NULL,
  version INT NOT NULL, progress INT NOT NULL DEFAULT 0, block_reason TEXT NULL, result TEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_task_public_id (public_id), KEY idx_task_target_status (target_role_id, status), KEY idx_task_claimed_lease (claimed_by_worker_id, lease_expires_at),
  CONSTRAINT fk_task_source_message FOREIGN KEY (source_message_id) REFERENCES message(id), CONSTRAINT fk_task_initiator_role FOREIGN KEY (initiator_role_id) REFERENCES app_role(id), CONSTRAINT fk_task_target_role FOREIGN KEY (target_role_id) REFERENCES app_role(id), CONSTRAINT fk_task_claimed_worker FOREIGN KEY (claimed_by_worker_id) REFERENCES worker(id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS task_event (
  id BIGINT NOT NULL AUTO_INCREMENT, task_id BIGINT NOT NULL, worker_id BIGINT NULL, event_type VARCHAR(50) NOT NULL, payload TEXT NOT NULL,
  delivery_status VARCHAR(20) NOT NULL DEFAULT 'PENDING', dispatch_lease_token VARCHAR(64) NULL, dispatch_lease_expires_at DATETIME NULL, attempts INT NOT NULL DEFAULT 0, next_attempt_at DATETIME NOT NULL, delivered_at DATETIME NULL, last_error VARCHAR(1000) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id), KEY idx_event_delivery (delivery_status, next_attempt_at), KEY idx_event_dispatch_lease (delivery_status, dispatch_lease_expires_at), KEY idx_event_task (task_id), KEY idx_event_worker (worker_id), CONSTRAINT fk_event_task FOREIGN KEY (task_id) REFERENCES worker_task(id), CONSTRAINT fk_event_worker FOREIGN KEY (worker_id) REFERENCES worker(id)
) ENGINE=InnoDB;
