ALTER TABLE flow_node_artifacts
    ADD COLUMN input_artifact_key VARCHAR(240),
    ADD COLUMN input_artifact_type VARCHAR(60),
    ADD COLUMN input_artifact_storage VARCHAR(40),
    ADD COLUMN input_artifact_state VARCHAR(24),
    ADD COLUMN input_resolution VARCHAR(40),
    ADD COLUMN input_content_fingerprint VARCHAR(64);

ALTER TABLE flow_node_artifacts
    ADD CONSTRAINT ck_flow_node_artifacts_input_lineage
        CHECK (
            (
                input_artifact_key IS NULL
                AND input_artifact_type IS NULL
                AND input_artifact_storage IS NULL
                AND input_artifact_state IS NULL
                AND input_resolution IS NULL
                AND input_content_fingerprint IS NULL
            )
            OR (
                input_artifact_key IS NOT NULL
                AND input_artifact_type IS NOT NULL
                AND input_artifact_storage IS NOT NULL
                AND input_artifact_state IN ('materialized', 'failed', 'skipped')
                AND input_resolution IN ('compiled-reference', 'persisted-artifact')
                AND (
                    (
                        input_artifact_state = 'materialized'
                        AND input_content_fingerprint IS NOT NULL
                    )
                    OR (
                        input_artifact_state IN ('failed', 'skipped')
                        AND input_content_fingerprint IS NULL
                    )
                )
            )
        );

CREATE INDEX ix_flow_node_artifacts_task_input_key
    ON flow_node_artifacts (task_id, input_artifact_key)
    WHERE input_artifact_key IS NOT NULL;
