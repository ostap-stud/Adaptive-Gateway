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

@Service
class RouteServiceImpl(
    private val serviceRouteRepository: ServiceRouteRepository,
    private val serviceRepository: ServiceRepository,
    private val roleRepository: RoleRepository,
    private val filterService: FilterService,
    private val predicateService: PredicateService,
    private val pathMatcher: PathMatcher = AntPathMatcher()
) : RouteService {

    override fun findRoutesByRequestPath(requestPath: String): List<ServiceRoute> {
        return serviceRouteRepository.findAll().filter { route ->
            pathMatcher.match(route.route, requestPath)
        }
    }

    override fun findRouteByRequestMethod(routes: List<ServiceRoute>, requestMethod: String): ServiceRoute? {
        val compatibleRoutes = mutableSetOf<ServiceRoute>()
        val allMethodPredicates = predicateService.getPredicatesByName("Method")
        routes.forEach { pm ->
            val pathMethods = allMethodPredicates.find { amp -> pm.predicates.any { amp.id == it.predicateId } }
            if (pathMethods == null || pathMethods.args["methods"]?.contains(requestMethod.uppercase(Locale.getDefault())) == true) {
                compatibleRoutes.add(pm)
            }
        }
        return when(compatibleRoutes.size){
            0 -> null
            1 -> compatibleRoutes.first()
            else -> compatibleRoutes.minBy { it.order!! }
        }
    }

    override fun insertRoute(routeDTO: ServiceRouteDTO): ServiceRoute {
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
        return saveRoute(route, routeDTO)
    }

    override fun updateRoute(currentRoute: Optional<ServiceRoute>, routeDTO: ServiceRouteDTO): ServiceRoute {
        val updated = currentRoute.get().copy(
            route = routeDTO.route,
            routeDesc = routeDTO.routeDescription,
            isInternal = routeDTO.isInternal,
            order = routeDTO.order,
            serviceId =
            if (routeDTO.isInternal) null
            else serviceRepository.findByServiceName(routeDTO.serviceName)?.id!!
        )
        updated.apply {
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