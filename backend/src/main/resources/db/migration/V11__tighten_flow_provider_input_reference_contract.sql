ALTER TABLE flow_provider_input_references
    ADD CONSTRAINT ck_flow_provider_inputs_identity
        CHECK (
            artifact_key <> ''
            AND artifact_type <> ''
            AND artifact_storage <> ''
            AND artifact_state <> ''
            AND input_resolution <> ''
            AND (
                content_fingerprint IS NULL
                OR content_fingerprint ~ '^[0-9a-f]{64}$'
            )
            AND (
                artifact_storage <> 'flow-snapshot'
                OR (
                    artifact_key = 'flow:objective'
                    AND artifact_type = 'flow-objective'
                )
            )
            AND (
                artifact_storage <> 'node-artifact'
                OR (
                    source_artifact_id IS NOT NULL
                    AND source_node_id <> ''
                )
            )
        );
