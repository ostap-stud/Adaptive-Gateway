package edu.ldubgd.alertSystem.gatewayserver

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/*
* Documenting exposed Spring Actuator API
* */
@RestController
@RequestMapping("/actuator")
@Tag(name = "Actuator API", description = "Exposed endpoints for monitoring and controlling Gateway")
class ActuatorStubController {

    @Operation(summary = "Refresh gateway routes")
    @ApiResponses(
        value = [
            ApiResponse(description = "Refreshed successfully!", responseCode = "200"),
            ApiResponse(description = "Access denied!", responseCode = "403")
        ]
    )
    @PostMapping("/gateway/refresh")
    fun gatewayRefresh(): ResponseEntity<Void> = ResponseEntity.ok().build()

    @Operation(summary = "Retrieve a list of gateway routes")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200"),
            ApiResponse(description = "Access denied!", responseCode = "403")
        ]
    )
    @GetMapping("/gateway/routes")
    fun gatewayRoutes(): ResponseEntity<Void> = ResponseEntity.ok().build()

    @Operation(summary = "Infrastructure health check")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200"),
            ApiResponse(description = "Access denied!", responseCode = "403")
        ]
    )
    @GetMapping("/health")
    fun gatewayHealth(): ResponseEntity<Void> = ResponseEntity.ok().build()

    @Operation(summary = "Gateway instance information")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200"),
            ApiResponse(description = "Access denied!", responseCode = "403")
        ]
    )
    @GetMapping("/info")
    fun gatewayInfo(): ResponseEntity<Void> = ResponseEntity.ok().build()

}