package com.gestormatic.backend.procedure.controller;

import com.gestormatic.backend.auth.security.SupabasePrincipal;
import com.gestormatic.backend.procedure.dto.AssignCaseRequest;
import com.gestormatic.backend.procedure.dto.CaseResponse;
import com.gestormatic.backend.procedure.dto.ChangeCaseStatusRequest;
import com.gestormatic.backend.procedure.dto.CreateCaseRequest;
import com.gestormatic.backend.procedure.service.CaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/cases")
@Tag(name = "Procedure Cases", description = "Manage concrete procedure case instances opened from active templates")
public class CaseController {

    private final CaseService service;

    public CaseController(CaseService service) {
        this.service = service;
    }

    @Operation(summary = "Open a new case",
            description = "Creates a new case from an ACTIVE template. Initial status is RECEIVED.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Case created"),
            @ApiResponse(responseCode = "400", description = "Missing required fields or template is not ACTIVE"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "User does not have the 'gestor' role"),
            @ApiResponse(responseCode = "404", description = "Template not found")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CaseResponse create(@RequestBody CreateCaseRequest request, Principal principal) {
        return service.createCase(extractPrincipal(principal), request);
    }

    @Operation(summary = "List cases",
            description = """
                    Returns cases for the authenticated gestor's tenant.
                    All filter parameters are optional and combinable.

                    **Status values:** RECEIVED, IN_REVIEW, PENDING_DOCS, APPROVED, REJECTED
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List returned"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "User does not have the 'gestor' role")
    })
    @GetMapping
    public List<CaseResponse> list(
            @Parameter(description = "Filter by status (e.g. RECEIVED, IN_REVIEW)")
            @RequestParam(required = false) String status,
            @Parameter(description = "Filter by customer user ID")
            @RequestParam(required = false) String customerId,
            @Parameter(description = "Filter by assigned gestor user ID")
            @RequestParam(required = false) String assignedTo,
            @Parameter(description = "Filter by template ID")
            @RequestParam(required = false) Long templateId,
            Principal principal) {
        return service.listCases(extractPrincipal(principal), status, customerId, assignedTo, templateId);
    }

    @Operation(summary = "Get a case",
            description = "Returns a single case by ID. Only cases belonging to the authenticated tenant are accessible.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Case found"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "User does not have the 'gestor' role"),
            @ApiResponse(responseCode = "404", description = "Case not found or belongs to another tenant")
    })
    @GetMapping("/{id}")
    public CaseResponse get(
            @Parameter(description = "Case ID") @PathVariable Long id,
            Principal principal) {
        return service.getCase(extractPrincipal(principal), id);
    }

    @Operation(summary = "Change case status",
            description = """
                    Transitions a case through its status lifecycle.

                    **Allowed transitions:**
                    - RECEIVED → IN_REVIEW
                    - IN_REVIEW → PENDING_DOCS | APPROVED | REJECTED
                    - PENDING_DOCS → IN_REVIEW | REJECTED
                    - APPROVED and REJECTED are terminal states (no further transitions allowed)
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status updated"),
            @ApiResponse(responseCode = "400", description = "Transition not allowed from current status"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "User does not have the 'gestor' role"),
            @ApiResponse(responseCode = "404", description = "Case not found")
    })
    @PatchMapping("/{id}/status")
    public CaseResponse changeStatus(
            @Parameter(description = "Case ID") @PathVariable Long id,
            @RequestBody ChangeCaseStatusRequest request,
            Principal principal) {
        return service.changeCaseStatus(extractPrincipal(principal), id, request);
    }

    @Operation(summary = "Assign a case to a gestor",
            description = "Sets or updates the gestor responsible for handling this case.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Case assigned"),
            @ApiResponse(responseCode = "400", description = "assignedTo is required"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "User does not have the 'gestor' role"),
            @ApiResponse(responseCode = "404", description = "Case not found")
    })
    @PatchMapping("/{id}/assignee")
    public CaseResponse assign(
            @Parameter(description = "Case ID") @PathVariable Long id,
            @RequestBody AssignCaseRequest request,
            Principal principal) {
        return service.assignCase(extractPrincipal(principal), id, request);
    }

    private SupabasePrincipal extractPrincipal(Principal principal) {
        return (SupabasePrincipal) ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
    }
}
