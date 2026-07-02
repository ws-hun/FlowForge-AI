package com.flowforge.ai.repository;

import com.flowforge.ai.entity.Prompt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PromptRepository extends JpaRepository<Prompt, UUID> {
}
