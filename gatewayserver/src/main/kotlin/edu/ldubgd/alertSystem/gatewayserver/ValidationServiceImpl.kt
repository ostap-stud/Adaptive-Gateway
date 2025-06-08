package edu.ldubgd.alertSystem.gatewayserver

import edu.ldubgd.alertSystem.gatewayserver.data.ValidateTokenRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.toEntity
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@Service
class ValidationServiceImpl(
    webBuilder: WebClient.Builder
) : ValidationService<Void> {

    private val webClient: WebClient = webBuilder.build()

    override fun validateTokenForPath(token: String?, path: String, exchange: ServerWebExchange, chain: (ServerWebExchange) -> Mono<Void>): Mono<Void> {
        return webClient.post()
            .uri("http://auth-service/auth-service/validate")
            .header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(ValidateTokenRequest(path))
            .retrieve()
            .onStatus(
                { it.is4xxClientError }
            ) { response ->
                response.bodyToMono(String::class.java)
                    .flatMap { body ->
                        Mono.error {
                            HttpClientErrorException.create(
                                response.statusCode(),
                                body,
                                response.headers().asHttpHeaders(),
                                byteArrayOf(), Charsets.UTF_8
                            )
                        }
                    }
            }
            .toEntity<String>()
            .flatMap { chain.invoke(exchange) }
            .onErrorResume { ex -> validationErrorHandler(exchange, ex) }
    }

    private fun validationErrorHandler(exchange: ServerWebExchange, ex: Throwable): Mono<Void> {
        val response = exchange.response
        response.headers.contentType = MediaType.TEXT_PLAIN
        response.statusCode = HttpStatus.INTERNAL_SERVER_ERROR
        val body = if (ex is HttpClientErrorException) {
            response.statusCode = ex.statusCode
            ex.responseHeaders?.forEach { name, values ->
                response.headers.addAll(name, values)
            }
            ex.statusText.toByteArray(Charsets.UTF_8)
        } else {
            ex.message?.toByteArray(Charsets.UTF_8) ?: byteArrayOf()
        }
        val buffer = response.bufferFactory().wrap(body)
        return response.writeWith(Mono.just(buffer))
    }


}