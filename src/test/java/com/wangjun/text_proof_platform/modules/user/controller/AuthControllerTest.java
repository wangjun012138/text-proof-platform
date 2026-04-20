package com.wangjun.text_proof_platform.modules.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangjun.text_proof_platform.common.GlobalExceptionHandler;
import com.wangjun.text_proof_platform.common.TooManyLoginAttemptsException;
import com.wangjun.text_proof_platform.config.SecurityConfig;
import com.wangjun.text_proof_platform.modules.user.entity.User;
import com.wangjun.text_proof_platform.modules.user.service.AuthService;
import com.wangjun.text_proof_platform.modules.user.service.LoginThrottleService;
import com.wangjun.text_proof_platform.modules.user.service.UserSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private UserSessionService userSessionService;

    @MockBean
    private LoginThrottleService loginThrottleService;

    @BeforeEach
    void setUp() {
        when(authService.normalizeAccount(anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0, String.class).trim());
        when(loginThrottleService.extractClientIp(any()))
                .thenReturn("127.0.0.1");
    }

    @Test
    void csrfShouldReturnTokenPayload() throws Exception {
        mockMvc.perform(get("/api/auth/csrf")
                        .secure(true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("CSRF token generated"))
                .andExpect(jsonPath("$.data.headerName", not(isEmptyOrNullString())))
                .andExpect(jsonPath("$.data.parameterName").value("_csrf"))
                .andExpect(jsonPath("$.data.token", not(isEmptyOrNullString())));
    }

    @Test
    void httpRequestsShouldRedirectToHttps() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://localhost/api/auth/csrf"));
    }

    @Test
    void loginShouldReturnUserIdWhenCredentialsValid() throws Exception {
        User user = new User();
        user.setId(42L);
        user.setUsername("demo");
        when(authService.login("demo", "secret")).thenReturn(user);

        mockMvc.perform(post("/api/auth/login")
                        .secure(true)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginPayload("demo", "secret"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Login succeeded"))
                .andExpect(jsonPath("$.data").value(42L));

        verify(loginThrottleService).assertAllowed("demo", "127.0.0.1");
        verify(loginThrottleService).recordSuccess("demo", "127.0.0.1");
        verify(userSessionService).registerCurrentSession(eq("demo"), anyString());
    }

    @Test
    void loginShouldReturnUnauthorizedWhenCredentialsInvalid() throws Exception {
        when(authService.login("demo", "wrong"))
                .thenThrow(new BadCredentialsException("Invalid credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .secure(true)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginPayload("demo", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("Invalid credentials"))
                .andExpect(jsonPath("$.data").isEmpty());

        verify(loginThrottleService).recordFailure("demo", "127.0.0.1");
        verify(loginThrottleService, never()).recordSuccess(anyString(), anyString());
    }

    @Test
    void loginShouldReturnTooManyRequestsWhenThrottled() throws Exception {
        doThrow(new TooManyLoginAttemptsException("Too many attempts. Please try again later", 60))
                .when(loginThrottleService)
                .assertAllowed("demo", "127.0.0.1");

        mockMvc.perform(post("/api/auth/login")
                        .secure(true)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginPayload("demo", "secret"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.code").value(429))
                .andExpect(jsonPath("$.message").value("Too many attempts. Please try again later"))
                .andExpect(jsonPath("$.data").isEmpty());

        verify(loginThrottleService, never()).recordFailure(anyString(), anyString());
        verify(loginThrottleService, never()).recordSuccess(anyString(), anyString());
    }

    @Test
    void loginShouldReturnBadRequestWhenAccountBlank() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .secure(true)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginPayload(" ", "secret"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", not(isEmptyOrNullString())));
    }

    @Test
    void loginShouldReturnForbiddenJsonWhenCsrfTokenMissing() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .secure(true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginPayload("demo", "secret"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("Access denied"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void loginShouldReturnBadRequestWhenRequestBodyMalformed() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .secure(true)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"demo\",\"password\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Malformed request body"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void changePasswordShouldReturnUnauthorizedJsonWhenAnonymous() throws Exception {
        mockMvc.perform(post("/api/auth/password/change")
                        .secure(true)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "oldPassword": "old123",
                                  "newPassword": "new123"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("Not logged in"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void resetPasswordShouldRejectNonNumericCode() throws Exception {
        mockMvc.perform(post("/api/auth/password/reset")
                        .secure(true)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "demo@example.com",
                                  "code": "abcdef",
                                  "newPassword": "New123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Verification code must contain only numbers"));
    }

    @Test
    void resetPasswordShouldRejectOverlongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/password/reset")
                        .secure(true)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "demo@example.com",
                                  "code": "123456",
                                  "newPassword": "A1A1A1A1A1A1A1A1A1A1A1A1A1A1A1A1A1A1A1A1A1A1A1A1A1A1"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Password length must be between 6 and 50 characters"));
    }

    private record LoginPayload(String account, String password) {
    }
}
