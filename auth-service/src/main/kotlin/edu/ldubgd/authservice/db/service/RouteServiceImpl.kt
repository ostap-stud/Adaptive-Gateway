package edu.ldubgd.authservice.db.service

import edu.ldubgd.authservice.db.RoleRepository
import edu.ldubgd.authservice.db.ServiceRepository
import edu.ldubgd.authservice.db.ServiceRoute
import edu.ldubgd.authservice.db.ServiceRouteRepository
import edu.ldubgd.authservice.db.dto.ServiceRouteDTO
import org.springframework.stereotype.Service
import org.springframework.util.AntPathMatcher
import org.springframework.util.PathMatcher
import java.util.*
import edu.ldubgd.authservice.adaptive.ConfigurationWriteLock
import org.springframework.transaction.annotation.Transactional

@Service
class RouteServiceImpl(
    private val serviceRouteRepository: ServiceRouteRepository,
    private val serviceRepository: ServiceRepository,
    private val roleRepository: RoleRepository,
    private val filterService: FilterService,
    private val predicateService: PredicateService,
    private val locks: ConfigurationWriteLock,
    private val pathMatcher: PathMatcher = AntPathMatcher()
) : RouteService {

    override fun findRoutesByRequestPath(requestPath: String): List<ServiceRoute> {
        return serviceRouteRepository.findAll().filter { route ->
            route.active && pathMatcher.match(route.route, requestPath)
        }
    }

    override fun findRouteByRequestMethod(routes: List<ServiceRoute>, requestMethod: String): ServiceRoute? {
        val method = requestMethod.uppercase(Locale.ROOT)
        val allMethodPredicates = predicateService.getPredicatesByName("Method")
        return routes.filter { route ->
            // Separate predicates are ANDed, while methods within one predicate are ORed.
            route.active && allMethodPredicates.filter { predicate ->
                route.predicates.any { it.predicateId == predicate.id }
            }.all { predicate ->
                predicate.args["methods"].orEmpty().split(',')
                    .map { it.trim().uppercase(Locale.ROOT) }
                    .contains(method)
            }
        }.minWithOrNull(compareBy<ServiceRoute> { it.order ?: 0 }.thenBy { it.id })
    }

    @Transactional
    override fun insertRoute(routeDTO: ServiceRouteDTO): ServiceRoute {
        locks.lock()
        val route = ServiceRoute(
            id = 0,
            route = routeDTO.route,
            routeDesc = routeDTO.routeDescription,
            isInternal = routeDTO.isInternal,
            order = routeDTO.order,
            serviceId =
            if (routeDTO.isInternal) null
            else serviceRepository.findByServiceName(routeDTO.serviceName)?.id!!
        )
        locks.service(route.serviceId)
        return saveRoute(route, routeDTO)
    }

    @Transactional
    override fun updateRoute(currentRoute: Optional<ServiceRoute>, routeDTO: ServiceRouteDTO): ServiceRoute {
        locks.lock()
        val latest = serviceRouteRepository.findById(currentRoute.get().id).orElseThrow()
        val targetService = if (routeDTO.isInternal) null else serviceRepository.findByServiceName(routeDTO.serviceName)?.id
        require(latest.source != edu.ldubgd.authservice.db.RouteSource.GENERATED ||
            (!routeDTO.isInternal && targetService == latest.serviceId)) {
            "A generated route cannot be transferred to another service or made internal"
        }
        locks.service(latest.serviceId)
        if (targetService != latest.serviceId) locks.service(targetService)
        val updated = latest.copy(
            route = routeDTO.route,
            routeDesc = routeDTO.routeDescription,
            isInternal = routeDTO.isInternal,
            order = routeDTO.order,
            serviceId =
            if (routeDTO.isInternal) null
            else serviceRepository.findByServiceName(routeDTO.serviceName)?.id!!
        )
        updated.apply {
            markManualChange()
            roles.clear()
            filters.clear()
            predicates.clear()
        }
        return saveRoute(updated, routeDTO)
    }

    private fun saveRoute(route: ServiceRoute, routeDTO: ServiceRouteDTO): ServiceRoute {
        val roles = roleRepository.findRolesByRoleIn(routeDTO.roles.toList())
        val filters = filterService.getFiltersFromDTO(routeDTO.filters)
        val predicates = predicateService.getPredicatesAndSaveNew(routeDTO.predicates)
        roles.forEach {
            route.addRole(it)
        }
        filters.forEach {
            route.addFilter(it)
        }
        predicates.forEach {
            route.addPredicate(it)
        }
        return serviceRouteRepository.save(route)
    }

}
