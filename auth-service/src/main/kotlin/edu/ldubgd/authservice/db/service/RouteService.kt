package edu.ldubgd.authservice.db.service

import edu.ldubgd.authservice.db.ServiceRoute
import edu.ldubgd.authservice.db.dto.ServiceRouteDTO
import java.util.*

interface RouteService {
    fun findRoutesByRequestPath(requestPath: String): List<ServiceRoute>
    fun findRouteByRequestMethod(routes: List<ServiceRoute>, requestMethod: String): ServiceRoute?
    fun insertRoute(routeDTO: ServiceRouteDTO): ServiceRoute
    fun updateRoute(currentRoute: Optional<ServiceRoute>, routeDTO: ServiceRouteDTO): ServiceRoute
}