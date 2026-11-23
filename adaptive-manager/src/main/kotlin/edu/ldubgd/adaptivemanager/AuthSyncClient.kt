package edu.ldubgd.adaptivemanager

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import java.net.URI
import java.util.UUID

/** Writes are restricted to the configured Auth origin, explicit APPLY mode and service allowlist. */
@Component
class AuthSyncClient(private val http: JsonHttpTransport, private val settings: ManagerProperties,
    private val json: ObjectMapper) {
    fun apply(name: String, desired: DesiredServiceConfiguration): SyncOutcome =
        exchange(name, "PUT", "/configuration", desired.requestId, json.writeValueAsBytes(desired), "APPLY")

    fun retry(name: String, event: SyncEvent): SyncOutcome =
        exchange(name, "POST", "/events/${event.requestId}/refresh", event.requestId, null, "RETRY_REFRESH")

    private fun exchange(name: String, method: String, path: String, requestId: UUID, body: ByteArray?, action: String): SyncOutcome {
        check(settings.mode == "APPLY" && settings.admits(name))
        val uri = URI(settings.authBaseUrl.trimEnd('/') + "/auth-service/internal/adaptive/services/$name" + path)
        return try {
            val response = http.request(method, uri, mapOf("X-Adaptive-Key" to settings.authKey), body)
            val returned = UUID.fromString(response.path("requestId").asText())
            val status = response.path("status").asText()
            val version = response.path("configurationVersion")
            require(returned == requestId && status in setOf("APPLIED","NO_CHANGE","PENDING_REFRESH","FAILED_REFRESH","SUPERSEDED") &&
                version.isIntegralNumber && version.canConvertToLong() && version.asLong() >= 0)
            SyncOutcome(action,status,returned,version.asLong())
        } catch (e: ObservationFailure) {
            SyncOutcome(action, if (e.code == "HTTP_409") "CONFLICT" else "REQUEST_FAILED", requestId, message=e.code)
        } catch (_: IllegalArgumentException) {
            SyncOutcome(action,"INVALID_RESPONSE",requestId)
        }
    }
}
