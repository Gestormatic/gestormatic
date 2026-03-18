package com.gestormatic.backend.procedure.repo;

import com.gestormatic.backend.procedure.model.ProcedureStep;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProcedureStepRepository extends CrudRepository<ProcedureStep, Long> {

    @Query("SELECT * FROM procedure_steps WHERE template_id = :templateId ORDER BY step_order ASC")
    List<ProcedureStep> findAllByTemplateId(@Param("templateId") Long templateId);
}
