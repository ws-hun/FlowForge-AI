package com.flowforge.ai.controller;

import com.flowforge.ai.dto.AiApiKeyRequest;
import com.flowforge.ai.dto.AiApiKeyResponse;
import com.flowforge.ai.service.AiApiKeyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/settings/api-keys")
@RequiredArgsConstructor
public class AiApiKeyController {

    private final AiApiKeyService aiApiKeyService;

    @GetMapping
    public List<AiApiKeyResponse> listKeys() {
        return aiApiKeyService.listKeys();
    }

    @PostMapping
    public AiApiKeyResponse saveKey(@Valid @RequestBody AiApiKeyRequest request) {
        return aiApiKeyService.saveKey(request);
    }

    @PatchMapping("/{id}/activate")
    public AiApiKeyResponse activate(@PathVariable UUID id) {
        return aiApiKeyService.activate(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        aiApiKeyService.delete(id);
    }
}
