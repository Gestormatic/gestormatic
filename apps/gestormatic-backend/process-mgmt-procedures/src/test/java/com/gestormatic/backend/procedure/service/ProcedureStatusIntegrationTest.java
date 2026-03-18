package com.gestormatic.backend.procedure.service;

import com.gestormatic.backend.auth.security.SupabasePrincipal;
import com.gestormatic.backend.procedure.dto.ChangeStatusRequest;
import com.gestormatic.backend.procedure.dto.CreateProcedureRequest;
import com.gestormatic.backend.procedure.dto.ProcedureResponse;
import com.gestormatic.backend.procedure.repo.ProcedureTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class ProcedureStatusIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Autowired
    ProcedureService service;

    @Autowired
    ProcedureTemplateRepository templateRepo;

    private static final String TENANT = "tenant-integ";
    private static final String GESTOR_UID = "gestor-integ";

    private SupabasePrincipal gestor;

    @BeforeEach
    void setUp() {
        templateRepo.deleteAll();
        gestor = new SupabasePrincipal(GESTOR_UID, "gestor@integ.com", "Gestor", TENANT, List.of("gestor"));
    }

    @Test
    void changeTemplateStatus_persistsActiveStatus_inDatabase() {
        ProcedureResponse created = service.createTemplate(gestor, new CreateProcedureRequest("Trámite licencia", null));

        ProcedureResponse updated = service.changeTemplateStatus(gestor, created.id(), new ChangeStatusRequest("ACTIVE"));

        assertThat(updated.status()).isEqualTo("ACTIVE");
        assertThat(templateRepo.findByIdAndTenantId(created.id(), TENANT))
            .isPresent()
            .get()
            .extracting(t -> t.getStatus())
            .isEqualTo("ACTIVE");
    }

    @Test
    void changeTemplateStatus_persistsInactiveStatus_inDatabase() {
        ProcedureResponse created = service.createTemplate(gestor, new CreateProcedureRequest("Trámite baja", null));
        service.changeTemplateStatus(gestor, created.id(), new ChangeStatusRequest("ACTIVE"));

        ProcedureResponse deactivated = service.changeTemplateStatus(gestor, created.id(), new ChangeStatusRequest("INACTIVE"));

        assertThat(deactivated.status()).isEqualTo("INACTIVE");
        assertThat(templateRepo.findByIdAndTenantId(created.id(), TENANT))
            .isPresent()
            .get()
            .extracting(t -> t.getStatus())
            .isEqualTo("INACTIVE");
    }

    @Test
    void changeTemplateStatus_rejectsBadStatus_withoutPersisting() {
        ProcedureResponse created = service.createTemplate(gestor, new CreateProcedureRequest("Trámite inválido", null));

        assertThatThrownBy(() -> service.changeTemplateStatus(gestor, created.id(), new ChangeStatusRequest("PUBLICADO")))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(BAD_REQUEST);

        assertThat(templateRepo.findByIdAndTenantId(created.id(), TENANT))
            .isPresent()
            .get()
            .extracting(t -> t.getStatus())
            .isEqualTo(ProcedureService.STATUS_DRAFT);
    }

    @Test
    void changeTemplateStatus_tenantIsolation_cannotModifyOtherTenantTemplate() {
        ProcedureResponse created = service.createTemplate(gestor, new CreateProcedureRequest("Trámite tenant A", null));

        SupabasePrincipal otherTenant = new SupabasePrincipal("other-uid", "other@tenant.com", "Other", "tenant-otro", List.of("gestor"));

        assertThatThrownBy(() -> service.changeTemplateStatus(otherTenant, created.id(), new ChangeStatusRequest("ACTIVE")))
            .isInstanceOf(ResponseStatusException.class);
    }
}
