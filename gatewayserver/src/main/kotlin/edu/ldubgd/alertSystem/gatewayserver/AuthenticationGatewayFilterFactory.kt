package edu.ldubgd.alertSystem.gatewayserver

import org.springframework.cloud.gateway.filter.GatewayFilter
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component

@Component
class AuthenticationGatewayFilterFactory(
    private val validationService: ValidationService<Void>
) : AbstractGatewayFilterFactory<AuthenticationGatewayFilterFactory.Config>(Config::class.java) {

    override fun apply(config: Config?): GatewayFilter {
        return GatewayFilter{ exchange, chain ->
            val path = exchange.request.path.value()
            val method = exchange.request.method.name()
            val token = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)
            if (!path.contains("/v3/api-docs")){     // DEVELOPMENT PURPOSES ONLY!
                validationService
                    .validateTokenForPath(token, path, method, exchange, chain::filter)
            } else{
                chain.filter(exchange)
            }
        }
    }

    /*data*/ class Config(
//        val roles: List<String>
    )
}