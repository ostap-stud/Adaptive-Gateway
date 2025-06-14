package edu.ldubgd.authservice.db.dto

import io.swagger.v3.oas.annotations.media.Schema

data class ServiceDTO(
    @Schema(description = "Назва сервісу", example = "test-service")
    val name: String,
    @Schema(description = "Опис сервісу")
    val description: String?
)
