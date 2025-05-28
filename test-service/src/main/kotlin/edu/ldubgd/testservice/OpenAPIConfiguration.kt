package edu.ldubgd.testservice

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenAPIConfiguration {
    @Bean
    fun configureOpenAPI(
        @Value("\${openapi.service.title}") title: String,
        @Value("\${openapi.service.version}") version: String,
        @Value("\${openapi.service.url}") url: String,
    ): OpenAPI {
        val securityScheme = "bearerAuth"
        return OpenAPI()
            .addServersItem(Server().url(url))
            .info(Info().title(title).version(version))
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