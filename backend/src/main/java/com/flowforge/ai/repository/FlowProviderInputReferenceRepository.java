package com.flowforge.ai.repository;

import com.flowforge.ai.entity.FlowProviderInputReference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FlowProviderInputReferenceRepository
        extends JpaRepository<FlowProviderInputReference, UUID> {

    List<FlowProviderInputReference> findByProviderArtifactIdOrderByInputOrderAsc(UUID providerArtifactId);
}
