package com.flowforge.ai.repository;

import com.flowforge.ai.entity.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    Optional<AuthSession> findByTokenHashAndExpiresAtAfter(String tokenHash, LocalDateTime now);

    void deleteByTokenHash(String tokenHash);

    void deleteByExpiresAtBefore(LocalDateTime now);

    @Modifying
    @Query("delete from AuthSession session where session.user.id = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
