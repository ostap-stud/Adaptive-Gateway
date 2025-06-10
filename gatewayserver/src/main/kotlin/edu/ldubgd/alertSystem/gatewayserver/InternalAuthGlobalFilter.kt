package edu.ldubgd.alertSystem.gatewayserver

import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

@Component
class InternalAuthGlobalFilter(
    private val validationService: ValidationService<Void>,
    private val internalRoutesCache: InternalRoutesCache
) : WebFilter {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val path = exchange.request.path.value()
        val method = exchange.request.method.name()
        val token = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)
        if (!internalRoutesCache.isInternal(path) || path.contains("/v3/api-docs")) {
            return chain.filter(exchange)
        }
        return validationService
            .validateTokenForPath(token, path, method, exchange, chain::filter)
    }

}