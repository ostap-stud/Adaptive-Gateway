package edu.ldubgd.authservice.db.dto

data class PredicateDTO(
    val name: String,
    val args: Map<String, Any?> = emptyMap()
)
