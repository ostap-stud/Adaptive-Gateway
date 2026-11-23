package edu.ldubgd.authservice.adaptive

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class RefreshOutcome(val status: String, val error: String? = null)

@Component
class GatewayRefreshClient(
    private val json: ObjectMapper,
    @Value("\${adaptive.gateway.base-url:}") private val baseUrl: String,
    @Value("\${adaptive.gateway.bearer-token:}") private val bearer: String,
    @Value("\${adaptive.gateway.timeout-ms:3000}") private val timeoutMs: Long,
    @Value("\${adaptive.gateway.key:}") private val gatewayKey: String = ""
) {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build()

    fun refresh(event: AdaptationEvent): RefreshOutcome {
        if (baseUrl.isBlank() || (bearer.isBlank() && gatewayKey.toByteArray().size < 32)) return RefreshOutcome("PENDING_REFRESH", "Gateway refresh credentials are not configured")
        return try {
            val base = URI(baseUrl)
            require(base.scheme in setOf("http", "https") && base.host != null && base.userInfo == null &&
                base.query == null && base.fragment == null && base.path.orEmpty() in setOf("", "/"))
            val response = send("/actuator/gateway/refresh", true)
            if (response.statusCode() != 200) return RefreshOutcome("FAILED_REFRESH", "Gateway refresh returned HTTP ${response.statusCode()}")
            val snapshot = send("/actuator/gateway/routes", false)
            if (snapshot.statusCode() != 200) return RefreshOutcome("FAILED_REFRESH", "Gateway inspection returned HTTP ${snapshot.statusCode()}")
            val routes = json.readTree(snapshot.body())
            if (!routes.isArray) return RefreshOutcome("FAILED_REFRESH", "Invalid Gateway route snapshot")
            val relevant = routes.filter { it.path("metadata").path("serviceDatabaseId").asInt(-1) == event.serviceId }
            val ids = relevant.map { it.path("route_id").asText() }.toSet()
            val version = event.result.configurationVersion
            if (ids == event.expectedRoutes && relevant.all {
                it.path("metadata").path("configurationVersion").asLong(-1) == version
            }) RefreshOutcome("APPLIED")
            else RefreshOutcome("PENDING_REFRESH", "Gateway has not exposed the expected route version yet")
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            RefreshOutcome("FAILED_REFRESH", "Gateway refresh was interrupted")
        } catch (_: Exception) {
            RefreshOutcome("FAILED_REFRESH", "Gateway refresh or verification failed")
        }
    }
    private fun send(path: String, post: Boolean): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI(baseUrl.trimEnd('/') + path))
            .timeout(Duration.ofMillis(timeoutMs))
        if (gatewayKey.toByteArray().size >= 32) request.header("X-Gateway-Key", gatewayKey)
        else request.header("Authorization", "Bearer $bearer")
        if (post) request.POST(HttpRequest.BodyPublishers.noBody())
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString())
    }
}
