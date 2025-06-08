package edu.ldubgd.alertSystem.gatewayserver.configuration

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
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
        return OpenAPI()
            .info(
                Info().title(title).version(version).description(description)
            )
    }
}