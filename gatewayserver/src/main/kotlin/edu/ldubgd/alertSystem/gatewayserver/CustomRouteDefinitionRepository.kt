package edu.ldubgd.alertSystem.gatewayserver

import edu.ldubgd.alertSystem.gatewayserver.data.ServiceRouteRepository
import org.slf4j.LoggerFactory
import org.springframework.cloud.gateway.filter.FilterDefinition
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition
import org.springframework.cloud.gateway.route.RouteDefinition
import org.springframework.cloud.gateway.route.RouteDefinitionRepository
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.net.URI

@Component
class CustomRouteDefinitionRepository(
    private val serviceRouteRepository: ServiceRouteRepository
) : RouteDefinitionRepository {

    private val logger = LoggerFactory.getLogger(CustomRouteDefinitionRepository::class.java)

    override fun getRouteDefinitions(): Flux<RouteDefinition> {
        val defs = serviceRouteRepository.findAllRoutes().map { routeDTO ->
            RouteDefinition().apply {
                id = "${routeDTO.serviceName}_route-${routeDTO.id}"
                uri = URI.create("lb://${routeDTO.serviceName}")
                order = routeDTO.order
                val extraPaths = routeDTO.predicates
                    .filter { it.name == "Path" }
                    .joinToString(separator = ",") { it.args?.get("patterns").orEmpty() }
                val allPaths =
                    if (extraPaths.isNotEmpty()) { routeDTO.route + ",$extraPaths" }
                    else routeDTO.route
                predicates = mutableListOf(
                    PredicateDefinition("Path=${allPaths}")
                ).apply{
                    addAll(
                        routeDTO.predicates.filterNot { it.name == "Path" }.map { predicateDTO ->
                            PredicateDefinition().apply {
                                name = predicateDTO.name
                                args = predicateDTO.args ?: emptyMap()
                            }
                        }
                    )
                }
                filters = routeDTO.filters.map { filterDTO ->
                    FilterDefinition().apply {
                        name = filterDTO.name
                        args = filterDTO.args ?: emptyMap()
                    }
                }
            }
        }
        logger.warn("Updating route definitions")
        defs.forEach { logger.info("$it") }
        return Flux.fromIterable(defs)
    }

    override fun save(route: Mono<RouteDefinition>?): Mono<Void> = Mono.error(UnsupportedOperationException())
    override fun delete(routeId: Mono<String>?): Mono<Void> = Mono.error(UnsupportedOperationException())
}