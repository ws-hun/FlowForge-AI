package com.flowforge.ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppConfigTest {

    private final AppConfig appConfig = new AppConfig();

    @Test
    void buildsAProviderClientWithExplicitTimeouts() {
        assertThat(appConfig.restClient(
                RestClient.builder(),
                Duration.ofSeconds(10),
                Duration.ofSeconds(120)
        )).isNotNull();
    }

    @Test
    void rejectsNonPositiveProviderTimeouts() {
        assertThatThrownBy(() -> appConfig.restClient(
                RestClient.builder(),
                Duration.ZERO,
                Duration.ofSeconds(120)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("AI Provider connect timeout must be greater than zero");
    }
}
