package com.flowforge.ai.service;

import com.flowforge.ai.dto.PromptRequest;
import com.flowforge.ai.dto.PromptResponse;
import com.flowforge.ai.dto.PromptVersionResponse;
import com.flowforge.ai.entity.Prompt;
import com.flowforge.ai.entity.PromptVersion;
import com.flowforge.ai.repository.PromptRepository;
import com.flowforge.ai.repository.PromptVersionRepository;
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
    private final PromptVersionRepository promptVersionRepository;

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
        saveVersionSnapshot(prompt);
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
        promptVersionRepository.deleteByPromptId(id);
        promptRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<PromptVersionResponse> listVersions(UUID promptId) {
        return promptVersionRepository.findTop8ByPromptIdOrderByVersionNumberDesc(promptId)
                .stream()
                .map(this::toVersionResponse)
                .toList();
    }

    @Transactional
    public PromptResponse restoreVersion(UUID promptId, UUID versionId) {
        Prompt prompt = promptRepository.findById(promptId)
                .orElseThrow(() -> new IllegalStateException("Prompt not found"));
        PromptVersion version = promptVersionRepository.findById(versionId)
                .filter(item -> item.getPromptId().equals(promptId))
                .orElseThrow(() -> new IllegalStateException("Prompt version not found"));

        saveVersionSnapshot(prompt);
        prompt.setTitle(version.getTitle());
        prompt.setCategory(version.getCategory());
        prompt.setDescription(version.getDescription());
        prompt.setContent(version.getContent());
        prompt.setTags(version.getTags());
        prompt.setFavorite(version.isFavorite());

        return toResponse(prompt);
    }

    private void saveVersionSnapshot(Prompt prompt) {
        int versionNumber = promptVersionRepository.findTopByPromptIdOrderByVersionNumberDesc(prompt.getId())
                .map(PromptVersion::getVersionNumber)
                .orElse(0) + 1;

        PromptVersion version = PromptVersion.builder()
                .promptId(prompt.getId())
                .versionNumber(versionNumber)
                .title(prompt.getTitle())
                .category(prompt.getCategory())
                .description(prompt.getDescription())
                .content(prompt.getContent())
                .tags(prompt.getTags())
                .favorite(prompt.isFavorite())
                .build();

        promptVersionRepository.save(version);
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

    private PromptVersionResponse toVersionResponse(PromptVersion version) {
        return new PromptVersionResponse(
                version.getId(),
                version.getPromptId(),
                version.getVersionNumber(),
                version.getTitle(),
                version.getCategory(),
                version.getDescription(),
                version.getContent(),
                splitTags(version.getTags()),
                version.isFavorite(),
                version.getCreatedAt()
        );
    }
}
