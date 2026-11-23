package edu.ldubgd.alertSystem.gatewayserver.configuration

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenAPIConfiguration {
    @Bean
    fun configureOpenAPI(
        @Value("\${openapi.service.title}") title: String,
        @Value("\${openapi.service.version}") version: String,
        @Value("\${openapi.service.description}") description: String,
    ): OpenAPI {
        val securityScheme = "bearerAuth"
        return OpenAPI()
            .info(Info().title(title).version(version).description(description))
            // Describe real Actuator endpoints without registering competing HTTP handlers.
            .path("/actuator/gateway/refresh", PathItem().post(
                actuatorOperation("Request route cache refresh")
            ))
            .path("/actuator/gateway/routes", PathItem().get(
                actuatorOperation("Get current gateway routes")
            ))
            .path("/actuator/health", PathItem().get(actuatorOperation("Get gateway health")))
            .path("/actuator/info", PathItem().get(actuatorOperation("Get gateway information")))
            .addSecurityItem(SecurityRequirement().addList(securityScheme))
            .components(
                Components().addSecuritySchemes(
                securityScheme,
                SecurityScheme()
                    .name(securityScheme)
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
            ))
    }

    private fun actuatorOperation(summary: String): Operation = Operation()
        .summary(summary)
        .addTagsItem("Actuator API")
        .responses(ApiResponses()
            .addApiResponse("200", ApiResponse().description("Successful response"))
            .addApiResponse("401", ApiResponse().description("Authorization required")))
}
