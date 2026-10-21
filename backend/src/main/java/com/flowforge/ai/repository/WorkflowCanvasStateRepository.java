package com.flowforge.ai.repository;

import com.flowforge.ai.entity.WorkflowCanvasState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WorkflowCanvasStateRepository extends JpaRepository<WorkflowCanvasState, UUID> {
}
