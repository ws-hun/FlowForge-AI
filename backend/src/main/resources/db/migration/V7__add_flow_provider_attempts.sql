CREATE TABLE flow_provider_attempts (
    id UUID PRIMARY KEY,
    artifact_id UUID NOT NULL,
    attempt_number INTEGER NOT NULL,
    trigger_type VARCHAR(24) NOT NULL,
    previous_attempt_id UUID,
    status VARCHAR(20) NOT NULL,
    provider VARCHAR(40),
    model VARCHAR(120),
    input_tokens INTEGER,
    output_tokens INTEGER,
    total_tokens INTEGER,
    duration_ms BIGINT NOT NULL,
    error_message TEXT,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_flow_provider_attempts_artifact
        FOREIGN KEY (artifact_id) REFERENCES flow_node_artifacts (id) ON DELETE CASCADE,
    CONSTRAINT fk_flow_provider_attempts_previous
        FOREIGN KEY (previous_attempt_id) REFERENCES flow_provider_attempts (id),
    CONSTRAINT ck_flow_provider_attempts_number
        CHECK (attempt_number > 0),
    CONSTRAINT ck_flow_provider_attempts_trigger
        CHECK (
            (attempt_number = 1 AND trigger_type = 'initial' AND previous_attempt_id IS NULL)
            OR (
                attempt_number > 1
                AND trigger_type IN ('automatic-retry', 'manual-recovery')
                AND previous_attempt_id IS NOT NULL
            )
        ),
    CONSTRAINT ck_flow_provider_attempts_metrics
        CHECK (
            duration_ms >= 0
            AND (input_tokens IS NULL OR input_tokens >= 0)
            AND (output_tokens IS NULL OR output_tokens >= 0)
            AND (total_tokens IS NULL OR total_tokens >= 0)
        ),
    CONSTRAINT ck_flow_provider_attempts_terminal_state
        CHECK (
            (status = 'completed' AND error_message IS NULL)
            OR (status = 'failed' AND error_message IS NOT NULL)
        )
);

CREATE UNIQUE INDEX ux_flow_provider_attempts_artifact_number
    ON flow_provider_attempts (artifact_id, attempt_number);

CREATE INDEX ix_flow_provider_attempts_artifact_created_at
    ON flow_provider_attempts (artifact_id, created_at);

INSERT INTO flow_provider_attempts (
    id,
    artifact_id,
    attempt_number,
    trigger_type,
    previous_attempt_id,
    status,
    provider,
    model,
    input_tokens,
    output_tokens,
    total_tokens,
    duration_ms,
    error_message,
    created_at
)
SELECT
    md5(random()::TEXT || clock_timestamp()::TEXT || id::TEXT)::UUID,
    id,
    1,
    'initial',
    NULL,
    provider_call_status,
    provider_name,
    provider_model,
    provider_input_tokens,
    provider_output_tokens,
    provider_total_tokens,
    provider_duration_ms,
    provider_error_message,
    created_at
FROM flow_node_artifacts
WHERE provider_call_status IS NOT NULL;
