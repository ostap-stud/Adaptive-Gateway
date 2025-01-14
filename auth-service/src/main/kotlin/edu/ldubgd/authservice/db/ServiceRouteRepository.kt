package edu.ldubgd.authservice.db

import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface ServiceRouteRepository : CrudRepository<ServiceRoute, Int> {
    fun findByRoute(route: String): ServiceRoute?
}