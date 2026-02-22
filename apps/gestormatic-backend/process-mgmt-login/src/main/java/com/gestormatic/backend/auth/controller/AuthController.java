package com.gestormatic.backend.auth.controller;


import com.gestormatic.backend.auth.dto.UserProfileResponse;
import com.gestormatic.backend.auth.security.SupabasePrincipal;
import com.gestormatic.backend.auth.service.AuthService;
import com.gestormatic.backend.auth.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ok");
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> me(@AuthenticationPrincipal SupabasePrincipal principal) {
        String tenantId = TenantContext.getRequired();
        return authService.getProfile(tenantId, principal, principal.roles())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}
