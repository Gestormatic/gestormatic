package com.gestormatic.backend.procedure.service;

import com.gestormatic.backend.auth.security.SupabasePrincipal;
import com.gestormatic.backend.procedure.dto.AssignCaseRequest;
import com.gestormatic.backend.procedure.dto.CaseResponse;
import com.gestormatic.backend.procedure.dto.ChangeCaseStatusRequest;
import com.gestormatic.backend.procedure.dto.CreateCaseRequest;
import com.gestormatic.backend.procedure.model.ProcedureCase;
import com.gestormatic.backend.procedure.model.ProcedureTemplate;
import com.gestormatic.backend.procedure.repo.CaseRepository;
import com.gestormatic.backend.procedure.repo.ProcedureTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@ActiveProfiles("test")
class CaseIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean JwtDecoder jwtDecoder;

    @Autowired CaseService caseService;
    @Autowired CaseRepository caseRepo;
    @Autowired ProcedureTemplateRepository templateRepo;

    private static final String TENANT = "tenant-1";
    private static final String GESTOR_UID = "gestor-uid";
    private SupabasePrincipal gestor;
    private Long activeTemplateId;

    @BeforeEach
    void setUp() {
        gestor = new SupabasePrincipal(GESTOR_UID, "g@g.com", "Gestor", TENANT, List.of("gestor"));

        caseRepo.deleteAll();
        templateRepo.deleteAll();

        ProcedureTemplate t = new ProcedureTemplate();
        t.setTenantId(TENANT);
        t.setName("Trámite Activo");
        t.setStatus(ProcedureService.STATUS_ACTIVE);
        t.setCreatedBy(GESTOR_UID);
        t.setCreatedAt(OffsetDateTime.now());
        t.setUpdatedAt(OffsetDateTime.now());
        activeTemplateId = templateRepo.save(t).getId();
    }

    @Test
    void createCase_persistsInDatabase() {
        CaseResponse response = caseService.createCase(gestor,
                new CreateCaseRequest(activeTemplateId, "customer-uid", "Nota inicial"));

        assertThat(response.id()).isNotNull();
        assertThat(response.status()).isEqualTo(CaseService.STATUS_RECEIVED);
        assertThat(response.notes()).isEqualTo("Nota inicial");
        assertThat(response.templateName()).isEqualTo("Trámite Activo");
        assertThat(caseRepo.findById(response.id())).isPresent();
    }

    @Test
    void listCases_returnsOnlyCasesForTenant() {
        caseService.createCase(gestor, new CreateCaseRequest(activeTemplateId, "cust-1", null));
        caseService.createCase(gestor, new CreateCaseRequest(activeTemplateId, "cust-2", null));

        SupabasePrincipal otherGestor = new SupabasePrincipal("other", "o@o.com", "O", "other-tenant", List.of("gestor"));
        ProcedureTemplate otherTemplate = new ProcedureTemplate();
        otherTemplate.setTenantId("other-tenant");
        otherTemplate.setName("Otro");
        otherTemplate.setStatus(ProcedureService.STATUS_ACTIVE);
        otherTemplate.setCreatedBy("other");
        otherTemplate.setCreatedAt(OffsetDateTime.now());
        otherTemplate.setUpdatedAt(OffsetDateTime.now());
        Long otherTemplateId = templateRepo.save(otherTemplate).getId();
        caseService.createCase(otherGestor, new CreateCaseRequest(otherTemplateId, "cust-3", null));

        List<CaseResponse> result = caseService.listCases(gestor, null, null, null, null);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(c -> c.tenantId().equals(TENANT));
    }

    @Test
    void listCases_filtersByStatus() {
        caseService.createCase(gestor, new CreateCaseRequest(activeTemplateId, "cust-1", null));
        CaseResponse second = caseService.createCase(gestor, new CreateCaseRequest(activeTemplateId, "cust-2", null));
        caseService.changeCaseStatus(gestor, second.id(), new ChangeCaseStatusRequest(CaseService.STATUS_IN_REVIEW));

        List<CaseResponse> received = caseService.listCases(gestor, CaseService.STATUS_RECEIVED, null, null, null);
        List<CaseResponse> inReview = caseService.listCases(gestor, CaseService.STATUS_IN_REVIEW, null, null, null);

        assertThat(received).hasSize(1);
        assertThat(inReview).hasSize(1);
    }

    @Test
    void changeCaseStatus_followsStateMachine() {
        CaseResponse created = caseService.createCase(gestor,
                new CreateCaseRequest(activeTemplateId, "cust", null));
        assertThat(created.status()).isEqualTo(CaseService.STATUS_RECEIVED);

        CaseResponse inReview = caseService.changeCaseStatus(gestor, created.id(),
                new ChangeCaseStatusRequest(CaseService.STATUS_IN_REVIEW));
        assertThat(inReview.status()).isEqualTo(CaseService.STATUS_IN_REVIEW);

        CaseResponse pendingDocs = caseService.changeCaseStatus(gestor, created.id(),
                new ChangeCaseStatusRequest(CaseService.STATUS_PENDING_DOCS));
        assertThat(pendingDocs.status()).isEqualTo(CaseService.STATUS_PENDING_DOCS);

        CaseResponse backToReview = caseService.changeCaseStatus(gestor, created.id(),
                new ChangeCaseStatusRequest(CaseService.STATUS_IN_REVIEW));
        assertThat(backToReview.status()).isEqualTo(CaseService.STATUS_IN_REVIEW);

        CaseResponse approved = caseService.changeCaseStatus(gestor, created.id(),
                new ChangeCaseStatusRequest(CaseService.STATUS_APPROVED));
        assertThat(approved.status()).isEqualTo(CaseService.STATUS_APPROVED);

        assertThatThrownBy(() -> caseService.changeCaseStatus(gestor, created.id(),
                new ChangeCaseStatusRequest(CaseService.STATUS_IN_REVIEW)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid transition");
    }

    @Test
    void assignCase_persistsAssignedTo() {
        CaseResponse created = caseService.createCase(gestor,
                new CreateCaseRequest(activeTemplateId, "cust", null));

        CaseResponse assigned = caseService.assignCase(gestor, created.id(),
                new AssignCaseRequest("gestor-2"));

        assertThat(assigned.assignedTo()).isEqualTo("gestor-2");

        ProcedureCase persisted = caseRepo.findById(created.id()).orElseThrow();
        assertThat(persisted.getAssignedTo()).isEqualTo("gestor-2");
    }

    @Test
    void listCases_filtersByAssignedTo() {
        CaseResponse c1 = caseService.createCase(gestor, new CreateCaseRequest(activeTemplateId, "cust-1", null));
        CaseResponse c2 = caseService.createCase(gestor, new CreateCaseRequest(activeTemplateId, "cust-2", null));

        caseService.assignCase(gestor, c1.id(), new AssignCaseRequest("gestor-2"));

        List<CaseResponse> result = caseService.listCases(gestor, null, null, "gestor-2", null);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(c1.id());
    }

    @Test
    void getCase_throws404_forOtherTenant() {
        CaseResponse created = caseService.createCase(gestor,
                new CreateCaseRequest(activeTemplateId, "cust", null));

        SupabasePrincipal otherGestor = new SupabasePrincipal("other", "o@o.com", "O", "other-tenant", List.of("gestor"));

        assertThatThrownBy(() -> caseService.getCase(otherGestor, created.id()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Case not found");
    }
}
