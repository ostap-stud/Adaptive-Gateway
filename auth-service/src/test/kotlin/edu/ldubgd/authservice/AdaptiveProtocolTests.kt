package edu.ldubgd.authservice

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sun.net.httpserver.HttpServer
import edu.ldubgd.authservice.adaptive.*
import edu.ldubgd.authservice.policy.AccessType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import java.net.InetSocketAddress
import java.util.UUID

class AdaptiveProtocolTests {
    private val mapper = jacksonObjectMapper()
    private val event = AdaptationEvent(1, 7, "a".repeat(64), setOf("demo_route-3"),
        AdaptiveSyncResult(UUID.randomUUID(), 4, "PENDING_REFRESH", emptyMap()))

    @Test fun `refresh acknowledges cached route version rather than HTTP 200 alone`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var snapshot = """[{"route_id":"demo_route-3","metadata":{"serviceDatabaseId":7,"configurationVersion":3}}]"""
        var postStatus = 200
        var getStatus = 200
        val authHeaders = mutableListOf<String?>()
        server.createContext("/actuator/gateway/refresh") { exchange ->
            authHeaders.add(exchange.requestHeaders.getFirst("Authorization"))
            assertEquals("POST", exchange.requestMethod)
            exchange.sendResponseHeaders(postStatus, -1); exchange.close()
        }
        server.createContext("/actuator/gateway/routes") { exchange ->
            authHeaders.add(exchange.requestHeaders.getFirst("Authorization"))
            val bytes = snapshot.toByteArray()
            exchange.sendResponseHeaders(getStatus, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val client = GatewayRefreshClient(mapper, "http://127.0.0.1:${server.address.port}", "test-token", 1000)
            assertEquals("PENDING_REFRESH", client.refresh(event).status)
            snapshot = """[{"route_id":"demo_route-3","metadata":{"serviceDatabaseId":7,"configurationVersion":4}}]"""
            assertEquals("APPLIED", client.refresh(event).status)
            snapshot = """[{"route_id":"demo_route-3","metadata":{"serviceDatabaseId":7,"configurationVersion":4}},
                {"route_id":"demo_route-9","metadata":{"serviceDatabaseId":7,"configurationVersion":4}}]"""
            assertEquals("PENDING_REFRESH", client.refresh(event).status)
            snapshot = "[]"
            assertEquals("PENDING_REFRESH", client.refresh(event).status)
            assertEquals("APPLIED", client.refresh(event.copy(expectedRoutes=emptySet())).status)
            snapshot = "{}"
            assertEquals("FAILED_REFRESH", client.refresh(event).status)
            postStatus = 401
            assertEquals("FAILED_REFRESH", client.refresh(event).status)
            postStatus = 200; getStatus = 500
            assertEquals("FAILED_REFRESH", client.refresh(event).status)
            assertTrue(authHeaders.all { it == "Bearer test-token" })
        } finally { server.stop(0) }
    }

    @Test fun `transport failure does not expose credentials or claim success`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val port = server.address.port
        server.start(); server.stop(0)
        val client = GatewayRefreshClient(mapper, "http://127.0.0.1:$port", "secret-test-token", 200)
        val outcome = client.refresh(event)
        assertEquals("FAILED_REFRESH", outcome.status)
        assertFalse(outcome.error.orEmpty().contains("secret-test-token"))
        assertEquals("PENDING_REFRESH", GatewayRefreshClient(mapper, "", "", 200).refresh(event).status)
    }

    @Test fun `normalization contract rejects rewrites catchalls duplicates and ambiguous parameters`() {
        val validator = DesiredConfigurationValidator()
        val op = DesiredOperation("/demo/items/{id}", "/demo/items/{id}", "GET")
        val input = DesiredServiceConfiguration(UUID.randomUUID(),0,"a".repeat(64),"/demo/v3/api-docs",
            DesiredRoute("/demo/**"),listOf(op))
        validator.validate("demo", input)
        val invalid = listOf(
            input.copy(operations=listOf(op.copy(backendPath="/items/{id}"))),
            input.copy(operations=listOf(op.copy(gatewayPath="/demo/**", backendPath="/demo/**"))),
            input.copy(operations=listOf(op, op)),
            input.copy(operations=listOf(op, op.copy(gatewayPath="/demo/items/{other}", backendPath="/demo/items/{other}"))),
            input.copy(operations=listOf(op.copy(access=AccessType.ROLES))),
            input.copy(route=null),
            input.copy(route=DesiredRoute("/**")),
            input.copy(specHash="invalid")
        )
        invalid.forEach { assertEquals(422, assertThrows(ResponseStatusException::class.java) {
            validator.validate("demo",it)
        }.statusCode.value()) }
        assertThrows(ResponseStatusException::class.java) { validator.validate("auth-service",input) }
        assertThrows(ResponseStatusException::class.java) { validator.validate("gateway",input) }
    }
}
