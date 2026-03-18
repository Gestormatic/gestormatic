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
import com.gestormatic.backend.procedure.repo.ProcedureMemberRepository;
import com.gestormatic.backend.procedure.repo.ProcedureStepRepository;
import com.gestormatic.backend.procedure.repo.ProcedureTemplateRepository;
import com.gestormatic.backend.procedure.repo.RequiredDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class ProcedureIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Autowired ProcedureService service;
    @Autowired ProcedureTemplateRepository templateRepo;
    @Autowired ProcedureMemberRepository memberRepo;
    @Autowired ProcedureStepRepository stepRepo;
    @Autowired RequiredDocumentRepository documentRepo;

    private static final String TENANT = "tenant-test";
    private static final String GESTOR_UID = "gestor-test-uid";

    private SupabasePrincipal gestor;

    @BeforeEach
    void cleanUp() {
        // Orden de borrado respetando FK: docs → steps → members → templates
        documentRepo.deleteAll();
        stepRepo.deleteAll();
        memberRepo.deleteAll();
        templateRepo.deleteAll();
        gestor = new SupabasePrincipal(GESTOR_UID, "gestor@integ.com", "Gestor", TENANT, List.of("gestor"));
    }

    // ─── createTemplate ────────────────────────────────────────────────────────

    @Nested
    class CreateTemplate {

        @Test
        void persistsTemplateWithDraftStatusAndOwnerMember() {
            ProcedureResponse response = service.createTemplate(gestor,
                new CreateProcedureRequest("Licencia de obras", "Descripción"));

            assertThat(response.id()).isNotNull();
            assertThat(response.status()).isEqualTo("DRAFT");
            assertThat(response.createdBy()).isEqualTo(GESTOR_UID);

            assertThat(templateRepo.findByIdAndTenantId(response.id(), TENANT)).isPresent();
            assertThat(memberRepo.findByTemplateIdAndUserId(response.id(), GESTOR_UID))
                .isPresent()
                .get()
                .extracting(m -> m.getMemberRole())
                .isEqualTo("OWNER");
        }

        @Test
        void forbidden_whenNotGestor() {
            SupabasePrincipal customer = new SupabasePrincipal("c-uid", "c@c.com", "C", TENANT, List.of("customer"));

            assertThatThrownBy(() -> service.createTemplate(customer, new CreateProcedureRequest("X", null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(FORBIDDEN);

            assertThat(templateRepo.findAllByTenantId(TENANT)).isEmpty();
        }
    }

    // ─── listTemplates ─────────────────────────────────────────────────────────

    @Nested
    class ListTemplates {

        @Test
        void returnsOnlyTemplatesOfCurrentTenant() {
            service.createTemplate(gestor, new CreateProcedureRequest("Trámite A", null));
            service.createTemplate(gestor, new CreateProcedureRequest("Trámite B", null));

            SupabasePrincipal otherTenant = new SupabasePrincipal("uid2", "x@x.com", "X", "otro-tenant", List.of("gestor"));
            service.createTemplate(otherTenant, new CreateProcedureRequest("Trámite otro tenant", null));

            List<ProcedureResponse> result = service.listTemplates(gestor);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(ProcedureResponse::name)
                .containsExactlyInAnyOrder("Trámite A", "Trámite B");
        }

        @Test
        void returnsEmptyListWhenNoTemplates() {
            assertThat(service.listTemplates(gestor)).isEmpty();
        }
    }

    // ─── getTemplate ───────────────────────────────────────────────────────────

    @Nested
    class GetTemplate {

        @Test
        void returnsFullHierarchy_stepsAndDocuments() {
            ProcedureResponse created = service.createTemplate(gestor, new CreateProcedureRequest("Trámite full", null));
            StepResponse step = service.addStep(gestor, created.id(), new CreateStepRequest(1, "Paso 1", null));
            service.addRequiredDocument(gestor, created.id(), step.id(), new CreateRequiredDocumentRequest("DNI", null, true));

            ProcedureResponse response = service.getTemplate(gestor, created.id());

            assertThat(response.steps()).hasSize(1);
            assertThat(response.steps().get(0).name()).isEqualTo("Paso 1");
            assertThat(response.steps().get(0).requiredDocuments()).hasSize(1);
            assertThat(response.steps().get(0).requiredDocuments().get(0).name()).isEqualTo("DNI");
            assertThat(response.steps().get(0).requiredDocuments().get(0).mandatory()).isTrue();
        }

        @Test
        void notFound_forOtherTenantTemplate() {
            ProcedureResponse created = service.createTemplate(gestor, new CreateProcedureRequest("Trámite privado", null));
            SupabasePrincipal other = new SupabasePrincipal("uid2", "x@x.com", "X", "otro-tenant", List.of("gestor"));

            assertThatThrownBy(() -> service.getTemplate(other, created.id()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(NOT_FOUND);
        }
    }

    // ─── updateTemplate ────────────────────────────────────────────────────────

    @Nested
    class UpdateTemplate {

        @Test
        void persistsNameAndDescriptionChanges() {
            ProcedureResponse created = service.createTemplate(gestor, new CreateProcedureRequest("Original", "Desc original"));

            service.updateTemplate(gestor, created.id(), new UpdateProcedureRequest("Nuevo nombre", "Nueva desc", null));

            ProcedureResponse updated = service.getTemplate(gestor, created.id());
            assertThat(updated.name()).isEqualTo("Nuevo nombre");
            assertThat(updated.description()).isEqualTo("Nueva desc");
        }

        @Test
        void patchBehavior_doesNotOverwriteFieldsWithNull() {
            ProcedureResponse created = service.createTemplate(gestor, new CreateProcedureRequest("Original", "Desc"));

            service.updateTemplate(gestor, created.id(), new UpdateProcedureRequest("Solo nombre", null, null));

            ProcedureResponse updated = service.getTemplate(gestor, created.id());
            assertThat(updated.name()).isEqualTo("Solo nombre");
            assertThat(updated.description()).isEqualTo("Desc"); // no sobreescrito
        }
    }

    // ─── addStep + updateStep ──────────────────────────────────────────────────

    @Nested
    class Steps {

        @Test
        void addStepPersistsAndAppearsInGetTemplate() {
            ProcedureResponse created = service.createTemplate(gestor, new CreateProcedureRequest("Con pasos", null));

            service.addStep(gestor, created.id(), new CreateStepRequest(1, "Solicitud", "Presentar solicitud"));
            service.addStep(gestor, created.id(), new CreateStepRequest(2, "Revisión", null));

            ProcedureResponse response = service.getTemplate(gestor, created.id());
            assertThat(response.steps()).hasSize(2);
            assertThat(response.steps()).extracting(StepResponse::stepOrder).containsExactly(1, 2);
        }

        @Test
        void updateStepPersistsChanges() {
            ProcedureResponse created = service.createTemplate(gestor, new CreateProcedureRequest("Trámite", null));
            StepResponse step = service.addStep(gestor, created.id(), new CreateStepRequest(1, "Nombre original", null));

            service.updateStep(gestor, created.id(), step.id(), new CreateStepRequest(null, "Nombre actualizado", "Nueva desc"));

            ProcedureResponse response = service.getTemplate(gestor, created.id());
            assertThat(response.steps().get(0).name()).isEqualTo("Nombre actualizado");
            assertThat(response.steps().get(0).description()).isEqualTo("Nueva desc");
            assertThat(response.steps().get(0).stepOrder()).isEqualTo(1); // unchanged
        }
    }

    // ─── addRequiredDocument ───────────────────────────────────────────────────

    @Nested
    class RequiredDocuments {

        @Test
        void addDocumentPersistsAndAppearsInStep() {
            ProcedureResponse created = service.createTemplate(gestor, new CreateProcedureRequest("Con docs", null));
            StepResponse step = service.addStep(gestor, created.id(), new CreateStepRequest(1, "Paso 1", null));

            service.addRequiredDocument(gestor, created.id(), step.id(),
                new CreateRequiredDocumentRequest("Pasaporte", "Pasaporte en vigor", true));
            service.addRequiredDocument(gestor, created.id(), step.id(),
                new CreateRequiredDocumentRequest("Foto", null, false));

            ProcedureResponse response = service.getTemplate(gestor, created.id());
            List<RequiredDocumentResponse> docs = response.steps().get(0).requiredDocuments();

            assertThat(docs).hasSize(2);
            assertThat(docs).extracting(RequiredDocumentResponse::name)
                .containsExactlyInAnyOrder("Pasaporte", "Foto");
            assertThat(docs).filteredOn(d -> d.name().equals("Pasaporte"))
                .first()
                .extracting(RequiredDocumentResponse::mandatory)
                .isEqualTo(true);
        }
    }

    // ─── members ───────────────────────────────────────────────────────────────

    @Nested
    class Members {

        @Test
        void addMember_persistsAndAppearsInList() {
            ProcedureResponse created = service.createTemplate(gestor, new CreateProcedureRequest("Con miembros", null));

            service.addMember(gestor, created.id(), new AddMemberRequest("customer-uid", "CUSTOMER"));

            List<MemberResponse> members = service.listMembers(gestor, created.id());
            assertThat(members).hasSize(2); // OWNER (creador) + CUSTOMER
            assertThat(members).extracting(MemberResponse::memberRole)
                .containsExactlyInAnyOrder("OWNER", "CUSTOMER");
        }

        @Test
        void removeMember_removesFromPersistence() {
            ProcedureResponse created = service.createTemplate(gestor, new CreateProcedureRequest("Miembros", null));
            service.addMember(gestor, created.id(), new AddMemberRequest("to-remove", "CUSTOMER"));

            service.removeMember(gestor, created.id(), "to-remove");

            List<MemberResponse> members = service.listMembers(gestor, created.id());
            assertThat(members).hasSize(1);
            assertThat(members.get(0).memberRole()).isEqualTo("OWNER");
        }

        @Test
        void conflict_whenAddingSameMemberTwice() {
            ProcedureResponse created = service.createTemplate(gestor, new CreateProcedureRequest("Miembros", null));
            service.addMember(gestor, created.id(), new AddMemberRequest("dup-uid", "CUSTOMER"));

            assertThatThrownBy(() -> service.addMember(gestor, created.id(), new AddMemberRequest("dup-uid", "CUSTOMER")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(CONFLICT);
        }

        @Test
        void badRequest_whenOwnerTriesToRemoveThemself() {
            ProcedureResponse created = service.createTemplate(gestor, new CreateProcedureRequest("Miembros", null));

            assertThatThrownBy(() -> service.removeMember(gestor, created.id(), GESTOR_UID))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(BAD_REQUEST);
        }
    }
}
