package edu.ldubgd.alertSystem.gatewayserver

import edu.ldubgd.alertSystem.gatewayserver.data.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.cloud.gateway.route.Route
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.mock.http.client.reactive.MockClientHttpRequest
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.reactive.function.client.*
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.net.URI
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

class OperationValidationTests {
    private fun exchange(path: String = "/op/public", source: String = "GENERATED"): MockServerWebExchange {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path).header("X-Route-Id", "999"))
        exchange.attributes[GATEWAY_ROUTE_ATTR] = Route.async().id("service_route-42")
            .uri("http://localhost:1").predicate { true }
            .metadata("databaseRouteId", 42).metadata("configurationSource", source)
            .metadata("documentationPath", "/service/v3/api-docs").build()
        return exchange
    }

    @Test fun `optional token and database route ID are serialized without trusting client header`() {
        var body = ""
        val validator = ValidationServiceImpl(WebClient.builder().exchangeFunction { request ->
            assertNull(request.headers().getFirst("Authorization"))
            val output = MockClientHttpRequest(HttpMethod.POST, URI("http://localhost/validate"))
            request.writeTo(output, ExchangeStrategies.withDefaults()).block(Duration.ofSeconds(2))
            body = output.bodyAsString.block(Duration.ofSeconds(2))!!
            Mono.just(ClientResponse.create(HttpStatus.OK).build())
        })
        val continued = AtomicBoolean()
        validator.validateTokenForPath(null, "/op/public", "GET", exchange()) {
            continued.set(true); Mono.empty()
        }.block(Duration.ofSeconds(2))
        assertTrue(continued.get())
        assertTrue(body.contains("\"routeId\":42"), body)
        assertTrue(body.contains("\"routePath\":\"/op/public\""), body)
        assertFalse(body.contains("999"))
    }

    @Test fun `empty deny responses are never mistaken for success`() {
        for (status in listOf(HttpStatus.BAD_REQUEST, HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND)) {
            val validator = ValidationServiceImpl(WebClient.builder().exchangeFunction {
                Mono.just(ClientResponse.create(status).build())
            })
            val exchange = exchange()
            validator.validateTokenForPath(null, "/op/private", "GET", exchange) {
                throw AssertionError("A denial must never reach the backend")
            }.block(Duration.ofSeconds(2))
            assertEquals(status, exchange.response.statusCode)
        }
    }

    @Test fun `Auth timeout and server failures are unavailable rather than access allowed`() {
        for (response in listOf(Mono.never<ClientResponse>(), Mono.error(IllegalStateException("private detail")),
            Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).body("secret diagnostic").build()))) {
            val validator = ValidationServiceImpl(WebClient.builder().exchangeFunction { response }, 25)
            val exchange = exchange()
            validator.validateTokenForPath(null, "/op/private", "GET", exchange) {
                throw AssertionError("Unavailable Auth must never reach backend")
            }.block(Duration.ofSeconds(2))
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.response.statusCode)
            assertEquals("Authorization service unavailable", exchange.response.bodyAsString.block())
        }
    }

    @Test fun `downstream error is not converted to an authorization failure`() {
        val validator = ValidationServiceImpl(WebClient.builder().exchangeFunction {
            Mono.just(ClientResponse.create(HttpStatus.OK).build())
        })
        val failure = IllegalStateException("backend failure")
        val thrown = assertThrows(IllegalStateException::class.java) {
            validator.validateTokenForPath(null, "/op/public", "GET", exchange()) {
                Mono.error(failure)
            }.block(Duration.ofSeconds(2))
        }
        assertSame(failure, thrown)
    }

    @Test fun `authentication uses original path and only exact legacy docs bypass validation`() {
        val recorder = RecordingValidation()
        val filter = AuthenticationGatewayFilterFactory(recorder).apply(AuthenticationGatewayFilterFactory.Config())
        val rewritten = exchange("/backend/1")
        rewritten.attributes[InternalAuthGlobalFilter.ORIGINAL_REQUEST_PATH] = "/op/1"
        filter.filter(rewritten) { Mono.empty() }.block()
        assertEquals("/op/1", recorder.path)
        recorder.path = null
        filter.filter(exchange("/service/v3/api-docs", "MANUAL")) { Mono.empty() }.block()
        assertNull(recorder.path)
        filter.filter(exchange("/service/v3/api-docs/hidden", "MANUAL")) { Mono.empty() }.block()
        assertEquals("/service/v3/api-docs/hidden", recorder.path)
        recorder.path = null
        filter.filter(exchange("/service/v3/api-docs")) { Mono.empty() }.block()
        assertEquals("/service/v3/api-docs", recorder.path)
    }

    @Test fun `generated routes get Authentication even when no filter was stored`() {
        val repository = mock(ServiceRouteRepository::class.java)
        `when`(repository.findAllRoutes()).thenReturn(listOf(ServiceRouteDTO(42, "/op/**", "service", source = "GENERATED")))
        val definition = CustomRouteDefinitionRepository(repository).routeDefinitions.blockFirst()!!
        assertEquals(42, definition.metadata["databaseRouteId"])
        assertEquals(1, definition.filters.count { it.name == "Authentication" })
    }

    private class RecordingValidation : ValidationService<Void> {
        var path: String? = null
        override fun validateTokenForPath(token: String?, path: String, method: String, exchange: ServerWebExchange,
            chain: (ServerWebExchange) -> Mono<Void>): Mono<Void> {
            this.path = path
            return chain(exchange)
        }
    }
}
