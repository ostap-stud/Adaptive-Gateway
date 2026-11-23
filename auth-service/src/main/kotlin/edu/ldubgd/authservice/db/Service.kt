package edu.ldubgd.authservice.db

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.MappedCollection
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime

@Table(name = "service")
data class Service(
    @Id
    val id: Int,
    val serviceName: String,
    val serviceDesc: String?,
    @MappedCollection(idColumn = "service_id")
    val routes: MutableSet<ServiceRoute> = mutableSetOf(),
    val managementMode: ManagementMode = ManagementMode.MANUAL,
    val discoveryStatus: DiscoveryStatus = DiscoveryStatus.UNKNOWN,
    val openapiPath: String? = null,
    val openapiHash: String? = null,
    val lastSeenAt: OffsetDateTime? = null,
    val lastSyncedAt: OffsetDateTime? = null,
    val configurationVersion: Long = 0
)
