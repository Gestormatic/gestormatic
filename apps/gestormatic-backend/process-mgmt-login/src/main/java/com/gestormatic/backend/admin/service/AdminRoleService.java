package com.gestormatic.backend.admin.service;


import com.gestormatic.backend.admin.dto.RoleResponse;
import com.gestormatic.backend.auth.model.Role;
import com.gestormatic.backend.auth.repo.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.StreamSupport;

@Service
public class AdminRoleService {
    private final RoleRepository roleRepository;

    public AdminRoleService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> listRoles(String tenantId) {
        return StreamSupport.stream(roleRepository.findAllByTenantIdAndActiveTrue(tenantId).spliterator(), false)
                .map(role -> new RoleResponse(role.getName()))
                .toList();
    }

    @Transactional
    public RoleResponse createRole(String tenantId, String name) {
        Role role = roleRepository.findByTenantIdAndName(tenantId, name)
                .orElseGet(() -> {
                    Role newRole = new Role();
                    newRole.setTenantId(tenantId);
                    newRole.setName(name);
                    newRole.setActive(true);
                    newRole.setCreatedAt(OffsetDateTime.now());
                    return roleRepository.save(newRole);
                });
        if (!role.isActive()) {
            role.setActive(true);
            roleRepository.save(role);
        }
        return new RoleResponse(role.getName());
    }

    @Transactional
    public RoleResponse updateRole(String tenantId, String name, String newName) {
        Role role = roleRepository.findByTenantIdAndName(tenantId, name)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + name));
        if (newName != null && !newName.isBlank() && !newName.equals(role.getName())) {
            role.setName(newName);
        }
        roleRepository.save(role);
        return new RoleResponse(role.getName());
    }

    @Transactional
    public void deactivateRole(String tenantId, String name) {
        Role role = roleRepository.findByTenantIdAndName(tenantId, name)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + name));
        role.setActive(false);
        roleRepository.save(role);
    }
}
