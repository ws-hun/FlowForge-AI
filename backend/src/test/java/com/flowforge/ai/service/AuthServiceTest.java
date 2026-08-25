package com.flowforge.ai.service;

import com.flowforge.ai.dto.AuthCredentialsRequest;
import com.flowforge.ai.dto.AuthPasswordChangeRequest;
import com.flowforge.ai.dto.AuthProfileUpdateRequest;
import com.flowforge.ai.dto.AuthSetupRequest;
import com.flowforge.ai.entity.AuthSession;
import com.flowforge.ai.entity.WorkspaceUser;
import com.flowforge.ai.exception.AuthenticationRequiredException;
import com.flowforge.ai.exception.ResourceConflictException;
import com.flowforge.ai.repository.AuthSessionRepository;
import com.flowforge.ai.repository.WorkspaceUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private WorkspaceUserRepository workspaceUserRepository;

    @Mock
    private AuthSessionRepository authSessionRepository;

    private BCryptPasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4);
        authService = new AuthService(
                workspaceUserRepository,
                authSessionRepository,
                passwordEncoder,
                Duration.ofDays(30)
        );
    }

    @Test
    void createsTheSingleWorkspaceOwnerAndPersistsOnlyTheSessionHash() {
        when(workspaceUserRepository.count()).thenReturn(0L);
        when(workspaceUserRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            WorkspaceUser user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });

        var result = authService.setup(new AuthSetupRequest(
                " Flow Creator ",
                " OWNER@EXAMPLE.COM ",
                "correct-horse-battery"
        ));

        ArgumentCaptor<WorkspaceUser> userCaptor = ArgumentCaptor.forClass(WorkspaceUser.class);
        verify(workspaceUserRepository).saveAndFlush(userCaptor.capture());
        WorkspaceUser savedUser = userCaptor.getValue();
        assertThat(savedUser.getDisplayName()).isEqualTo("Flow Creator");
        assertThat(savedUser.getEmail()).isEqualTo("owner@example.com");
        assertThat(passwordEncoder.matches("correct-horse-battery", savedUser.getPasswordHash())).isTrue();

        ArgumentCaptor<AuthSession> sessionCaptor = ArgumentCaptor.forClass(AuthSession.class);
        verify(authSessionRepository).save(sessionCaptor.capture());
        assertThat(result.token()).hasSize(43);
        assertThat(sessionCaptor.getValue().getTokenHash())
                .hasSize(64)
                .isNotEqualTo(result.token());
        assertThat(result.status().authenticated()).isTrue();
        assertThat(result.status().setupRequired()).isFalse();
    }

    @Test
    void rejectsSetupAfterAnOwnerExists() {
        when(workspaceUserRepository.count()).thenReturn(1L);

        assertThatThrownBy(() -> authService.setup(new AuthSetupRequest(
                "Another Owner",
                "other@example.com",
                "another-secure-password"
        ))).isInstanceOf(ResourceConflictException.class);

        verify(workspaceUserRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsUnknownEmailAndWrongPasswordWithTheSameError() {
        when(workspaceUserRepository.findByEmailIgnoreCase("owner@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new AuthCredentialsRequest(
                "owner@example.com",
                "incorrect-password"
        )))
                .isInstanceOf(AuthenticationRequiredException.class)
                .hasMessage("邮箱或密码不正确");
    }

    @Test
    void resolvesAnUnexpiredSessionAndRevokesItOnLogout() {
        WorkspaceUser user = WorkspaceUser.builder()
                .id(UUID.randomUUID())
                .email("owner@example.com")
                .displayName("Flow Creator")
                .passwordHash("unused")
                .build();
        AuthSession session = AuthSession.builder()
                .user(user)
                .tokenHash("hash")
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();
        when(authSessionRepository.findByTokenHashAndExpiresAtAfter(any(), any())).thenReturn(Optional.of(session));

        assertThat(authService.findAuthenticatedUser("raw-session-token"))
                .get()
                .extracting(response -> response.email())
                .isEqualTo("owner@example.com");

        authService.logout("raw-session-token");
        verify(authSessionRepository).deleteByTokenHash(any());
    }

    @Test
    void updatesTheAuthenticatedOwnerProfile() {
        WorkspaceUser user = owner("Flow Creator", "old-password-value");
        AuthSession session = sessionFor(user);
        when(authSessionRepository.findByTokenHashAndExpiresAtAfter(any(), any())).thenReturn(Optional.of(session));

        var result = authService.updateProfile("raw-session-token", new AuthProfileUpdateRequest(" New Name "));

        assertThat(user.getDisplayName()).isEqualTo("New Name");
        assertThat(result.user().displayName()).isEqualTo("New Name");
        verify(workspaceUserRepository).saveAndFlush(user);
    }

    @Test
    void rotatesEverySessionAfterChangingThePassword() {
        WorkspaceUser user = owner("Flow Creator", "old-password-value");
        AuthSession session = sessionFor(user);
        when(authSessionRepository.findByTokenHashAndExpiresAtAfter(any(), any())).thenReturn(Optional.of(session));
        when(authSessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = authService.changePassword(
                "raw-session-token",
                new AuthPasswordChangeRequest("old-password-value", "new-password-value")
        );

        assertThat(passwordEncoder.matches("new-password-value", user.getPasswordHash())).isTrue();
        assertThat(result.token()).hasSize(43);
        verify(authSessionRepository).deleteByUserId(user.getId());
        verify(authSessionRepository).save(any(AuthSession.class));
    }

    @Test
    void rejectsPasswordChangeWithTheWrongCurrentPassword() {
        WorkspaceUser user = owner("Flow Creator", "old-password-value");
        when(authSessionRepository.findByTokenHashAndExpiresAtAfter(any(), any()))
                .thenReturn(Optional.of(sessionFor(user)));

        assertThatThrownBy(() -> authService.changePassword(
                "raw-session-token",
                new AuthPasswordChangeRequest("wrong-password", "new-password-value")
        ))
                .isInstanceOf(AuthenticationRequiredException.class)
                .hasMessage("当前密码不正确");

        verify(workspaceUserRepository, never()).saveAndFlush(any());
        verify(authSessionRepository, never()).deleteByUserId(any());
    }

    private WorkspaceUser owner(String displayName, String rawPassword) {
        return WorkspaceUser.builder()
                .id(UUID.randomUUID())
                .email("owner@example.com")
                .displayName(displayName)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .build();
    }

    private AuthSession sessionFor(WorkspaceUser user) {
        return AuthSession.builder()
                .id(UUID.randomUUID())
                .user(user)
                .tokenHash("hash")
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();
    }
}
