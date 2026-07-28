package com.flowforge.ai.service;

import com.flowforge.ai.dto.AiApiKeyRequest;
import com.flowforge.ai.dto.AiApiKeyResponse;
import com.flowforge.ai.entity.AiApiKey;
import com.flowforge.ai.exception.ResourceNotFoundException;
import com.flowforge.ai.repository.AiApiKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiApiKeyService {

    private final AiApiKeyRepository aiApiKeyRepository;

    @Transactional(readOnly = true)
    public List<AiApiKeyResponse> listKeys() {
        return aiApiKeyRepository.findAll(Sort.by(Sort.Direction.ASC, "provider"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AiApiKeyResponse saveKey(AiApiKeyRequest request) {
        String provider = normalizeProvider(request.provider());
        boolean active = request.active() == null || request.active();

        if (active) {
            aiApiKeyRepository.findAll().forEach(key -> key.setActive(false));
        }

        AiApiKey key = aiApiKeyRepository.findByProviderIgnoreCase(provider)
                .orElseGet(() -> AiApiKey.builder().provider(provider).build());

        key.setApiKey(request.apiKey().trim());
        key.setBaseUrl(normalizeBaseUrl(request.baseUrl()));
        key.setModel(request.model().trim());
        key.setActive(active);

        return toResponse(aiApiKeyRepository.save(key));
    }

    @Transactional
    public AiApiKeyResponse activate(UUID id) {
        AiApiKey selected = aiApiKeyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("API key config not found"));

        aiApiKeyRepository.findAll().forEach(key -> key.setActive(false));
        selected.setActive(true);

        return toResponse(selected);
    }

    @Transactional
    public void delete(UUID id) {
        if (!aiApiKeyRepository.existsById(id)) {
            throw new ResourceNotFoundException("API key config not found");
        }
        aiApiKeyRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public AiApiKey getActiveKey() {
        return aiApiKeyRepository.findFirstByActiveTrue()
                .orElseThrow(() -> new IllegalStateException("No active AI API key configured"));
    }

    private String normalizeProvider(String provider) {
        if (!StringUtils.hasText(provider)) {
            throw new IllegalArgumentException("provider is required");
        }
        String normalized = provider.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals("openai") && !normalized.equals("deepseek")) {
            throw new IllegalArgumentException("Unsupported AI provider: " + provider);
        }
        return normalized;
    }

    private AiApiKeyResponse toResponse(AiApiKey key) {
        return new AiApiKeyResponse(
                key.getId(),
                key.getProvider(),
                maskKey(key.getApiKey()),
                key.getBaseUrl(),
                key.getModel(),
                key.isActive(),
                key.getUpdatedAt()
        );
    }

    private String normalizeBaseUrl(String baseUrl) {
        String value = baseUrl == null ? "" : baseUrl.trim();
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if ((!"http".equals(scheme) && !"https".equals(scheme)) || !StringUtils.hasText(uri.getHost())) {
                throw new IllegalArgumentException("baseUrl must be an absolute HTTP or HTTPS URL");
            }
            if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("baseUrl cannot contain credentials, query parameters, or fragments");
            }
            URI normalized = uri.normalize();
            if (!normalized.equals(uri)) {
                throw new IllegalArgumentException("baseUrl cannot contain relative path segments");
            }
            String result = normalized.toString();
            return result.endsWith("/") ? result.substring(0, result.length() - 1) : result;
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("baseUrl is not a valid URL", ex);
        }
    }

    private String maskKey(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return "";
        }
        String trimmed = apiKey.trim();
        if (trimmed.length() <= 10) {
            return "********";
        }
        return trimmed.substring(0, 5) + "..." + trimmed.substring(trimmed.length() - 4);
    }
}
