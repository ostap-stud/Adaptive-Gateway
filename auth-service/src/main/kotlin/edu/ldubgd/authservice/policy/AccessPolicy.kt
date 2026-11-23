package edu.ldubgd.authservice.policy

enum class AccessType { PUBLIC, AUTHENTICATED, ROLES, BLOCKED }
enum class PolicySource { DEFAULT, OPENAPI, CENTRAL, ADMIN }

data class AccessPolicy(
    val id: Long,
    val source: PolicySource,
    val accessType: AccessType,
    val forceOverride: Boolean = false,
    val roles: Set<String> = emptySet(),
    val permissionGroups: List<Set<String>> = emptyList()
)

// Each group is OR; all groups are required. This avoids widening two different role restrictions.
data class EffectivePolicy(val accessType: AccessType, val roleGroups: List<Set<String>> = emptyList())
