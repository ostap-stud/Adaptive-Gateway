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
@Tag(name = "Actuator API", description = "Моніторинг та управління шлюзом")
class ActuatorStubController {

    @Operation(summary = "Провокує оновлення кешу маршрутів шлюзу")
    @ApiResponses(
        value = [
            ApiResponse(description = "Refreshed successfully!", responseCode = "200"),
            ApiResponse(description = "Access denied!", responseCode = "401")
        ]
    )
    @PostMapping("/gateway/refresh")
    fun gatewayRefresh(): ResponseEntity<Void> = ResponseEntity.ok().build()

    @Operation(summary = "Отримати список маршрутів, зареєстрованих шлюзом")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200"),
            ApiResponse(description = "Access denied!", responseCode = "401")
        ]
    )
    @GetMapping("/gateway/routes")
    fun gatewayRoutes(): ResponseEntity<Void> = ResponseEntity.ok().build()

    @Operation(summary = "Перевірка стану інфраструктури")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200"),
            ApiResponse(description = "Access denied!", responseCode = "401")
        ]
    )
    @GetMapping("/health")
    fun gatewayHealth(): ResponseEntity<Void> = ResponseEntity.ok().build()

    @Operation(summary = "Інформація про даний екземпляр (instance) шлюзу")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200"),
            ApiResponse(description = "Access denied!", responseCode = "401")
        ]
    )
    @GetMapping("/info")
    fun gatewayInfo(): ResponseEntity<Void> = ResponseEntity.ok().build()

}