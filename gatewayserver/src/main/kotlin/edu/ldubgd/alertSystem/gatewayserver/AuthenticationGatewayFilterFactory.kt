package edu.ldubgd.alertSystem.gatewayserver

import org.springframework.cloud.gateway.filter.GatewayFilter
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter
import org.springframework.cloud.gateway.route.Route
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component

@Component
class AuthenticationGatewayFilterFactory(
    private val validationService: ValidationService<Void>
) : AbstractGatewayFilterFactory<AuthenticationGatewayFilterFactory.Config>(Config::class.java) {

    override fun apply(config: Config?): GatewayFilter {
        return OrderedGatewayFilter(GatewayFilter { exchange, chain ->
            val path = exchange.getAttribute<String>(InternalAuthGlobalFilter.ORIGINAL_REQUEST_PATH)
                ?: exchange.request.path.value()
            val method = exchange.request.method.name()
            val token = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)
            val route = exchange.getAttribute<Route>(GATEWAY_ROUTE_ATTR)
            val legacyDocumentation = route?.metadata?.get("configurationSource") == "MANUAL" &&
                method in setOf("GET", "HEAD") &&
                path == route.metadata["documentationPath"]
            if (!legacyDocumentation) {
                validationService
                    .validateTokenForPath(token, path, method, exchange, chain::filter)
            } else{
                chain.filter(exchange)
            }
        }, -100)
    }

    class Config
}
