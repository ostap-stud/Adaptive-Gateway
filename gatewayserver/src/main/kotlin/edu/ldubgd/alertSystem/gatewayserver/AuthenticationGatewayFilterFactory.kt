package edu.ldubgd.alertSystem.gatewayserver

import edu.ldubgd.alertSystem.gatewayserver.data.ValidateTokenRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cloud.gateway.filter.GatewayFilter
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.toEntity
import reactor.core.publisher.Mono

@Component
class AuthenticationGatewayFilterFactory() : AbstractGatewayFilterFactory<AuthenticationGatewayFilterFactory.Config>(Config::class.java) {

    private lateinit var webClient: WebClient

    @Autowired
    constructor(webBuilder: WebClient.Builder) : this() {
        webClient = webBuilder.build()
    }

    override fun apply(config: Config?): GatewayFilter {
        return GatewayFilter{ exchange, chain ->
            val path = exchange.request.path.value()
            if (!path.contains("/v3/api-docs")){     // DEVELOPMENT PURPOSES ONLY!
                webClient.post()
                    .uri("http://auth-service/auth/validate")
                    .header("Authorization", exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(ValidateTokenRequest(/*config?.roles ?: emptyList()*/exchange.request.path.value()))
                    .retrieve()
                    .onStatus(
                        { it.is4xxClientError },
                        {
                            Mono.error {
                                HttpClientErrorException(HttpStatus.UNAUTHORIZED)
                            }
                        }
                    )
                    .toEntity<String>()
                    .then(chain.filter(exchange))
                    .onErrorResume { error ->
//                    error.printStackTrace()
                        exchange.response.statusCode = HttpStatus.UNAUTHORIZED
                        exchange.response.headers.contentType = MediaType.TEXT_PLAIN
                        val buffer = exchange.response.bufferFactory().wrap(
                            error.message?.toByteArray(charset = Charsets.UTF_8) ?: byteArrayOf()
                        )
                        return@onErrorResume exchange.response.writeWith(Mono.just(buffer))
                    }
            } else{
                chain.filter(exchange)
            }
        }
    }

    /*data*/ class Config(
//        val roles: List<String>
    )
}