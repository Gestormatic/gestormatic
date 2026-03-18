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
@RequestMapping("/api/procedures")
public class ProcedureController {

    private final ProcedureService service;

    public ProcedureController(ProcedureService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProcedureResponse create(@RequestBody CreateProcedureRequest request, Principal principal) {
        return service.createTemplate(extractPrincipal(principal), request);
    }

    @GetMapping
    public List<ProcedureResponse> list(Principal principal) {
        return service.listTemplates(extractPrincipal(principal));
    }

    @GetMapping("/{id}")
    public ProcedureResponse get(@PathVariable Long id, Principal principal) {
        return service.getTemplate(extractPrincipal(principal), id);
    }

    @PutMapping("/{id}")
    public ProcedureResponse update(@PathVariable Long id,
                                    @RequestBody UpdateProcedureRequest request,
                                    Principal principal) {
        return service.updateTemplate(extractPrincipal(principal), id, request);
    }

    @PatchMapping("/{id}/status")
    public ProcedureResponse changeStatus(@PathVariable Long id,
                                          @RequestBody ChangeStatusRequest request,
                                          Principal principal) {
        return service.changeTemplateStatus(extractPrincipal(principal), id, request);
    }

    @PostMapping("/{id}/steps")
    @ResponseStatus(HttpStatus.CREATED)
    public StepResponse addStep(@PathVariable Long id,
                                @RequestBody CreateStepRequest request,
                                Principal principal) {
        return service.addStep(extractPrincipal(principal), id, request);
    }

    @PutMapping("/{id}/steps/{stepId}")
    public StepResponse updateStep(@PathVariable Long id,
                                   @PathVariable Long stepId,
                                   @RequestBody CreateStepRequest request,
                                   Principal principal) {
        return service.updateStep(extractPrincipal(principal), id, stepId, request);
    }

    @PostMapping("/{id}/steps/{stepId}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public RequiredDocumentResponse addDocument(@PathVariable Long id,
                                                @PathVariable Long stepId,
                                                @RequestBody CreateRequiredDocumentRequest request,
                                                Principal principal) {
        return service.addRequiredDocument(extractPrincipal(principal), id, stepId, request);
    }

    @GetMapping("/{id}/members")
    public List<MemberResponse> listMembers(@PathVariable Long id, Principal principal) {
        return service.listMembers(extractPrincipal(principal), id);
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberResponse addMember(@PathVariable Long id,
                                    @RequestBody AddMemberRequest request,
                                    Principal principal) {
        return service.addMember(extractPrincipal(principal), id, request);
    }

    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable Long id,
                             @PathVariable String userId,
                             Principal principal) {
        service.removeMember(extractPrincipal(principal), id, userId);
    }

    private SupabasePrincipal extractPrincipal(Principal principal) {
        return (SupabasePrincipal) ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
    }
}
