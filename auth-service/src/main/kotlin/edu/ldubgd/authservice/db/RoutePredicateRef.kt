package edu.ldubgd.authservice.db

import org.springframework.data.relational.core.mapping.Table

@Table(name = "route_predicate")
data class RoutePredicateRef(
    val predicateId: Int
)
