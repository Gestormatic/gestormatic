package com.gestormatic.backend.procedure.service;

import com.gestormatic.backend.auth.security.SupabasePrincipal;
import com.gestormatic.backend.procedure.dto.AddMemberRequest;
import com.gestormatic.backend.procedure.dto.ChangeStatusRequest;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class ProcedureService {

    private static final String ROLE_GESTOR = "gestor";
    private static final String MEMBER_OWNER = "OWNER";
    private static final String MEMBER_CUSTOMER = "CUSTOMER";
    static final String STATUS_DRAFT = "DRAFT";
    static final String STATUS_ACTIVE = "ACTIVE";
    static final String STATUS_INACTIVE = "INACTIVE";
    private static final List<String> VALID_STATUSES = List.of(STATUS_DRAFT, STATUS_ACTIVE, STATUS_INACTIVE);

    private final ProcedureTemplateRepository templateRepo;
    private final ProcedureStepRepository stepRepo;
    private final RequiredDocumentRepository documentRepo;
    private final ProcedureMemberRepository memberRepo;

    public ProcedureService(ProcedureTemplateRepository templateRepo,
                            ProcedureStepRepository stepRepo,
                            RequiredDocumentRepository documentRepo,
                            ProcedureMemberRepository memberRepo) {
        this.templateRepo = templateRepo;
        this.stepRepo = stepRepo;
        this.documentRepo = documentRepo;
        this.memberRepo = memberRepo;
    }

    public ProcedureResponse createTemplate(SupabasePrincipal principal, CreateProcedureRequest request) {
        requireGestor(principal);

        ProcedureTemplate template = new ProcedureTemplate();
        template.setTenantId(principal.tenantId());
        template.setName(request.name());
        template.setDescription(request.description());
        template.setStatus(STATUS_DRAFT);
        template.setCreatedBy(principal.uid());
        template.setCreatedAt(OffsetDateTime.now());
        template.setUpdatedAt(OffsetDateTime.now());
        template = templateRepo.save(template);

        // Creator is automatically an OWNER
        ProcedureMember owner = new ProcedureMember();
        owner.setTemplateId(template.getId());
        owner.setUserId(principal.uid());
        owner.setMemberRole(MEMBER_OWNER);
        memberRepo.save(owner);

        return toResponse(template, List.of());
    }

    public List<ProcedureResponse> listTemplates(SupabasePrincipal principal) {
        return templateRepo.findAllByTenantId(principal.tenantId()).stream()
            .map(t -> toResponse(t, List.of()))
            .toList();
    }

    public ProcedureResponse getTemplate(SupabasePrincipal principal, Long id) {
        ProcedureTemplate template = findTemplate(principal.tenantId(), id);
        List<StepResponse> steps = buildStepResponses(template.getId());
        return toResponse(template, steps);
    }

    public ProcedureResponse updateTemplate(SupabasePrincipal principal, Long id, UpdateProcedureRequest request) {
        ProcedureTemplate template = findTemplate(principal.tenantId(), id);
        requireOwner(principal, id);

        if (request.name() != null) template.setName(request.name());
        if (request.description() != null) template.setDescription(request.description());
        if (request.status() != null) {
            validateStatus(request.status());
            template.setStatus(request.status());
        }
        template.setUpdatedAt(OffsetDateTime.now());
        templateRepo.save(template);

        List<StepResponse> steps = buildStepResponses(template.getId());
        return toResponse(template, steps);
    }

    public StepResponse addStep(SupabasePrincipal principal, Long templateId, CreateStepRequest request) {
        findTemplate(principal.tenantId(), templateId);
        requireOwner(principal, templateId);

        ProcedureStep step = new ProcedureStep();
        step.setTemplateId(templateId);
        step.setStepOrder(request.stepOrder());
        step.setName(request.name());
        step.setDescription(request.description());
        step = stepRepo.save(step);

        return new StepResponse(step.getId(), step.getStepOrder(), step.getName(), step.getDescription(), List.of());
    }

    public StepResponse updateStep(SupabasePrincipal principal, Long templateId, Long stepId, CreateStepRequest request) {
        findTemplate(principal.tenantId(), templateId);
        requireOwner(principal, templateId);

        ProcedureStep step = stepRepo.findById(stepId)
            .filter(s -> s.getTemplateId().equals(templateId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Step not found"));

        if (request.stepOrder() != null) step.setStepOrder(request.stepOrder());
        if (request.name() != null) step.setName(request.name());
        if (request.description() != null) step.setDescription(request.description());
        stepRepo.save(step);

        List<RequiredDocumentResponse> docs = documentRepo.findAllByStepId(stepId).stream()
            .map(d -> new RequiredDocumentResponse(d.getId(), d.getName(), d.getDescription(), d.isMandatory()))
            .toList();

        return new StepResponse(step.getId(), step.getStepOrder(), step.getName(), step.getDescription(), docs);
    }

    public RequiredDocumentResponse addRequiredDocument(SupabasePrincipal principal, Long templateId,
                                                        Long stepId, CreateRequiredDocumentRequest request) {
        findTemplate(principal.tenantId(), templateId);
        requireOwner(principal, templateId);

        stepRepo.findById(stepId)
            .filter(s -> s.getTemplateId().equals(templateId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Step not found"));

        RequiredDocument doc = new RequiredDocument();
        doc.setStepId(stepId);
        doc.setName(request.name());
        doc.setDescription(request.description());
        doc.setMandatory(request.mandatory());
        doc = documentRepo.save(doc);

        return new RequiredDocumentResponse(doc.getId(), doc.getName(), doc.getDescription(), doc.isMandatory());
    }

    public MemberResponse addMember(SupabasePrincipal principal, Long templateId, AddMemberRequest request) {
        findTemplate(principal.tenantId(), templateId);
        requireOwner(principal, templateId);

        String role = request.memberRole().toUpperCase();
        if (!role.equals(MEMBER_OWNER) && !role.equals(MEMBER_CUSTOMER)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "memberRole must be OWNER or CUSTOMER");
        }

        memberRepo.findByTemplateIdAndUserId(templateId, request.userId()).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already a member of this procedure");
        });

        ProcedureMember member = new ProcedureMember();
        member.setTemplateId(templateId);
        member.setUserId(request.userId());
        member.setMemberRole(role);
        member = memberRepo.save(member);

        return new MemberResponse(member.getId(), member.getUserId(), member.getMemberRole());
    }

    public void removeMember(SupabasePrincipal principal, Long templateId, String userId) {
        findTemplate(principal.tenantId(), templateId);
        requireOwner(principal, templateId);

        if (principal.uid().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An owner cannot remove themselves");
        }

        memberRepo.deleteByTemplateIdAndUserId(templateId, userId);
    }

    public List<MemberResponse> listMembers(SupabasePrincipal principal, Long templateId) {
        findTemplate(principal.tenantId(), templateId);
        return memberRepo.findAllByTemplateId(templateId).stream()
            .map(m -> new MemberResponse(m.getId(), m.getUserId(), m.getMemberRole()))
            .toList();
    }

    @Transactional
    public ProcedureResponse changeTemplateStatus(SupabasePrincipal principal, Long id, ChangeStatusRequest request) {
        requireGestor(principal);
        ProcedureTemplate template = findTemplate(principal.tenantId(), id);
        requireOwner(principal, id);
        validateStatus(request.status());

        template.setStatus(request.status().toUpperCase());
        template.setUpdatedAt(OffsetDateTime.now());
        templateRepo.save(template);

        List<StepResponse> steps = buildStepResponses(template.getId());
        return toResponse(template, steps);
    }

    // --- private helpers ---

    private ProcedureTemplate findTemplate(String tenantId, Long id) {
        return templateRepo.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Procedure not found"));
    }

    private void requireGestor(SupabasePrincipal principal) {
        if (!principal.hasRole(ROLE_GESTOR)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only gestores can perform this action");
        }
    }

    private void requireOwner(SupabasePrincipal principal, Long templateId) {
        memberRepo.findByTemplateIdAndUserId(templateId, principal.uid())
            .filter(m -> m.getMemberRole().equals(MEMBER_OWNER))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not an owner of this procedure"));
    }

    private List<StepResponse> buildStepResponses(Long templateId) {
        return stepRepo.findAllByTemplateId(templateId).stream()
            .map(step -> {
                List<RequiredDocumentResponse> docs = documentRepo.findAllByStepId(step.getId()).stream()
                    .map(d -> new RequiredDocumentResponse(d.getId(), d.getName(), d.getDescription(), d.isMandatory()))
                    .toList();
                return new StepResponse(step.getId(), step.getStepOrder(), step.getName(), step.getDescription(), docs);
            })
            .toList();
    }

    private void validateStatus(String status) {
        if (status == null || !VALID_STATUSES.contains(status.toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Invalid status. Allowed values: " + VALID_STATUSES);
        }
    }

    private ProcedureResponse toResponse(ProcedureTemplate template, List<StepResponse> steps) {
        return new ProcedureResponse(
            template.getId(),
            template.getName(),
            template.getDescription(),
            template.getStatus(),
            template.getCreatedBy(),
            template.getCreatedAt(),
            steps
        );
    }
}
