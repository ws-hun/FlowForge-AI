package com.flowforge.ai.repository;

import com.flowforge.ai.entity.Task;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from Task task where task.id = :id")
    Optional<Task> findByIdForAssetPromotion(@Param("id") UUID id);

    List<Task> findTop6BySourcePromptIdOrderByCreatedAtDesc(UUID sourcePromptId);

    List<Task> findTop6BySourceFlowIdOrderByCreatedAtDesc(UUID sourceFlowId);
}
