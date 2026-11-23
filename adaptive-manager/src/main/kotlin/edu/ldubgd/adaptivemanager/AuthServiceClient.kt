package edu.ldubgd.adaptivemanager

import org.springframework.stereotype.Component
import java.net.URI
import java.util.UUID
import com.fasterxml.jackson.databind.JsonNode

@Component
class AuthServiceClient(private val http: ReadOnlyJsonClient, private val settings: ManagerProperties) {
    fun inspect(name: String): AuthPreflight {
        if (settings.authBaseUrl.isBlank()) return AuthPreflight("NOT_CONFIGURED")
        require(name.matches(Regex("[a-z][a-z0-9-]{0,62}")))
        return try {
            val result = http.get(URI(settings.authBaseUrl.trimEnd('/') +
                "/auth-service/internal/adaptive/services/$name/configuration"), mapOf("X-Adaptive-Key" to settings.authKey))
            val service = result.path("service")
            val version = service.path("configuration_version")
            if (service.path("service_name").asText() != name || !version.isIntegralNumber ||
                !version.canConvertToLong() || version.asLong() < 0)
                return AuthPreflight("INVALID_RESPONSE")
            val hash = service.get("openapi_hash")?.takeUnless { it.isNull }?.let {
                require(it.isTextual && it.asText().matches(Regex("[a-f0-9]{64}")))
                it.asText()
            }
            val latest = result.get("latestEvent")?.takeUnless { it.isNull }?.let(::event)
            val pending = result.get("pendingRefresh")?.let { value ->
                require(value.isArray)
                value.map(::event).also { events -> require(events.all {
                    it.configurationVersion == version.asLong() && it.status in setOf("PENDING_REFRESH", "FAILED_REFRESH")
                }) }
            }.orEmpty()
            when (service.path("management_mode").asText()) {
                "AUTOMATIC" -> AuthPreflight("AUTOMATIC", version.asLong(), specHash=hash, latestEvent=latest, pendingRefresh=pending)
                "MANUAL", "DISABLED" -> AuthPreflight(service.path("management_mode").asText(), version.asLong())
                else -> AuthPreflight("INVALID_RESPONSE")
            }
        } catch (e: ObservationFailure) {
            AuthPreflight(if (e.code == "HTTP_404") "NOT_REGISTERED" else "UNAVAILABLE", message=e.code)
        } catch (_: IllegalArgumentException) {
            AuthPreflight("INVALID_RESPONSE")
        }
    }

    private fun event(node: JsonNode): SyncEvent {
        val version = node.path("configuration_version")
        require(version.isIntegralNumber && version.canConvertToLong() && version.asLong() >= 0)
        val status = node.path("status").asText()
        require(status in setOf("PENDING_REFRESH","FAILED_REFRESH","APPLIED","NO_CHANGE","SUPERSEDED"))
        return SyncEvent(UUID.fromString(node.path("request_id").asText()), status, version.asLong())
    }
}
