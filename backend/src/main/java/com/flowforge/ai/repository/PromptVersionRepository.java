package com.flowforge.ai.repository;

import com.flowforge.ai.entity.PromptVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PromptVersionRepository extends JpaRepository<PromptVersion, UUID> {

    List<PromptVersion> findTop8ByPromptIdOrderByVersionNumberDesc(UUID promptId);

    Optional<PromptVersion> findTopByPromptIdOrderByVersionNumberDesc(UUID promptId);

    void deleteByPromptId(UUID promptId);
}
