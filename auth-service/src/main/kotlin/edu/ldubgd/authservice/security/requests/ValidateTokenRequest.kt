package edu.ldubgd.authservice.security.requests

import io.swagger.v3.oas.annotations.media.Schema

data class ValidateTokenRequest(
    @Schema(description = "Шлях запиту клієнта", example = "/test-service/services/add")
    val routePath: String,
    @Schema(description = "Метод запиту клієнта", example = "POST")
    val method: String
)
