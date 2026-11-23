package edu.ldubgd.authservice.admin

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Component
class ManagerAdminClient(private val json: ObjectMapper,
    @Value("\${adaptive.manager.base-url:}") private val baseUrl: String,
    @Value("\${adaptive.sync-key:}") private val key: String,
    @Value("\${adaptive.manager.timeout-ms:60000}") private val timeoutMs: Long) {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()

    fun snapshot(): JsonNode = send("GET", "/observations")
    fun poll(): JsonNode = send("POST", "/poll")
    fun mode(mode: String): JsonNode {
        if (mode !in setOf("OBSERVE", "APPLY")) throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Use OBSERVE or APPLY")
        return send("PUT", "/mode", json.writeValueAsString(mapOf("mode" to mode)))
    }

    private fun send(method: String, path: String, payload: String? = null): JsonNode {
        if (baseUrl.isBlank() || key.toByteArray().size < 32)
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Manager administration is not configured")
        try {
            val base = URI(baseUrl)
            require(base.scheme in setOf("http", "https") && base.host != null && base.userInfo == null &&
                base.query == null && base.fragment == null && base.path.orEmpty() in setOf("", "/"))
            val request = HttpRequest.newBuilder(URI(baseUrl.trimEnd('/') + "/internal/admin" + path))
                .timeout(Duration.ofMillis(timeoutMs)).header("X-Adaptive-Key", key)
            if (payload != null) request.header("Content-Type", "application/json")
            request.method(method, if (payload == null) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofString(payload))
            val response = http.send(request.build(), HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 409)
                throw ResponseStatusException(HttpStatus.CONFLICT, "Observation is already running")
            if (response.statusCode() == 422)
                throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Manager rejected mode change; APPLY requires Auth configuration and service admission")
            if (response.statusCode() != 200)
                throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Manager returned HTTP ${response.statusCode()}")
            val result = json.readTree(response.body())
            if (result == null || !result.isObject || !result.path("mode").isTextual || !result.path("observations").isArray)
                throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid Manager response")
            return result
        } catch (e: ResponseStatusException) { throw e }
        catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Manager request interrupted")
        } catch (_: Exception) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Manager unavailable; inspect its latest result before retrying")
        }
    }
}
