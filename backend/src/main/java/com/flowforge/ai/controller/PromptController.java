package com.flowforge.ai.controller;

import com.flowforge.ai.dto.PromptRequest;
import com.flowforge.ai.dto.PromptResponse;
import com.flowforge.ai.service.PromptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/prompts")
@RequiredArgsConstructor
public class PromptController {

    private final PromptService promptService;

    @GetMapping
    public List<PromptResponse> listPrompts(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean favorite
    ) {
        return promptService.listPrompts(query, category, favorite);
    }

    @PostMapping
    public PromptResponse createPrompt(@Valid @RequestBody PromptRequest request) {
        return promptService.createPrompt(request);
    }

    @PutMapping("/{id}")
    public PromptResponse updatePrompt(@PathVariable UUID id, @Valid @RequestBody PromptRequest request) {
        return promptService.updatePrompt(id, request);
    }

    @PatchMapping("/{id}/favorite")
    public PromptResponse toggleFavorite(@PathVariable UUID id) {
        return promptService.toggleFavorite(id);
    }

    @DeleteMapping("/{id}")
    public void deletePrompt(@PathVariable UUID id) {
        promptService.deletePrompt(id);
    }
}
