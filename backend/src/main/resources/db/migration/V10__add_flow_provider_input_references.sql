CREATE TABLE flow_provider_input_references (
    id UUID PRIMARY KEY,
    provider_artifact_id UUID NOT NULL,
    input_order INTEGER NOT NULL,
    artifact_key VARCHAR(240) NOT NULL,
    artifact_type VARCHAR(60) NOT NULL,
    artifact_storage VARCHAR(40) NOT NULL,
    artifact_state VARCHAR(24) NOT NULL,
    input_resolution VARCHAR(40) NOT NULL,
    content_fingerprint VARCHAR(64),
    source_artifact_id UUID,
    source_node_id VARCHAR(80),
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_flow_provider_inputs_provider_artifact
        FOREIGN KEY (provider_artifact_id) REFERENCES flow_node_artifacts (id) ON DELETE CASCADE,
    CONSTRAINT fk_flow_provider_inputs_source_artifact
        FOREIGN KEY (source_artifact_id) REFERENCES flow_node_artifacts (id),
    CONSTRAINT ck_flow_provider_inputs_order
        CHECK (input_order > 0),
    CONSTRAINT ck_flow_provider_inputs_contract
        CHECK (
            artifact_storage IN ('flow-snapshot', 'node-artifact')
            AND artifact_state IN ('materialized', 'failed', 'skipped')
            AND input_resolution IN ('compiled-reference', 'persisted-artifact')
            AND (
                (
                    artifact_state = 'materialized'
                    AND content_fingerprint IS NOT NULL
                )
                OR (
                    artifact_state IN ('failed', 'skipped')
                    AND content_fingerprint IS NULL
                )
            )
            AND (
                (
                    artifact_storage = 'flow-snapshot'
                    AND source_artifact_id IS NULL
                    AND source_node_id IS NULL
                )
                OR (
                    artifact_storage = 'node-artifact'
                    AND source_artifact_id IS NOT NULL
                    AND source_node_id IS NOT NULL
                )
            )
        )
);

CREATE UNIQUE INDEX ux_flow_provider_inputs_artifact_order
    ON flow_provider_input_references (provider_artifact_id, input_order);

CREATE UNIQUE INDEX ux_flow_provider_inputs_artifact_key
    ON flow_provider_input_references (provider_artifact_id, artifact_key);

CREATE INDEX ix_flow_provider_inputs_source_artifact
    ON flow_provider_input_references (source_artifact_id)
    WHERE source_artifact_id IS NOT NULL;
