-- H2 测试库建表（MODE=MySQL）
DROP TABLE IF EXISTS message;
DROP TABLE IF EXISTS auth_session;
DROP TABLE IF EXISTS auth_code;
DROP TABLE IF EXISTS app_role;
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

CREATE TABLE app_role (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  name        VARCHAR(100) NOT NULL,
  description VARCHAR(500) NULL,
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
