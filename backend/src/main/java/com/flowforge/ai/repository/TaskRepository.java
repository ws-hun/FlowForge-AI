package com.flowforge.ai.repository;

import com.flowforge.ai.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    List<Task> findTop6BySourcePromptIdOrderByCreatedAtDesc(UUID sourcePromptId);

    List<Task> findTop6BySourceFlowIdOrderByCreatedAtDesc(UUID sourceFlowId);
}
