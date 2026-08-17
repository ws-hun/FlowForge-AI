CREATE TABLE flow_node_artifacts (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL,
    flow_id UUID NOT NULL,
    node_id VARCHAR(80) NOT NULL,
    sequence_number INTEGER NOT NULL,
    artifact_key VARCHAR(240) NOT NULL,
    artifact_type VARCHAR(60) NOT NULL,
    state VARCHAR(24) NOT NULL,
    media_type VARCHAR(80),
    payload TEXT,
    content_fingerprint VARCHAR(64),
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_flow_node_artifacts_task
        FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT ck_flow_node_artifacts_sequence
        CHECK (sequence_number > 0),
    CONSTRAINT ck_flow_node_artifacts_payload
        CHECK (
            (state = 'materialized' AND payload IS NOT NULL AND content_fingerprint IS NOT NULL)
            OR (state IN ('failed', 'skipped') AND payload IS NULL AND content_fingerprint IS NULL)
        )
);

CREATE UNIQUE INDEX ux_flow_node_artifacts_task_key
    ON flow_node_artifacts (task_id, artifact_key);

CREATE INDEX ix_flow_node_artifacts_task_sequence
    ON flow_node_artifacts (task_id, sequence_number);

CREATE INDEX ix_flow_node_artifacts_flow_created_at
    ON flow_node_artifacts (flow_id, created_at DESC);
