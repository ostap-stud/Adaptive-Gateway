package edu.ldubgd.authservice.openapi

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfiguration {
    @Bean
    fun setSecurityScheme(): OpenAPI{
        val securityScheme = "bearerAuth"
        return OpenAPI()
            .addSecurityItem(SecurityRequirement().addList(securityScheme))
            .components(Components().addSecuritySchemes(
                securityScheme,
                SecurityScheme()
                    .name(securityScheme)
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
            ))
    }
}