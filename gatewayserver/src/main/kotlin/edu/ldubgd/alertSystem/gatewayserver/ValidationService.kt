package edu.ldubgd.alertSystem.gatewayserver

import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

interface ValidationService<T> {
    fun validateTokenForPath(token: String?, path: String, exchange: ServerWebExchange, chain: (ServerWebExchange) -> Mono<T>) : Mono<T>
}
