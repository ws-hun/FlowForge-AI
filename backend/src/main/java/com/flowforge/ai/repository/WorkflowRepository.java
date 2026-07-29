package com.flowforge.ai.repository;

import com.flowforge.ai.entity.Workflow;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WorkflowRepository extends JpaRepository<Workflow, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select workflow from Workflow workflow where workflow.id = :id")
    Optional<Workflow> findByIdForUpdate(@Param("id") UUID id);
}
