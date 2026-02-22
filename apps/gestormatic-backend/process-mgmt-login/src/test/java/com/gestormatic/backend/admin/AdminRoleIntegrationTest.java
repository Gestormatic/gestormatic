package com.gestormatic.backend.admin;


import com.gestormatic.backend.admin.dto.RoleResponse;
import com.gestormatic.backend.admin.service.AdminRoleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
@ActiveProfiles("test")
class AdminRoleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

        @MockitoBean
    private JwtDecoder jwtDecoder;

        @MockitoBean
    private AdminRoleService adminRoleService;

    @Test
    void listRolesRequiresAdmin() throws Exception {
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

        mockMvc.perform(get("/admin/roles")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listRolesReturnsRolesForAdmin() throws Exception {
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
        when(adminRoleService.listRoles(eq("acme")))
                .thenReturn(List.of(new RoleResponse("admin"), new RoleResponse("gestor")));

        mockMvc.perform(get("/admin/roles")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("admin"))
                .andExpect(jsonPath("$[1].name").value("gestor"));
    }

    @Test
    void createRoleRequiresAdmin() throws Exception {
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

        mockMvc.perform(post("/admin/roles")
                        .header("Authorization", "Bearer token")
                        .contentType("application/json")
                        .content("{\"name\":\"gestor\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createRoleReturnsCreatedRole() throws Exception {
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
        when(adminRoleService.createRole(eq("acme"), eq("gestor")))
                .thenReturn(new RoleResponse("gestor"));

        mockMvc.perform(post("/admin/roles")
                        .header("Authorization", "Bearer token")
                        .contentType("application/json")
                        .content("{\"name\":\"gestor\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("gestor"));
    }

    @Test
    void updateRoleReturnsUpdatedRole() throws Exception {
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
        when(adminRoleService.updateRole(eq("acme"), eq("gestor"), eq("gestor_editado")))
                .thenReturn(new RoleResponse("gestor_editado"));

        mockMvc.perform(put("/admin/roles/gestor")
                        .header("Authorization", "Bearer token")
                        .contentType("application/json")
                        .content("{\"name\":\"gestor_editado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("gestor_editado"));
    }

    @Test
    void deactivateRoleReturnsNoContent() throws Exception {
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

        mockMvc.perform(delete("/admin/roles/gestor")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isNoContent());
    }
}
