package com.flowforge.ai.controller;

import com.flowforge.ai.dto.AuthSessionResult;
import com.flowforge.ai.dto.AuthStatusResponse;
import com.flowforge.ai.dto.AuthUserResponse;
import com.flowforge.ai.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @Test
    void exposesWhetherFirstRunSetupIsRequired() throws Exception {
        when(authService.getStatus(null)).thenReturn(new AuthStatusResponse(true, false, null));

        mockMvc.perform(get("/api/auth/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setupRequired").value(true))
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void createsTheOwnerAndIssuesAnHttpOnlySameSiteCookie() throws Exception {
        AuthUserResponse user = new AuthUserResponse(UUID.randomUUID(), "owner@example.com", "Flow Creator");
        when(authService.getSessionDuration()).thenReturn(Duration.ofDays(30));
        when(authService.setup(any())).thenReturn(new AuthSessionResult(
                "raw-token",
                new AuthStatusResponse(false, true, user)
        ));

        mockMvc.perform(post("/api/auth/setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Flow Creator",
                                  "email": "owner@example.com",
                                  "password": "correct-horse-battery"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("flowforge_session=raw-token"),
                        org.hamcrest.Matchers.containsString("HttpOnly"),
                        org.hamcrest.Matchers.containsString("SameSite=Lax")
                )))
                .andExpect(jsonPath("$.user.email").value("owner@example.com"));
    }

    @Test
    void validatesLoginInputBeforeCallingTheService() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void revokesTheCurrentSessionAndClearsTheCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new jakarta.servlet.http.Cookie(AuthService.SESSION_COOKIE_NAME, "raw-token")))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("flowforge_session="),
                        org.hamcrest.Matchers.containsString("Max-Age=0")
                )));

        verify(authService).logout("raw-token");
    }

    @Test
    void updatesTheOwnerProfile() throws Exception {
        AuthUserResponse user = new AuthUserResponse(UUID.randomUUID(), "owner@example.com", "New Name");
        when(authService.updateProfile(any(), any())).thenReturn(new AuthStatusResponse(false, true, user));

        mockMvc.perform(patch("/api/auth/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"New Name\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.displayName").value("New Name"));
    }

    @Test
    void changesThePasswordAndRotatesTheSessionCookie() throws Exception {
        AuthUserResponse user = new AuthUserResponse(UUID.randomUUID(), "owner@example.com", "Flow Creator");
        when(authService.getSessionDuration()).thenReturn(Duration.ofDays(30));
        when(authService.changePassword(any(), any())).thenReturn(new AuthSessionResult(
                "rotated-token",
                new AuthStatusResponse(false, true, user)
        ));

        mockMvc.perform(post("/api/auth/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "old-password-value",
                                  "newPassword": "new-password-value"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("flowforge_session=rotated-token"),
                        org.hamcrest.Matchers.containsString("HttpOnly"),
                        org.hamcrest.Matchers.containsString("SameSite=Lax")
                )))
                .andExpect(jsonPath("$.authenticated").value(true));
    }
}
