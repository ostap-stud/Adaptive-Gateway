package edu.ldubgd.alertSystem.gatewayserver.data

import org.springframework.stereotype.Repository

@Repository
interface ServiceRouteRepository{
    fun findAllRoutes(): List<ServiceRouteDTO>
}