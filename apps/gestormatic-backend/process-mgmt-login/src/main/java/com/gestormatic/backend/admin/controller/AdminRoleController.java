package com.gestormatic.backend.admin.controller;

import com.gestormatic.backend.admin.dto.CreateRoleRequest;
import com.gestormatic.backend.admin.dto.RoleResponse;
import com.gestormatic.backend.admin.dto.UpdateRoleRequest;
import com.gestormatic.backend.admin.service.AdminRoleService;
import com.gestormatic.backend.auth.security.SupabasePrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@RestController
@RequestMapping("/admin/roles")
public class AdminRoleController {
    private final AdminRoleService adminRoleService;

    public AdminRoleController(AdminRoleService adminRoleService) {
        this.adminRoleService = adminRoleService;
    }

    @GetMapping
    public ResponseEntity<?> listRoles(@AuthenticationPrincipal SupabasePrincipal principal) {
        if (principal == null || principal.roles() == null || !principal.roles().contains("admin")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("{\"error\":\"forbidden\",\"message\":\"Admin role required\"}");
        }
        List<RoleResponse> roles = adminRoleService.listRoles(principal.tenantId());
        return ResponseEntity.ok(roles);
    }

    @PostMapping
    public ResponseEntity<?> createRole(@AuthenticationPrincipal SupabasePrincipal principal,
                                        @RequestBody CreateRoleRequest request) {
        if (principal == null || principal.roles() == null || !principal.roles().contains("admin")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("{\"error\":\"forbidden\",\"message\":\"Admin role required\"}");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("{\"error\":\"invalid_request\",\"message\":\"name is required\"}");
        }
        RoleResponse role = adminRoleService.createRole(principal.tenantId(), request.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(role);
    }

    @PutMapping("/{name}")
    public ResponseEntity<?> updateRole(@AuthenticationPrincipal SupabasePrincipal principal,
                                        @PathVariable("name") String name,
                                        @RequestBody UpdateRoleRequest request) {
        if (principal == null || principal.roles() == null || !principal.roles().contains("admin")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("{\"error\":\"forbidden\",\"message\":\"Admin role required\"}");
        }
        if (name == null || name.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("{\"error\":\"invalid_request\",\"message\":\"name is required\"}");
        }
        try {
            RoleResponse role = adminRoleService.updateRole(principal.tenantId(), name, request.getName());
            return ResponseEntity.ok(role);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("{\"error\":\"invalid_request\",\"message\":\"" + ex.getMessage() + "\"}");
        }
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<?> deactivateRole(@AuthenticationPrincipal SupabasePrincipal principal,
                                            @PathVariable("name") String name) {
        if (principal == null || principal.roles() == null || !principal.roles().contains("admin")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("{\"error\":\"forbidden\",\"message\":\"Admin role required\"}");
        }
        if (name == null || name.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("{\"error\":\"invalid_request\",\"message\":\"name is required\"}");
        }
        try {
            adminRoleService.deactivateRole(principal.tenantId(), name);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("{\"error\":\"invalid_request\",\"message\":\"" + ex.getMessage() + "\"}");
        }
    }
}
