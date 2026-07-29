package com.flowforge.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class AppConfig {

    @Bean
    public RestClient restClient(
            RestClient.Builder builder,
            @Value("${flowforge.ai.http.connect-timeout:10s}") Duration connectTimeout,
            @Value("${flowforge.ai.http.read-timeout:120s}") Duration readTimeout
    ) {
        requirePositive(connectTimeout, "AI Provider connect timeout");
        requirePositive(readTimeout, "AI Provider read timeout");

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return builder.requestFactory(requestFactory).build();
    }

    private void requirePositive(Duration timeout, String name) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
    }
}
