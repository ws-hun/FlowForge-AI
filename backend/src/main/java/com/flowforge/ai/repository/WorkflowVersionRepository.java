package com.flowforge.ai.repository;

import com.flowforge.ai.entity.WorkflowVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowVersionRepository extends JpaRepository<WorkflowVersion, UUID> {

    List<WorkflowVersion> findTop8ByFlowIdOrderByVersionNumberDesc(UUID flowId);

    List<WorkflowVersion> findByFlowIdOrderByVersionNumberDesc(UUID flowId);

    Optional<WorkflowVersion> findTopByFlowIdOrderByVersionNumberDesc(UUID flowId);

    void deleteByFlowId(UUID flowId);
}
