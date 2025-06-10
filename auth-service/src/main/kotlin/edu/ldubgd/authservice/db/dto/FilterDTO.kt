package edu.ldubgd.authservice.db.dto

data class FilterDTO(
    val name: String,
    val args: Map<String, Any?> = emptyMap()
)
