package edu.ldubgd.authservice

import edu.ldubgd.authservice.policy.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OperationPolicyUnitTests {
    private val resolver = PolicyResolver()
    private val matcher = OperationMatcher()
    private fun policy(source: PolicySource, type: AccessType, roles: Set<String> = emptySet(), force: Boolean = false) =
        AccessPolicy(source.ordinal.toLong(), source, type, force, roles)
    private fun operation(id: Long, path: String, active: Boolean = true) = ApiOperation(id, 1, path, path, "GET", active)

    @Test fun `missing declaration defaults to authentication and explicit public is supported`() {
        assertEquals(AccessType.AUTHENTICATED, resolver.resolve(emptyList()).accessType)
        assertEquals(AccessType.PUBLIC, resolver.resolve(listOf(
            policy(PolicySource.DEFAULT, AccessType.AUTHENTICATED), policy(PolicySource.OPENAPI, AccessType.PUBLIC)
        )).accessType)
    }

    @Test fun `normal admin and central declarations cannot weaken service requirements`() {
        val resolved = resolver.resolve(listOf(
            policy(PolicySource.OPENAPI, AccessType.ROLES, setOf("ROLE_A")),
            policy(PolicySource.CENTRAL, AccessType.PUBLIC), policy(PolicySource.ADMIN, AccessType.PUBLIC)
        ))
        assertEquals(AccessType.ROLES, resolved.accessType)
        assertEquals(listOf(setOf("ROLE_A")), resolved.roleGroups)
    }

    @Test fun `different role restrictions are conjunctive not a permissive union`() {
        assertEquals(2, resolver.resolve(listOf(
            policy(PolicySource.OPENAPI, AccessType.ROLES, setOf("ROLE_A")),
            policy(PolicySource.CENTRAL, AccessType.ROLES, setOf("ROLE_B"))
        )).roleGroups.size)
    }

    @Test fun `explicit forced admin override can relax a blocked declaration`() {
        assertEquals(AccessType.PUBLIC, resolver.resolve(listOf(
            policy(PolicySource.OPENAPI, AccessType.BLOCKED),
            policy(PolicySource.ADMIN, AccessType.PUBLIC, force = true)
        )).accessType)
    }

    @Test fun `empty role requirement and blocked policy deny access`() {
        assertEquals(AccessType.BLOCKED, resolver.resolve(listOf(policy(PolicySource.OPENAPI, AccessType.ROLES))).accessType)
        assertEquals(AccessType.BLOCKED, resolver.resolve(listOf(
            policy(PolicySource.OPENAPI, AccessType.PUBLIC), policy(PolicySource.CENTRAL, AccessType.BLOCKED)
        )).accessType)
    }

    @Test fun `static path outranks parameter and retired static path cannot fall through`() {
        val template = operation(1, "/items/{id}")
        assertEquals(2L, matcher.find(listOf(template, operation(2, "/items/current")), "/items/current", "GET")?.id)
        assertNull(matcher.find(listOf(template, operation(2, "/items/current", false)), "/items/current", "GET"))
        assertEquals(1L, matcher.find(listOf(template), "/items/123", "get")?.id)
    }

    @Test fun `ambiguous templates wrong method and unsupported wildcard fail closed`() {
        assertNull(matcher.find(listOf(operation(1, "/items/{id}"), operation(2, "/items/{name}")), "/items/1", "GET"))
        assertNull(matcher.find(listOf(operation(1, "/items/{id}")), "/items/1", "POST"))
        assertNull(matcher.find(listOf(operation(1, "/items/**")), "/items/1", "GET"))
    }
}
