package com.flowforge.ai.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class ApiKeyCipher {

    private static final String ENCRYPTED_PREFIX = "enc:v1:";
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec secretKey;

    public ApiKeyCipher(
            @Value("${flowforge.security.encryption-key:}") String configuredKey,
            @Value("${flowforge.security.key-file:.flowforge/master.key}") String keyFile
    ) {
        Path resolvedKeyPath = Path.of(StringUtils.hasText(keyFile) ? keyFile.trim() : ".flowforge/master.key");
        this.secretKey = new SecretKeySpec(resolveKey(configuredKey, resolvedKeyPath), "AES");
    }

    public String encrypt(String plaintext) {
        if (!StringUtils.hasText(plaintext)) {
            throw new IllegalArgumentException("API key cannot be empty");
        }
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(nonce.length + encrypted.length)
                    .put(nonce)
                    .put(encrypted)
                    .array();
            return ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to encrypt API key", ex);
        }
    }

    public boolean isEncrypted(String storedValue) {
        return StringUtils.hasText(storedValue) && storedValue.startsWith(ENCRYPTED_PREFIX);
    }

    public String decrypt(String storedValue) {
        if (!isEncrypted(storedValue)) {
            return storedValue;
        }
        try {
            byte[] payload = Base64.getDecoder().decode(storedValue.substring(ENCRYPTED_PREFIX.length()));
            if (payload.length <= NONCE_BYTES) {
                throw new IllegalStateException("Encrypted API key payload is invalid");
            }
            byte[] nonce = new byte[NONCE_BYTES];
            byte[] encrypted = new byte[payload.length - NONCE_BYTES];
            System.arraycopy(payload, 0, nonce, 0, nonce.length);
            System.arraycopy(payload, nonce.length, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to decrypt API key", ex);
        }
    }

    private byte[] resolveKey(String configuredKey, Path keyPath) {
        if (StringUtils.hasText(configuredKey)) {
            return decodeKey(configuredKey.trim(), "FLOWFORGE_ENCRYPTION_KEY");
        }
        try {
            if (Files.exists(keyPath)) {
                return readKeyFile(keyPath);
            }
            Path parent = keyPath.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            byte[] generatedKey = new byte[KEY_BYTES];
            secureRandom.nextBytes(generatedKey);
            String encodedKey = Base64.getEncoder().encodeToString(generatedKey);
            try {
                Files.writeString(keyPath, encodedKey, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                restrictFilePermissions(keyPath);
                return generatedKey;
            } catch (FileAlreadyExistsException ignored) {
                return readKeyFile(keyPath);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load or create API key encryption key", ex);
        }
    }

    private byte[] readKeyFile(Path keyPath) throws IOException {
        return decodeKey(Files.readString(keyPath).trim(), keyPath.toString());
    }

    private byte[] decodeKey(String encodedKey, String source) {
        try {
            byte[] key = Base64.getDecoder().decode(encodedKey);
            if (key.length != KEY_BYTES) {
                throw new IllegalStateException(source + " must decode to exactly 32 bytes");
            }
            return key;
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(source + " must be a valid Base64 value", ex);
        }
    }

    private void restrictFilePermissions(Path keyPath) {
        try {
            Files.setPosixFilePermissions(keyPath, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Non-POSIX filesystems still keep the generated key outside source control.
        }
    }
}
