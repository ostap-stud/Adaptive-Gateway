package edu.ldubgd.alertSystem.gatewayserver

import edu.ldubgd.alertSystem.gatewayserver.data.ValidateTokenRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.gateway.route.Route
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.time.Duration

@Service
class ValidationServiceImpl(
    webBuilder: WebClient.Builder,
    @Value("\${gateway.validation.timeout-ms:3000}") private val timeoutMs: Long = 3000
) : ValidationService<Void> {
    private val webClient = webBuilder.build()

    override fun validateTokenForPath(
        token: String?, path: String, method: String,
        exchange: ServerWebExchange, chain: (ServerWebExchange) -> Mono<Void>
    ): Mono<Void> {
        val route = exchange.getAttribute<Route>(GATEWAY_ROUTE_ATTR)
        val routeId = (route?.metadata?.get("databaseRouteId") as? Number)?.toInt()
        val decision: Mono<HttpStatusCode> = webClient.post()
            .uri("http://auth-service/auth-service/validate")
            .headers { if (!token.isNullOrBlank()) it.set(HttpHeaders.AUTHORIZATION, token) }
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(ValidateTokenRequest(path, method, routeId))
            .exchangeToMono { response -> response.releaseBody().thenReturn(response.statusCode()) }
            .timeout(Duration.ofMillis(timeoutMs))
            .onErrorReturn(HttpStatus.SERVICE_UNAVAILABLE)

        // Catch only Auth transport failures; downstream failures keep their own semantics.
        return decision.flatMap { status ->
            if (status.value() == 200) chain(exchange)
            else {
                val clientStatus = if (status.value() in setOf(400, 401, 403, 404)) status else HttpStatus.SERVICE_UNAVAILABLE
                exchange.response.statusCode = clientStatus
                exchange.response.headers.contentType = MediaType.TEXT_PLAIN
                val message = if (clientStatus.value() == 503) "Authorization service unavailable" else "Access denied"
                exchange.response.writeWith(Mono.just(exchange.response.bufferFactory().wrap(message.toByteArray(Charsets.UTF_8))))
            }
        }
    }
}
