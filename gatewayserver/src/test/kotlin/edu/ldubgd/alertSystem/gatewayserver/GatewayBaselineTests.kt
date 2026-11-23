package edu.ldubgd.alertSystem.gatewayserver

import edu.ldubgd.alertSystem.gatewayserver.data.ServiceRouteRepository
import edu.ldubgd.alertSystem.gatewayserver.data.ServiceRouteDTO
import edu.ldubgd.alertSystem.gatewayserver.data.ServiceRoutePathDTO
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.cloud.gateway.route.RouteLocator
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = [
    "server.ssl.enabled=false", "eureka.client.enabled=false",
    "spring.cloud.discovery.enabled=false", "gateway.internal-routes.refresh-delay-ms=50"
])
class GatewayBaselineTests {
    @LocalServerPort private var port: Int = 0
    @MockBean private lateinit var repository: ServiceRouteRepository
    @Autowired private lateinit var locator: RouteLocator
    @Autowired private lateinit var cache: InternalRoutesCache
    private val http = HttpClient.newHttpClient()

    @Test
    fun `real Actuator refresh reloads routes and returns their representation`() {
        `when`(repository.findAllRoutes()).thenReturn(listOf(
            ServiceRouteDTO(9876, "/baseline/**", "http://localhost:1", serviceDatabaseId=77, configurationVersion=9)
        ))
        val refresh = request("/actuator/gateway/refresh", true)
        assertEquals(200, refresh.statusCode())
        awaitCondition {
            locator.routes.collectList().block(Duration.ofSeconds(2))!!
                .any { it.id == "http://localhost:1_route-9876" }
        }
        val routes = request("/actuator/gateway/routes")
        assertEquals(200, routes.statusCode())
        assertTrue(routes.body().contains("baseline"), routes.body())
        val published = jacksonObjectMapper().readTree(routes.body()).first { it["route_id"].asText() == "http://localhost:1_route-9876" }
        assertEquals(77, published["metadata"]["serviceDatabaseId"].asInt())
        assertEquals(9L, published["metadata"]["configurationVersion"].asLong())
        val health = request("/actuator/health")
        assertTrue(health.body().contains("status"), health.body())
    }

    @Test
    fun `scheduled refresh changes internal path matching without manual invocation`() {
        `when`(repository.findAllInternalRoutes())
            .thenReturn(listOf(ServiceRoutePathDTO("/scheduled-check/**")))
        awaitCondition { cache.isInternal("/scheduled-check/resource") }
        `when`(repository.findAllInternalRoutes()).thenReturn(emptyList())
        awaitCondition { !cache.isInternal("/scheduled-check/resource") }
    }

    private fun request(path: String, post: Boolean = false): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://localhost:$port$path"))
            .timeout(Duration.ofSeconds(5))
        if (post) builder.POST(HttpRequest.BodyPublishers.noBody())
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos()
        while (System.nanoTime() < deadline) {
            if (condition()) return
            Thread.sleep(25)
        }
        assertTrue(condition(), "Expected state was not applied within 5 seconds")
    }
}
