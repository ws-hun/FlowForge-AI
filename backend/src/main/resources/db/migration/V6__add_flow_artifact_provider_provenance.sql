ALTER TABLE flow_node_artifacts
    ADD COLUMN provider_call_status VARCHAR(20),
    ADD COLUMN provider_name VARCHAR(40),
    ADD COLUMN provider_model VARCHAR(120),
    ADD COLUMN provider_input_tokens INTEGER,
    ADD COLUMN provider_output_tokens INTEGER,
    ADD COLUMN provider_total_tokens INTEGER,
    ADD COLUMN provider_duration_ms BIGINT,
    ADD COLUMN provider_error_message TEXT;

ALTER TABLE flow_node_artifacts
    ADD CONSTRAINT ck_flow_node_artifacts_provider_provenance
        CHECK (
            (
                provider_call_status IS NULL
                AND provider_name IS NULL
                AND provider_model IS NULL
                AND provider_input_tokens IS NULL
                AND provider_output_tokens IS NULL
                AND provider_total_tokens IS NULL
                AND provider_duration_ms IS NULL
                AND provider_error_message IS NULL
            )
            OR (
                artifact_type = 'provider-result'
                AND provider_call_status IN ('completed', 'failed')
                AND provider_duration_ms IS NOT NULL
                AND provider_duration_ms >= 0
                AND (provider_input_tokens IS NULL OR provider_input_tokens >= 0)
                AND (provider_output_tokens IS NULL OR provider_output_tokens >= 0)
                AND (provider_total_tokens IS NULL OR provider_total_tokens >= 0)
                AND (
                    (
                        provider_call_status = 'completed'
                        AND state = 'materialized'
                        AND provider_error_message IS NULL
                    )
                    OR (
                        provider_call_status = 'failed'
                        AND state = 'failed'
                        AND provider_error_message IS NOT NULL
                    )
                )
            )
        );
