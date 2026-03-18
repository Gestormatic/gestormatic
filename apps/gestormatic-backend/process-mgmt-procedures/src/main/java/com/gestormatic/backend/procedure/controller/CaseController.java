package com.gestormatic.backend.procedure.controller;

import com.gestormatic.backend.auth.security.SupabasePrincipal;
import com.gestormatic.backend.procedure.dto.AssignCaseRequest;
import com.gestormatic.backend.procedure.dto.CaseResponse;
import com.gestormatic.backend.procedure.dto.ChangeCaseStatusRequest;
import com.gestormatic.backend.procedure.dto.CreateCaseRequest;
import com.gestormatic.backend.procedure.service.CaseService;
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
public class CaseController {

    private final CaseService service;

    public CaseController(CaseService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CaseResponse create(@RequestBody CreateCaseRequest request, Principal principal) {
        return service.createCase(extractPrincipal(principal), request);
    }

    @GetMapping
    public List<CaseResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String assignedTo,
            @RequestParam(required = false) Long templateId,
            Principal principal) {
        return service.listCases(extractPrincipal(principal), status, customerId, assignedTo, templateId);
    }

    @GetMapping("/{id}")
    public CaseResponse get(@PathVariable Long id, Principal principal) {
        return service.getCase(extractPrincipal(principal), id);
    }

    @PatchMapping("/{id}/status")
    public CaseResponse changeStatus(@PathVariable Long id,
                                     @RequestBody ChangeCaseStatusRequest request,
                                     Principal principal) {
        return service.changeCaseStatus(extractPrincipal(principal), id, request);
    }

    @PatchMapping("/{id}/assignee")
    public CaseResponse assign(@PathVariable Long id,
                               @RequestBody AssignCaseRequest request,
                               Principal principal) {
        return service.assignCase(extractPrincipal(principal), id, request);
    }

    private SupabasePrincipal extractPrincipal(Principal principal) {
        return (SupabasePrincipal) ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
    }
}
