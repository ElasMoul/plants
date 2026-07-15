package com.plantpal.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  // Relative "/" resolves against whatever origin actually served the docs, so prod/staging
  // never need a hardcoded public URL here — only dev gets a concrete absolute server entry
  // since it's always the same fixed local port (see CLAUDE.md's "Running the Project").
  @Value("${app.openapi.server-url:/}")
  private String prodServerUrl;

  @Bean
  public OpenAPI openAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("PlantPal API")
                .version("1.0.0")
                .description("AI-powered plant care companion"))
        .servers(
            List.of(
                new Server().url("http://localhost:8080").description("Local development"),
                new Server().url(prodServerUrl).description("Current environment")))
        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
        .components(
            new Components()
                .addSecuritySchemes(
                    "bearerAuth",
                    new SecurityScheme()
                        .name("bearerAuth")
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
  }
}
