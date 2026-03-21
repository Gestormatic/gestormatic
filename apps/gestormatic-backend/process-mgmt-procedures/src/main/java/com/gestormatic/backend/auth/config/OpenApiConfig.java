package com.gestormatic.backend.auth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Gestormatic – Process Management API")
                        .version("1.0.0")
                        .description("""
                                API for managing procedure templates and case instances.

                                **Authentication**: All endpoints require a valid Supabase JWT.
                                Obtain the token via the `process-mgmt-login` service and pass it
                                as `Authorization: Bearer <token>`.

                                **Multi-tenancy**: The `tenant_id` is extracted automatically from the
                                JWT `app_metadata` claim — no need to send it manually.

                                **Roles**: All endpoints require the `gestor` role inside the JWT.
                                """)
                        .contact(new Contact()
                                .name("Gestormatic")
                                .url("https://github.com/Gestormatic/gestormatic")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .name("bearerAuth")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Paste your Supabase JWT access_token here (without the 'Bearer' prefix)")));
    }
}
