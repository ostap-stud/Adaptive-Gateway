package edu.ldubgd.alertSystem.gatewayserver

import edu.ldubgd.alertSystem.gatewayserver.data.ServiceRoutePathDTO
import edu.ldubgd.alertSystem.gatewayserver.data.ServiceRouteRepository
import jakarta.annotation.PostConstruct
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.util.AntPathMatcher
import org.springframework.util.PathMatcher


@Component
class InternalRoutesCache(
    private val serviceRouteRepository: ServiceRouteRepository
) {

    @Volatile
    private var internalRoutes: Set<ServiceRoutePathDTO> = emptySet()
    private val pathMatcher: PathMatcher = AntPathMatcher()

    @PostConstruct
    fun init() = refreshCachedRoutes()

    @Scheduled(fixedDelay = 300_000)
    fun refreshCachedRoutes(){
        internalRoutes = serviceRouteRepository.findAllInternalRoutes().toSet()
    }

    fun isInternal(path: String) : Boolean{
        return internalRoutes.any { internal ->
            pathMatcher.match(internal.route, path)
        }
    }
}
