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

CREATE TABLE IF NOT EXISTS app_role (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  name        VARCHAR(100) NOT NULL,
  description VARCHAR(500) NULL,
  enabled     TINYINT(1)   NOT NULL DEFAULT 1,
  deleted     TINYINT(1)   NOT NULL DEFAULT 0,
  deleted_at  DATETIME     NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_name (name)
) ENGINE=InnoDB;

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
