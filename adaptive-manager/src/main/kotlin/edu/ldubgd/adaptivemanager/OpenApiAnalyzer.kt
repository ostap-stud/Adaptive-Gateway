package edu.ldubgd.adaptivemanager

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.stereotype.Component
import org.springframework.web.util.pattern.PathPatternParser
import java.net.URI
import java.util.Locale

@Component
class OpenApiAnalyzer(private val builder: DesiredConfigurationBuilder) {
    fun analyze(target: ServiceTarget, document: JsonNode): Analysis {
        valid(document.isObject && document.path("openapi").asText().matches(Regex("3\\.[01]\\.[0-9]+")), "UNSUPPORTED_OPENAPI")
        valid(document.path("info").isObject && document.path("info").path("title").isTextual &&
            document.path("info").path("version").isTextual, "INVALID_INFO")
        valid(document.path("paths").isObject, "INVALID_PATHS")
        checkServers(document)
        val operations = mutableListOf<DesiredOperation>()
        val warnings = mutableListOf<String>()
        val signatures = mutableSetOf<Pair<String, String>>()
        document.path("paths").fields().forEach { (path, item) ->
            if (path.startsWith("x-")) return@forEach
            valid(path.startsWith("/${target.name}/") && validPath(path), "PATH_OUTSIDE_SUPPORTED_NAMESPACE")
            valid(item.isObject && !item.has("\$ref"), "UNSUPPORTED_PATH_REFERENCE")
            checkServers(item)
            valid(item.fieldNames().asSequence().all { it in METHODS || it in PATH_FIELDS || it.startsWith("x-") }, "UNSUPPORTED_PATH_FIELD")
            METHODS.forEach operationLoop@ { method ->
                val operation = item.get(method) ?: return@operationLoop
                valid(operation.isObject && !operation.has("\$ref"), "INVALID_OPERATION")
                checkServers(operation)
                valid(operation.path("responses").isObject && operation.path("responses").size() > 0, "INVALID_RESPONSES")
                val id = operation.get("operationId")
                valid(id == null || (id.isTextual && id.asText().length <= 255), "INVALID_OPERATION_ID")
                valid(signatures.add(path.replace(Regex("\\{[^/]+}"), "{}") to method), "AMBIGUOUS_OPERATIONS")
                val (access, reason, permissions) = policy(document, item, operation)
                if (reason != null) warnings.add("${method.uppercase(Locale.ROOT)} $path: $reason")
                operations.add(DesiredOperation(path, path, method.uppercase(Locale.ROOT), id?.asText(), access, permissions=permissions))
            }
        }
        // A valid, successfully fetched empty specification removes the last operation too.
        // Transport errors and malformed/missing paths have already failed before this point.
        valid(operations.size <= 2000, "OVERSIZED_OPERATION_SET")
        if (operations.isEmpty()) warnings.add("No API operations declared; generated operations will be deactivated in APPLY")
        return Analysis(builder.candidate(target, operations), warnings)
    }

