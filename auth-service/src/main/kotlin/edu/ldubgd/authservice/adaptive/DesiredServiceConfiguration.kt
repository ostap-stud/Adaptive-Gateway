package edu.ldubgd.authservice.adaptive

import edu.ldubgd.authservice.policy.AccessType
import java.util.UUID

/** Complete snapshot for one service. No arbitrary filters, URLs, mode or admin policy writes. */
data class DesiredServiceConfiguration(
    val requestId: UUID,
    val expectedVersion: Long,
    val specHash: String,
    val openapiPath: String,
    val route: DesiredRoute?,
    val operations: List<DesiredOperation>
)
data class DesiredRoute(val path: String, val order: Int = 0)
data class DesiredOperation(
    val gatewayPath: String,
    val backendPath: String,
    val httpMethod: String,
    val operationId: String? = null,
    val access: AccessType = AccessType.AUTHENTICATED,
    val roles: Set<String> = emptySet(),
    val permissions: Set<String> = emptySet()
)
data class AdaptiveSyncResult(
    val requestId: UUID,
    val configurationVersion: Long,
    val status: String,
    val counts: Map<String, Int>,
    val error: String? = null
)
