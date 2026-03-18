package com.gestormatic.backend.procedure.repo;

import com.gestormatic.backend.procedure.model.ProcedureCase;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface CaseRepository extends CrudRepository<ProcedureCase, Long> {

    Optional<ProcedureCase> findByIdAndTenantId(Long id, String tenantId);
}
