package com.gestormatic.backend.procedure.controller;

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
import com.gestormatic.backend.procedure.service.ProcedureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/procedures")
@Tag(name = "Procedure Templates", description = "Manage procedure templates, their steps, required documents and members")
public class ProcedureController {

    private final ProcedureService service;

    public ProcedureController(ProcedureService service) {
        this.service = service;
    }

    // ─── Templates ────────────────────────────────────────────────────────────

    @Operation(summary = "Create a procedure template",
            description = "Creates a new procedure template in DRAFT status. The authenticated gestor is automatically added as OWNER.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Template created"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "User does not have the 'gestor' role")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProcedureResponse create(@RequestBody CreateProcedureRequest request, Principal principal) {
        return service.createTemplate(extractPrincipal(principal), request);
    }

    @Operation(summary = "List procedure templates",
            description = "Returns all procedure templates belonging to the authenticated gestor's tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List returned"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT")
    })
    @GetMapping
    public List<ProcedureResponse> list(Principal principal) {
        return service.listTemplates(extractPrincipal(principal));
    }

    @Operation(summary = "Get a procedure template",
            description = "Returns a procedure template with its steps and required documents.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Template found"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
            @ApiResponse(responseCode = "404", description = "Template not found or belongs to another tenant")
    })
    @GetMapping("/{id}")
    public ProcedureResponse get(
            @Parameter(description = "Template ID") @PathVariable Long id,
            Principal principal) {
        return service.getTemplate(extractPrincipal(principal), id);
    }

    @Operation(summary = "Update a procedure template",
            description = "Updates name, description or status of a template. Requires OWNER membership.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Template updated"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an OWNER of this template"),
            @ApiResponse(responseCode = "404", description = "Template not found")
    })
    @PutMapping("/{id}")
    public ProcedureResponse update(
            @Parameter(description = "Template ID") @PathVariable Long id,
            @RequestBody UpdateProcedureRequest request,
            Principal principal) {
        return service.updateTemplate(extractPrincipal(principal), id, request);
    }

    @Operation(summary = "Change template status",
            description = "Transitions a template between statuses: DRAFT → ACTIVE → INACTIVE. Requires OWNER membership.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status updated"),
            @ApiResponse(responseCode = "400", description = "Invalid status value"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an OWNER of this template"),
            @ApiResponse(responseCode = "404", description = "Template not found")
    })
    @PatchMapping("/{id}/status")
    public ProcedureResponse changeStatus(
            @Parameter(description = "Template ID") @PathVariable Long id,
            @RequestBody ChangeStatusRequest request,
            Principal principal) {
        return service.changeTemplateStatus(extractPrincipal(principal), id, request);
    }

    // ─── Steps ────────────────────────────────────────────────────────────────

    @Operation(summary = "Add a step to a template",
            description = "Adds a new step to an existing template. Requires OWNER membership.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Step created"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an OWNER"),
            @ApiResponse(responseCode = "404", description = "Template not found")
    })
    @PostMapping("/{id}/steps")
    @ResponseStatus(HttpStatus.CREATED)
    public StepResponse addStep(
            @Parameter(description = "Template ID") @PathVariable Long id,
            @RequestBody CreateStepRequest request,
            Principal principal) {
        return service.addStep(extractPrincipal(principal), id, request);
    }

    @Operation(summary = "Update a step",
            description = "Updates the name, description or order of an existing step. Requires OWNER membership.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Step updated"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an OWNER"),
            @ApiResponse(responseCode = "404", description = "Template or step not found")
    })
    @PutMapping("/{id}/steps/{stepId}")
    public StepResponse updateStep(
            @Parameter(description = "Template ID") @PathVariable Long id,
            @Parameter(description = "Step ID") @PathVariable Long stepId,
            @RequestBody CreateStepRequest request,
            Principal principal) {
        return service.updateStep(extractPrincipal(principal), id, stepId, request);
    }

    // ─── Required Documents ───────────────────────────────────────────────────

    @Operation(summary = "Add a required document to a step",
            description = "Adds a required document to a specific step of a template. Requires OWNER membership.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Document added"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an OWNER"),
            @ApiResponse(responseCode = "404", description = "Template or step not found")
    })
    @PostMapping("/{id}/steps/{stepId}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public RequiredDocumentResponse addDocument(
            @Parameter(description = "Template ID") @PathVariable Long id,
            @Parameter(description = "Step ID") @PathVariable Long stepId,
            @RequestBody CreateRequiredDocumentRequest request,
            Principal principal) {
        return service.addRequiredDocument(extractPrincipal(principal), id, stepId, request);
    }

    // ─── Members ──────────────────────────────────────────────────────────────

    @Operation(summary = "List members of a template",
            description = "Returns all members (gestores and customers) associated with a template.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Members returned"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
            @ApiResponse(responseCode = "404", description = "Template not found")
    })
    @GetMapping("/{id}/members")
    public List<MemberResponse> listMembers(
            @Parameter(description = "Template ID") @PathVariable Long id,
            Principal principal) {
        return service.listMembers(extractPrincipal(principal), id);
    }

    @Operation(summary = "Add a member to a template",
            description = "Adds a user as OWNER or CUSTOMER of a template. Requires OWNER membership.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Member added"),
            @ApiResponse(responseCode = "400", description = "Invalid memberRole or user already a member"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an OWNER"),
            @ApiResponse(responseCode = "409", description = "User is already a member of this template")
    })
    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberResponse addMember(
            @Parameter(description = "Template ID") @PathVariable Long id,
            @RequestBody AddMemberRequest request,
            Principal principal) {
        return service.addMember(extractPrincipal(principal), id, request);
    }

    @Operation(summary = "Remove a member from a template",
            description = "Removes a user from the member list. An OWNER cannot remove themselves.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Member removed"),
            @ApiResponse(responseCode = "400", description = "Cannot remove yourself as owner"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an OWNER"),
            @ApiResponse(responseCode = "404", description = "Template not found")
    })
    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(
            @Parameter(description = "Template ID") @PathVariable Long id,
            @Parameter(description = "User ID to remove") @PathVariable String userId,
            Principal principal) {
        service.removeMember(extractPrincipal(principal), id, userId);
    }

    private SupabasePrincipal extractPrincipal(Principal principal) {
        return (SupabasePrincipal) ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
    }
}
