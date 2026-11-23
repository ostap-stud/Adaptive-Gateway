package edu.ldubgd.authservice.policy

import edu.ldubgd.authservice.db.*
import edu.ldubgd.authservice.db.service.PredicateService
import edu.ldubgd.authservice.db.service.RouteService
import edu.ldubgd.authservice.security.UserDetailsImpl
import edu.ldubgd.authservice.security.jwt.JwtUtil
import edu.ldubgd.authservice.security.requests.ValidateTokenRequest
import io.jsonwebtoken.JwtException
import org.springframework.http.HttpStatus
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.AntPathMatcher

data class AuthorizationDecision(val status: HttpStatus, val message: String)

@Service
class AuthorizationDecisionService(
    private val routes: ServiceRouteRepository,
    private val routeService: RouteService,
    private val predicates: PredicateService,
    private val roles: RoleRepository,
    private val operations: ApiOperationRepository,
    private val policies: AccessPolicyRepository,
    private val matcher: OperationMatcher,
    private val resolver: PolicyResolver,
    private val jwt: JwtUtil,
    private val users: UserDetailsService
) {
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun decide(authorization: String?, request: ValidateTokenRequest): AuthorizationDecision {
        if (!request.routePath.startsWith('/') || request.routePath.contains('?') || request.routePath.contains('#'))
            return result(HttpStatus.BAD_REQUEST, "Expected an absolute request path without query or fragment")
        val selected = if (request.routeId != null) {
            routes.findById(request.routeId).orElse(null)?.takeIf { route ->
                pathMatches(route, request.routePath) && routeService.findRouteByRequestMethod(listOf(route), request.method) != null
            } ?: return notFound()
        } else {
            // Old clients are supported only for legacy paths. Never bypass operation policies.
            val matches = routes.findAll().filter { pathMatches(it, request.routePath) }
            if (matches.any { it.source == RouteSource.GENERATED }) return notFound()
            val user = authenticatedUser(authorization) ?: return unauthorized()
            val route = routeService.findRouteByRequestMethod(matches, request.method) ?: return notFound()
            return legacyDecision(route, user)
        }
        if (selected.source == RouteSource.MANUAL) {
            val user = authenticatedUser(authorization) ?: return unauthorized()
            return legacyDecision(selected, user)
        }
        val serviceId = selected.serviceId ?: return notFound()
        val operation = matcher.find(operations.findByService(serviceId), request.routePath, request.method) ?: return notFound()
        val policy = resolver.resolve(policies.findByOperation(operation.id))
        if (policy.accessType == AccessType.BLOCKED) return forbidden()
        if (policy.accessType == AccessType.PUBLIC) return allowed()
        val user = authenticatedUser(authorization) ?: return unauthorized()
        if (policy.accessType == AccessType.ROLES && !policy.roleGroups.all { group -> user.getRoles().any { it in group } })
            return forbidden()
        return allowed()
    }

    private fun pathMatches(route: ServiceRoute, path: String): Boolean {
        val paths = listOf(route.route) + predicates.getPredicatesByName("Path")
            .filter { p -> route.predicates.any { it.predicateId == p.id } }
            .flatMap { it.args["patterns"].orEmpty().split(',').filter(String::isNotBlank) }
        return paths.any { AntPathMatcher().match(it.trim(), path) }
    }

    private fun legacyDecision(route: ServiceRoute, user: UserDetailsImpl): AuthorizationDecision {
        val required = roles.findByIdIn(route.getRolesId()).mapNotNull { it.role }.toSet()
        return if (user.getRoles().any { it in required }) allowed() else unauthorized()
    }

    private fun authenticatedUser(header: String?): UserDetailsImpl? {
        if (header == null || !header.startsWith("Bearer ")) return null
        return try {
            val name = jwt.getNameFromToken(header.substring(7)) ?: return null
            if (!jwt.getClaimFromToken(header.substring(7)) { it.expiration?.after(java.util.Date()) == true }) return null
            users.loadUserByUsername(name) as UserDetailsImpl
        } catch (_: JwtException) { null }
          catch (_: IllegalArgumentException) { null }
          catch (_: UsernameNotFoundException) { null }
    }

    private fun result(status: HttpStatus, message: String) = AuthorizationDecision(status, message)
    private fun allowed() = result(HttpStatus.OK, "Access allowed")
    private fun unauthorized() = result(HttpStatus.UNAUTHORIZED, "Authentication or legacy route permission required")
    private fun forbidden() = result(HttpStatus.FORBIDDEN, "Access denied by operation policy")
    private fun notFound() = result(HttpStatus.NOT_FOUND, "No active unambiguous route or operation")
}
