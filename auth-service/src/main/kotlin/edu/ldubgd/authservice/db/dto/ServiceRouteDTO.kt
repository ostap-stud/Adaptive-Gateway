package edu.ldubgd.authservice.db.dto

data class ServiceRouteDTO(
    val route: String,
    val routeDescription: String?,
    val serviceName: String,
    val roles: Set<String> = emptySet()
)
