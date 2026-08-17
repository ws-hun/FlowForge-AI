package com.flowforge.ai.repository;

import com.flowforge.ai.entity.FlowNodeArtifact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FlowNodeArtifactRepository extends JpaRepository<FlowNodeArtifact, UUID> {

    List<FlowNodeArtifact> findByTaskIdOrderBySequenceNumberAsc(UUID taskId);

    Optional<FlowNodeArtifact> findByTaskIdAndArtifactKey(UUID taskId, String artifactKey);
}
