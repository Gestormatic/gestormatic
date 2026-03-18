package com.gestormatic.backend.procedure.repo;

import com.gestormatic.backend.procedure.model.ProcedureStep;
import com.gestormatic.backend.procedure.model.ProcedureTemplate;
import com.gestormatic.backend.procedure.model.RequiredDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@ActiveProfiles("test")
class RequiredDocumentRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean JwtDecoder jwtDecoder;

    @Autowired RequiredDocumentRepository docRepo;
    @Autowired ProcedureStepRepository stepRepo;
    @Autowired ProcedureTemplateRepository templateRepo;

    private Long stepId;
    private Long otherStepId;

    @BeforeEach
    void setUp() {
        docRepo.deleteAll();
        stepRepo.deleteAll();
        templateRepo.deleteAll();

        Long templateId = saveTemplate().getId();
        stepId = saveStep(templateId, 1).getId();
        otherStepId = saveStep(templateId, 2).getId();
    }

    @Test
    void findAllByStepId_returnsOnlyDocumentsOfStep() {
        saveDoc(stepId, "DNI", true);
        saveDoc(stepId, "Pasaporte", false);
        saveDoc(otherStepId, "Contrato", true);

        List<RequiredDocument> result = docRepo.findAllByStepId(stepId);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(RequiredDocument::getName)
                .containsExactlyInAnyOrder("DNI", "Pasaporte");
    }

    @Test
    void findAllByStepId_returnsMandatoryFlagCorrectly() {
        saveDoc(stepId, "DNI", true);
        saveDoc(stepId, "Foto", false);

        List<RequiredDocument> result = docRepo.findAllByStepId(stepId);

        assertThat(result).filteredOn(d -> d.getName().equals("DNI"))
                .first().extracting(RequiredDocument::isMandatory).isEqualTo(true);
        assertThat(result).filteredOn(d -> d.getName().equals("Foto"))
                .first().extracting(RequiredDocument::isMandatory).isEqualTo(false);
    }

    @Test
    void findAllByStepId_returnsOrderedByIdAsc() {
        RequiredDocument first = saveDoc(stepId, "Primero", true);
        RequiredDocument second = saveDoc(stepId, "Segundo", true);

        List<RequiredDocument> result = docRepo.findAllByStepId(stepId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isLessThan(result.get(1).getId());
    }

    @Test
    void findAllByStepId_returnsEmptyList_whenNoDocuments() {
        List<RequiredDocument> result = docRepo.findAllByStepId(stepId);

        assertThat(result).isEmpty();
    }

    private ProcedureTemplate saveTemplate() {
        ProcedureTemplate t = new ProcedureTemplate();
        t.setTenantId("tenant-1");
        t.setName("Trámite");
        t.setStatus("DRAFT");
        t.setCreatedBy("uid");
        t.setCreatedAt(OffsetDateTime.now());
        t.setUpdatedAt(OffsetDateTime.now());
        return templateRepo.save(t);
    }

    private ProcedureStep saveStep(Long templateId, int order) {
        ProcedureStep s = new ProcedureStep();
        s.setTemplateId(templateId);
        s.setStepOrder(order);
        s.setName("Paso " + order);
        return stepRepo.save(s);
    }

    private RequiredDocument saveDoc(Long sId, String name, boolean mandatory) {
        RequiredDocument d = new RequiredDocument();
        d.setStepId(sId);
        d.setName(name);
        d.setMandatory(mandatory);
        return docRepo.save(d);
    }
}
