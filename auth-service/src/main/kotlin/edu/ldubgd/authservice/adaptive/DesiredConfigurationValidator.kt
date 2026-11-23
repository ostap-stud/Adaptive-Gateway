package edu.ldubgd.authservice.adaptive

import edu.ldubgd.authservice.policy.AccessType
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.util.pattern.PathPatternParser

@Component
class DesiredConfigurationValidator {
    fun validate(name: String, desired: DesiredServiceConfiguration) {
        val reserved = setOf("auth-service", "gateway", "gatewayserver", "gateway-server", "discoveryserver", "discovery-server", "adaptive-manager")
        valid(name.matches(Regex("[a-z][a-z0-9-]{0,62}")) && name !in reserved, "Unsupported service name")
        valid(desired.expectedVersion >= 0, "expectedVersion must be non-negative")
        valid(desired.specHash.matches(Regex("[a-f0-9]{64}")), "specHash must be a lowercase SHA-256")
        valid(literalPath(desired.openapiPath) && desired.openapiPath.startsWith("/$name/"), "Invalid OpenAPI path")
        valid(desired.operations.size <= 2000, "Too many operations")
        val route = desired.route
        valid(route != null || desired.operations.isEmpty(), "An absent route requires an empty operation list")
        if (route != null) {
            valid(route.path == "/$name/**", "The first sync supports only /service-name/**")
            valid(route.order in 0..10000, "Route order must be between 0 and 10000")
        }
        valid(desired.operations.map { it.gatewayPath to it.httpMethod }.distinct().size == desired.operations.size,
            "Duplicate operation")
        val signatures = mutableSetOf<Pair<String, String>>()
        desired.operations.forEach { op ->
            valid(op.httpMethod in setOf("GET","HEAD","POST","PUT","DELETE","PATCH","OPTIONS","TRACE"), "Invalid HTTP method")
            valid(op.backendPath == op.gatewayPath, "Path rewriting is not supported in this sync")
            valid(op.gatewayPath.startsWith("/$name/") && operationPath(op.gatewayPath), "Invalid operation path")
            valid(op.operationId == null || op.operationId.length <= 255, "operationId is too long")
            val signature = op.gatewayPath.replace(Regex("\\{[^/]+}"), "{}") to op.httpMethod
            valid(signatures.add(signature), "Ambiguous parameterized operations")
            valid(op.roles.size <= 100 && op.roles.all { it.isNotBlank() && it.length <= 255 }, "Invalid roles")
            valid(if (op.access == AccessType.ROLES) op.roles.isNotEmpty() else op.roles.isEmpty(),
                "Only ROLES policies accept a non-empty role set")
            valid(op.permissions.size <= 100 && op.permissions.all { it.matches(Regex("[a-zA-Z][a-zA-Z0-9_.:-]{0,127}")) }, "Invalid permissions")
            valid(op.permissions.isEmpty() || (op.access == AccessType.AUTHENTICATED && op.roles.isEmpty()),
                "Permission declarations require AUTHENTICATED and no service-defined roles")
        }
    }
    private fun literalPath(path: String) = path.length <= 2048 && path.startsWith("/") &&
        path.split('/').drop(1).all { it.matches(Regex("[A-Za-z0-9_.~-]+")) && it != "." && it != ".." }
    private fun operationPath(path: String): Boolean = try {
        path.length <= 2048 && path.split('/').drop(1).all {
            (it.matches(Regex("[A-Za-z0-9_.~-]+")) && it != "." && it != "..") ||
                it.matches(Regex("\\{[A-Za-z_][A-Za-z0-9_]*}"))
        } && PathPatternParser().parse(path).patternString == path
    } catch (_: IllegalArgumentException) { false }
    private fun valid(ok: Boolean, message: String) {
        if (!ok) throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message)
    }
}
