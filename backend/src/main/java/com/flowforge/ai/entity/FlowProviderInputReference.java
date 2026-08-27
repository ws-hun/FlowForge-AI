package com.flowforge.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "flow_provider_input_references",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "ux_flow_provider_inputs_artifact_order",
                        columnNames = {"provider_artifact_id", "input_order"}
                ),
                @UniqueConstraint(
                        name = "ux_flow_provider_inputs_artifact_key",
                        columnNames = {"provider_artifact_id", "artifact_key"}
                )
        }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowProviderInputReference {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "provider_artifact_id", nullable = false, updatable = false)
    private UUID providerArtifactId;

    @Column(name = "input_order", nullable = false, updatable = false)
    private Integer inputOrder;

    @Column(name = "artifact_key", nullable = false, updatable = false, length = 240)
    private String artifactKey;

    @Column(name = "artifact_type", nullable = false, updatable = false, length = 60)
    private String artifactType;

    @Column(name = "artifact_storage", nullable = false, updatable = false, length = 40)
    private String artifactStorage;

    @Column(name = "artifact_state", nullable = false, updatable = false, length = 24)
    private String artifactState;

    @Column(name = "input_resolution", nullable = false, updatable = false, length = 40)
    private String inputResolution;

    @Column(name = "content_fingerprint", updatable = false, length = 64)
    private String contentFingerprint;

    @Column(name = "source_artifact_id", updatable = false)
    private UUID sourceArtifactId;

    @Column(name = "source_node_id", updatable = false, length = 80)
    private String sourceNodeId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
