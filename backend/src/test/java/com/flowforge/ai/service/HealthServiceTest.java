package com.flowforge.ai.service;

import com.flowforge.ai.dto.HealthResponse;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthServiceTest {

    @Test
    void reportsUpOnlyWhenTheDatabaseResponds() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

        HealthResponse response = new HealthService(jdbcTemplate).check();

        assertThat(response.status()).isEqualTo("up");
        assertThat(response.database()).isEqualTo("reachable");
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    void failsReadinessWhenTheDatabaseResponseIsInvalid() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(0);

        assertThatThrownBy(() -> new HealthService(jdbcTemplate).check())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Database readiness check failed");
    }
}
