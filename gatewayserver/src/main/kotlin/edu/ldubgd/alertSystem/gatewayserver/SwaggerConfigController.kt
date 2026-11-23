package edu.ldubgd.alertSystem.gatewayserver

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.*
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration

/** Documentation is available for registered Eureka services, independently of routing and APPLY. */
@RestController
@RequestMapping("/swagger")
class SwaggerConfigController(private val jdbc: JdbcTemplate, webBuilder: WebClient.Builder) {
    private val client = webBuilder.clone().codecs { it.defaultCodecs().maxInMemorySize(2097152) }.build()
    private val json = jacksonObjectMapper()
    private fun catalog() = jdbc.queryForList("""SELECT s.service_name,
        COALESCE(s.openapi_path, '/' || s.service_name || '/v3/api-docs') AS docs_path
        FROM users.service s
        ORDER BY s.service_name""").filter { it["service_name"].toString().matches(Regex("[a-z][a-z0-9-]{0,62}")) }

    @GetMapping("/config")
    fun swaggerConfig(): Mono<Map<String, Any>> = Mono.fromCallable {
        mapOf<String,Any>("urls" to (listOf(mapOf("name" to "gateway", "url" to "/v3/api-docs")) +
            catalog().map { mapOf("name" to it["service_name"], "url" to "/swagger/services/${it["service_name"]}") }),
            "persistAuthorization" to true)
    }.subscribeOn(Schedulers.boundedElastic())

    @GetMapping("/services/{name}")
    fun serviceDocs(@PathVariable name: String): Mono<JsonNode> = Mono.fromCallable {
        catalog().singleOrNull { it["service_name"] == name }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND,"No registered Eureka service")
    }.subscribeOn(Schedulers.boundedElastic()).flatMap { service ->
        val path=service["docs_path"].toString()
        if (!path.startsWith("/$name/") || !path.split('/').drop(1).all { it.matches(Regex("[A-Za-z0-9_.~-]+")) && it !in setOf(".","..") })
            return@flatMap Mono.error<JsonNode>(ResponseStatusException(HttpStatus.BAD_GATEWAY,"Invalid documentation path"))
        client.get().uri("http://$name$path").retrieve().bodyToMono(ObjectNode::class.java)
            .timeout(Duration.ofSeconds(5)).map<JsonNode> { document ->
                if (!document.path("openapi").asText().startsWith("3.") || !document.path("paths").isObject)
                    throw ResponseStatusException(HttpStatus.BAD_GATEWAY,"Invalid OpenAPI document")
                document.set<JsonNode>("servers",json.readTree("""[{"url":"/"}]"""))
                document.path("paths").forEach { item ->
                    if(item is ObjectNode) {
                        item.remove("servers")
                        item.forEach { operation -> if(operation is ObjectNode) operation.remove("servers") }
                    }
                }
                document
            }.onErrorMap { if(it is ResponseStatusException) it else ResponseStatusException(HttpStatus.BAD_GATEWAY,"Service documentation unavailable",it) }
    }
}
