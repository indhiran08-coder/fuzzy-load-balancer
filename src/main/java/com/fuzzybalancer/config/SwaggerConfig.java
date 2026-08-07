package com.fuzzybalancer.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SwaggerConfig — Configures the SpringDoc OpenAPI / Swagger documentation.
 *
 * Access the docs at:
 *   Swagger UI:  http://localhost:8080/swagger-ui.html
 *   OpenAPI JSON: http://localhost:8080/api-docs
 *
 * Key configuration:
 *   1. API metadata (title, version, description, contact)
 *   2. JWT Bearer auth scheme — allows testing secured endpoints from Swagger UI
 *
 * How JWT in Swagger works:
 *   1. User calls /api/auth/login in Swagger UI → gets a token
 *   2. Clicks "Authorize" button → pastes the token
 *   3. All subsequent requests include: Authorization: Bearer <token>
 *
 * SecurityScheme:
 *   Type: HTTP, Scheme: bearer, bearerFormat: JWT
 *   This generates an "Authorize" button in Swagger UI.
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Intelligent API Load Balancer — Fuzzy Logic")
                .description("""
                    ## Intelligent API Load Balancer using Fuzzy Logic
                    
                    A Spring Boot application that routes incoming requests to the optimal
                    backend server using a **Mamdani Fuzzy Inference System** instead of
                    traditional Round Robin or Least Connection algorithms.
                    
                    ### Fuzzy Logic Inputs
                    - **CPU Usage** (0–100%): Low / Medium / High
                    - **RAM Usage** (0–100%): Low / Medium / High
                    - **Active Requests** (0–200): Low / Medium / High
                    - **Response Time** (0–5000ms): Fast / Normal / Slow
                    
                    ### Fuzzy Logic Output
                    - **Server Priority** (0–100): Very Low / Low / Medium / High / Very High
                    
                    ### Authentication
                    Use **POST /api/auth/login** to get a JWT token, then click **Authorize**
                    and paste the token (without "Bearer " prefix).
                    
                    ### Quick Start
                    1. Register: `POST /api/auth/register`
                    2. Login: `POST /api/auth/login` → copy the `accessToken`
                    3. Authorize: Click 🔓 Authorize → enter token
                    4. Start simulation: `POST /api/simulation/start`
                    5. Route a request: `POST /api/loadbalancer/route`
                    6. View dashboard: `GET /api/dashboard/summary`
                    """)
                .version("1.0.0")
                .contact(new Contact()
                    .name("Fuzzy Load Balancer Project")
                    .email("project@fuzzybalancer.com"))
                .license(new License()
                    .name("MIT License")))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
            .components(new Components()
                .addSecuritySchemes("bearerAuth",
                    new SecurityScheme()
                        .name("bearerAuth")
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Enter your JWT token (obtained from /api/auth/login)")
                ));
    }
}
