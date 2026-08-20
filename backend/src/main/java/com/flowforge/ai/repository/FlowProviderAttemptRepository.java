package com.flowforge.ai.repository;

import com.flowforge.ai.entity.FlowProviderAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FlowProviderAttemptRepository extends JpaRepository<FlowProviderAttempt, UUID> {

    List<FlowProviderAttempt> findByArtifactIdOrderByAttemptNumberAsc(UUID artifactId);

    List<FlowProviderAttempt> findByArtifactIdInOrderByArtifactIdAscAttemptNumberAsc(
            Collection<UUID> artifactIds
    );
}