    private fun policy(root: JsonNode, path: JsonNode, operation: JsonNode): Triple<AccessType, String?, Set<String>> {
        val nodes = listOf(operation, path, root)
        if (nodes.any { it.has("x-permissions") || it.has("x-roles") })
            return Triple(AccessType.BLOCKED, "Use the supported x-access contract", emptySet())
        val standard = standardPolicy(root, operation)
        val declaration = nodes.firstOrNull { it.has("x-access") }?.get("x-access")
            ?: return Triple(standard.first, standard.second, emptySet())
        fun blocked(reason: String) = Triple(AccessType.BLOCKED, reason, emptySet<String>())
        if (!declaration.isObject || declaration.fieldNames().asSequence().any { it !in setOf("type", "permissions") })
            return blocked("Malformed x-access")
        val kind = declaration.path("type").asText()
        if (kind !in setOf("PUBLIC", "AUTHENTICATED", "PERMISSION", "INTERNAL")) return blocked("Unknown x-access type")
        if (kind != "PERMISSION" && declaration.has("permissions")) return blocked("Unexpected permissions")
        if (kind == "INTERNAL" || standard.first == AccessType.BLOCKED) return blocked("Internal or unsupported security requirement")
        if (kind == "PERMISSION") {
            val names = declaration.path("permissions")
            if (!names.isArray || names.isEmpty || names.size() > 100 || names.any {
                !it.isTextual || !it.asText().matches(Regex("[a-zA-Z][a-zA-Z0-9_.:-]{0,127}"))
            }) return blocked("Invalid permission declaration")
            return Triple(AccessType.AUTHENTICATED, null, names.map { it.asText() }.toSortedSet())
        }
        // An explicit JWT security declaration cannot be weakened by x-access PUBLIC.
        val explicitSecurity = operation.has("security") || root.has("security")
        val access = if (kind == "AUTHENTICATED" || (explicitSecurity && standard.first == AccessType.AUTHENTICATED))
            AccessType.AUTHENTICATED else AccessType.PUBLIC
        return Triple(access, null, emptySet())
    }

    private fun standardPolicy(root: JsonNode, operation: JsonNode): Pair<AccessType, String?> {
        val security = if (operation.has("security")) operation.get("security") else root.get("security")
        if (security == null) return AccessType.AUTHENTICATED to "No declaration; conservative AUTHENTICATED default"
        if (!security.isArray || security.any { !it.isObject || it.fields().asSequence().any { entry ->
            !entry.value.isArray || entry.value.any { scope -> !scope.isTextual }
        } }) return AccessType.BLOCKED to "Malformed security declaration"
        if (security.isEmpty || security.any { it.isEmpty }) return AccessType.PUBLIC to null
        val schemes = root.path("components").path("securitySchemes")
        // OR alternatives: a supported JWT alternative is sufficient.
        // AND of several independent mechanisms cannot be represented by one central JWT.
        val supported = security.any { alternative ->
            alternative.size() == 1 && alternative.fields().asSequence().all { (name, scopes) ->
                val scheme = schemes.path(name)
                scopes.isEmpty && !scheme.has("\$ref") &&
                    scheme.path("type").asText() == "http" &&
                    scheme.path("scheme").asText().equals("bearer", true) &&
                    scheme.path("bearerFormat").asText().equals("JWT", true)
            }
        }
        return if (supported) AccessType.AUTHENTICATED to
            (if (security.size() > 1) "Only the supported JWT alternative will be accepted" else null)
        else AccessType.BLOCKED to "Unsupported security scheme, scopes, roles, reference or combined requirement"
    }

    private fun validPath(path: String): Boolean = try {
        path.length <= 2048 && path.split('/').drop(1).all {
            (it.matches(Regex("[A-Za-z0-9_.~-]+")) && it !in setOf(".", "..")) ||
                it.matches(Regex("\\{[A-Za-z_][A-Za-z0-9_]*}"))
        } && PathPatternParser().parse(path).patternString == path
    } catch (_: IllegalArgumentException) { false }

    private fun checkServers(node: JsonNode) {
        val servers = node.get("servers") ?: return
        valid(servers.isArray, "UNSUPPORTED_SERVERS")
        servers.forEach {
            val url = it.path("url")
            valid(url.isTextual, "UNSUPPORTED_SERVERS")
            val uri = try { URI(url.asText()) } catch (_: Exception) { throw ObservationFailure("UNSUPPORTED_SERVERS") }
            valid(uri.rawPath.orEmpty() in setOf("", "/") && uri.query == null && uri.fragment == null &&
                uri.userInfo == null && (uri.scheme == null || uri.scheme in setOf("http", "https")), "UNSUPPORTED_SERVER_BASE_PATH")
        }
    }
    private fun valid(value: Boolean, code: String) { if (!value) throw ObservationFailure(code) }
    companion object {
        private val METHODS = listOf("get","put","post","delete","options","head","patch","trace")
        private val PATH_FIELDS = setOf("summary","description","servers","parameters")
    }
}
