package com.gestormatic.backend.procedure.repo;

import com.gestormatic.backend.procedure.model.ProcedureMember;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProcedureMemberRepository extends CrudRepository<ProcedureMember, Long> {

    @Query("SELECT * FROM procedure_members WHERE template_id = :templateId")
    List<ProcedureMember> findAllByTemplateId(@Param("templateId") Long templateId);

    @Query("SELECT * FROM procedure_members WHERE template_id = :templateId AND user_id = :userId")
    Optional<ProcedureMember> findByTemplateIdAndUserId(@Param("templateId") Long templateId, @Param("userId") String userId);

    @Modifying
    @Query("DELETE FROM procedure_members WHERE template_id = :templateId AND user_id = :userId")
    void deleteByTemplateIdAndUserId(@Param("templateId") Long templateId, @Param("userId") String userId);
}
