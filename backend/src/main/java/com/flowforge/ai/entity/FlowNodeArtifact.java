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
        name = "flow_node_artifacts",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_flow_node_artifacts_task_key",
                columnNames = {"task_id", "artifact_key"}
        )
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowNodeArtifact {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Column(name = "flow_id", nullable = false, updatable = false)
    private UUID flowId;

    @Column(name = "node_id", nullable = false, updatable = false, length = 80)
    private String nodeId;

    @Column(name = "sequence_number", nullable = false, updatable = false)
    private Integer sequenceNumber;

    @Column(name = "artifact_key", nullable = false, updatable = false, length = 240)
    private String artifactKey;

    @Column(name = "artifact_type", nullable = false, updatable = false, length = 60)
    private String artifactType;

    @Column(nullable = false, updatable = false, length = 24)
    private String state;

    @Column(name = "media_type", updatable = false, length = 80)
    private String mediaType;

    @Column(updatable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "content_fingerprint", updatable = false, length = 64)
    private String contentFingerprint;

    @Column(name = "input_artifact_key", updatable = false, length = 240)
    private String inputArtifactKey;

    @Column(name = "input_artifact_type", updatable = false, length = 60)
    private String inputArtifactType;

    @Column(name = "input_artifact_storage", updatable = false, length = 40)
    private String inputArtifactStorage;

    @Column(name = "input_artifact_state", updatable = false, length = 24)
    private String inputArtifactState;

    @Column(name = "input_resolution", updatable = false, length = 40)
    private String inputResolution;

    @Column(name = "input_content_fingerprint", updatable = false, length = 64)
    private String inputContentFingerprint;

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
