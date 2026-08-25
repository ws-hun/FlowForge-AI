package com.flowforge.ai.service;

import com.flowforge.ai.dto.AuthCredentialsRequest;
import com.flowforge.ai.dto.AuthPasswordChangeRequest;
import com.flowforge.ai.dto.AuthProfileUpdateRequest;
import com.flowforge.ai.dto.AuthSessionResult;
import com.flowforge.ai.dto.AuthSetupRequest;
import com.flowforge.ai.dto.AuthStatusResponse;
import com.flowforge.ai.dto.AuthUserResponse;
import com.flowforge.ai.entity.AuthSession;
import com.flowforge.ai.entity.WorkspaceUser;
import com.flowforge.ai.exception.AuthenticationRequiredException;
import com.flowforge.ai.exception.ResourceConflictException;
import com.flowforge.ai.repository.AuthSessionRepository;
import com.flowforge.ai.repository.WorkspaceUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {

    public static final String SESSION_COOKIE_NAME = "flowforge_session";
    public static final String AUTHENTICATED_USER_ATTRIBUTE = "flowforge.authenticatedUser";

    private static final int TOKEN_BYTES = 32;

    private final WorkspaceUserRepository workspaceUserRepository;
    private final AuthSessionRepository authSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final Duration sessionDuration;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String dummyPasswordHash;

    public AuthService(
            WorkspaceUserRepository workspaceUserRepository,
            AuthSessionRepository authSessionRepository,
            PasswordEncoder passwordEncoder,
            @Value("${flowforge.auth.session-duration:30d}") Duration sessionDuration
    ) {
        if (sessionDuration == null || sessionDuration.isZero() || sessionDuration.isNegative()) {
            throw new IllegalArgumentException("Authentication session duration must be greater than zero");
        }
        this.workspaceUserRepository = workspaceUserRepository;
        this.authSessionRepository = authSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionDuration = sessionDuration;
        this.dummyPasswordHash = passwordEncoder.encode(randomToken());
    }

    @Transactional(readOnly = true)
    public AuthStatusResponse getStatus(String rawToken) {
        boolean setupRequired = workspaceUserRepository.count() == 0;
        Optional<AuthUserResponse> user = findAuthenticatedUser(rawToken);
        return new AuthStatusResponse(setupRequired, user.isPresent(), user.orElse(null));
    }

    @Transactional
    public synchronized AuthSessionResult setup(AuthSetupRequest request) {
        if (workspaceUserRepository.count() > 0) {
            throw new ResourceConflictException("工作区所有者已创建，请直接登录");
        }

        WorkspaceUser user = workspaceUserRepository.saveAndFlush(WorkspaceUser.builder()
                .email(normalizeEmail(request.email()))
                .displayName(request.displayName().trim())
                .passwordHash(passwordEncoder.encode(request.password()))
                .build());
        return issueSession(user);
    }

    @Transactional
    public AuthSessionResult login(AuthCredentialsRequest request) {
        String email = normalizeEmail(request.email());
        Optional<WorkspaceUser> candidate = workspaceUserRepository.findByEmailIgnoreCase(email);
        String passwordHash = candidate.map(WorkspaceUser::getPasswordHash).orElse(dummyPasswordHash);

        if (!passwordEncoder.matches(request.password(), passwordHash) || candidate.isEmpty()) {
            throw new AuthenticationRequiredException("邮箱或密码不正确");
        }
        return issueSession(candidate.orElseThrow());
    }

    @Transactional
    public AuthStatusResponse updateProfile(String rawToken, AuthProfileUpdateRequest request) {
        WorkspaceUser user = requireAuthenticatedSession(rawToken).getUser();
        user.setDisplayName(request.displayName().trim());
        workspaceUserRepository.saveAndFlush(user);
        return new AuthStatusResponse(false, true, toResponse(user));
    }

    @Transactional
    public AuthSessionResult changePassword(String rawToken, AuthPasswordChangeRequest request) {
        WorkspaceUser user = requireAuthenticatedSession(rawToken).getUser();
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new AuthenticationRequiredException("当前密码不正确");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("新密码必须与当前密码不同");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        workspaceUserRepository.saveAndFlush(user);
        authSessionRepository.deleteByUserId(user.getId());
        return issueSession(user);
    }

    @Transactional(readOnly = true)
    public Optional<AuthUserResponse> findAuthenticatedUser(String rawToken) {
        return findAuthenticatedSession(rawToken)
                .map(AuthSession::getUser)
                .map(this::toResponse);
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        authSessionRepository.deleteByTokenHash(hashToken(rawToken));
    }

    public Duration getSessionDuration() {
        return sessionDuration;
    }

    private AuthSessionResult issueSession(WorkspaceUser user) {
        LocalDateTime now = LocalDateTime.now();
        authSessionRepository.deleteByExpiresAtBefore(now);

        String rawToken = randomToken();
        authSessionRepository.save(AuthSession.builder()
                .user(user)
                .tokenHash(hashToken(rawToken))
                .expiresAt(now.plus(sessionDuration))
                .build());

        AuthStatusResponse status = new AuthStatusResponse(false, true, toResponse(user));
        return new AuthSessionResult(rawToken, status);
    }

    private AuthSession requireAuthenticatedSession(String rawToken) {
        return findAuthenticatedSession(rawToken)
                .orElseThrow(() -> new AuthenticationRequiredException("登录已失效，请重新登录"));
    }

    private Optional<AuthSession> findAuthenticatedSession(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return authSessionRepository.findByTokenHashAndExpiresAtAfter(
                hashToken(rawToken),
                LocalDateTime.now()
        );
    }

    private AuthUserResponse toResponse(WorkspaceUser user) {
        return new AuthUserResponse(user.getId(), user.getEmail(), user.getDisplayName());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String randomToken() {
        byte[] token = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
