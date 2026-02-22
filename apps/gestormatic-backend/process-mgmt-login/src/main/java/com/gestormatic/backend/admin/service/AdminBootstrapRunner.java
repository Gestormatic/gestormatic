package com.gestormatic.backend.admin.service;


import com.gestormatic.backend.admin.config.AdminBootstrapProperties;
import com.gestormatic.backend.admin.dto.SetClaimsRequest;
import com.gestormatic.backend.auth.model.Role;
import com.gestormatic.backend.auth.repo.RoleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AdminBootstrapRunner implements CommandLineRunner {
    private final AdminBootstrapProperties properties;
    private final AdminUserService adminUserService;
    private final RoleRepository roleRepository;

    public AdminBootstrapRunner(AdminBootstrapProperties properties,
                                AdminUserService adminUserService,
                                RoleRepository roleRepository) {
        this.properties = properties;
        this.adminUserService = adminUserService;
        this.roleRepository = roleRepository;
    }

    @Override
    public void run(String... args) {
        if (!properties.isEnabled()) {
            return;
        }

        if (properties.getUid() == null || properties.getUid().isBlank()) {
            throw new IllegalStateException("app.bootstrap.uid is required when bootstrap is enabled");
        }
        if (properties.getTenantId() == null || properties.getTenantId().isBlank()) {
            throw new IllegalStateException("app.bootstrap.tenant-id is required when bootstrap is enabled");
        }

        List<String> roles = properties.getRoles() == null ? List.of("admin") : properties.getRoles();
        ensureRolesExist(properties.getTenantId(), roles);

        SetClaimsRequest request = new SetClaimsRequest();
        request.setUid(properties.getUid());
        request.setTenantId(properties.getTenantId());
        request.setRoles(roles);
        adminUserService.setClaims(request);
    }

    private void ensureRolesExist(String tenantId, List<String> roles) {
        for (String roleName : roles) {
            if (roleName == null || roleName.isBlank()) {
                continue;
            }
            roleRepository.findByTenantIdAndName(tenantId, roleName)
                    .orElseGet(() -> {
                        Role role = new Role();
                        role.setTenantId(tenantId);
                        role.setName(roleName);
                        role.setActive(true);
                        role.setCreatedAt(java.time.OffsetDateTime.now());
                        return roleRepository.save(role);
                    });
        }
    }
}
