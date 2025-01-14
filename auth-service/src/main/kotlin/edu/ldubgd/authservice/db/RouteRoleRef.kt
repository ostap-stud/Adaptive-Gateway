package edu.ldubgd.authservice.db

import org.springframework.data.relational.core.mapping.Table

@Table("route_role")
data class RouteRoleRef(
    val roleId: Int
)
