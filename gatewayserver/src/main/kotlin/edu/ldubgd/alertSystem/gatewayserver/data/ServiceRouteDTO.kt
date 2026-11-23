package edu.ldubgd.alertSystem.gatewayserver.data

data class ServiceRouteDTO(
    val id: Int,
    val route: String,
    val serviceName: String,
    val order: Int = 0,
    val filters: List<ServiceRouteFilterDTO> = emptyList(),
    val predicates: List<ServiceRoutePredicateDTO> = emptyList(),
    val source: String = "MANUAL",
    val serviceDatabaseId: Int = 0,
    val configurationVersion: Long = 0
)

data class ServiceRoutePathDTO(
    val route: String
)

data class ServiceRouteFilterDTO(
    val name: String,
    val args: Map<String, String>?
)

data class ServiceRoutePredicateDTO(
    val name: String,
    val args: Map<String, String>?
)
