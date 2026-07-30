package com.flowforge.ai.repository;

import com.flowforge.ai.entity.Prompt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PromptRepository extends JpaRepository<Prompt, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select prompt from Prompt prompt where prompt.id = :id")
    Optional<Prompt> findByIdForUpdate(@Param("id") UUID id);
}
