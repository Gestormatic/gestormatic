package com.gestormatic.backend.admin.controller;


import com.gestormatic.backend.admin.dto.SetClaimsRequest;
import com.gestormatic.backend.admin.dto.SetClaimsResponse;
import com.gestormatic.backend.admin.dto.UpdateUserRolesRequest;
import com.gestormatic.backend.admin.service.AdminUserService;
import com.gestormatic.backend.auth.security.SupabasePrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/users")
public class AdminUserController {
    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @PostMapping("/claims")
    public ResponseEntity<?> setClaims(@AuthenticationPrincipal SupabasePrincipal principal,
                                       @RequestBody SetClaimsRequest request) {
        if (principal == null || principal.roles() == null || !principal.roles().contains("admin")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("{\"error\":\"forbidden\",\"message\":\"Admin role required\"}");
        }

        if (request.getTenantId() == null || request.getTenantId().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("{\"error\":\"invalid_request\",\"message\":\"tenantId is required\"}");
        }

        if (!request.getTenantId().equals(principal.tenantId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("{\"error\":\"cross_tenant\",\"message\":\"Cannot set claims for another tenant\"}");
        }

        if (request.getUid() == null || request.getUid().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("{\"error\":\"invalid_request\",\"message\":\"uid is required\"}");
        }

        if (request.getRoles() == null) {
            request.setRoles(List.of());
        }

        try {
            SetClaimsResponse response = adminUserService.setClaims(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("{\"error\":\"invalid_request\",\"message\":\"" + ex.getMessage() + "\"}");
        }
    }

    @PutMapping("/{uid}/roles")
    public ResponseEntity<?> updateUserRoles(@AuthenticationPrincipal SupabasePrincipal principal,
                                             @PathVariable("uid") String uid,
                                             @RequestBody UpdateUserRolesRequest request) {
        if (principal == null || principal.roles() == null || !principal.roles().contains("admin")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("{\"error\":\"forbidden\",\"message\":\"Admin role required\"}");
        }
        if (uid == null || uid.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("{\"error\":\"invalid_request\",\"message\":\"uid is required\"}");
        }

        SetClaimsRequest claimsRequest = new SetClaimsRequest();
        claimsRequest.setUid(uid);
        claimsRequest.setTenantId(principal.tenantId());
        claimsRequest.setRoles(request.getRoles() == null ? List.of() : request.getRoles());

        try {
            SetClaimsResponse response = adminUserService.setClaims(claimsRequest);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("{\"error\":\"invalid_request\",\"message\":\"" + ex.getMessage() + "\"}");
        }
    }
}
