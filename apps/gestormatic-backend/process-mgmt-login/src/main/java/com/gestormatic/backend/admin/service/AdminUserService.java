package com.gestormatic.backend.admin.service;


import com.gestormatic.backend.admin.dto.SetClaimsRequest;
import com.gestormatic.backend.admin.dto.SetClaimsResponse;
import com.gestormatic.backend.auth.config.SupabaseProperties;
import com.gestormatic.backend.auth.model.Role;
import com.gestormatic.backend.auth.model.User;
import com.gestormatic.backend.auth.model.UserRole;
import com.gestormatic.backend.auth.repo.RoleRepository;
import com.gestormatic.backend.auth.repo.UserRepository;
import com.gestormatic.backend.auth.repo.UserRoleRepository;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AdminUserService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final RestClient restClient;

    public AdminUserService(SupabaseProperties supabaseProperties,
                            UserRepository userRepository,
                            RoleRepository roleRepository,
                            UserRoleRepository userRoleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
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
                .defaultHeader("Authorization", "Bearer " + serviceKey)
                .defaultHeader("apikey", serviceKey)
                .build();
    }

    @Transactional
    public SetClaimsResponse setClaims(SetClaimsRequest request) {
        String uid = request.getUid();
        String tenantId = request.getTenantId();
        List<String> roles = request.getRoles() == null ? Collections.emptyList() : request.getRoles();

        User user = ensureUserInDb(uid, tenantId);
        List<String> storedRoles = syncRolesInDb(user.getId(), tenantId, roles);

        Map<String, Object> appMetadata = new HashMap<>();
        appMetadata.put("tenant_id", tenantId);
        appMetadata.put("roles", storedRoles);

        Map<String, Object> payload = new HashMap<>();
        payload.put("app_metadata", appMetadata);

        restClient.put()
                .uri("/auth/v1/admin/users/{uid}", uid)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();

        return new SetClaimsResponse(uid, tenantId, storedRoles);
    }

    private User ensureUserInDb(String uid, String tenantId) {
        Optional<User> existing = userRepository.findByTenantIdAndAuthUid(tenantId, uid);
        if (existing.isPresent()) {
            return existing.get();
        }

        User user = new User();
        user.setTenantId(tenantId);
        user.setAuthUid(uid);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        return userRepository.save(user);
    }

    private List<String> syncRolesInDb(Long userId, String tenantId, List<String> roles) {
        userRoleRepository.deleteByUserId(userId);
        if (roles.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> storedRoles = new java.util.ArrayList<>();
        for (String roleName : roles) {
            if (roleName == null || roleName.isBlank()) {
                continue;
            }
            Role role = roleRepository.findByTenantIdAndNameAndActiveTrue(tenantId, roleName)
                    .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleName));
            UserRole userRole = new UserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(role.getId());
            userRoleRepository.save(userRole);
            storedRoles.add(role.getName());
        }
        return storedRoles;
    }
}
