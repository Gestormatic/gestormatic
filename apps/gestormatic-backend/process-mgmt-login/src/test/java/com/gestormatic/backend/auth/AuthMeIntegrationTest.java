package com.gestormatic.backend.auth;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
@ActiveProfiles("test")
class AuthMeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

        @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from user_roles");
        jdbcTemplate.update("delete from roles");
        jdbcTemplate.update("delete from users");
    }

    @Test
    void meReturnsMergedRolesFromClaimsAndDb() throws Exception {
        String tenantId = "acme";
        String uid = "user-123";

        jdbcTemplate.update(
                "insert into users (tenant_id, auth_uid, email, display_name, created_at, updated_at) values (?,?,?,?,now(),now())",
                tenantId, uid, "user@example.com", "User"
        );
        Long userId = jdbcTemplate.queryForObject(
                "select id from users where tenant_id = ? and auth_uid = ?",
                Long.class,
                tenantId,
                uid
        );

        jdbcTemplate.update(
                "insert into roles (tenant_id, name, created_at) values (?,?,now())",
                tenantId, "gestor"
        );
        Long roleId = jdbcTemplate.queryForObject(
                "select id from roles where tenant_id = ? and name = ?",
                Long.class,
                tenantId,
                "gestor"
        );
        jdbcTemplate.update("insert into user_roles (user_id, role_id) values (?,?)", userId, roleId);

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(uid)
                .claim("email", "user@example.com")
                .claim("app_metadata", Map.of(
                        "tenant_id", tenantId,
                        "roles", List.of("admin")
                ))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(jwtDecoder.decode(eq("token"))).thenReturn(jwt);

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(tenantId))
                .andExpect(jsonPath("$.authUid").value(uid))
                .andExpect(jsonPath("$.roles[0]").value("admin"))
                .andExpect(jsonPath("$.roles[1]").value("gestor"));
    }

      @Test
      void signinRequiresCredentialsBody() throws Exception {
              mockMvc.perform(post("/auth/signin")
                              .contentType("application/json")
                              .content("{}"))
                              .andExpect(status().isBadRequest())
                              .andExpect(jsonPath("$.error").value("email and password are required"));
    }
}
