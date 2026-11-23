package edu.ldubgd.alertSystem.gatewayserver

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import java.time.Duration

class SwaggerDocumentationTests {
    private val jdbc = mock(JdbcTemplate::class.java)
    private var requested = ""
    private var document = """{"openapi":"3.0.1","servers":[{"url":"http://backend:9999"}],"paths":{"/demo/read":{"servers":[{"url":"http://backend:9999"}],"get":{"servers":[{"url":"http://backend:9999"}],"responses":{}}}}}"""
    private fun controller(path: String = "/demo/v3/api-docs"): SwaggerConfigController {
        `when`(jdbc.queryForList(anyString())).thenReturn(listOf(mapOf("service_name" to "demo", "docs_path" to path)))
        val web = WebClient.builder().exchangeFunction { request ->
            requested = request.url().toString()
            Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json").body(document).build())
        }
        return SwaggerConfigController(jdbc, web)
    }

    @Test fun `registered manual services need neither routes nor adaptive synchronization for documentation`() {
        val controller = controller()
        val config = controller.swaggerConfig().block(Duration.ofSeconds(2))!!
        assertTrue(config["urls"].toString().contains("/swagger/services/demo"))
        val sql = ArgumentCaptor.forClass(String::class.java)
        verify(jdbc).queryForList(sql.capture())
        assertFalse(sql.value.contains("users.route"))
        val docs = controller.serviceDocs("demo").block(Duration.ofSeconds(2))!!
        assertEquals("http://demo/demo/v3/api-docs", requested)
        assertEquals("/", docs["servers"][0]["url"].asText())
        assertFalse(docs["paths"]["/demo/read"].has("servers"))
        assertFalse(docs["paths"]["/demo/read"]["get"].has("servers"))
    }

    @Test fun `custom stored documentation path is respected`() {
        controller("/demo/custom/docs").serviceDocs("demo").block(Duration.ofSeconds(2))
        assertEquals("http://demo/demo/custom/docs", requested)
    }

    @Test fun `unknown services and unsafe documentation paths never issue outgoing requests`() {
        val controller = controller()
        assertEquals(404, assertThrows(ResponseStatusException::class.java) {
            controller.serviceDocs("missing").block(Duration.ofSeconds(2))
        }.statusCode.value())
        for (path in listOf("/other/docs", "/demo/../docs", "/demo/docs?redirect=1")) {
            assertEquals(502, assertThrows(ResponseStatusException::class.java) {
                controller(path).serviceDocs("demo").block(Duration.ofSeconds(2))
            }.statusCode.value())
        }
        assertEquals("", requested)
    }

    @Test fun `non OpenAPI responses are rejected`() {
        document = """{"message":"not an OpenAPI document"}"""
        assertEquals(502, assertThrows(ResponseStatusException::class.java) {
            controller().serviceDocs("demo").block(Duration.ofSeconds(2))
        }.statusCode.value())
    }
}
