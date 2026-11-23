package edu.ldubgd.adaptivemanager

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.stereotype.Component
import java.net.URI
import java.util.Locale

@Component
class DiscoveryWatcher(private val http: ReadOnlyJsonClient, private val settings: ManagerProperties) {
    fun discover(): List<DiscoveryEntry> = parse(http.get(URI(settings.eurekaUrl.trimEnd('/') + "/apps")))

    fun parse(root: JsonNode): List<DiscoveryEntry> {
        val applications = root.path("applications")
        if (!applications.isObject) throw ObservationFailure("INVALID_REGISTRY")
        val apps = collection(applications.get("application"))
        val grouped = apps.groupBy {
            val raw = it.path("name")
            if (!raw.isTextual) throw ObservationFailure("INVALID_REGISTRY")
            raw.asText().lowercase(Locale.ROOT)
        }
        if (grouped.size > 200) throw ObservationFailure("REGISTRY_TOO_LARGE")
        return grouped.toSortedMap().map { (name, entries) ->
            val instances = entries.flatMap { collection(it.get("instance")) }
            when {
                name in RESERVED -> DiscoveryEntry(name, "IGNORED", message="Infrastructure service")
                !name.matches(Regex("[a-z][a-z0-9-]{0,62}")) -> DiscoveryEntry(name, "INVALID_METADATA", message="Unsupported service name")
                instances.none { it.path("metadata").path("gateway.enabled").asText() == "true" } ->
                    DiscoveryEntry(name, "IGNORED", message="No explicit gateway.enabled=true")
                else -> {
                    val up = instances.filter { it.path("status").asText() == "UP" }
                    when {
                        up.isEmpty() -> DiscoveryEntry(name, "NO_UP_INSTANCE")
                        up.size != 1 -> DiscoveryEntry(name, "MULTIPLE_INSTANCES", message="Single-instance observation only")
                        else -> target(name, up.single())
                    }
                }
            }
        }
    }

    private fun target(name: String, instance: JsonNode): DiscoveryEntry {
        val metadata = instance.path("metadata")
        if (metadata.path("gateway.enabled").asText() != "true" ||
            metadata.path("gateway.base-path").asText() != "/$name")
            return DiscoveryEntry(name, "INVALID_METADATA", message="Explicit matching base path and opt-in are required")
        val path = metadata.path("gateway.openapi-path").asText()
        if (!path.startsWith("/$name/") || path.length > 2048 ||
            !path.split('/').drop(1).all { it.matches(Regex("[A-Za-z0-9_.~-]+")) && it !in setOf(".", "..") })
            return DiscoveryEntry(name, "INVALID_METADATA", message="Invalid OpenAPI path")
        return try {
            val secure = instance.path("securePort").path("@enabled").asText() == "true"
            val selected = instance.path(if (secure) "securePort" else "port")
            require(selected.path("@enabled").asText() == "true")
            val port = selected.path("$").asInt(-1)
            require(port in 1..65535)
            val host = instance.path("hostName").asText()
            require(host.isNotBlank())
            val origin = URI(if (secure) "https" else "http", null, host, port, null, null, null)
            require(origin.host != null)
            val id = instance.path("instanceId").asText()
            require(id.isNotBlank())
            DiscoveryEntry(name, "DISCOVERED", ServiceTarget(name, id, origin.toString(), path))
        } catch (_: IllegalArgumentException) {
            DiscoveryEntry(name, "INVALID_METADATA", message="Invalid instance address")
        } catch (_: java.net.URISyntaxException) {
            DiscoveryEntry(name, "INVALID_METADATA", message="Invalid instance address")
        }
    }

    private fun collection(node: JsonNode?): List<JsonNode> = when {
        node == null -> emptyList()
        node.isArray -> node.toList().also { if (it.any { value -> !value.isObject }) throw ObservationFailure("INVALID_REGISTRY") }
        node.isObject -> listOf(node)
        else -> throw ObservationFailure("INVALID_REGISTRY")
    }
    companion object {
        val RESERVED = setOf("auth-service", "gateway", "gatewayserver", "gateway-server", "discoveryserver", "discovery-server", "adaptive-manager")
    }
}
