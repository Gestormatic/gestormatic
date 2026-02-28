package com.gestormatic.backend.auth.config;


import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
@EnableConfigurationProperties(SupabaseProperties.class)
public class SupabaseConfig {

  @Bean
  public JwtDecoder jwtDecoder(SupabaseProperties properties) {
    String jwks = resolveJwks(properties);

    if (jwks == null || jwks.isBlank()) {
      throw new IllegalStateException("No JWKS URI configured.");
    }

    System.out.println(">>> Using JWKS: " + jwks);

    return NimbusJwtDecoder.withJwkSetUri(jwks)
      .jwsAlgorithm(SignatureAlgorithm.ES256)
      .build();
  }

    private String resolveJwks(SupabaseProperties properties) {
        String jwks = properties.getJwks();
        if (jwks != null && !jwks.isBlank()) {
            return jwks;
        }

        String baseUrl = properties.getUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        return baseUrl.replaceAll("/+$", "") + "/auth/v1/.well-known/jwks.json";
    }
}
