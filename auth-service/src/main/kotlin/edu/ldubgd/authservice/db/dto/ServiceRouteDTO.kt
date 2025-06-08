package edu.ldubgd.authservice.db.dto

data class ServiceRouteDTO(
    val route: String,
    val routeDescription: String?,
    val serviceName: String,
    val isInternal: Boolean = false,
    val roles: Set<String> = emptySet()
)
