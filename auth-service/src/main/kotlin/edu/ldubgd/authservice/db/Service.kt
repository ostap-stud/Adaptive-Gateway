package edu.ldubgd.authservice.db

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.MappedCollection
import org.springframework.data.relational.core.mapping.Table

@Table(name = "service")
data class Service(
    @Id
    val id: Int,
    val serviceName: String,
    val serviceDesc: String?,
    @MappedCollection(idColumn = "service_id")
    val routes: MutableSet<ServiceRoute> = mutableSetOf()
)
