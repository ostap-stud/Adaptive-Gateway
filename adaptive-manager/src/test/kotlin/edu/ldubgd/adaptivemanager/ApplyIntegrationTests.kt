package edu.ldubgd.adaptivemanager

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.UUID

class ApplyIntegrationTests {
    @Test fun `missing service is registered through the first snapshot without an admin API call`() {
        Harness().use { h ->
            h.authStatus=404
            assertEquals("PENDING_REFRESH",h.poll().sync!!.status)
            assertEquals(0,h.lastExpectedVersion)
            assertEquals(1,h.puts)
        }
    }
    @Test fun `explicit apply commits once retries pending refresh then skips after manager restart`() {
        Harness().use { h ->
            val first=h.poll()
            assertEquals("PENDING_REFRESH",first.sync!!.status)
            assertEquals(1,h.puts)
            assertEquals("APPLIED",h.poll().sync!!.status)
            assertEquals(1,h.retries)
            h.restartManager()
            assertEquals("UNCHANGED",h.poll().sync!!.status)
            assertEquals(1,h.puts)
            assertEquals(1,h.retries)
        }
    }
    @Test fun `uncertain PUT response is recovered from Auth durable event without duplicate PUT`() {
        Harness().use { h ->
            h.losePutResponse=true
            assertEquals("REQUEST_FAILED",h.poll().sync!!.status)
            assertEquals(1,h.puts)
            h.restartManager()
            assertEquals("APPLIED",h.poll().sync!!.status)
            assertEquals(1,h.puts)
            assertEquals(1,h.retries)
        }
    }
    @Test fun `conflict re-reads the new version before another snapshot`() {
        Harness().use { h ->
            h.conflictNext=true
            assertEquals("CONFLICT",h.poll().sync!!.status)
            assertEquals(1,h.version)
            assertEquals("PENDING_REFRESH",h.poll().sync!!.status)
            assertEquals(2,h.puts)
            assertEquals(1,h.lastExpectedVersion)
        }
    }
    @Test fun `changed policy creates a new revision with a new request ID`() {
        Harness().use { h ->
            h.poll(); h.poll()
            val first=h.requestId
            h.publicAccess=false
            assertEquals("PENDING_REFRESH",h.poll().sync!!.status)
            assertEquals(2,h.puts)
            assertEquals(2,h.version)
            assertNotEquals(first,h.requestId)
        }
    }
    @Test fun `valid empty OpenAPI synchronizes removal of the last operation`() {
        Harness().use { h ->
            h.poll(); h.poll()
            h.specOverride="""{"openapi":"3.0.3","info":{"title":"Demo","version":"2"},"paths":{}}"""
            assertEquals("PENDING_REFRESH",h.poll().sync!!.status)
            assertEquals(0,h.lastOperationCount)
            assertEquals(2,h.puts)
            h.poll()
            h.specOverride=null
            assertEquals("PENDING_REFRESH",h.poll().sync!!.status)
            assertEquals(1,h.lastOperationCount)
        }
    }
    @Test fun `temporary outages and Eureka disappearance preserve a connected service and recover without writes`() {
        Harness().use { h ->
            h.poll(); h.poll()
            h.specStatus=503
            assertEquals("OBSERVATION_FAILED",h.poll().status)
            assertEquals(1,h.puts)
            h.specStatus=200
            assertEquals("UNCHANGED",h.poll().sync!!.status)
            h.registryStatus=503
            assertEquals("STALE",h.poll().status)
            assertEquals(1,h.puts)
            h.registryStatus=200; h.registryMissing=true
            assertEquals("MISSING",h.poll().status)
            assertEquals(1,h.puts)
            h.registryMissing=false
            assertEquals("UNCHANGED",h.poll().sync!!.status)
            assertEquals(1,h.puts)
        }
    }
    @Test fun `observe non-allowlisted manual and disabled services never write`() {
        Harness(mode="OBSERVE").use { h -> assertNull(h.poll().sync); assertEquals(0,h.puts) }
        Harness(allowed=setOf("another-service")).use { h ->
            assertEquals("NOT_ALLOWLISTED",h.poll().sync!!.status); assertEquals(0,h.puts)
        }
        for (mode in listOf("MANUAL","DISABLED")) Harness().use { h ->
            h.managementMode=mode
            assertEquals(mode,h.poll().sync!!.status); assertEquals(0,h.puts)
        }
    }
    @Test fun `failed discovery or document and unavailable Auth never trigger writes`() {
        Harness().use { h ->
            h.specStatus=500
            assertEquals("OBSERVATION_FAILED",h.poll().status); assertEquals(0,h.puts)
            h.specStatus=200; h.authStatus=503
            assertEquals("UNAVAILABLE",h.poll().sync!!.status); assertEquals(0,h.puts)
            h.registryStatus=503
            assertEquals("STALE",h.poll().status); assertEquals(0,h.puts)
        }
    }
    @Test fun `infrastructure cannot be allowlisted and write client guards OBSERVE itself`() {
        assertThrows(IllegalArgumentException::class.java) { ManagerProperties(mode="APPLY",allowedServices=setOf("gateway"),
            authBaseUrl="http://localhost",authKey="a".repeat(32)) }
        val json=jacksonObjectMapper()
        val settings=ManagerProperties()
        val client=AuthSyncClient(JsonHttpTransport(json,settings),settings,json)
        assertThrows(IllegalStateException::class.java) {
            client.retry("demo",SyncEvent(UUID.randomUUID(),"PENDING_REFRESH",1))
        }
    }

