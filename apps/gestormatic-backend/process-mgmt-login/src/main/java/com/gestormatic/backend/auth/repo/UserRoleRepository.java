package com.gestormatic.backend.auth.repo;


import com.gestormatic.backend.auth.model.UserRole;
import org.springframework.data.repository.CrudRepository;

public interface UserRoleRepository extends CrudRepository<UserRole, Long> {
    void deleteByUserId(Long userId);
}
