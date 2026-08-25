CREATE TABLE workspace_users (
    id UUID PRIMARY KEY,
    workspace_slot SMALLINT NOT NULL DEFAULT 1,
    email VARCHAR(254) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT ux_workspace_users_slot UNIQUE (workspace_slot),
    CONSTRAINT ck_workspace_users_single_owner CHECK (workspace_slot = 1)
);

CREATE UNIQUE INDEX ux_workspace_users_email_lower
    ON workspace_users (LOWER(email));

CREATE TABLE auth_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_auth_sessions_user
        FOREIGN KEY (user_id) REFERENCES workspace_users (id) ON DELETE CASCADE,
    CONSTRAINT ux_auth_sessions_token_hash UNIQUE (token_hash)
);

CREATE INDEX ix_auth_sessions_user_expires_at
    ON auth_sessions (user_id, expires_at DESC);

CREATE INDEX ix_auth_sessions_expires_at
    ON auth_sessions (expires_at);
