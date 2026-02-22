package com.gestormatic.backend.auth.config;


import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
@EnableConfigurationProperties(SupabaseProperties.class)
public class SupabaseConfig {

    @Bean
    public JwtDecoder jwtDecoder(SupabaseProperties properties) {
        String jwks = properties.getJwks();
        if (jwks == null || jwks.isBlank()) {
            String baseUrl = properties.getUrl();
            if (baseUrl == null || baseUrl.isBlank()) {
                throw new IllegalStateException("app.supabase.url or app.supabase.jwks is required");
            }
            jwks = baseUrl.replaceAll("/+$", "") + "/auth/v1/.well-known/jwks.json";
        }
        return NimbusJwtDecoder.withJwkSetUri(jwks).build();
    }
}
