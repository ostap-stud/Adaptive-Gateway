package edu.ldubgd.authservice.db

import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface ServiceRepository : CrudRepository<Service, Int> {
    fun findByServiceName(serviceName: String): Service?
}