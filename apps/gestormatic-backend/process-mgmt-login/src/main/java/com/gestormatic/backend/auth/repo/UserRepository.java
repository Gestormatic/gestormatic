package com.gestormatic.backend.auth.repo;


import com.gestormatic.backend.auth.model.User;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface UserRepository extends CrudRepository<User, Long> {
    Optional<User> findByTenantIdAndAuthUid(String tenantId, String authUid);
}
