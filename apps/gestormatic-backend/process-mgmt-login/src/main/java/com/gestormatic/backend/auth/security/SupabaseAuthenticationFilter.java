package com.gestormatic.backend.auth.security;


import com.gestormatic.backend.auth.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SupabaseAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(SupabaseAuthenticationFilter.class);
    private final JwtDecoder jwtDecoder;

    public SupabaseAuthenticationFilter(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/auth/health") || path.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring("Bearer ".length());
        try {
            Jwt decoded = jwtDecoder.decode(token);
            Map<String, Object> appMetadata = extractMap(decoded.getClaim("app_metadata"));
            String tenantId = extractString(appMetadata, "tenant_id");
            if (tenantId == null || tenantId.isBlank()) {
                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"missing_tenant\",\"message\":\"Missing tenant_id claim\"}");
                return;
            }

            List<String> roles = extractRoles(appMetadata.get("roles"));
            SupabasePrincipal principal = new SupabasePrincipal(
                    decoded.getSubject(),
                    decoded.getClaimAsString("email"),
                    decoded.getClaimAsString("email"),
                    tenantId,
                    roles
            );

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, token, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(authentication);

            try {
                TenantContext.set(tenantId);
                filterChain.doFilter(request, response);
            } finally {
                TenantContext.clear();
            }
        } catch (JwtException ex) {
            log.warn("Rejected bearer token: {}", ex.getMessage());
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"invalid_token\",\"message\":\"Invalid bearer token. Use a valid Supabase user access token.\"}");
        }
    }

    private Map<String, Object> extractMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> casted = (Map<String, Object>) map;
            return casted;
        }
        return Collections.emptyMap();
    }

    private String extractString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof String ? (String) value : null;
    }

    private List<String> extractRoles(Object rawRoles) {
        if (rawRoles instanceof List<?> list) {
            List<String> roles = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof String role && !role.isBlank()) {
                    roles.add(role);
                }
            }
            return roles;
        }
        return Collections.emptyList();
    }
}
