package edu.ldubgd.alertSystem.gatewayserver

import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import org.springframework.beans.factory.annotation.Value
import java.security.MessageDigest

@Component
class InternalAuthGlobalFilter(
    private val validationService: ValidationService<Void>,
    private val internalRoutesCache: InternalRoutesCache,
    @Value("\${gateway.refresh-key:}") private val refreshKey: String = ""
) : WebFilter {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val path = exchange.request.path.value()
        exchange.attributes.putIfAbsent(ORIGINAL_REQUEST_PATH, path)
        val method = exchange.request.method.name()
        val token = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)
        if (exchange.request.headers.containsKey("X-Gateway-Key")) {
            val allowed = (method == "POST" && path == "/actuator/gateway/refresh") ||
                (method == "GET" && path == "/actuator/gateway/routes")
            val supplied = exchange.request.headers.getFirst("X-Gateway-Key").orEmpty()
            if (allowed && refreshKey.toByteArray().size >= 32 && MessageDigest.isEqual(refreshKey.toByteArray(),supplied.toByteArray()))
                return chain.filter(exchange)
            exchange.response.statusCode = org.springframework.http.HttpStatus.UNAUTHORIZED
            return exchange.response.setComplete()
        }
        if (!internalRoutesCache.isInternal(path)) {
            return chain.filter(exchange)
        }
        return validationService
            .validateTokenForPath(token, path, method, exchange, chain::filter)
    }

    companion object {
        const val ORIGINAL_REQUEST_PATH = "adaptiveGateway.originalRequestPath"
    }

}
