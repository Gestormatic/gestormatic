package com.gestormatic.backend.procedure.service;

import com.gestormatic.backend.auth.security.SupabasePrincipal;
import com.gestormatic.backend.procedure.dto.AssignCaseRequest;
import com.gestormatic.backend.procedure.dto.CaseResponse;
import com.gestormatic.backend.procedure.dto.ChangeCaseStatusRequest;
import com.gestormatic.backend.procedure.dto.CreateCaseRequest;
import com.gestormatic.backend.procedure.model.ProcedureCase;
import com.gestormatic.backend.procedure.model.ProcedureTemplate;
import com.gestormatic.backend.procedure.repo.CaseQueryRepository;
import com.gestormatic.backend.procedure.repo.CaseRepository;
import com.gestormatic.backend.procedure.repo.ProcedureTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaseServiceTest {

    @Mock CaseRepository caseRepo;
    @Mock CaseQueryRepository caseQueryRepo;
    @Mock ProcedureTemplateRepository templateRepo;

    @InjectMocks CaseService service;

    private static final String TENANT = "tenant-1";
    private static final String GESTOR_UID = "gestor-uid";
    private static final Long TEMPLATE_ID = 1L;
    private static final Long CASE_ID = 10L;

    private SupabasePrincipal gestor;
    private ProcedureTemplate activeTemplate;

    @BeforeEach
    void setUp() {
        gestor = new SupabasePrincipal(GESTOR_UID, "g@g.com", "Gestor", TENANT, List.of("gestor"));

        activeTemplate = new ProcedureTemplate();
        activeTemplate.setId(TEMPLATE_ID);
        activeTemplate.setTenantId(TENANT);
        activeTemplate.setName("Trámite Test");
        activeTemplate.setStatus(ProcedureService.STATUS_ACTIVE);
        activeTemplate.setCreatedBy(GESTOR_UID);
        activeTemplate.setCreatedAt(OffsetDateTime.now());
        activeTemplate.setUpdatedAt(OffsetDateTime.now());
    }

    private ProcedureCase savedCase(String status) {
        ProcedureCase c = new ProcedureCase();
        c.setId(CASE_ID);
        c.setTenantId(TENANT);
        c.setTemplateId(TEMPLATE_ID);
        c.setCustomerId("customer-uid");
        c.setStatus(status);
        c.setCreatedBy(GESTOR_UID);
        c.setCreatedAt(OffsetDateTime.now());
        c.setUpdatedAt(OffsetDateTime.now());
        return c;
    }

    // ─── createCase ──────────────────────────────────────────────────────────

    @Nested
    class CreateCase {

        @Test
        void createsCase_whenTemplateIsActive() {
            when(templateRepo.findByIdAndTenantId(TEMPLATE_ID, TENANT)).thenReturn(Optional.of(activeTemplate));
            when(caseRepo.save(any())).thenReturn(savedCase(CaseService.STATUS_RECEIVED));

            CaseResponse response = service.createCase(gestor, new CreateCaseRequest(TEMPLATE_ID, "customer-uid", null));

            assertThat(response.status()).isEqualTo(CaseService.STATUS_RECEIVED);
            assertThat(response.templateName()).isEqualTo("Trámite Test");
            verify(caseRepo).save(any());
        }

        @Test
        void throws400_whenTemplateIdIsNull() {
            assertThatThrownBy(() -> service.createCase(gestor, new CreateCaseRequest(null, "cust", null)))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("templateId is required");
        }

        @Test
        void throws400_whenCustomerIdIsBlank() {
            assertThatThrownBy(() -> service.createCase(gestor, new CreateCaseRequest(TEMPLATE_ID, "", null)))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("customerId is required");
        }

        @Test
        void throws404_whenTemplateNotFound() {
            when(templateRepo.findByIdAndTenantId(TEMPLATE_ID, TENANT)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createCase(gestor, new CreateCaseRequest(TEMPLATE_ID, "cust", null)))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Template not found");
        }

        @Test
        void throws400_whenTemplateNotActive() {
            activeTemplate.setStatus(ProcedureService.STATUS_DRAFT);
            when(templateRepo.findByIdAndTenantId(TEMPLATE_ID, TENANT)).thenReturn(Optional.of(activeTemplate));

            assertThatThrownBy(() -> service.createCase(gestor, new CreateCaseRequest(TEMPLATE_ID, "cust", null)))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("non-active template");
        }

        @Test
        void throws403_whenNotGestor() {
            SupabasePrincipal customer = new SupabasePrincipal("uid", "c@c.com", "C", TENANT, List.of("customer"));

            assertThatThrownBy(() -> service.createCase(customer, new CreateCaseRequest(TEMPLATE_ID, "cust", null)))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Only gestores");
        }
    }

    // ─── listCases ───────────────────────────────────────────────────────────

    @Nested
    class ListCases {

        @Test
        void delegatesToQueryRepository_withFilters() {
            CaseResponse dto = new CaseResponse(CASE_ID, TENANT, TEMPLATE_ID, "T", "cust", null,
                    CaseService.STATUS_RECEIVED, null, GESTOR_UID, OffsetDateTime.now(), OffsetDateTime.now());
            when(caseQueryRepo.findByFilters(TENANT, "RECEIVED", null, null, null))
                    .thenReturn(List.of(dto));

            List<CaseResponse> result = service.listCases(gestor, "RECEIVED", null, null, null);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).status()).isEqualTo(CaseService.STATUS_RECEIVED);
        }

        @Test
        void throws403_whenNotGestor() {
            SupabasePrincipal customer = new SupabasePrincipal("uid", "c@c.com", "C", TENANT, List.of("customer"));

            assertThatThrownBy(() -> service.listCases(customer, null, null, null, null))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Only gestores");
        }
    }

    // ─── getCase ─────────────────────────────────────────────────────────────

    @Nested
    class GetCase {

        @Test
        void returnsCase_whenExists() {
            when(caseRepo.findByIdAndTenantId(CASE_ID, TENANT)).thenReturn(Optional.of(savedCase(CaseService.STATUS_RECEIVED)));
            when(templateRepo.findById(TEMPLATE_ID)).thenReturn(Optional.of(activeTemplate));

            CaseResponse response = service.getCase(gestor, CASE_ID);

            assertThat(response.id()).isEqualTo(CASE_ID);
            assertThat(response.templateName()).isEqualTo("Trámite Test");
        }

        @Test
        void throws404_whenNotFound() {
            when(caseRepo.findByIdAndTenantId(CASE_ID, TENANT)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getCase(gestor, CASE_ID))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Case not found");
        }
    }

    // ─── changeCaseStatus ────────────────────────────────────────────────────

    @Nested
    class ChangeCaseStatus {

        @Test
        void transitionsFromReceivedToInReview() {
            ProcedureCase c = savedCase(CaseService.STATUS_RECEIVED);
            when(caseRepo.findByIdAndTenantId(CASE_ID, TENANT)).thenReturn(Optional.of(c));
            when(caseRepo.save(any())).thenReturn(c);
            when(templateRepo.findById(TEMPLATE_ID)).thenReturn(Optional.of(activeTemplate));

            CaseResponse response = service.changeCaseStatus(gestor, CASE_ID,
                    new ChangeCaseStatusRequest(CaseService.STATUS_IN_REVIEW));

            assertThat(response.status()).isEqualTo(CaseService.STATUS_IN_REVIEW);
        }

        @Test
        void transitionsFromInReviewToApproved() {
            ProcedureCase c = savedCase(CaseService.STATUS_IN_REVIEW);
            when(caseRepo.findByIdAndTenantId(CASE_ID, TENANT)).thenReturn(Optional.of(c));
            when(caseRepo.save(any())).thenReturn(c);
            when(templateRepo.findById(TEMPLATE_ID)).thenReturn(Optional.of(activeTemplate));

            CaseResponse response = service.changeCaseStatus(gestor, CASE_ID,
                    new ChangeCaseStatusRequest(CaseService.STATUS_APPROVED));

            assertThat(response.status()).isEqualTo(CaseService.STATUS_APPROVED);
        }

        @Test
        void transitionsFromInReviewToRejected() {
            ProcedureCase c = savedCase(CaseService.STATUS_IN_REVIEW);
            when(caseRepo.findByIdAndTenantId(CASE_ID, TENANT)).thenReturn(Optional.of(c));
            when(caseRepo.save(any())).thenReturn(c);
            when(templateRepo.findById(TEMPLATE_ID)).thenReturn(Optional.of(activeTemplate));

            CaseResponse response = service.changeCaseStatus(gestor, CASE_ID,
                    new ChangeCaseStatusRequest(CaseService.STATUS_REJECTED));

            assertThat(response.status()).isEqualTo(CaseService.STATUS_REJECTED);
        }

        @Test
        void transitionsFromInReviewToPendingDocs() {
            ProcedureCase c = savedCase(CaseService.STATUS_IN_REVIEW);
            when(caseRepo.findByIdAndTenantId(CASE_ID, TENANT)).thenReturn(Optional.of(c));
            when(caseRepo.save(any())).thenReturn(c);
            when(templateRepo.findById(TEMPLATE_ID)).thenReturn(Optional.of(activeTemplate));

            CaseResponse response = service.changeCaseStatus(gestor, CASE_ID,
                    new ChangeCaseStatusRequest(CaseService.STATUS_PENDING_DOCS));

            assertThat(response.status()).isEqualTo(CaseService.STATUS_PENDING_DOCS);
        }

        @Test
        void transitionsFromPendingDocsToInReview() {
            ProcedureCase c = savedCase(CaseService.STATUS_PENDING_DOCS);
            when(caseRepo.findByIdAndTenantId(CASE_ID, TENANT)).thenReturn(Optional.of(c));
            when(caseRepo.save(any())).thenReturn(c);
            when(templateRepo.findById(TEMPLATE_ID)).thenReturn(Optional.of(activeTemplate));

            CaseResponse response = service.changeCaseStatus(gestor, CASE_ID,
                    new ChangeCaseStatusRequest(CaseService.STATUS_IN_REVIEW));

            assertThat(response.status()).isEqualTo(CaseService.STATUS_IN_REVIEW);
        }

        @Test
        void throws400_whenInvalidTransition_fromApproved() {
            ProcedureCase c = savedCase(CaseService.STATUS_APPROVED);
            when(caseRepo.findByIdAndTenantId(CASE_ID, TENANT)).thenReturn(Optional.of(c));

            assertThatThrownBy(() -> service.changeCaseStatus(gestor, CASE_ID,
                    new ChangeCaseStatusRequest(CaseService.STATUS_IN_REVIEW)))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Invalid transition");
        }

        @Test
        void throws400_whenInvalidTransition_fromRejected() {
            ProcedureCase c = savedCase(CaseService.STATUS_REJECTED);
            when(caseRepo.findByIdAndTenantId(CASE_ID, TENANT)).thenReturn(Optional.of(c));

            assertThatThrownBy(() -> service.changeCaseStatus(gestor, CASE_ID,
                    new ChangeCaseStatusRequest(CaseService.STATUS_IN_REVIEW)))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Invalid transition");
        }

        @Test
        void throws400_whenInvalidTransition_fromReceivedToApproved() {
            ProcedureCase c = savedCase(CaseService.STATUS_RECEIVED);
            when(caseRepo.findByIdAndTenantId(CASE_ID, TENANT)).thenReturn(Optional.of(c));

            assertThatThrownBy(() -> service.changeCaseStatus(gestor, CASE_ID,
                    new ChangeCaseStatusRequest(CaseService.STATUS_APPROVED)))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Invalid transition");
        }

        @Test
        void throws400_whenStatusIsNull() {
            ProcedureCase c = savedCase(CaseService.STATUS_RECEIVED);
            when(caseRepo.findByIdAndTenantId(CASE_ID, TENANT)).thenReturn(Optional.of(c));

            assertThatThrownBy(() -> service.changeCaseStatus(gestor, CASE_ID,
                    new ChangeCaseStatusRequest(null)))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("status is required");
        }

        @Test
        void statusIsCaseInsensitive() {
            ProcedureCase c = savedCase(CaseService.STATUS_RECEIVED);
            when(caseRepo.findByIdAndTenantId(CASE_ID, TENANT)).thenReturn(Optional.of(c));
            when(caseRepo.save(any())).thenReturn(c);
            when(templateRepo.findById(TEMPLATE_ID)).thenReturn(Optional.of(activeTemplate));

            CaseResponse response = service.changeCaseStatus(gestor, CASE_ID,
                    new ChangeCaseStatusRequest("in_review"));

            assertThat(response.status()).isEqualTo(CaseService.STATUS_IN_REVIEW);
        }
    }

    // ─── assignCase ──────────────────────────────────────────────────────────

    @Nested
    class AssignCase {

        @Test
        void assignsCase_whenValid() {
            ProcedureCase c = savedCase(CaseService.STATUS_IN_REVIEW);
            when(caseRepo.findByIdAndTenantId(CASE_ID, TENANT)).thenReturn(Optional.of(c));
            when(caseRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(templateRepo.findById(TEMPLATE_ID)).thenReturn(Optional.of(activeTemplate));

            CaseResponse response = service.assignCase(gestor, CASE_ID, new AssignCaseRequest("gestor-2"));

            assertThat(response.assignedTo()).isEqualTo("gestor-2");
        }

        @Test
        void throws400_whenAssignedToIsBlank() {
            ProcedureCase c = savedCase(CaseService.STATUS_IN_REVIEW);
            when(caseRepo.findByIdAndTenantId(CASE_ID, TENANT)).thenReturn(Optional.of(c));

            assertThatThrownBy(() -> service.assignCase(gestor, CASE_ID, new AssignCaseRequest("")))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("assignedTo is required");
        }

        @Test
        void throws404_whenCaseNotFound() {
            when(caseRepo.findByIdAndTenantId(CASE_ID, TENANT)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.assignCase(gestor, CASE_ID, new AssignCaseRequest("uid")))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Case not found");
        }
    }
}
