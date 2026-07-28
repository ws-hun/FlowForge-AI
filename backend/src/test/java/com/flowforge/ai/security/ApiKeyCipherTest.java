package com.flowforge.ai.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiKeyCipherTest {

    @TempDir
    private Path tempDir;

    @Test
    void encryptsWithRandomNoncesAndReusesTheGeneratedKeyAcrossRestarts() {
        Path keyFile = tempDir.resolve("secrets/master.key");
        ApiKeyCipher firstCipher = new ApiKeyCipher("", keyFile.toString());

        String firstEncrypted = firstCipher.encrypt("sk-production-secret");
        String secondEncrypted = firstCipher.encrypt("sk-production-secret");

        assertThat(firstEncrypted).startsWith("enc:v1:");
        assertThat(firstCipher.isEncrypted(firstEncrypted)).isTrue();
        assertThat(firstCipher.isEncrypted("legacy-plaintext-key")).isFalse();
        assertThat(secondEncrypted).startsWith("enc:v1:");
        assertThat(firstEncrypted).isNotEqualTo(secondEncrypted);
        assertThat(firstCipher.decrypt(firstEncrypted)).isEqualTo("sk-production-secret");
        assertThat(Files.exists(keyFile)).isTrue();

        ApiKeyCipher restartedCipher = new ApiKeyCipher("", keyFile.toString());
        assertThat(restartedCipher.decrypt(firstEncrypted)).isEqualTo("sk-production-secret");
    }

    @Test
    void supportsAnInjectedBase64KeyAndLegacyPlaintextValues() {
        String configuredKey = Base64.getEncoder().encodeToString(new byte[32]);
        ApiKeyCipher cipher = new ApiKeyCipher(configuredKey, tempDir.resolve("unused.key").toString());

        assertThat(cipher.decrypt("legacy-plaintext-key")).isEqualTo("legacy-plaintext-key");
        assertThat(cipher.decrypt(cipher.encrypt("sk-injected-key"))).isEqualTo("sk-injected-key");
        assertThat(Files.exists(tempDir.resolve("unused.key"))).isFalse();
    }

    @Test
    void rejectsInvalidConfiguredKeysAndTamperedCiphertext() {
        assertThatThrownBy(() -> new ApiKeyCipher("not-base64", tempDir.resolve("unused.key").toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("FLOWFORGE_ENCRYPTION_KEY must be a valid Base64 value");

        String configuredKey = Base64.getEncoder().encodeToString(new byte[32]);
        ApiKeyCipher cipher = new ApiKeyCipher(configuredKey, tempDir.resolve("unused.key").toString());
        String encrypted = cipher.encrypt("sk-tamper-test");
        String tampered = encrypted.substring(0, encrypted.length() - 2) + "AA";

        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to decrypt API key");
    }
}
