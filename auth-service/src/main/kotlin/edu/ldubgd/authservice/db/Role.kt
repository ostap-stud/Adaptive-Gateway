package edu.ldubgd.authservice.db

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table

@Table("role")
data class Role(
    @Id
    val id: Int,
    val roleName: String,
    val roleDesc: String? = null,
    val role: String,
    val application: String
)
