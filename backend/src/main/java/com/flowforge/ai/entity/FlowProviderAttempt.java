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
        name = "flow_provider_attempts",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_flow_provider_attempts_artifact_number",
                columnNames = {"artifact_id", "attempt_number"}
        )
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowProviderAttempt {

    public static final String TRIGGER_INITIAL = "initial";

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "artifact_id", nullable = false, updatable = false)
    private UUID artifactId;

    @Column(name = "attempt_number", nullable = false, updatable = false)
    private Integer attemptNumber;

    @Column(name = "trigger_type", nullable = false, updatable = false, length = 24)
    private String triggerType;

    @Column(name = "previous_attempt_id", updatable = false)
    private UUID previousAttemptId;

    @Column(nullable = false, updatable = false, length = 20)
    private String status;

    @Column(updatable = false, length = 40)
    private String provider;

    @Column(updatable = false, length = 120)
    private String model;

    @Column(name = "input_tokens", updatable = false)
    private Integer inputTokens;

    @Column(name = "output_tokens", updatable = false)
    private Integer outputTokens;

    @Column(name = "total_tokens", updatable = false)
    private Integer totalTokens;

    @Column(name = "duration_ms", nullable = false, updatable = false)
    private Long durationMs;

    @Column(name = "error_message", updatable = false, columnDefinition = "TEXT")
    private String errorMessage;

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
