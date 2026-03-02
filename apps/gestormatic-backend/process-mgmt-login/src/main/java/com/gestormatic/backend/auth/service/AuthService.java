package com.gestormatic.backend.auth.service;


import com.gestormatic.backend.auth.config.SupabaseProperties;
import com.gestormatic.backend.auth.dto.SignUpRequest;
import com.gestormatic.backend.auth.dto.SignUpResponse;
import com.gestormatic.backend.auth.dto.UserProfileResponse;
import com.gestormatic.backend.auth.model.User;
import com.gestormatic.backend.auth.repo.UserRepository;
import com.gestormatic.backend.auth.security.SupabasePrincipal;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final RestClient supabaseClient;

    public AuthService(UserRepository userRepository,
                       NamedParameterJdbcTemplate jdbcTemplate,
                       SupabaseProperties supabaseProperties) {
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.supabaseClient = RestClient.builder()
                .baseUrl(supabaseProperties.getUrl().replaceAll("/+$", ""))
                .defaultHeader("Authorization", "Bearer " + supabaseProperties.getServiceRoleKey())
                .defaultHeader("apikey", supabaseProperties.getServiceRoleKey())
                .build();
    }

    @Transactional
    public SignUpResponse signUp(SignUpRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new IllegalArgumentException("email is required");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("password is required");
        }
        if (request.getDisplayName() == null || request.getDisplayName().isBlank()) {
            throw new IllegalArgumentException("displayName is required");
        }
        if (request.getTenantId() == null || request.getTenantId().isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("email", request.getEmail());
        payload.put("password", request.getPassword());
        payload.put("email_confirm", false);
        payload.put("user_metadata", Map.of("display_name", request.getDisplayName()));
        payload.put("app_metadata", Map.of("tenant_id", request.getTenantId()));

        Map<String, Object> supabaseUser = supabaseClient.post()
                .uri("/auth/v1/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        String uid = (String) supabaseUser.get("id");

        User user = new User();
        user.setTenantId(request.getTenantId());
        user.setAuthUid(uid);
        user.setEmail(request.getEmail());
        user.setDisplayName(request.getDisplayName());
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);

        return new SignUpResponse(uid, request.getEmail(), request.getDisplayName());
    }

    @Transactional
    public Optional<UserProfileResponse> getProfile(String tenantId,
                                                    SupabasePrincipal principal,
                                                    List<String> claimRoles) {
        Optional<User> user = userRepository.findByTenantIdAndAuthUid(tenantId, principal.uid());
        return user.map(found -> {
            syncIfChanged(found, principal.email(), principal.name());
            return new UserProfileResponse(
                    found.getId(),
                    found.getTenantId(),
                    found.getAuthUid(),
                    found.getEmail(),
                    found.getDisplayName(),
                    mergeRoles(claimRoles, loadRoles(found.getId()))
            );
        });
    }

    private void syncIfChanged(User user, String email, String displayName) {
        boolean changed = false;
        if (email != null && !email.equals(user.getEmail())) {
            user.setEmail(email);
            changed = true;
        }
        if (displayName != null && !displayName.equals(user.getDisplayName())) {
            user.setDisplayName(displayName);
            changed = true;
        }
        if (changed) {
            user.setUpdatedAt(java.time.OffsetDateTime.now());
            userRepository.save(user);
        }
    }

    private List<String> loadRoles(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }

        String sql = """
                select r.name
                from roles r
                join user_roles ur on ur.role_id = r.id
                where ur.user_id = :userId
                order by r.name
                """;

        MapSqlParameterSource params = new MapSqlParameterSource("userId", userId);
        return new ArrayList<>(jdbcTemplate.query(sql, params, (rs, rowNum) -> rs.getString("name")));
    }

    private List<String> mergeRoles(List<String> claimRoles, List<String> dbRoles) {
        Set<String> merged = new LinkedHashSet<>();
        if (claimRoles != null) {
            merged.addAll(claimRoles);
        }
        if (dbRoles != null) {
            merged.addAll(dbRoles);
        }
        return new ArrayList<>(merged);
    }
}
