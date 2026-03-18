package com.gestormatic.backend.procedure.repo;

import com.gestormatic.backend.procedure.model.ProcedureTemplate;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProcedureTemplateRepository extends CrudRepository<ProcedureTemplate, Long> {

    @Query("SELECT * FROM procedure_templates WHERE tenant_id = :tenantId ORDER BY created_at DESC")
    List<ProcedureTemplate> findAllByTenantId(@Param("tenantId") String tenantId);

    @Query("SELECT * FROM procedure_templates WHERE id = :id AND tenant_id = :tenantId")
    Optional<ProcedureTemplate> findByIdAndTenantId(@Param("id") Long id, @Param("tenantId") String tenantId);
}
