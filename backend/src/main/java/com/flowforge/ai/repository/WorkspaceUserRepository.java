package com.flowforge.ai.repository;

import com.flowforge.ai.entity.WorkspaceUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WorkspaceUserRepository extends JpaRepository<WorkspaceUser, UUID> {

    Optional<WorkspaceUser> findByEmailIgnoreCase(String email);
}
