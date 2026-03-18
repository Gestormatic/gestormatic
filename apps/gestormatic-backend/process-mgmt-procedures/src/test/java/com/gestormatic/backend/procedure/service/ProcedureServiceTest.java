package com.gestormatic.backend.procedure.service;

import com.gestormatic.backend.auth.security.SupabasePrincipal;
import com.gestormatic.backend.procedure.dto.AddMemberRequest;
import com.gestormatic.backend.procedure.dto.CreateProcedureRequest;
import com.gestormatic.backend.procedure.dto.CreateRequiredDocumentRequest;
import com.gestormatic.backend.procedure.dto.CreateStepRequest;
import com.gestormatic.backend.procedure.dto.MemberResponse;
import com.gestormatic.backend.procedure.dto.ProcedureResponse;
import com.gestormatic.backend.procedure.dto.RequiredDocumentResponse;
import com.gestormatic.backend.procedure.dto.StepResponse;
import com.gestormatic.backend.procedure.dto.UpdateProcedureRequest;
import com.gestormatic.backend.procedure.model.ProcedureMember;
import com.gestormatic.backend.procedure.model.ProcedureStep;
import com.gestormatic.backend.procedure.model.ProcedureTemplate;
import com.gestormatic.backend.procedure.model.RequiredDocument;
import com.gestormatic.backend.procedure.repo.ProcedureMemberRepository;
import com.gestormatic.backend.procedure.repo.ProcedureStepRepository;
import com.gestormatic.backend.procedure.repo.ProcedureTemplateRepository;
import com.gestormatic.backend.procedure.repo.RequiredDocumentRepository;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@ExtendWith(MockitoExtension.class)
class ProcedureServiceTest {

    @Mock private ProcedureTemplateRepository templateRepo;
    @Mock private ProcedureStepRepository stepRepo;
    @Mock private RequiredDocumentRepository documentRepo;
    @Mock private ProcedureMemberRepository memberRepo;

    @InjectMocks
    private ProcedureService service;

    private static final String TENANT = "tenant-1";
    private static final String GESTOR_UID = "gestor-uid";
    private static final String OTHER_UID = "other-uid";
    private static final Long TEMPLATE_ID = 1L;
    private static final Long STEP_ID = 10L;

    private SupabasePrincipal gestor;
    private SupabasePrincipal nonGestor;
    private ProcedureTemplate template;
    private ProcedureMember ownerMember;

    @BeforeEach
    void setUp() {
        gestor = new SupabasePrincipal(GESTOR_UID, "gestor@test.com", "Gestor", TENANT, List.of("gestor"));
        nonGestor = new SupabasePrincipal(OTHER_UID, "user@test.com", "User", TENANT, List.of("customer"));

        template = new ProcedureTemplate();
        template.setId(TEMPLATE_ID);
        template.setTenantId(TENANT);
        template.setName("Trámite de alta");
        template.setDescription("Descripción");
        template.setStatus(ProcedureService.STATUS_DRAFT);
        template.setCreatedBy(GESTOR_UID);
        template.setCreatedAt(OffsetDateTime.now());
        template.setUpdatedAt(OffsetDateTime.now());

        ownerMember = new ProcedureMember();
        ownerMember.setId(1L);
        ownerMember.setTemplateId(TEMPLATE_ID);
        ownerMember.setUserId(GESTOR_UID);
        ownerMember.setMemberRole("OWNER");
    }

    // ─── createTemplate ────────────────────────────────────────────────────────

    @Nested
    class CreateTemplate {

