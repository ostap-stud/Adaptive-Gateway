package edu.ldubgd.alertSystem.gatewayserver

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.client.discovery.DiscoveryClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/swagger")
@Tag(name = "OpenAPI Config", description = "Endpoints for aggregation of OpenAPI Specifications")
class SwaggerConfigController(
    private val discoveryClient: DiscoveryClient,
    @Value("\${spring.application.name}") private val applicationName: String
) {

    @Operation(summary = "Retrieve a map of registered Services and URLs of their OpenAPI Specifications")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200"),
            ApiResponse(description = "Access denied!", responseCode = "401")
        ]
    )
    @GetMapping("/config")
    fun swaggerConfig(): Map<String, Any> {
        val urls = discoveryClient.services
            .map { serviceName ->
                mapOf(
                    "name" to serviceName,
                    "url" to "${if (!serviceName.equals(applicationName)) "/${serviceName}" else ""}/v3/api-docs"
                )
            }
        return mapOf("urls" to urls)
    }

}