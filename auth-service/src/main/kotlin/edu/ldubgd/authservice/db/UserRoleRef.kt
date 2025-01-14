package edu.ldubgd.authservice.db

import org.springframework.data.relational.core.mapping.Table

@Table("user_role")
data class UserRoleRef(
    val roleId: Int
)
