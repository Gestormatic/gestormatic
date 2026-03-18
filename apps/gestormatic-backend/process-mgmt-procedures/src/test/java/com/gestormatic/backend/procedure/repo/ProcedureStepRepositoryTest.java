package com.gestormatic.backend.procedure.repo;

import com.gestormatic.backend.procedure.model.ProcedureStep;
import com.gestormatic.backend.procedure.model.ProcedureTemplate;
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
class ProcedureStepRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean JwtDecoder jwtDecoder;

    @Autowired ProcedureStepRepository stepRepo;
    @Autowired ProcedureTemplateRepository templateRepo;

    private Long templateId;
    private Long otherTemplateId;

    @BeforeEach
    void setUp() {
        stepRepo.deleteAll();
        templateRepo.deleteAll();
        templateId = saveTemplate("Trámite A").getId();
        otherTemplateId = saveTemplate("Trámite B").getId();
    }

    @Test
    void findAllByTemplateId_returnsOnlyStepsOfTemplate() {
        saveStep(templateId, 1, "Paso 1");
        saveStep(templateId, 2, "Paso 2");
        saveStep(otherTemplateId, 1, "Paso otro");

        List<ProcedureStep> result = stepRepo.findAllByTemplateId(templateId);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ProcedureStep::getName)
                .containsExactlyInAnyOrder("Paso 1", "Paso 2");
    }

    @Test
    void findAllByTemplateId_returnsOrderedByStepOrderAsc() {
        saveStep(templateId, 3, "Tercero");
        saveStep(templateId, 1, "Primero");
        saveStep(templateId, 2, "Segundo");

        List<ProcedureStep> result = stepRepo.findAllByTemplateId(templateId);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(ProcedureStep::getStepOrder)
                .containsExactly(1, 2, 3);
        assertThat(result).extracting(ProcedureStep::getName)
                .containsExactly("Primero", "Segundo", "Tercero");
    }

    @Test
    void findAllByTemplateId_returnsEmptyList_whenNoSteps() {
        List<ProcedureStep> result = stepRepo.findAllByTemplateId(templateId);

        assertThat(result).isEmpty();
    }

    private ProcedureTemplate saveTemplate(String name) {
        ProcedureTemplate t = new ProcedureTemplate();
        t.setTenantId("tenant-1");
        t.setName(name);
        t.setStatus("DRAFT");
        t.setCreatedBy("uid");
        t.setCreatedAt(OffsetDateTime.now());
        t.setUpdatedAt(OffsetDateTime.now());
        return templateRepo.save(t);
    }

    private ProcedureStep saveStep(Long tId, int order, String name) {
        ProcedureStep s = new ProcedureStep();
        s.setTemplateId(tId);
        s.setStepOrder(order);
        s.setName(name);
        return stepRepo.save(s);
    }
}
