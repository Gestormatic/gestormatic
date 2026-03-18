package com.gestormatic.backend.procedure.repo;

import com.gestormatic.backend.procedure.model.RequiredDocument;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RequiredDocumentRepository extends CrudRepository<RequiredDocument, Long> {

    @Query("SELECT * FROM required_documents WHERE step_id = :stepId ORDER BY id ASC")
    List<RequiredDocument> findAllByStepId(@Param("stepId") Long stepId);
}