    private class Harness(mode: String="APPLY",allowed: Set<String> = setOf("demo")) : AutoCloseable {
        val json=jacksonObjectMapper()
        val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        var puts=0; var retries=0; var version=0; var lastExpectedVersion=-1
        var lastOperationCount=-1; var specOverride: String?=null; var registryMissing=false
        var requestId: String?=null; var hash: String?=null; var eventStatus: String?=null
        var losePutResponse=false; var conflictNext=false; var publicAccess=true
        var managementMode="AUTOMATIC"; var registryStatus=200; var specStatus=200; var authStatus=200
        private val settings=ManagerProperties(mode=mode,allowedServices=allowed,
            eurekaUrl="http://127.0.0.1:${server.address.port}/eureka/",
            authBaseUrl="http://127.0.0.1:${server.address.port}",authKey="test-key-32-characters-long-for-apply")
        private lateinit var observer: ObservationService
        init {
            server.createContext("/") { exchange ->
                val path=exchange.requestURI.path
                var status=200
                val response: String
                when {
                    path=="/eureka/apps" -> {
                        status=registryStatus
                        response=if (registryMissing) """{"applications":{"application":[]}}""" else """{"applications":{"application":[{"name":"DEMO","instance":[{
                            "instanceId":"demo-1","hostName":"127.0.0.1","status":"UP",
                            "port":{"$":${server.address.port},"@enabled":"true"},
                            "metadata":{"gateway.enabled":"true","gateway.base-path":"/demo","gateway.openapi-path":"/demo/v3/api-docs"}
                        }]}]}}"""
                        assertNull(exchange.requestHeaders.getFirst("X-Adaptive-Key"))
                    }
                    path=="/demo/v3/api-docs" -> {
                        status=specStatus
                        response=specOverride ?: """{"openapi":"3.0.3","info":{"title":"Demo","version":"1"},
                            "security":${if(publicAccess) "[]" else "[{\"jwt\":[]}]"},
                            "components":{"securitySchemes":{"jwt":{"type":"http","scheme":"bearer","bearerFormat":"JWT"}}},
                            "paths":{"/demo/get":{"get":{"responses":{"200":{"description":"ok"}}}}}}"""
                        assertNull(exchange.requestHeaders.getFirst("X-Adaptive-Key"))
                    }
                    path.endsWith("/configuration") && exchange.requestMethod=="GET" -> {
                        status=authStatus
                        val event=if(requestId==null) null else mapOf("request_id" to requestId,"status" to eventStatus,"configuration_version" to version)
                        response=json.writeValueAsString(mapOf("service" to mapOf("service_name" to "demo","management_mode" to managementMode,
                            "configuration_version" to version,"openapi_hash" to hash),"latestEvent" to event,
                            "pendingRefresh" to if(eventStatus in setOf("PENDING_REFRESH","FAILED_REFRESH")) listOf(event) else emptyList<Any>()))
                    }
                    path.endsWith("/configuration") && exchange.requestMethod=="PUT" -> {
                        assertEquals(settings.authKey,exchange.requestHeaders.getFirst("X-Adaptive-Key"))
                        puts++
                        val input=json.readTree(exchange.requestBody)
                        lastOperationCount=input["operations"].size()
                        lastExpectedVersion=input["expectedVersion"].asInt()
                        assertEquals(version,lastExpectedVersion)
                        if(conflictNext) {
                            conflictNext=false; version++; status=409; response="{}"
                        } else {
                            hash=input["specHash"].asText(); version++
                            requestId=input["requestId"].asText(); eventStatus="PENDING_REFRESH"
                            response=result()
                            if(losePutResponse) { losePutResponse=false; status=500 }
                        }
                    }
                    path.endsWith("/refresh") && exchange.requestMethod=="POST" -> {
                        assertEquals(settings.authKey,exchange.requestHeaders.getFirst("X-Adaptive-Key"))
                        assertTrue(path.contains(requestId!!))
                        retries++; eventStatus="APPLIED"; response=result()
                    }
                    else -> { status=404; response="{}" }
                }
                val bytes=response.toByteArray()
                exchange.sendResponseHeaders(status,bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
            }
            server.start(); restartManager()
        }
        private fun result()=json.writeValueAsString(mapOf("requestId" to requestId,"status" to eventStatus,"configurationVersion" to version))
        fun restartManager() {
            val transport=JsonHttpTransport(json,settings)
            val reader=ReadOnlyJsonClient(transport)
            val builder=DesiredConfigurationBuilder(json)
            observer=ObservationService(DiscoveryWatcher(reader,settings),OpenApiClient(reader),OpenApiAnalyzer(builder),
                AuthServiceClient(reader,settings),builder,settings,SyncCoordinator(settings,AuthSyncClient(transport,settings,json)))
        }
        fun poll()=observer.poll().observations.single { it.name=="demo" }
        override fun close() { server.stop(0) }
    }
}
