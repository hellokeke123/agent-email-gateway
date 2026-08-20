-- H2 测试库建表（MODE=MySQL）
DROP TABLE IF EXISTS message;
DROP TABLE IF EXISTS auth_session;
DROP TABLE IF EXISTS auth_code;
DROP TABLE IF EXISTS app_role;
DROP TABLE IF EXISTS app_config;
DROP TABLE IF EXISTS totp_config;

CREATE TABLE totp_config (
  id              TINYINT       NOT NULL DEFAULT 1,
  secret_b32      VARCHAR(255)  NOT NULL,
  enabled         BOOLEAN       NOT NULL DEFAULT FALSE,
  failed_attempts INT           NOT NULL DEFAULT 0,
  locked_until    TIMESTAMP     NULL,
  created_at      TIMESTAMP     NULL,
  updated_at      TIMESTAMP     NULL,
  PRIMARY KEY (id)
);

CREATE TABLE app_config (
  id              TINYINT       NOT NULL DEFAULT 1,
  base_url        VARCHAR(2048) NULL,
  allow_private_webhook_urls BOOLEAN NOT NULL DEFAULT FALSE,
  created_at      TIMESTAMP     NULL,
  updated_at      TIMESTAMP     NULL,
  PRIMARY KEY (id)
);

CREATE TABLE app_role (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  name        VARCHAR(100) NOT NULL,
  description CLOB         NULL,
  enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
  deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
  deleted_at  TIMESTAMP    NULL,
  created_at  TIMESTAMP    NULL,
  updated_at  TIMESTAMP    NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_role_name UNIQUE (name)
);

CREATE TABLE auth_code (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  code           VARCHAR(36)  NOT NULL,
  role_id        BIGINT       NOT NULL,
  is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
  active_role_key BIGINT      AS (CASE WHEN is_active THEN role_id ELSE NULL END),
  issued_at      TIMESTAMP    NULL,
  expires_at     TIMESTAMP    NOT NULL,
  revoked        BOOLEAN      NOT NULL DEFAULT FALSE,
  revoked_at     TIMESTAMP    NULL,
  revoked_reason VARCHAR(50)  NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_code UNIQUE (code),
  CONSTRAINT uk_role_active UNIQUE (active_role_key),
  CONSTRAINT fk_code_role FOREIGN KEY (role_id) REFERENCES app_role (id)
);

CREATE TABLE auth_session (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  session_id    VARCHAR(36)  NOT NULL,
  state         VARCHAR(20)  NOT NULL DEFAULT 'waiting_totp',
  role_id       BIGINT       NULL,
  auth_code_id  BIGINT       NULL,
  created_at    TIMESTAMP    NULL,
  updated_at    TIMESTAMP    NULL,
  expires_at    TIMESTAMP    NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_session_id UNIQUE (session_id),
  CONSTRAINT fk_session_role FOREIGN KEY (role_id) REFERENCES app_role (id),
  CONSTRAINT fk_session_code FOREIGN KEY (auth_code_id) REFERENCES auth_code (id)
);

CREATE TABLE message (
  id             BIGINT        NOT NULL AUTO_INCREMENT,
  message_id     VARCHAR(100)  NOT NULL,
  from_role_id   BIGINT        NOT NULL,
  to_role_id     BIGINT        NOT NULL,
  auth_code_id   BIGINT        NULL,
  subject        VARCHAR(500)  NULL,
  body_text      CLOB          NULL,
  body_html      CLOB          NULL,
  is_read        BOOLEAN       NOT NULL DEFAULT FALSE,
  read_at        TIMESTAMP     NULL,
  is_completed   BOOLEAN       NOT NULL DEFAULT FALSE,
  completed_at   TIMESTAMP     NULL,
  in_reply_to    VARCHAR(100)  NULL,
  references_chain CLOB        NULL,
  deleted        BOOLEAN       NOT NULL DEFAULT FALSE,
  deleted_at     TIMESTAMP     NULL,
  created_at     TIMESTAMP     NULL,
  updated_at     TIMESTAMP     NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_message_from_role FOREIGN KEY (from_role_id) REFERENCES app_role (id),
  CONSTRAINT fk_message_to_role FOREIGN KEY (to_role_id) REFERENCES app_role (id),
  CONSTRAINT fk_message_code FOREIGN KEY (auth_code_id) REFERENCES auth_code (id)
);

