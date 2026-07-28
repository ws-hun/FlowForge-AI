package com.flowforge.ai.service;

import com.flowforge.ai.dto.AiApiKeyRequest;
import com.flowforge.ai.dto.AiApiKeyResponse;
import com.flowforge.ai.entity.AiApiKey;
import com.flowforge.ai.exception.ResourceNotFoundException;
import com.flowforge.ai.repository.AiApiKeyRepository;
import com.flowforge.ai.security.ApiKeyCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiApiKeyServiceTest {

    @Mock
    private AiApiKeyRepository repository;

    private AiApiKeyService service;
    private ApiKeyCipher apiKeyCipher;

    @TempDir
    private Path tempDir;

    @BeforeEach
    void setUp() {
        apiKeyCipher = new ApiKeyCipher("", tempDir.resolve("master.key").toString());
        service = new AiApiKeyService(repository, apiKeyCipher);
    }

    @Test
    void savesAndActivatesANormalizedProviderWhileMaskingTheKey() {
        AiApiKey previousActiveKey = AiApiKey.builder()
                .id(UUID.randomUUID())
                .provider("openai")
                .apiKey("sk-existing-openai-key")
                .baseUrl("https://api.openai.com/v1")
                .model("gpt-4o-mini")
                .active(true)
                .build();
        when(repository.findAll()).thenReturn(List.of(previousActiveKey));
        when(repository.findByProviderIgnoreCase("deepseek")).thenReturn(Optional.empty());
        when(repository.save(org.mockito.ArgumentMatchers.any(AiApiKey.class))).thenAnswer(invocation -> {
            AiApiKey key = invocation.getArgument(0);
            key.setId(UUID.randomUUID());
            key.setUpdatedAt(LocalDateTime.of(2026, 7, 28, 16, 0));
            return key;
        });

        AiApiKeyResponse response = service.saveKey(new AiApiKeyRequest(
                " DeepSeek ",
                " sk-1234567890abcdef ",
                " https://api.deepseek.com ",
                " deepseek-chat ",
                true
        ));

        ArgumentCaptor<AiApiKey> keyCaptor = ArgumentCaptor.forClass(AiApiKey.class);
        verify(repository).save(keyCaptor.capture());
        assertThat(previousActiveKey.isActive()).isFalse();
        assertThat(keyCaptor.getValue().getProvider()).isEqualTo("deepseek");
        assertThat(keyCaptor.getValue().getApiKey()).startsWith("enc:v1:");
        assertThat(apiKeyCipher.decrypt(keyCaptor.getValue().getApiKey())).isEqualTo("sk-1234567890abcdef");
        assertThat(keyCaptor.getValue().getBaseUrl()).isEqualTo("https://api.deepseek.com");
        assertThat(keyCaptor.getValue().getModel()).isEqualTo("deepseek-chat");
        assertThat(keyCaptor.getValue().isActive()).isTrue();
        assertThat(response.maskedKey()).isEqualTo("sk-12...cdef");
    }

    @Test
    void normalizesAValidCustomCompatibleBaseUrl() {
        when(repository.findAll()).thenReturn(List.of());
        when(repository.findByProviderIgnoreCase("openai")).thenReturn(Optional.empty());
        when(repository.save(org.mockito.ArgumentMatchers.any(AiApiKey.class))).thenAnswer(invocation -> {
            AiApiKey key = invocation.getArgument(0);
            key.setId(UUID.randomUUID());
            key.setUpdatedAt(LocalDateTime.now());
            return key;
        });

        service.saveKey(new AiApiKeyRequest(
                "openai",
                "sk-custom-compatible-key",
                "https://models.example.com/v1/",
                "custom-model",
                true
        ));

        ArgumentCaptor<AiApiKey> keyCaptor = ArgumentCaptor.forClass(AiApiKey.class);
        verify(repository).save(keyCaptor.capture());
        assertThat(keyCaptor.getValue().getBaseUrl()).isEqualTo("https://models.example.com/v1");
    }

    @Test
    void savingAnInactiveProviderDoesNotDeactivateTheCurrentProvider() {
        AiApiKey currentProvider = AiApiKey.builder()
                .id(UUID.randomUUID())
                .provider("openai")
                .apiKey("sk-existing-openai-key")
                .baseUrl("https://api.openai.com/v1")
                .model("gpt-4o-mini")
                .active(true)
                .updatedAt(LocalDateTime.now())
                .build();
        AiApiKey inactiveProvider = AiApiKey.builder()
                .id(UUID.randomUUID())
                .provider("deepseek")
                .apiKey("sk-existing-deepseek-key")
                .baseUrl("https://api.deepseek.com")
                .model("deepseek-chat")
                .active(false)
                .updatedAt(LocalDateTime.now())
                .build();
        when(repository.findByProviderIgnoreCase("deepseek")).thenReturn(Optional.of(inactiveProvider));
        when(repository.save(inactiveProvider)).thenReturn(inactiveProvider);

        service.saveKey(new AiApiKeyRequest(
                "deepseek",
                "sk-replacement-deepseek-key",
                "https://api.deepseek.com",
                "deepseek-reasoner",
                false
        ));

        verify(repository, never()).findAll();
        assertThat(currentProvider.isActive()).isTrue();
        assertThat(inactiveProvider.isActive()).isFalse();
        assertThat(inactiveProvider.getModel()).isEqualTo("deepseek-reasoner");
    }

    @Test
    void rejectsUnsupportedProvidersAsClientInput() {
        assertThatThrownBy(() -> service.saveKey(new AiApiKeyRequest(
                "claude",
                "secret-key",
                "https://api.anthropic.com",
                "claude-sonnet",
                true
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported AI provider: claude");
    }

    @Test
    void rejectsUnsafeOrMalformedBaseUrlsAsClientInput() {
        assertThatThrownBy(() -> service.saveKey(new AiApiKeyRequest(
                "openai",
                "secret-key",
                "file:///tmp/provider",
                "custom-model",
                true
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("baseUrl must be an absolute HTTP or HTTPS URL");

        assertThatThrownBy(() -> service.saveKey(new AiApiKeyRequest(
                "openai",
                "secret-key",
                "https://user:password@models.example.com/v1?debug=true",
                "custom-model",
                true
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("baseUrl cannot contain credentials, query parameters, or fragments");
    }

    @Test
    void reportsMissingProviderConfigWhenActivatingOrDeleting() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        when(repository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> service.activate(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("API key config not found");
        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("API key config not found");
    }

    @Test
    void keepsMissingActiveProviderAsAnExecutionFailure() {
        when(repository.findFirstByActiveTrue()).thenReturn(Optional.empty());

        assertThatThrownBy(service::getActiveKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No active AI API key configured");
    }

    @Test
    void decryptsTheActiveProviderWithoutMutatingTheStoredEntity() {
        String encryptedKey = apiKeyCipher.encrypt("sk-active-provider-key");
        AiApiKey storedKey = AiApiKey.builder()
                .id(UUID.randomUUID())
                .provider("openai")
                .apiKey(encryptedKey)
                .baseUrl("https://api.openai.com/v1")
                .model("gpt-4.1-mini")
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        when(repository.findFirstByActiveTrue()).thenReturn(Optional.of(storedKey));

        AiApiKey activeKey = service.getActiveKey();

        assertThat(activeKey).isNotSameAs(storedKey);
        assertThat(activeKey.getApiKey()).isEqualTo("sk-active-provider-key");
        assertThat(storedKey.getApiKey()).isEqualTo(encryptedKey);
        verify(repository, never()).save(storedKey);
    }

    @Test
    void migratesLegacyPlaintextKeysWhenTheVaultIsListed() {
        AiApiKey legacyKey = AiApiKey.builder()
                .id(UUID.randomUUID())
                .provider("deepseek")
                .apiKey("sk-legacy-plaintext-key")
                .baseUrl("https://api.deepseek.com")
                .model("deepseek-chat")
                .active(true)
                .updatedAt(LocalDateTime.now())
                .build();
        when(repository.findAll(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.ASC,
                "provider"
        ))).thenReturn(List.of(legacyKey));
        when(repository.saveAll(List.of(legacyKey))).thenReturn(List.of(legacyKey));

        List<AiApiKeyResponse> response = service.listKeys();

        assertThat(response).singleElement().extracting(AiApiKeyResponse::maskedKey)
                .isEqualTo("sk-le...-key");
        assertThat(legacyKey.getApiKey()).startsWith("enc:v1:");
        verify(repository).saveAll(List.of(legacyKey));
    }
}
