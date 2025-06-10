package edu.ldubgd.authservice.db

import org.springframework.data.relational.core.mapping.Table

@Table(name = "route_filter")
data class RouteFilterRef(
    val filterId: Int
)
