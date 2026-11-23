package edu.ldubgd.authservice.policy

import org.springframework.stereotype.Component

@Component
class PolicyResolver {
    fun resolve(policies: List<AccessPolicy>): EffectivePolicy {
        if (policies.groupBy { it.source }.any { it.value.size > 1 }) return blocked()
        val forced = policies.singleOrNull { it.source == PolicySource.ADMIN && it.forceOverride }
        val declared = policies.filter { it.source != PolicySource.DEFAULT }
        val applicable = if (forced != null) listOf(forced) else declared.ifEmpty { policies }
        if (applicable.isEmpty()) return EffectivePolicy(AccessType.AUTHENTICATED)
        if (applicable.any { it.accessType == AccessType.BLOCKED ||
                (it.accessType == AccessType.ROLES && it.roles.isEmpty()) || it.permissionGroups.any { group -> group.isEmpty() } }) return blocked()
        val groups = applicable.filter { it.accessType == AccessType.ROLES }.map { it.roles } + applicable.flatMap { it.permissionGroups }
        if (groups.isNotEmpty()) return EffectivePolicy(AccessType.ROLES, groups)
        return EffectivePolicy(if (applicable.any { it.accessType == AccessType.AUTHENTICATED })
            AccessType.AUTHENTICATED else AccessType.PUBLIC)
    }

    private fun blocked() = EffectivePolicy(AccessType.BLOCKED)
}
