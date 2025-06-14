package edu.ldubgd.authservice.db.dto

import io.swagger.v3.oas.annotations.media.Schema

data class PredicateDTO(
    @Schema(description = "Назва предиката", example = "Method")
    val name: String,
    @Schema(description = "Параметри предиката (ключ-значення)")
    val args: Map<String, Any?> = emptyMap()
)
