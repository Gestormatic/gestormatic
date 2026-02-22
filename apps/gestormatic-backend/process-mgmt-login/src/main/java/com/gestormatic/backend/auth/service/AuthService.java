package com.gestormatic.backend.auth.service;


import com.gestormatic.backend.auth.dto.UserProfileResponse;
import com.gestormatic.backend.auth.model.User;
import com.gestormatic.backend.auth.repo.UserRepository;
import com.gestormatic.backend.auth.security.SupabasePrincipal;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AuthService(UserRepository userRepository,
                       NamedParameterJdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
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
