package edu.ldubgd.alertSystem.gatewayserver

import kotlinx.coroutines.*
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cloud.gateway.filter.GatewayFilter
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.toEntity

@Component
class AuthenticationGatewayFilterFactory() : AbstractGatewayFilterFactory<AuthenticationGatewayFilterFactory.Config>(Config::class.java) {

    private lateinit var webClient: WebClient

    @Autowired
    constructor(webBuilder: WebClient.Builder) : this() {
        webClient = webBuilder.build()
    }

    override fun apply(config: Config?): GatewayFilter {
        return GatewayFilter{ exchange, chain ->
            val response: ResponseEntity<String>
            runBlocking {
                response = CoroutineScope(Dispatchers.IO).async{
                    webClient.post()
                        .uri("http://auth-service/auth/validate")
                        .header("Authorization", exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ValidateTokenRequest(/*config?.roles ?: emptyList()*/exchange.request.path.value()))
                        .retrieve()
                        .toEntity<String>()
                        .awaitSingle()
                }.await()
            }
            if (response.statusCode.is2xxSuccessful){
                println("Successfully validated")
            } else{
                println("Error: ${response.statusCode}")
            }
            chain.filter(exchange)
        }
    }

    /*data*/ class Config(
//        val roles: List<String>
    )
}