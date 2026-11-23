package edu.ldubgd.authservice

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sun.net.httpserver.HttpServer
import edu.ldubgd.authservice.admin.ManagerAdminClient
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import java.net.InetSocketAddress

class ManagerAdminClientTests {
    @Test fun `proxy sends scoped key preserves busy response and reports upstream failure`() {
        val key="manager-admin-client-test-key-at-least-32"
        val seen=mutableListOf<String>()
        var code=200
        val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        server.createContext("/") { exchange ->
            assertEquals(key,exchange.requestHeaders.getFirst("X-Adaptive-Key"))
            assertNull(exchange.requestHeaders.getFirst("Authorization"))
            seen.add("${exchange.requestMethod} ${exchange.requestURI.path}")
            val body="""{"mode":"APPLY","observations":[]}""".toByteArray()
            exchange.sendResponseHeaders(code,body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val client=ManagerAdminClient(jacksonObjectMapper(),"http://127.0.0.1:${server.address.port}",key,1000)
            assertEquals("APPLY",client.snapshot().path("mode").asText())
            assertEquals("APPLY",client.poll().path("mode").asText())
            assertEquals("APPLY",client.mode("APPLY").path("mode").asText())
            assertEquals(listOf("GET /internal/admin/observations","POST /internal/admin/poll","PUT /internal/admin/mode"),seen)
            assertEquals(422,assertThrows(ResponseStatusException::class.java) { client.mode("APPLE") }.statusCode.value())
            code=422
            assertEquals(422,assertThrows(ResponseStatusException::class.java) { client.mode("APPLY") }.statusCode.value())
            code=409
            assertEquals(409,assertThrows(ResponseStatusException::class.java) { client.poll() }.statusCode.value())
            code=500
            assertEquals(502,assertThrows(ResponseStatusException::class.java) { client.snapshot() }.statusCode.value())
        } finally { server.stop(0) }
    }

    @Test fun `unconfigured manager cannot trigger a request`() {
        val client=ManagerAdminClient(jacksonObjectMapper(),"","",1000)
        assertEquals(503,assertThrows(ResponseStatusException::class.java) { client.poll() }.statusCode.value())
    }
}
