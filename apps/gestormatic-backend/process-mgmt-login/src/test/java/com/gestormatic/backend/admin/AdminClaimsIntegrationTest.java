package com.gestormatic.backend.admin;


import com.gestormatic.backend.admin.dto.SetClaimsRequest;
import com.gestormatic.backend.admin.dto.SetClaimsResponse;
import com.gestormatic.backend.admin.service.AdminUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
@ActiveProfiles("test")
class AdminClaimsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

        @MockitoBean
    private JwtDecoder jwtDecoder;

        @MockitoBean
    private AdminUserService adminUserService;

    @Test
    void setClaimsForbiddenWithoutAdminRole() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-1")
                .claim("email", "user@example.com")
                .claim("app_metadata", Map.of(
                        "tenant_id", "acme",
                        "roles", List.of("gestor")
                ))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(jwtDecoder.decode(eq("token"))).thenReturn(jwt);

        SetClaimsRequest request = new SetClaimsRequest();
        request.setUid("user-2");
        request.setTenantId("acme");
        request.setRoles(List.of("gestor"));

        mockMvc.perform(post("/admin/users/claims")
                        .header("Authorization", "Bearer token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void setClaimsOkWithAdminRole() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("admin-1")
                .claim("email", "admin@example.com")
                .claim("app_metadata", Map.of(
                        "tenant_id", "acme",
                        "roles", List.of("admin")
                ))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(jwtDecoder.decode(eq("token"))).thenReturn(jwt);
        when(adminUserService.setClaims(any())).thenReturn(new SetClaimsResponse("user-2", "acme", List.of("gestor")));

        SetClaimsRequest request = new SetClaimsRequest();
        request.setUid("user-2");
        request.setTenantId("acme");
        request.setRoles(List.of("gestor"));

        mockMvc.perform(post("/admin/users/claims")
                        .header("Authorization", "Bearer token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uid").value("user-2"))
                .andExpect(jsonPath("$.tenantId").value("acme"))
                .andExpect(jsonPath("$.roles[0]").value("gestor"));
    }

    @Test
    void updateUserRolesUsesAdminTenant() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("admin-1")
                .claim("email", "admin@example.com")
                .claim("app_metadata", Map.of(
                        "tenant_id", "acme",
                        "roles", List.of("admin")
                ))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(jwtDecoder.decode(eq("token"))).thenReturn(jwt);
        when(adminUserService.setClaims(any())).thenReturn(new SetClaimsResponse("user-2", "acme", List.of("gestor")));

        mockMvc.perform(put("/admin/users/user-2/roles")
                        .header("Authorization", "Bearer token")
                        .contentType("application/json")
                        .content("{\"roles\":[\"gestor\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uid").value("user-2"))
                .andExpect(jsonPath("$.tenantId").value("acme"))
                .andExpect(jsonPath("$.roles[0]").value("gestor"));
    }
}
