package com.flowforge.ai.service;

import com.flowforge.ai.dto.HealthResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class HealthService {

    private final JdbcTemplate jdbcTemplate;

    public HealthResponse check() {
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        if (result == null || result != 1) {
            throw new IllegalStateException("Database readiness check failed");
        }
        return new HealthResponse("up", "reachable", LocalDateTime.now());
    }
}
