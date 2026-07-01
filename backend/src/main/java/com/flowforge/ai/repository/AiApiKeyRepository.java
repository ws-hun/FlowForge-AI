package com.flowforge.ai.repository;

import com.flowforge.ai.entity.AiApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AiApiKeyRepository extends JpaRepository<AiApiKey, UUID> {

    Optional<AiApiKey> findByProviderIgnoreCase(String provider);

    Optional<AiApiKey> findFirstByActiveTrue();
}
