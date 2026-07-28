package com.flowforge.ai.controller;

import com.flowforge.ai.dto.HealthResponse;
import com.flowforge.ai.service.HealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final HealthService healthService;

    @GetMapping
    public HealthResponse check() {
        return healthService.check();
    }
}
