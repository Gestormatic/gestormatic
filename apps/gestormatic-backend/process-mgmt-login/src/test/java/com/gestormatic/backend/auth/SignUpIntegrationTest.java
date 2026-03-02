package com.gestormatic.backend.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestormatic.backend.auth.dto.SignUpRequest;
import com.gestormatic.backend.auth.dto.SignUpResponse;
import com.gestormatic.backend.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
@ActiveProfiles("test")
class SignUpIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ─── happy path ───────────────────────────────────────────────────────────

    @Test
    void signUpReturns201WithValidRequest() throws Exception {
        when(authService.signUp(any()))
                .thenReturn(new SignUpResponse("supabase-uid-1", "carlos@example.com", "Carlos Vargas"));

        mockMvc.perform(post("/auth/signup")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uid").value("supabase-uid-1"))
                .andExpect(jsonPath("$.email").value("carlos@example.com"))
                .andExpect(jsonPath("$.displayName").value("Carlos Vargas"));
    }

    @Test
    void signUpIsAccessibleWithoutAuthToken() throws Exception {
        when(authService.signUp(any()))
                .thenReturn(new SignUpResponse("uid-1", "user@example.com", "Test User"));

        mockMvc.perform(post("/auth/signup")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated());
    }

    // ─── validation: required fields ─────────────────────────────────────────

    @Test
    void signUpReturns400WhenDisplayNameMissing() throws Exception {
        when(authService.signUp(any()))
                .thenThrow(new IllegalArgumentException("displayName is required"));

        SignUpRequest req = validRequest();
        req.setDisplayName(null);

        mockMvc.perform(post("/auth/signup")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"))
                .andExpect(jsonPath("$.message").value("displayName is required"));
    }

    @Test
    void signUpReturns400WhenEmailMissing() throws Exception {
        when(authService.signUp(any()))
                .thenThrow(new IllegalArgumentException("email is required"));

        SignUpRequest req = validRequest();
        req.setEmail(null);

        mockMvc.perform(post("/auth/signup")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"))
                .andExpect(jsonPath("$.message").value("email is required"));
    }

    @Test
    void signUpReturns400WhenPasswordMissing() throws Exception {
        when(authService.signUp(any()))
                .thenThrow(new IllegalArgumentException("password is required"));

        SignUpRequest req = validRequest();
        req.setPassword(null);

        mockMvc.perform(post("/auth/signup")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"))
                .andExpect(jsonPath("$.message").value("password is required"));
    }

    @Test
    void signUpReturns400WhenTenantIdMissing() throws Exception {
        when(authService.signUp(any()))
                .thenThrow(new IllegalArgumentException("tenantId is required"));

        SignUpRequest req = validRequest();
        req.setTenantId(null);

        mockMvc.perform(post("/auth/signup")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"))
                .andExpect(jsonPath("$.message").value("tenantId is required"));
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private SignUpRequest validRequest() {
        SignUpRequest req = new SignUpRequest();
        req.setEmail("carlos@example.com");
        req.setPassword("password123");
        req.setDisplayName("Carlos Vargas");
        req.setTenantId("default");
        return req;
    }
}
