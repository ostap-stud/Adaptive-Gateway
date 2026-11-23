package edu.ldubgd.adaptivemanager

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.Executors

class ReadOnlyJsonClientTests {
    @Test fun `GET client refuses redirects duplicate JSON trailing data and excessive bodies`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        var content = """{"ok":true}"""
        var status = 200
        var followed = false
        server.createContext("/doc") {
            assertEquals("GET",it.requestMethod)
            if (status == 302) it.responseHeaders.add("Location","/redirect-target")
            val bytes = content.toByteArray()
            it.sendResponseHeaders(status,bytes.size.toLong()); it.responseBody.use { out -> out.write(bytes) }
        }
        server.createContext("/redirect-target") { followed=true; it.sendResponseHeaders(200,-1); it.close() }
        server.start()
        try {
            val client = ReadOnlyJsonClient(JsonHttpTransport(jacksonObjectMapper(),ManagerProperties(maxResponseBytes=1024)))
            val uri = URI("http://127.0.0.1:${server.address.port}/doc")
            assertTrue(client.get(uri).path("ok").asBoolean())
            for (body in listOf("""{"ok":true,"ok":false}""","{} {}","<html>error</html>")) {
                content=body
                assertEquals("INVALID_JSON",assertThrows(ObservationFailure::class.java) { client.get(uri) }.code)
            }
            content="x".repeat(1025)
            assertEquals("RESPONSE_TOO_LARGE",assertThrows(ObservationFailure::class.java) { client.get(uri) }.code)
            content="{}"; status=302
            assertEquals("HTTP_302",assertThrows(ObservationFailure::class.java) { client.get(uri) }.code)
            assertFalse(followed)
            status=503
            assertEquals("HTTP_503",assertThrows(ObservationFailure::class.java) { client.get(uri) }.code)
        } finally { server.stop(0) }
    }
    @Test fun `whole response deadline bounds a stalled body`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        val executor = Executors.newSingleThreadExecutor()
        server.executor=executor
        server.createContext("/slow") {
            it.sendResponseHeaders(200,10)
            try { Thread.sleep(1000) } catch (_: InterruptedException) { }
            it.close()
        }
        server.start()
        try {
            val client = ReadOnlyJsonClient(JsonHttpTransport(jacksonObjectMapper(),ManagerProperties(timeoutMs=150)))
            assertEquals("TIMEOUT",assertThrows(ObservationFailure::class.java) {
                client.get(URI("http://127.0.0.1:${server.address.port}/slow"))
            }.code)
        } finally { server.stop(0); executor.shutdownNow() }
    }
    @Test fun `write mode and unsafe configured endpoints fail at startup`() {
        assertThrows(IllegalArgumentException::class.java) { ManagerProperties(mode="APPLY") }
        assertThrows(IllegalArgumentException::class.java) { ManagerProperties(eurekaUrl="file:///tmp/registry") }
        assertThrows(IllegalArgumentException::class.java) { ManagerProperties(authBaseUrl="http://localhost:8334",authKey="short") }
    }
}
