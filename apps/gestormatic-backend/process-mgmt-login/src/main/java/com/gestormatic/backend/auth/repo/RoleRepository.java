package com.gestormatic.backend.auth.repo;


import com.gestormatic.backend.auth.model.Role;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface RoleRepository extends CrudRepository<Role, Long> {
    Optional<Role> findByTenantIdAndName(String tenantId, String name);

    Iterable<Role> findAllByTenantId(String tenantId);

    Iterable<Role> findAllByTenantIdAndActiveTrue(String tenantId);

    Optional<Role> findByTenantIdAndNameAndActiveTrue(String tenantId, String name);
}
