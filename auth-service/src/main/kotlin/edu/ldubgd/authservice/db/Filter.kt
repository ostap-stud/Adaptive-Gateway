package edu.ldubgd.authservice.db

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table

@Table(name = "filter")
data class Filter(
    @Id
    val id: Int? = null,
    val name: String,
    val args: Map<String, String> = emptyMap()
)
