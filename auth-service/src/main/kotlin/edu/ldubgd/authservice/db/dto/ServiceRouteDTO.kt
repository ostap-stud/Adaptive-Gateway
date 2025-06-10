package edu.ldubgd.authservice.db.dto

data class ServiceRouteDTO(
    val route: String,
    val routeDescription: String?,
    val serviceName: String,
    val isInternal: Boolean = false,
    val order: Int = 0,
    val roles: Set<String> = emptySet(),
    val filters: Set<FilterDTO> = emptySet(),
    val predicates: Set<PredicateDTO> = emptySet()
)
