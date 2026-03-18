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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CaseService {

    static final String STATUS_RECEIVED = "RECEIVED";
    static final String STATUS_IN_REVIEW = "IN_REVIEW";
    static final String STATUS_PENDING_DOCS = "PENDING_DOCS";
    static final String STATUS_APPROVED = "APPROVED";
    static final String STATUS_REJECTED = "REJECTED";

    /**
     * Valid transitions: from → allowed next statuses.
     * Terminal states (APPROVED, REJECTED) have no outgoing transitions.
     */
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            STATUS_RECEIVED, Set.of(STATUS_IN_REVIEW),
            STATUS_IN_REVIEW, Set.of(STATUS_PENDING_DOCS, STATUS_APPROVED, STATUS_REJECTED),
            STATUS_PENDING_DOCS, Set.of(STATUS_IN_REVIEW, STATUS_REJECTED)
    );

    private static final String ROLE_GESTOR = "gestor";

    private final CaseRepository caseRepo;
    private final CaseQueryRepository caseQueryRepo;
    private final ProcedureTemplateRepository templateRepo;

    public CaseService(CaseRepository caseRepo,
                       CaseQueryRepository caseQueryRepo,
                       ProcedureTemplateRepository templateRepo) {
        this.caseRepo = caseRepo;
        this.caseQueryRepo = caseQueryRepo;
        this.templateRepo = templateRepo;
    }

    @Transactional
    public CaseResponse createCase(SupabasePrincipal principal, CreateCaseRequest request) {
        requireGestor(principal);

        if (request.templateId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "templateId is required");
        }
        if (request.customerId() == null || request.customerId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "customerId is required");
        }

        ProcedureTemplate template = templateRepo.findByIdAndTenantId(request.templateId(), principal.tenantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found"));

        if (!ProcedureService.STATUS_ACTIVE.equals(template.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot create a case for a non-active template");
        }

        ProcedureCase procedureCase = new ProcedureCase();
        procedureCase.setTenantId(principal.tenantId());
        procedureCase.setTemplateId(request.templateId());
        procedureCase.setCustomerId(request.customerId());
        procedureCase.setStatus(STATUS_RECEIVED);
        procedureCase.setNotes(request.notes());
        procedureCase.setCreatedBy(principal.uid());
        procedureCase.setCreatedAt(OffsetDateTime.now());
        procedureCase.setUpdatedAt(OffsetDateTime.now());
        procedureCase = caseRepo.save(procedureCase);

        return toResponse(procedureCase, template.getName());
    }

    public List<CaseResponse> listCases(SupabasePrincipal principal,
                                        String status,
                                        String customerId,
                                        String assignedTo,
                                        Long templateId) {
        requireGestor(principal);
        return caseQueryRepo.findByFilters(principal.tenantId(), status, customerId, assignedTo, templateId);
    }

    public CaseResponse getCase(SupabasePrincipal principal, Long id) {
        requireGestor(principal);
        ProcedureCase procedureCase = findCase(principal.tenantId(), id);
        ProcedureTemplate template = templateRepo.findById(procedureCase.getTemplateId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Template not found for case"));
        return toResponse(procedureCase, template.getName());
    }

    @Transactional
    public CaseResponse changeCaseStatus(SupabasePrincipal principal, Long id, ChangeCaseStatusRequest request) {
        requireGestor(principal);
        ProcedureCase procedureCase = findCase(principal.tenantId(), id);

        String currentStatus = procedureCase.getStatus();
        String newStatus = request.status() != null ? request.status().toUpperCase() : null;

        if (newStatus == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status is required");
        }

        Set<String> allowed = TRANSITIONS.get(currentStatus);
        if (allowed == null || !allowed.contains(newStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid transition from " + currentStatus + " to " + newStatus);
        }

        procedureCase.setStatus(newStatus);
        procedureCase.setUpdatedAt(OffsetDateTime.now());
        caseRepo.save(procedureCase);

        ProcedureTemplate template = templateRepo.findById(procedureCase.getTemplateId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Template not found for case"));
        return toResponse(procedureCase, template.getName());
    }

    @Transactional
    public CaseResponse assignCase(SupabasePrincipal principal, Long id, AssignCaseRequest request) {
        requireGestor(principal);
        ProcedureCase procedureCase = findCase(principal.tenantId(), id);

        if (request.assignedTo() == null || request.assignedTo().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "assignedTo is required");
        }

        procedureCase.setAssignedTo(request.assignedTo());
        procedureCase.setUpdatedAt(OffsetDateTime.now());
        caseRepo.save(procedureCase);

        ProcedureTemplate template = templateRepo.findById(procedureCase.getTemplateId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Template not found for case"));
        return toResponse(procedureCase, template.getName());
    }

    // --- private helpers ---

    private ProcedureCase findCase(String tenantId, Long id) {
        return caseRepo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found"));
    }

    private void requireGestor(SupabasePrincipal principal) {
        if (!principal.hasRole(ROLE_GESTOR)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only gestores can perform this action");
        }
    }

    private CaseResponse toResponse(ProcedureCase c, String templateName) {
        return new CaseResponse(
                c.getId(),
                c.getTenantId(),
                c.getTemplateId(),
                templateName,
                c.getCustomerId(),
                c.getAssignedTo(),
                c.getStatus(),
                c.getNotes(),
                c.getCreatedBy(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }
}
