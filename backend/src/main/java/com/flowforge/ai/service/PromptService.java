package com.flowforge.ai.service;

import com.flowforge.ai.dto.PromptRequest;
import com.flowforge.ai.dto.PromptResponse;
import com.flowforge.ai.entity.Prompt;
import com.flowforge.ai.repository.PromptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PromptService {

    private static final String TAG_SEPARATOR = ",";

    private final PromptRepository promptRepository;

    @Transactional(readOnly = true)
    public List<PromptResponse> listPrompts(String query, String category, Boolean favorite) {
        String normalizedQuery = normalizeNullable(query);
        String normalizedCategory = normalizeNullable(category);

        return promptRepository.findAll(Sort.by(Sort.Direction.DESC, "updatedAt"))
                .stream()
                .filter(prompt -> matchesQuery(prompt, normalizedQuery))
                .filter(prompt -> matchesCategory(prompt, normalizedCategory))
                .filter(prompt -> favorite == null || prompt.isFavorite() == favorite)
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PromptResponse createPrompt(PromptRequest request) {
        Prompt prompt = Prompt.builder().build();
        applyRequest(prompt, request);
        return toResponse(promptRepository.save(prompt));
    }

    @Transactional
    public PromptResponse updatePrompt(UUID id, PromptRequest request) {
        Prompt prompt = promptRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Prompt not found"));
        applyRequest(prompt, request);
        return toResponse(prompt);
    }

    @Transactional
    public PromptResponse toggleFavorite(UUID id) {
        Prompt prompt = promptRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Prompt not found"));
        prompt.setFavorite(!prompt.isFavorite());
        return toResponse(prompt);
    }

    @Transactional
    public void deletePrompt(UUID id) {
        promptRepository.deleteById(id);
    }

    private void applyRequest(Prompt prompt, PromptRequest request) {
        prompt.setTitle(cleanRequired(request.title(), "title"));
        prompt.setCategory(cleanRequired(request.category(), "category"));
        prompt.setDescription(cleanRequired(request.description(), "description"));
        prompt.setContent(cleanRequired(request.content(), "content"));
        prompt.setTags(joinTags(request.tags()));
        prompt.setFavorite(Boolean.TRUE.equals(request.favorite()));
    }

    private boolean matchesQuery(Prompt prompt, String query) {
        if (!StringUtils.hasText(query)) {
            return true;
        }
        String haystack = String.join(" ",
                prompt.getTitle(),
                prompt.getCategory(),
                prompt.getDescription(),
                prompt.getContent(),
                prompt.getTags()
        ).toLowerCase(Locale.ROOT);
        return haystack.contains(query);
    }

    private boolean matchesCategory(Prompt prompt, String category) {
        return !StringUtils.hasText(category)
                || "all".equals(category)
                || prompt.getCategory().toLowerCase(Locale.ROOT).equals(category);
    }

    private String cleanRequired(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(field + " is required");
        }
        return value.trim();
    }

    private String normalizeNullable(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String joinTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "";
        }
        Set<String> cleaned = new LinkedHashSet<>();
        for (String tag : tags) {
            if (StringUtils.hasText(tag)) {
                cleaned.add(tag.trim());
            }
        }
        return String.join(TAG_SEPARATOR, cleaned);
    }

    private List<String> splitTags(String tags) {
        if (!StringUtils.hasText(tags)) {
            return List.of();
        }
        return Arrays.stream(tags.split(TAG_SEPARATOR))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private PromptResponse toResponse(Prompt prompt) {
        return new PromptResponse(
                prompt.getId(),
                prompt.getTitle(),
                prompt.getCategory(),
                prompt.getDescription(),
                prompt.getContent(),
                splitTags(prompt.getTags()),
                prompt.isFavorite(),
                prompt.getCreatedAt(),
                prompt.getUpdatedAt()
        );
    }
}
