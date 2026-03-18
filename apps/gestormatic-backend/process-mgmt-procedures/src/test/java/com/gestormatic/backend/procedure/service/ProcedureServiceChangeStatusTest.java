package com.gestormatic.backend.procedure.service;

import com.gestormatic.backend.auth.security.SupabasePrincipal;
import com.gestormatic.backend.procedure.dto.ChangeStatusRequest;
import com.gestormatic.backend.procedure.dto.ProcedureResponse;
import com.gestormatic.backend.procedure.model.ProcedureMember;
import com.gestormatic.backend.procedure.model.ProcedureTemplate;
import com.gestormatic.backend.procedure.repo.ProcedureMemberRepository;
import com.gestormatic.backend.procedure.repo.ProcedureStepRepository;
import com.gestormatic.backend.procedure.repo.ProcedureTemplateRepository;
import com.gestormatic.backend.procedure.repo.RequiredDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
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
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@ExtendWith(MockitoExtension.class)
class ProcedureServiceChangeStatusTest {

    @Mock private ProcedureTemplateRepository templateRepo;
    @Mock private ProcedureStepRepository stepRepo;
    @Mock private RequiredDocumentRepository documentRepo;
    @Mock private ProcedureMemberRepository memberRepo;

    @InjectMocks
    private ProcedureService service;

    private static final String TENANT = "tenant-1";
    private static final String GESTOR_UID = "gestor-uid";
    private static final Long TEMPLATE_ID = 1L;

    private SupabasePrincipal gestorPrincipal;
    private ProcedureTemplate template;
    private ProcedureMember ownerMember;

    @BeforeEach
    void setUp() {
        gestorPrincipal = new SupabasePrincipal(GESTOR_UID, "gestor@test.com", "Gestor", TENANT, List.of("gestor"));

        template = new ProcedureTemplate();
        template.setId(TEMPLATE_ID);
        template.setTenantId(TENANT);
        template.setName("Trámite de prueba");
        template.setStatus(ProcedureService.STATUS_DRAFT);
        template.setCreatedBy(GESTOR_UID);
        template.setCreatedAt(OffsetDateTime.now());
        template.setUpdatedAt(OffsetDateTime.now());

        ownerMember = new ProcedureMember();
        ownerMember.setTemplateId(TEMPLATE_ID);
        ownerMember.setUserId(GESTOR_UID);
        ownerMember.setMemberRole("OWNER");
    }

    @Test
    void changeTemplateStatus_activatesTemplate_whenOwnerGestor() {
        when(templateRepo.findByIdAndTenantId(TEMPLATE_ID, TENANT)).thenReturn(Optional.of(template));
        when(memberRepo.findByTemplateIdAndUserId(TEMPLATE_ID, GESTOR_UID)).thenReturn(Optional.of(ownerMember));
        when(templateRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stepRepo.findAllByTemplateId(TEMPLATE_ID)).thenReturn(List.of());

        ProcedureResponse response = service.changeTemplateStatus(gestorPrincipal, TEMPLATE_ID, new ChangeStatusRequest("ACTIVE"));

        assertThat(response.status()).isEqualTo("ACTIVE");
        verify(templateRepo).save(any(ProcedureTemplate.class));
    }

    @Test
    void changeTemplateStatus_deactivatesTemplate_whenOwnerGestor() {
        template.setStatus(ProcedureService.STATUS_ACTIVE);
        when(templateRepo.findByIdAndTenantId(TEMPLATE_ID, TENANT)).thenReturn(Optional.of(template));
        when(memberRepo.findByTemplateIdAndUserId(TEMPLATE_ID, GESTOR_UID)).thenReturn(Optional.of(ownerMember));
        when(templateRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stepRepo.findAllByTemplateId(TEMPLATE_ID)).thenReturn(List.of());

        ProcedureResponse response = service.changeTemplateStatus(gestorPrincipal, TEMPLATE_ID, new ChangeStatusRequest("INACTIVE"));

        assertThat(response.status()).isEqualTo("INACTIVE");
    }

    @Test
    void changeTemplateStatus_throwsForbidden_whenNotGestor() {
        SupabasePrincipal noGestor = new SupabasePrincipal("other", "other@test.com", "Other", TENANT, List.of("customer"));

        assertThatThrownBy(() -> service.changeTemplateStatus(noGestor, TEMPLATE_ID, new ChangeStatusRequest("ACTIVE")))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(FORBIDDEN);
    }

    @Test
    void changeTemplateStatus_throwsForbidden_whenNotOwner() {
        SupabasePrincipal otherGestor = new SupabasePrincipal("other-gestor", "other@test.com", "Other", TENANT, List.of("gestor"));
        when(templateRepo.findByIdAndTenantId(TEMPLATE_ID, TENANT)).thenReturn(Optional.of(template));
        when(memberRepo.findByTemplateIdAndUserId(TEMPLATE_ID, "other-gestor")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeTemplateStatus(otherGestor, TEMPLATE_ID, new ChangeStatusRequest("ACTIVE")))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(FORBIDDEN);
    }

    @Test
    void changeTemplateStatus_throwsBadRequest_whenInvalidStatus() {
        when(templateRepo.findByIdAndTenantId(TEMPLATE_ID, TENANT)).thenReturn(Optional.of(template));
        when(memberRepo.findByTemplateIdAndUserId(TEMPLATE_ID, GESTOR_UID)).thenReturn(Optional.of(ownerMember));

        assertThatThrownBy(() -> service.changeTemplateStatus(gestorPrincipal, TEMPLATE_ID, new ChangeStatusRequest("PUBLICADO")))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(BAD_REQUEST);
    }

    @Test
    void changeTemplateStatus_throwsNotFound_whenTemplateNotFound() {
        when(templateRepo.findByIdAndTenantId(TEMPLATE_ID, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeTemplateStatus(gestorPrincipal, TEMPLATE_ID, new ChangeStatusRequest("ACTIVE")))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(NOT_FOUND);
    }

    @Test
    void changeTemplateStatus_isCaseInsensitive() {
        when(templateRepo.findByIdAndTenantId(TEMPLATE_ID, TENANT)).thenReturn(Optional.of(template));
        when(memberRepo.findByTemplateIdAndUserId(TEMPLATE_ID, GESTOR_UID)).thenReturn(Optional.of(ownerMember));
        when(templateRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stepRepo.findAllByTemplateId(TEMPLATE_ID)).thenReturn(List.of());

        ProcedureResponse response = service.changeTemplateStatus(gestorPrincipal, TEMPLATE_ID, new ChangeStatusRequest("active"));

        assertThat(response.status()).isEqualTo("ACTIVE");
    }
}
