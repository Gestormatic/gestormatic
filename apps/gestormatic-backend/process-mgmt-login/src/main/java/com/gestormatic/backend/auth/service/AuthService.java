package com.gestormatic.backend.auth.service;


import com.gestormatic.backend.auth.dto.UserProfileResponse;
import com.gestormatic.backend.auth.config.SupabaseProperties;
import com.gestormatic.backend.auth.model.User;
import com.gestormatic.backend.auth.repo.UserRepository;
import com.gestormatic.backend.auth.security.SupabasePrincipal;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;

@Service
public class AuthService {
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final UserRepository userRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final RestClient restClient;

    public AuthService(SupabaseProperties supabaseProperties,
                       UserRepository userRepository,
                       NamedParameterJdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
        String baseUrl = supabaseProperties.getUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("app.supabase.url is required");
        }

        String serviceKey = supabaseProperties.getServiceRoleKey();
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new IllegalStateException("app.supabase.service-role-key is required");
        }

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl.replaceAll("/+$", ""))
                .defaultHeader("apikey", serviceKey)
                .build();
    }

    public Map<String, Object> signInWithPassword(String email, String password) {
        Map<String, String> payload = new HashMap<>();
        payload.put("email", email);
        payload.put("password", password);

        try {
            Map<String, Object> response = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/auth/v1/token")
                            .queryParam("grant_type", "password")
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(MAP_TYPE);

            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Supabase signin returned empty response");
            }
            return response;
        } catch (RestClientResponseException ex) {
            throw new ResponseStatusException(ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Supabase signin is unavailable", ex);
        }
    }

    @Transactional(readOnly = true)
    public Optional<UserProfileResponse> getProfile(String tenantId,
                                                    SupabasePrincipal principal,
                                                    List<String> claimRoles) {
        Optional<User> user = userRepository.findByTenantIdAndAuthUid(tenantId, principal.uid());
        return user.map(found -> new UserProfileResponse(
                found.getId(),
                found.getTenantId(),
                found.getAuthUid(),
                found.getEmail(),
                found.getDisplayName(),
                mergeRoles(claimRoles, loadRoles(found.getId()))
        ));
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