        @Test
        void success_createsTemplateWithDraftStatusAndOwnerMember() {
            when(templateRepo.save(any())).thenAnswer(inv -> {
                ProcedureTemplate t = inv.getArgument(0);
                t.setId(TEMPLATE_ID);
                return t;
            });
            when(memberRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ProcedureResponse response = service.createTemplate(gestor, new CreateProcedureRequest("Alta", "Desc"));

            assertThat(response.name()).isEqualTo("Alta");
            assertThat(response.status()).isEqualTo(ProcedureService.STATUS_DRAFT);
            assertThat(response.createdBy()).isEqualTo(GESTOR_UID);
            verify(templateRepo).save(any(ProcedureTemplate.class));
            verify(memberRepo).save(argThat(m -> m.getMemberRole().equals("OWNER") && m.getUserId().equals(GESTOR_UID)));
        }

        @Test
        void forbidden_whenNotGestor() {
            assertThatThrownBy(() -> service.createTemplate(nonGestor, new CreateProcedureRequest("Alta", null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(FORBIDDEN);

            verifyNoInteractions(templateRepo);
        }
    }

    // ─── listTemplates ─────────────────────────────────────────────────────────

    @Nested
    class ListTemplates {

        @Test
        void returnsAllTemplatesForTenant() {
            ProcedureTemplate t2 = new ProcedureTemplate();
            t2.setId(2L);
            t2.setTenantId(TENANT);
            t2.setName("Trámite baja");
            t2.setStatus(ProcedureService.STATUS_ACTIVE);
            t2.setCreatedAt(OffsetDateTime.now());
            t2.setUpdatedAt(OffsetDateTime.now());

            when(templateRepo.findAllByTenantId(TENANT)).thenReturn(List.of(template, t2));

            List<ProcedureResponse> result = service.listTemplates(gestor);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(ProcedureResponse::name)
                .containsExactly("Trámite de alta", "Trámite baja");
        }

        @Test
        void returnsEmptyListWhenNoTemplates() {
            when(templateRepo.findAllByTenantId(TENANT)).thenReturn(List.of());

            assertThat(service.listTemplates(gestor)).isEmpty();
        }
    }

    // ─── getTemplate ───────────────────────────────────────────────────────────

    @Nested
    class GetTemplate {

        @Test
        void success_returnsTemplateWithStepsAndDocuments() {
            ProcedureStep step = buildStep(STEP_ID, TEMPLATE_ID, 1, "Paso 1");
            RequiredDocument doc = buildDoc(20L, STEP_ID, "DNI", true);

            when(templateRepo.findByIdAndTenantId(TEMPLATE_ID, TENANT)).thenReturn(Optional.of(template));
            when(stepRepo.findAllByTemplateId(TEMPLATE_ID)).thenReturn(List.of(step));
            when(documentRepo.findAllByStepId(STEP_ID)).thenReturn(List.of(doc));

            ProcedureResponse response = service.getTemplate(gestor, TEMPLATE_ID);

            assertThat(response.id()).isEqualTo(TEMPLATE_ID);
            assertThat(response.steps()).hasSize(1);
            assertThat(response.steps().get(0).requiredDocuments()).hasSize(1);
            assertThat(response.steps().get(0).requiredDocuments().get(0).name()).isEqualTo("DNI");
        }

        @Test
        void notFound_whenTemplateDoesNotExist() {
            when(templateRepo.findByIdAndTenantId(TEMPLATE_ID, TENANT)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getTemplate(gestor, TEMPLATE_ID))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(NOT_FOUND);
        }

        @Test
        void notFound_whenTemplateBelongsToOtherTenant() {
            SupabasePrincipal otherTenant = new SupabasePrincipal("uid", "x@x.com", "X", "other-tenant", List.of("gestor"));
            when(templateRepo.findByIdAndTenantId(TEMPLATE_ID, "other-tenant")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getTemplate(otherTenant, TEMPLATE_ID))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(NOT_FOUND);
        }
    }

    // ─── updateTemplate ────────────────────────────────────────────────────────

    @Nested
    class UpdateTemplate {

        @Test
        void success_updatesOnlyProvidedFields() {
            when(templateRepo.findByIdAndTenantId(TEMPLATE_ID, TENANT)).thenReturn(Optional.of(template));
            when(memberRepo.findByTemplateIdAndUserId(TEMPLATE_ID, GESTOR_UID)).thenReturn(Optional.of(ownerMember));
            when(templateRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(stepRepo.findAllByTemplateId(TEMPLATE_ID)).thenReturn(List.of());

            ProcedureResponse response = service.updateTemplate(gestor, TEMPLATE_ID,
                new UpdateProcedureRequest("Nuevo nombre", null, null));

            assertThat(response.name()).isEqualTo("Nuevo nombre");
            assertThat(response.description()).isEqualTo("Descripción"); // unchanged
        }

        @Test
        void success_updatesStatus_whenValid() {
            when(templateRepo.findByIdAndTenantId(TEMPLATE_ID, TENANT)).thenReturn(Optional.of(template));
            when(memberRepo.findByTemplateIdAndUserId(TEMPLATE_ID, GESTOR_UID)).thenReturn(Optional.of(ownerMember));
            when(templateRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(stepRepo.findAllByTemplateId(TEMPLATE_ID)).thenReturn(List.of());

            ProcedureResponse response = service.updateTemplate(gestor, TEMPLATE_ID,
                new UpdateProcedureRequest(null, null, "ACTIVE"));

            assertThat(response.status()).isEqualTo("ACTIVE");
        }

        @Test
        void badRequest_whenInvalidStatus() {
            when(templateRepo.findByIdAndTenantId(TEMPLATE_ID, TENANT)).thenReturn(Optional.of(template));
            when(memberRepo.findByTemplateIdAndUserId(TEMPLATE_ID, GESTOR_UID)).thenReturn(Optional.of(ownerMember));

            assertThatThrownBy(() -> service.updateTemplate(gestor, TEMPLATE_ID,
                new UpdateProcedureRequest(null, null, "PUBLICADO")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(BAD_REQUEST);
        }

        @Test
        void forbidden_whenNotOwner() {
            SupabasePrincipal otherGestor = new SupabasePrincipal("other-gestor", "x@x.com", "X", TENANT, List.of("gestor"));
            when(templateRepo.findByIdAndTenantId(TEMPLATE_ID, TENANT)).thenReturn(Optional.of(template));
            when(memberRepo.findByTemplateIdAndUserId(TEMPLATE_ID, "other-gestor")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateTemplate(otherGestor, TEMPLATE_ID,
                new UpdateProcedureRequest("X", null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(FORBIDDEN);
        }

        @Test
        void notFound_whenTemplateDoesNotExist() {
            when(templateRepo.findByIdAndTenantId(TEMPLATE_ID, TENANT)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateTemplate(gestor, TEMPLATE_ID,
                new UpdateProcedureRequest("X", null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(NOT_FOUND);
        }
    }

    // ─── addStep ───────────────────────────────────────────────────────────────

    @Nested
    class AddStep {

        @Test
        void success_addsStepToTemplate() {
            ProcedureStep saved = buildStep(STEP_ID, TEMPLATE_ID, 1, "Paso 1");
            when(templateRepo.findByIdAndTenantId(TEMPLATE_ID, TENANT)).thenReturn(Optional.of(template));
            when(memberRepo.findByTemplateIdAndUserId(TEMPLATE_ID, GESTOR_UID)).thenReturn(Optional.of(ownerMember));
            when(stepRepo.save(any())).thenReturn(saved);

            StepResponse response = service.addStep(gestor, TEMPLATE_ID,
                new CreateStepRequest(1, "Paso 1", "Descripción paso"));

            assertThat(response.name()).isEqualTo("Paso 1");
            assertThat(response.stepOrder()).isEqualTo(1);
            assertThat(response.requiredDocuments()).isEmpty();
        }

        @Test
        void forbidden_whenNotOwner() {
            when(templateRepo.findByIdAndTenantId(TEMPLATE_ID, TENANT)).thenReturn(Optional.of(template));
            when(memberRepo.findByTemplateIdAndUserId(TEMPLATE_ID, GESTOR_UID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.addStep(gestor, TEMPLATE_ID, new CreateStepRequest(1, "Paso", null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(FORBIDDEN);
        }

        @Test
        void notFound_whenTemplateDoesNotExist() {
            when(templateRepo.findByIdAndTenantId(TEMPLATE_ID, TENANT)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.addStep(gestor, TEMPLATE_ID, new CreateStepRequest(1, "Paso", null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(NOT_FOUND);
        }
    }

    // ─── updateStep ────────────────────────────────────────────────────────────

    @Nested
    class UpdateStep {

        @Test
        void success_updatesOnlyProvidedFields() {
            ProcedureStep step = buildStep(STEP_ID, TEMPLATE_ID, 1, "Paso original");
            when(templateRepo.findByIdAndTenantId(TEMPLATE_ID, TENANT)).thenReturn(Optional.of(template));
            when(memberRepo.findByTemplateIdAndUserId(TEMPLATE_ID, GESTOR_UID)).thenReturn(Optional.of(ownerMember));
            when(stepRepo.findById(STEP_ID)).thenReturn(Optional.of(step));
            when(stepRepo.save(any())).thenReturn(step);
            when(documentRepo.findAllByStepId(STEP_ID)).thenReturn(List.of());

            StepResponse response = service.updateStep(gestor, TEMPLATE_ID, STEP_ID,
                new CreateStepRequest(null, "Paso renombrado", null));

            assertThat(response.name()).isEqualTo("Paso renombrado");
            assertThat(response.stepOrder()).isEqualTo(1); // unchanged
        }

        @Test
        void notFound_whenStepDoesNotExist() {
            when(templateRepo.findByIdAndTenantId(TEMPLATE_ID, TENANT)).thenReturn(Optional.of(template));
            when(memberRepo.findByTemplateIdAndUserId(TEMPLATE_ID, GESTOR_UID)).thenReturn(Optional.of(ownerMember));
            when(stepRepo.findById(STEP_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateStep(gestor, TEMPLATE_ID, STEP_ID,
                new CreateStepRequest(null, "X", null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(NOT_FOUND);
        }

        @Test
        void notFound_whenStepBelongsToDifferentTemplate() {
            ProcedureStep step = buildStep(STEP_ID, 99L, 1, "Paso ajeno");
            when(templateRepo.findByIdAndTenantId(TEMPLATE_ID, TENANT)).thenReturn(Optional.of(template));
            when(memberRepo.findByTemplateIdAndUserId(TEMPLATE_ID, GESTOR_UID)).thenReturn(Optional.of(ownerMember));
            when(stepRepo.findById(STEP_ID)).thenReturn(Optional.of(step));

            assertThatThrownBy(() -> service.updateStep(gestor, TEMPLATE_ID, STEP_ID,
                new CreateStepRequest(null, "X", null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(NOT_FOUND);
        }
    }

    // ─── addRequiredDocument ───────────────────────────────────────────────────

    @Nested
    class AddRequiredDocument {

        @Test
        void success_addsMandatoryDocument() {
            ProcedureStep step = buildStep(STEP_ID, TEMPLATE_ID, 1, "Paso 1");
            RequiredDocument saved = buildDoc(20L, STEP_ID, "DNI", true);

            when(templateRepo.findByIdAndTenantId(TEMPLATE_ID, TENANT)).thenReturn(Optional.of(template));
            when(memberRepo.findByTemplateIdAndUserId(TEMPLATE_ID, GESTOR_UID)).thenReturn(Optional.of(ownerMember));
            when(stepRepo.findById(STEP_ID)).thenReturn(Optional.of(step));
            when(documentRepo.save(any())).thenReturn(saved);

            RequiredDocumentResponse response = service.addRequiredDocument(gestor, TEMPLATE_ID, STEP_ID,
                new CreateRequiredDocumentRequest("DNI", "Documento de identidad", true));

            assertThat(response.name()).isEqualTo("DNI");
            assertThat(response.mandatory()).isTrue();
        }

        @Test
        void success_addsOptionalDocument() {
            ProcedureStep step = buildStep(STEP_ID, TEMPLATE_ID, 1, "Paso 1");
            RequiredDocument saved = buildDoc(21L, STEP_ID, "Foto", false);

            when(templateRepo.findByIdAndTenantId(TEMPLATE_ID, TENANT)).thenReturn(Optional.of(template));
            when(memberRepo.findByTemplateIdAndUserId(TEMPLATE_ID, GESTOR_UID)).thenReturn(Optional.of(ownerMember));
            when(stepRepo.findById(STEP_ID)).thenReturn(Optional.of(step));
            when(documentRepo.save(any())).thenReturn(saved);

            RequiredDocumentResponse response = service.addRequiredDocument(gestor, TEMPLATE_ID, STEP_ID,
                new CreateRequiredDocumentRequest("Foto", null, false));

            assertThat(response.mandatory()).isFalse();
        }

        @Test
        void notFound_whenStepBelongsToDifferentTemplate() {
            ProcedureStep step = buildStep(STEP_ID, 99L, 1, "Paso ajeno");
            when(templateRepo.findByIdAndTenantId(TEMPLATE_ID, TENANT)).thenReturn(Optional.of(template));
            when(memberRepo.findByTemplateIdAndUserId(TEMPLATE_ID, GESTOR_UID)).thenReturn(Optional.of(ownerMember));
            when(stepRepo.findById(STEP_ID)).thenReturn(Optional.of(step));

            assertThatThrownBy(() -> service.addRequiredDocument(gestor, TEMPLATE_ID, STEP_ID,
                new CreateRequiredDocumentRequest("DNI", null, true)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(NOT_FOUND);
        }
    }

    // ─── addMember ─────────────────────────────────────────────────────────────

    @Nested
    class AddMember {

        @Test
        void success_addsOwnerMember() {
            ProcedureMember saved = new ProcedureMember();
            saved.setId(5L);
            saved.setTemplateId(TEMPLATE_ID);
            saved.setUserId("new-owner");
            saved.setMemberRole("OWNER");

            when(templateRepo.findByIdAndTenantId(TEMPLATE_ID, TENANT)).thenReturn(Optional.of(template));
            when(memberRepo.findByTemplateIdAndUserId(TEMPLATE_ID, GESTOR_UID)).thenReturn(Optional.of(ownerMember));
            when(memberRepo.findByTemplateIdAndUserId(TEMPLATE_ID, "new-owner")).thenReturn(Optional.empty());
            when(memberRepo.save(any())).thenReturn(saved);

            MemberResponse response = service.addMember(gestor, TEMPLATE_ID,
                new AddMemberRequest("new-owner", "OWNER"));

            assertThat(response.memberRole()).isEqualTo("OWNER");
            assertThat(response.userId()).isEqualTo("new-owner");
        }

        @Test
        void success_addsCustomerMember() {
            ProcedureMember saved = new ProcedureMember();
            saved.setId(6L);
            saved.setTemplateId(TEMPLATE_ID);
            saved.setUserId("customer-uid");
            saved.setMemberRole("CUSTOMER");

            when(templateRepo.findByIdAndTenantId(TEMPLATE_ID, TENANT)).thenReturn(Optional.of(template));
            when(memberRepo.findByTemplateIdAndUserId(TEMPLATE_ID, GESTOR_UID)).thenReturn(Optional.of(ownerMember));
            when(memberRepo.findByTemplateIdAndUserId(TEMPLATE_ID, "customer-uid")).thenReturn(Optional.empty());
            when(memberRepo.save(any())).thenReturn(saved);

            MemberResponse response = service.addMember(gestor, TEMPLATE_ID,
                new AddMemberRequest("customer-uid", "customer")); // lowercase → normalize

            assertThat(response.memberRole()).isEqualTo("CUSTOMER");
        }

        @Test
        void conflict_whenUserAlreadyMember() {
            ProcedureMember existing = new ProcedureMember();
            existing.setUserId(OTHER_UID);
            existing.setMemberRole("CUSTOMER");

            when(templateRepo.findByIdAndTenantId(TEMPLATE_ID, TENANT)).thenReturn(Optional.of(template));
            when(memberRepo.findByTemplateIdAndUserId(TEMPLATE_ID, GESTOR_UID)).thenReturn(Optional.of(ownerMember));
            when(memberRepo.findByTemplateIdAndUserId(TEMPLATE_ID, OTHER_UID)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> service.addMember(gestor, TEMPLATE_ID,
                new AddMemberRequest(OTHER_UID, "CUSTOMER")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(CONFLICT);
        }

        @Test
        void badRequest_whenInvalidRole() {
            when(templateRepo.findByIdAndTenantId(TEMPLATE_ID, TENANT)).thenReturn(Optional.of(template));
            when(memberRepo.findByTemplateIdAndUserId(TEMPLATE_ID, GESTOR_UID)).thenReturn(Optional.of(ownerMember));

            assertThatThrownBy(() -> service.addMember(gestor, TEMPLATE_ID,
                new AddMemberRequest(OTHER_UID, "ADMIN")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(BAD_REQUEST);
        }

        @Test
        void forbidden_whenNotOwner() {
            when(templateRepo.findByIdAndTenantId(TEMPLATE_ID, TENANT)).thenReturn(Optional.of(template));
            when(memberRepo.findByTemplateIdAndUserId(TEMPLATE_ID, GESTOR_UID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.addMember(gestor, TEMPLATE_ID,
                new AddMemberRequest(OTHER_UID, "CUSTOMER")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(FORBIDDEN);
        }
    }

    // ─── removeMember ──────────────────────────────────────────────────────────

    @Nested
    class RemoveMember {

        @Test
        void success_removesOtherMember() {
            when(templateRepo.findByIdAndTenantId(TEMPLATE_ID, TENANT)).thenReturn(Optional.of(template));
            when(memberRepo.findByTemplateIdAndUserId(TEMPLATE_ID, GESTOR_UID)).thenReturn(Optional.of(ownerMember));

            service.removeMember(gestor, TEMPLATE_ID, OTHER_UID);

            verify(memberRepo).deleteByTemplateIdAndUserId(TEMPLATE_ID, OTHER_UID);
        }

        @Test
        void badRequest_whenOwnerTriesToRemoveThemself() {
            when(templateRepo.findByIdAndTenantId(TEMPLATE_ID, TENANT)).thenReturn(Optional.of(template));
            when(memberRepo.findByTemplateIdAndUserId(TEMPLATE_ID, GESTOR_UID)).thenReturn(Optional.of(ownerMember));

            assertThatThrownBy(() -> service.removeMember(gestor, TEMPLATE_ID, GESTOR_UID))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(BAD_REQUEST);

            verify(memberRepo, never()).deleteByTemplateIdAndUserId(any(), any());
        }

        @Test
        void forbidden_whenNotOwner() {
            when(templateRepo.findByIdAndTenantId(TEMPLATE_ID, TENANT)).thenReturn(Optional.of(template));
            when(memberRepo.findByTemplateIdAndUserId(TEMPLATE_ID, GESTOR_UID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.removeMember(gestor, TEMPLATE_ID, OTHER_UID))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(FORBIDDEN);
        }
    }

    // ─── listMembers ───────────────────────────────────────────────────────────

    @Nested
    class ListMembers {

        @Test
        void success_returnsAllMembersOfTemplate() {
            ProcedureMember customer = new ProcedureMember();
            customer.setId(2L);
            customer.setTemplateId(TEMPLATE_ID);
            customer.setUserId(OTHER_UID);
            customer.setMemberRole("CUSTOMER");

            when(templateRepo.findByIdAndTenantId(TEMPLATE_ID, TENANT)).thenReturn(Optional.of(template));
            when(memberRepo.findAllByTemplateId(TEMPLATE_ID)).thenReturn(List.of(ownerMember, customer));

            List<MemberResponse> result = service.listMembers(gestor, TEMPLATE_ID);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(MemberResponse::memberRole)
                .containsExactlyInAnyOrder("OWNER", "CUSTOMER");
        }

        @Test
        void notFound_whenTemplateDoesNotExist() {
            when(templateRepo.findByIdAndTenantId(TEMPLATE_ID, TENANT)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.listMembers(gestor, TEMPLATE_ID))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(NOT_FOUND);
        }
    }

    // ─── helpers ───────────────────────────────────────────────────────────────

    private ProcedureStep buildStep(Long id, Long templateId, int order, String name) {
        ProcedureStep step = new ProcedureStep();
        step.setId(id);
        step.setTemplateId(templateId);
        step.setStepOrder(order);
        step.setName(name);
        return step;
    }

    private RequiredDocument buildDoc(Long id, Long stepId, String name, boolean mandatory) {
        RequiredDocument doc = new RequiredDocument();
        doc.setId(id);
        doc.setStepId(stepId);
        doc.setName(name);
        doc.setMandatory(mandatory);
        return doc;
    }
}