CREATE TABLE worker (
  id BIGINT NOT NULL AUTO_INCREMENT, role_id BIGINT NOT NULL, name VARCHAR(100) NOT NULL, status VARCHAR(20) NOT NULL,
  webhook_url VARCHAR(2048), disabled_at TIMESTAMP, created_at TIMESTAMP, updated_at TIMESTAMP,
  PRIMARY KEY (id), CONSTRAINT uk_worker_role UNIQUE (role_id), CONSTRAINT fk_worker_role FOREIGN KEY (role_id) REFERENCES app_role(id)
);
CREATE TABLE worker_credential (
  id BIGINT NOT NULL AUTO_INCREMENT, worker_id BIGINT NOT NULL, token_hash VARCHAR(64) NOT NULL, webhook_signing_secret_hash VARCHAR(64) NOT NULL, token_prefix VARCHAR(32) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE, expires_at TIMESTAMP, revoked_at TIMESTAMP, created_at TIMESTAMP, updated_at TIMESTAMP,
  PRIMARY KEY (id), CONSTRAINT uk_worker_token_hash UNIQUE (token_hash), CONSTRAINT fk_credential_worker FOREIGN KEY (worker_id) REFERENCES worker(id)
);
CREATE TABLE worker_task (
  id BIGINT NOT NULL AUTO_INCREMENT, public_id VARCHAR(36) NOT NULL, source_message_id BIGINT NOT NULL, initiator_role_id BIGINT NOT NULL, target_role_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL, title VARCHAR(500) NOT NULL, payload CLOB, claimed_by_worker_id BIGINT, lease_token VARCHAR(64), lease_expires_at TIMESTAMP,
  version INT NOT NULL, progress INT NOT NULL DEFAULT 0, block_reason CLOB, result CLOB, created_at TIMESTAMP, updated_at TIMESTAMP,
  PRIMARY KEY (id), CONSTRAINT uk_task_public_id UNIQUE (public_id), INDEX idx_task_target_status (target_role_id, status), INDEX idx_task_claimed_lease (claimed_by_worker_id, lease_expires_at), CONSTRAINT fk_task_source_message FOREIGN KEY (source_message_id) REFERENCES message(id),
  CONSTRAINT fk_task_initiator_role FOREIGN KEY (initiator_role_id) REFERENCES app_role(id), CONSTRAINT fk_task_target_role FOREIGN KEY (target_role_id) REFERENCES app_role(id), CONSTRAINT fk_task_claimed_worker FOREIGN KEY (claimed_by_worker_id) REFERENCES worker(id)
);
CREATE TABLE task_event (
  id BIGINT NOT NULL AUTO_INCREMENT, task_id BIGINT NOT NULL, worker_id BIGINT, event_type VARCHAR(50) NOT NULL, payload CLOB NOT NULL,
  delivery_status VARCHAR(20) NOT NULL, dispatch_lease_token VARCHAR(64), dispatch_lease_expires_at TIMESTAMP, attempts INT NOT NULL, next_attempt_at TIMESTAMP NOT NULL, delivered_at TIMESTAMP, last_error VARCHAR(1000), created_at TIMESTAMP, updated_at TIMESTAMP,
  PRIMARY KEY (id), INDEX idx_event_delivery (delivery_status, next_attempt_at), INDEX idx_event_dispatch_lease (delivery_status, dispatch_lease_expires_at), INDEX idx_event_task (task_id), INDEX idx_event_worker (worker_id), CONSTRAINT fk_event_task FOREIGN KEY (task_id) REFERENCES worker_task(id), CONSTRAINT fk_event_worker FOREIGN KEY (worker_id) REFERENCES worker(id)
);
