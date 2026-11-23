package edu.ldubgd.adaptivemanager

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CopyOnWriteArrayList

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties=["manager.polling-enabled=false"])
class ObservationIntegrationTests {
    @Autowired private lateinit var observer: ObservationService
    @Autowired private lateinit var discovery: DiscoveryWatcher
    @LocalServerPort private var managerPort = 0
    private val json = jacksonObjectMapper()

    @BeforeEach fun reset() {
        requests.clear()
        registryStatus=200
        specStatus=200
        authStatus=200
        registryBody=registry(instance())
        specBody=spec()
        authBody="""{"service":{"service_name":"demo","management_mode":"AUTOMATIC","configuration_version":7}}"""
        observer.poll()
        requests.clear()
    }

    @Test fun `discovery fetches instance directly and builds a compatible read-only proposal`() {
        val result = observer.poll()
        val item = result.observations.single { it.name == "demo" }
        assertEquals("AVAILABLE",result.registryStatus)
        assertEquals("ANALYZED",item.status)
        assertEquals("/demo/**",item.candidate!!.route.path)
        assertEquals(7L,item.proposedRequest!!.expectedVersion)
        assertEquals(AccessType.AUTHENTICATED,item.proposedRequest!!.operations.single().access)
        val payload = json.valueToTree<com.fasterxml.jackson.databind.JsonNode>(item.proposedRequest)
        assertEquals(setOf("requestId","expectedVersion","specHash","openapiPath","route","operations"),payload.fieldNames().asSequence().toSet())
        assertTrue(requests.all { it.method == "GET" })
        assertEquals(listOf("/eureka/apps","/demo/v3/api-docs","/auth-service/internal/adaptive/services/demo/configuration"),requests.map { it.path })
        assertTrue(requests.filterNot { it.path.startsWith("/auth-service/") }.all { it.key == null })
        assertEquals(KEY,requests.last().key)
        val response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI("http://127.0.0.1:$managerPort/observations")).GET().build(),
            HttpResponse.BodyHandlers.ofString())
        assertEquals(200,response.statusCode())
        assertFalse(response.body().contains(KEY))
        assertEquals("OBSERVE",json.readTree(response.body()).path("mode").asText())
    }

    @Test fun `repeat poll retains hash and request identity while policy change changes both`() {
        val first=observer.poll().observations.single { it.name=="demo" }
        val again=observer.poll().observations.single { it.name=="demo" }
        assertEquals(first.candidate!!.specHash,again.candidate!!.specHash)
        assertEquals(first.proposedRequest,again.proposedRequest)
        specBody=spec(public=true)
        val changed=observer.poll().observations.single { it.name=="demo" }
        assertNotEquals(first.candidate!!.specHash,changed.candidate!!.specHash)
        assertNotEquals(first.proposedRequest!!.requestId,changed.proposedRequest!!.requestId)
    }

    @Test fun `manual endpoint requires a technical key and OBSERVE remains read only`() {
        val url=URI("http://127.0.0.1:$managerPort/internal/admin/poll")
        val http=HttpClient.newHttpClient()
        for (key in listOf("", "wrong")) {
            val request=HttpRequest.newBuilder(url).header("X-Adaptive-Key",key).POST(HttpRequest.BodyPublishers.noBody()).build()
            assertEquals(401,http.send(request,HttpResponse.BodyHandlers.ofString()).statusCode())
        }
        assertTrue(requests.isEmpty())
        val request=HttpRequest.newBuilder(url).header("X-Adaptive-Key",KEY).POST(HttpRequest.BodyPublishers.noBody()).build()
        val response=http.send(request,HttpResponse.BodyHandlers.ofString())
        assertEquals(200,response.statusCode())
        assertEquals("OBSERVE",json.readTree(response.body()).path("mode").asText())
        assertTrue(requests.all { it.method=="GET" })
        assertFalse(response.body().contains(KEY))
    }

    @Test fun `fetch failure invalid document and disappeared service never create empty desired state`() {
        specStatus=500
        var item=observer.poll().observations.single { it.name=="demo" }
        assertEquals("OBSERVATION_FAILED",item.status)
        assertNull(item.candidate); assertNull(item.proposedRequest)
        specStatus=200; specBody="""{"openapi":"3.0.3","info":{"title":"D","version":"1"}}"""
        item=observer.poll().observations.single { it.name=="demo" }
        assertEquals("INVALID_PATHS",item.message)
        assertNull(item.proposedRequest)
        registryBody="""{"applications":{"application":[]}}"""
        item=observer.poll().observations.single { it.name=="demo" }
        assertEquals("MISSING",item.status)
        assertNull(item.candidate); assertNull(item.proposedRequest)
    }

    @Test fun `registry outage retains historical candidate but withdraws actionable proposal`() {
        registryStatus=503
        val result=observer.poll()
        val item=result.observations.single { it.name=="demo" }
        assertEquals("UNAVAILABLE",result.registryStatus)
        assertEquals("STALE",item.status)
        assertNotNull(item.candidate)
        assertNull(item.proposedRequest)
        assertNotNull(result.lastSuccessfulPollAt)
    }

    @Test fun `infrastructure opt-out multiple instances and wrong prefix are not fetched`() {
        registryBody=registry(instance(),name="AUTH-SERVICE")
        observer.poll()
        assertEquals(listOf("/eureka/apps"),requests.map { it.path })
        requests.clear()
        registryBody=registry(instance(enabled=false))
        assertEquals("IGNORED",observer.poll().observations.single { it.name=="demo" }.status)
        assertEquals(listOf("/eureka/apps"),requests.map { it.path })
        requests.clear()
        registryBody=registry(instance()+","+instance(id="two"))
        assertEquals("MULTIPLE_INSTANCES",observer.poll().observations.single { it.name=="demo" }.status)
        assertEquals(listOf("/eureka/apps"),requests.map { it.path })
        requests.clear()
        registryBody=registry(instance(base="/other"))
        assertEquals("INVALID_METADATA",observer.poll().observations.single { it.name=="demo" }.status)
        assertEquals(listOf("/eureka/apps"),requests.map { it.path })
    }

    @Test fun `down instances and invalid discovery addresses are reported`() {
        registryBody=registry(instance(status="DOWN"))
        assertEquals("NO_UP_INSTANCE",observer.poll().observations.single { it.name=="demo" }.status)
        registryBody=registry(instance(path="http://evil.invalid/doc"))
        assertEquals("INVALID_METADATA",observer.poll().observations.single { it.name=="demo" }.status)
        assertThrows(ObservationFailure::class.java) { discovery.parse(json.readTree("{}")) }
        assertThrows(ObservationFailure::class.java) { discovery.parse(json.readTree("""{"applications":{"application":null}}""")) }
    }

    @Test fun `manual disabled absent or unavailable Auth never receives a write or a ready proposal`() {
        for (mode in listOf("MANUAL","DISABLED")) {
            authBody="""{"service":{"service_name":"demo","management_mode":"$mode","configuration_version":7}}"""
            val item=observer.poll().observations.single { it.name=="demo" }
            assertEquals(mode,item.auth!!.status)
            assertNotNull(item.candidate)
            assertNull(item.proposedRequest)
        }
        authStatus=404
        assertEquals("NOT_REGISTERED",observer.poll().observations.single { it.name=="demo" }.auth!!.status)
        authStatus=401
        assertEquals("UNAVAILABLE",observer.poll().observations.single { it.name=="demo" }.auth!!.status)
        assertTrue(requests.all { it.method=="GET" })
    }

    companion object {
        private const val KEY="observer-test-technical-key-32-characters"
        data class Seen(val method: String,val path: String,val key: String?)
        private val requests=CopyOnWriteArrayList<Seen>()
        @Volatile private var registryStatus=200
        @Volatile private var specStatus=200
        @Volatile private var authStatus=200
        @Volatile private var registryBody="{}"
        @Volatile private var specBody="{}"
        @Volatile private var authBody="{}"
        private val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0).apply {
            createContext("/") { exchange ->
                val path=exchange.requestURI.path
                requests.add(Seen(exchange.requestMethod,path,exchange.requestHeaders.getFirst("X-Adaptive-Key")))
                val (code,body)=when(path) {
                    "/eureka/apps" -> registryStatus to registryBody
                    "/demo/v3/api-docs" -> specStatus to specBody
                    "/auth-service/internal/adaptive/services/demo/configuration" -> authStatus to authBody
                    else -> 404 to "{}"
                }
                val bytes=body.toByteArray()
                exchange.sendResponseHeaders(code,bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }
        @JvmStatic @DynamicPropertySource fun properties(registry: DynamicPropertyRegistry) {
            registry.add("manager.eureka-url") { "http://127.0.0.1:${server.address.port}/eureka/" }
            registry.add("manager.auth-base-url") { "http://127.0.0.1:${server.address.port}" }
            registry.add("manager.auth-key") { KEY }
        }
        @JvmStatic @AfterAll fun stop() { server.stop(0) }
        private fun registry(instances: String,name: String="DEMO")=
            """{"applications":{"application":[{"name":"$name","instance":[$instances]}]}}"""
        private fun instance(id: String="one",enabled: Boolean=true,base: String="/demo",path: String="/demo/v3/api-docs",status: String="UP")=
            """{"instanceId":"$id","hostName":"127.0.0.1","status":"$status",
                "port":{"$":${server.address.port},"@enabled":"true"},"securePort":{"$":443,"@enabled":"false"},
                "metadata":{"gateway.enabled":"$enabled","gateway.base-path":"$base","gateway.openapi-path":"$path"}}"""
        private fun spec(public: Boolean=false)=
            """{"openapi":"3.0.3","info":{"title":"Demo","version":"1"},
                "security":${if(public) "[]" else "[{\"jwt\":[]}]"},
                "components":{"securitySchemes":{"jwt":{"type":"http","scheme":"bearer","bearerFormat":"JWT"}}},
                "paths":{"/demo/get":{"get":{"responses":{"200":{"description":"ok"}}}}}}"""
    }
}
