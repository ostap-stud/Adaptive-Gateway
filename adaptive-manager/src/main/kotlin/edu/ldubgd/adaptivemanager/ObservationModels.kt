package edu.ldubgd.adaptivemanager

import java.time.Instant
import java.util.UUID

enum class AccessType { PUBLIC, AUTHENTICATED, BLOCKED }
data class DesiredOperation(val gatewayPath: String, val backendPath: String, val httpMethod: String,
    val operationId: String? = null, val access: AccessType, val roles: Set<String> = emptySet(),
    val permissions: Set<String> = emptySet())
data class DesiredRoute(val path: String, val order: Int = 0)
data class ConfigurationCandidate(val specHash: String, val openapiPath: String, val route: DesiredRoute,
    val operations: List<DesiredOperation>)
data class DesiredServiceConfiguration(val requestId: UUID, val expectedVersion: Long, val specHash: String,
    val openapiPath: String, val route: DesiredRoute, val operations: List<DesiredOperation>)
data class Analysis(val candidate: ConfigurationCandidate, val warnings: List<String>)
data class ServiceTarget(val name: String, val instanceId: String, val origin: String, val openapiPath: String)
data class DiscoveryEntry(val name: String, val status: String, val target: ServiceTarget? = null, val message: String? = null)
data class SyncEvent(val requestId: UUID, val status: String, val configurationVersion: Long)
data class SyncOutcome(val action: String, val status: String, val requestId: UUID? = null,
    val configurationVersion: Long? = null, val message: String? = null)
data class AuthPreflight(val status: String, val expectedVersion: Long? = null, val message: String? = null,
    val specHash: String? = null, val latestEvent: SyncEvent? = null, val pendingRefresh: List<SyncEvent> = emptyList())
data class ServiceObservation(val name: String, val status: String, val observedAt: Instant,
    val instanceId: String? = null, val candidate: ConfigurationCandidate? = null,
    val warnings: List<String> = emptyList(), val auth: AuthPreflight? = null,
    val proposedRequest: DesiredServiceConfiguration? = null, val message: String? = null,
    val sync: SyncOutcome? = null)
data class ObservationSnapshot(val mode: String = "OBSERVE", val registryStatus: String = "NOT_POLLED",
    val observedAt: Instant? = null, val lastSuccessfulPollAt: Instant? = null,
    val observations: List<ServiceObservation> = emptyList(), val message: String? = null)
class ObservationFailure(val code: String) : RuntimeException(code)
