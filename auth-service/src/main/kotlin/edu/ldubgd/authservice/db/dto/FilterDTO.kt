package edu.ldubgd.authservice.db.dto

import io.swagger.v3.oas.annotations.media.Schema

data class FilterDTO(
    @Schema(description = "Назва фільтра", example = "Authentication")
    val name: String,
    @Schema(description = "Параметри фільтра (ключ-значення)")
    val args: Map<String, Any?> = emptyMap()
)
