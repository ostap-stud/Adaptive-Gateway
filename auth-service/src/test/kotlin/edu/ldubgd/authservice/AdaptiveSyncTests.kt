package edu.ldubgd.authservice

import com.fasterxml.jackson.databind.ObjectMapper
import edu.ldubgd.authservice.adaptive.*
import edu.ldubgd.authservice.db.controller.ServiceController
import edu.ldubgd.authservice.db.dto.RouteStateRequest
import edu.ldubgd.authservice.policy.AccessType
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest(properties = ["eureka.client.enabled=false", "adaptive.sync-key=integration-test-key-32-characters-long"])
@AutoConfigureMockMvc
class AdaptiveSyncTests {
    @Autowired private lateinit var sync: AdaptiveSyncService
    @Autowired private lateinit var refresh: AdaptiveRefreshService
    @Autowired private lateinit var admin: ServiceController
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var json: ObjectMapper
    @Autowired private lateinit var transactionManager: PlatformTransactionManager
    @Autowired private lateinit var locks: ConfigurationWriteLock
    @Autowired private lateinit var events: AdaptationEventRepository
    private val name = "sync-integration-test"
    private val key = "integration-test-key-32-characters-long"
    private val endpoint get() = "/auth-service/internal/adaptive/services/$name/configuration"
    private var sid = 0

    @BeforeEach fun createService() {
        sid = jdbc.queryForObject("INSERT INTO users.service(service_name, management_mode) VALUES (?, 'AUTOMATIC') RETURNING id",
            Int::class.java, name)!!
    }
    @AfterEach fun cleanup() {
        jdbc.update("DELETE FROM users.service WHERE id=?", sid)
    }
    private fun desired(version: Long = 0) = DesiredServiceConfiguration(UUID.randomUUID(), version, "a".repeat(64),
        "/$name/v3/api-docs", DesiredRoute("/$name/**"),
        listOf(DesiredOperation("/$name/public", "/$name/public", "GET", access=AccessType.PUBLIC),
            DesiredOperation("/$name/private", "/$name/private", "POST")))
    private fun version() = jdbc.queryForObject("SELECT configuration_version FROM users.service WHERE id=?", Long::class.java, sid)!!
    private fun routeId() = jdbc.queryForObject("SELECT id FROM users.route WHERE service_id=? AND managed_key='ingress'", Int::class.java, sid)!!
    private fun operationId() = jdbc.queryForObject("SELECT id FROM users.api_operation WHERE service_id=? AND http_method='GET'", Long::class.java, sid)!!

    @Test fun `same request is replayed and equal new snapshot changes no IDs or version`() {
        val input = desired()
        val first = sync.persist(name, input)
        val rid = routeId()
        val oid = operationId()
        assertEquals(1L, first.result.configurationVersion)
        assertEquals(first, sync.persist(name, input))
        val equal = sync.persist(name, input.copy(requestId=UUID.randomUUID(), expectedVersion=1))
        assertEquals("NO_CHANGE", equal.result.status)
        assertEquals(1L, version())
        assertEquals(rid, routeId())
        assertEquals(oid, operationId())
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM users.route_filter WHERE route_id=?", Int::class.java, rid))
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM users.adaptation_event WHERE service_id=?", Int::class.java, sid))
    }

    @Test fun `version and request identity conflicts leave committed state unchanged`() {
        val input = desired()
        sync.persist(name, input)
        assertEquals(409, assertThrows(ResponseStatusException::class.java) { sync.persist(name, desired()) }.statusCode.value())
        assertEquals(409, assertThrows(ResponseStatusException::class.java) {
            sync.persist(name, input.copy(specHash="b".repeat(64)))
        }.statusCode.value())
        assertEquals(1L, version())
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM users.adaptation_event WHERE service_id=?", Int::class.java, sid))
    }

    @Test fun `manual and disabled services cannot be synchronized`() {
        for (mode in listOf("MANUAL", "DISABLED")) {
            jdbc.update("UPDATE users.service SET management_mode=? WHERE id=?", mode, sid)
            assertEquals(409, assertThrows(ResponseStatusException::class.java) { sync.persist(name, desired()) }.statusCode.value())
        }
        assertEquals(0L, version())
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM users.route WHERE service_id=?", Int::class.java, sid))
    }

    @Test fun `unknown role rolls back routes operations policies and event together`() {
        val input = desired()
        val bad = input.copy(operations=input.operations + DesiredOperation("/$name/roles", "/$name/roles", "GET",
            access=AccessType.ROLES, roles=setOf("ROLE_MISSING_SYNC_TEST")))
        assertEquals(422, assertThrows(ResponseStatusException::class.java) { sync.persist(name, bad) }.statusCode.value())
        assertEquals(0L, version())
        for (table in listOf("route","api_operation","adaptation_event"))
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM users.$table WHERE service_id=?", Int::class.java, sid))
    }

    @Test fun `manual routes and operations are never adopted by sync`() {
        val manual = jdbc.queryForObject("INSERT INTO users.route(route,service_id) VALUES (?,?) RETURNING id",
            Int::class.java, "/$name/**", sid)!!
        assertEquals(409, assertThrows(ResponseStatusException::class.java) { sync.persist(name, desired()) }.statusCode.value())
        jdbc.update("UPDATE users.route SET route='/unrelated-manual-test/**' WHERE id=?", manual)
        jdbc.update("INSERT INTO users.api_operation(service_id,gateway_path,backend_path,http_method) VALUES (?,?,?,'GET')",
            sid, "/$name/public", "/$name/public")
        assertEquals(409, assertThrows(ResponseStatusException::class.java) { sync.persist(name, desired()) }.statusCode.value())
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM users.route WHERE service_id=?", Int::class.java, sid))
        assertEquals("MANUAL", jdbc.queryForObject("SELECT source FROM users.api_operation WHERE service_id=?", String::class.java, sid))
    }

    @Test fun `a manual subpath conflicts before and after generated ingress and disabled routes are ignored`() {
        val manual = jdbc.queryForObject("INSERT INTO users.route(route,service_id) VALUES (?,?) RETURNING id",
            Int::class.java, "/$name/new-route", sid)!!
        val conflict = assertThrows(ResponseStatusException::class.java) { sync.persist(name, desired()) }
        assertEquals(409, conflict.statusCode.value())
        assertTrue(conflict.reason!!.contains("overlaps"))
        assertEquals(0L, version())
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM users.api_operation WHERE service_id=?", Int::class.java, sid))
        jdbc.update("UPDATE users.route SET active=false WHERE id=?", manual)
        sync.persist(name, desired())
        val ingress = routeId()
        val currentVersion = version()
        jdbc.update("UPDATE users.route SET active=true WHERE id=?", manual)
        assertEquals(409, assertThrows(ResponseStatusException::class.java) {
            sync.persist(name, desired(currentVersion).copy(specHash="b".repeat(64)))
        }.statusCode.value())
        assertEquals(currentVersion, version())
        assertEquals(ingress, routeId())
        assertEquals("MANUAL", jdbc.queryForObject("SELECT source FROM users.route WHERE id=?", String::class.java, manual))
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM users.api_operation WHERE service_id=?", Int::class.java, sid))
    }

    @Test fun `manual tombstone and central admin policies survive sync`() {
        sync.persist(name, desired())
        val rid = routeId()
        val oid = operationId()
        jdbc.update("INSERT INTO users.access_policy(api_operation_id,source,access_type) VALUES (?,'CENTRAL','BLOCKED'),(?,'ADMIN','AUTHENTICATED')", oid, oid)
        admin.updateRouteState(rid, RouteStateRequest(false))
        val manualVersion = version()
        val result = sync.persist(name, desired(manualVersion).copy(specHash="b".repeat(64)))
        assertEquals(1, result.result.counts["overridesPreserved"])
        assertFalse(jdbc.queryForObject("SELECT active FROM users.route WHERE id=?", Boolean::class.java, rid)!!)
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM users.access_policy WHERE api_operation_id=? AND source IN ('CENTRAL','ADMIN')",
            Int::class.java, oid))
        assertEquals(rid, routeId())
    }

    @Test fun `omitted generated entries deactivate and reappear with stable IDs`() {
        sync.persist(name, desired())
        val rid = routeId()
        val oid = operationId()
        sync.persist(name, desired(1).copy(route=null, operations=emptyList()))
        assertFalse(jdbc.queryForObject("SELECT active FROM users.route WHERE id=?", Boolean::class.java, rid)!!)
        assertFalse(jdbc.queryForObject("SELECT active FROM users.api_operation WHERE id=?", Boolean::class.java, oid)!!)
        sync.persist(name, desired(2))
        assertEquals(rid, routeId())
        assertEquals(oid, operationId())
        assertTrue(jdbc.queryForObject("SELECT active FROM users.route WHERE id=?", Boolean::class.java, rid)!!)
    }

    @Test fun `connected service adds changes removes and restores operations without replacing ingress`() {
        val initial=desired()
        sync.persist(name,initial)
        val rid=routeId()
        val oid=operationId()
        val added=DesiredOperation("/$name/added","/$name/added","GET",permissions=setOf("lifecycle.read"))
        val changed=initial.operations.first().copy(access=AccessType.AUTHENTICATED)
        val revision=initial.copy(requestId=UUID.randomUUID(),expectedVersion=1,specHash="b".repeat(64),
            operations=listOf(changed,added))
        sync.persist(name,revision)
        assertEquals(rid,routeId())
        assertEquals(oid,operationIdFor("public"))
        assertEquals("AUTHENTICATED",jdbc.queryForObject("SELECT access_type FROM users.access_policy WHERE api_operation_id=? AND source='OPENAPI'",String::class.java,oid))
        assertFalse(jdbc.queryForObject("SELECT active FROM users.api_operation WHERE service_id=? AND http_method='POST'",Boolean::class.java,sid)!!)
        val addedId=operationIdFor("added")
        sync.persist(name,revision.copy(requestId=UUID.randomUUID(),expectedVersion=2,specHash="c".repeat(64),
            operations=listOf(added.copy(permissions=setOf("lifecycle.write")))))
        assertEquals(listOf("lifecycle.write"),jdbc.queryForList("SELECT permission FROM users.operation_permission WHERE operation_id=?",String::class.java,addedId))
        // The route may remain, but deleted operations must never fall back to its legacy roles.
        sync.persist(name,revision.copy(requestId=UUID.randomUUID(),expectedVersion=3,specHash="d".repeat(64),operations=emptyList()))
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM users.api_operation WHERE service_id=? AND active",Int::class.java,sid))
        mvc.perform(post("/auth-service/validate").contentType(MediaType.APPLICATION_JSON)
            .content("""{"routePath":"/$name/public","method":"GET","routeId":$rid}"""))
            .andExpect(status().isNotFound)
        sync.persist(name,initial.copy(requestId=UUID.randomUUID(),expectedVersion=4))
        assertEquals(oid,operationIdFor("public"))
        assertEquals(rid,routeId())
    }

    private fun operationIdFor(path: String) = jdbc.queryForObject(
        "SELECT id FROM users.api_operation WHERE service_id=? AND gateway_path=?",Long::class.java,sid,"/$name/$path")!!

    @Test fun `technical API rejects missing key and a user bearer but accepts scoped key`() {
        mvc.perform(get(endpoint)).andExpect(status().isUnauthorized)
        mvc.perform(get(endpoint).header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized)
        mvc.perform(put(endpoint).header("X-Adaptive-Key","wrong").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(desired())))
            .andExpect(status().isUnauthorized)
        mvc.perform(get(endpoint).header("X-Adaptive-Key",key)).andExpect(status().isOk)
        mvc.perform(get("/auth-service/service/").header("X-Adaptive-Key",key)).andExpect(status().isUnauthorized)
        val response = mvc.perform(put(endpoint).header("X-Adaptive-Key",key).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsBytes(desired()))).andExpect(status().isOk).andReturn().response.contentAsString
        assertEquals("PENDING_REFRESH", json.readTree(response)["status"].asText())
        assertEquals(1L, version())
    }

    @Test fun `new policy is used immediately by validation and unknown operation denied`() {
        sync.persist(name, desired())
        val rid = routeId()
        for ((path, code) in listOf("public" to 200, "missing" to 404)) {
            mvc.perform(post("/auth-service/validate").contentType(MediaType.APPLICATION_JSON)
                .content("""{"routePath":"/$name/$path","method":"GET","routeId":$rid}"""))
                .andExpect(status().`is`(code))
        }
    }

    @Test fun `two competing snapshots only commit one revision`() {
        val pool = Executors.newFixedThreadPool(2)
        try {
            val start = CountDownLatch(1)
            val futures = (1..2).map {
                pool.submit<Int> {
                    start.await()
                    try { sync.persist(name, desired()); 200 } catch (e: ResponseStatusException) { e.statusCode.value() }
                }
            }
            start.countDown()
            assertEquals(listOf(200,409), futures.map { it.get(10, TimeUnit.SECONDS) }.sorted())
            assertEquals(1L, version())
        } finally { pool.shutdownNow() }
    }

    @Test fun `admin lock serializes sync and stale writer cannot revive override`() {
        sync.persist(name, desired())
        val rid = routeId()
        val before = version()
        val pool = Executors.newSingleThreadExecutor()
        try {
            val ready = CountDownLatch(1)
            val future = TransactionTemplate(transactionManager).execute {
                locks.lock()
                val task = pool.submit<Int> {
                    ready.countDown()
                    try { sync.persist(name, desired(before)); 200 } catch (e: ResponseStatusException) { e.statusCode.value() }
                }
                assertTrue(ready.await(2, TimeUnit.SECONDS))
                admin.updateRouteState(rid, RouteStateRequest(false))
                task
            }!!
            assertEquals(409, future.get(10, TimeUnit.SECONDS))
            assertFalse(jdbc.queryForObject("SELECT active FROM users.route WHERE id=?", Boolean::class.java, rid)!!)
        } finally { pool.shutdownNow() }
    }

    @Test fun `durable pending refresh can retry and obsolete event cannot claim applied`() {
        val event = sync.persist(name, desired())
        assertEquals("PENDING_REFRESH", refresh.refresh(event).status)
        assertEquals("PENDING_REFRESH", refresh.refresh(event).status)
        admin.updateRouteState(routeId(), RouteStateRequest(false))
        assertEquals("SUPERSEDED", refresh.refresh(event).status)
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM users.adaptation_event WHERE service_id=?", Int::class.java, sid))
    }

    @Test fun `refresh executes after commit and failure is durable until verified retry`() {
        val event = sync.persist(name, desired())
        var calls = 0
        val gateway = org.mockito.Mockito.mock(GatewayRefreshClient::class.java) { invocation ->
            if (invocation.method.name != "refresh") return@mock org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation)
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            assertEquals(1L, version())
            calls++
            if (calls == 1) RefreshOutcome("FAILED_REFRESH", "Gateway unavailable") else RefreshOutcome("APPLIED")
        }
        val dispatcher = AdaptiveRefreshService(jdbc, events, locks, gateway, transactionManager)
        assertEquals("FAILED_REFRESH", dispatcher.refresh(event).status)
        assertEquals("FAILED_REFRESH", events.find(sid, event.result.requestId)!!.result.status)
        assertEquals(1L, version())
        val noop = sync.persist(name, desired(1))
        assertEquals("NO_CHANGE", noop.result.status)
        assertEquals(1, (sync.configuration(name)["pendingRefresh"] as List<*>).size)
        assertEquals("APPLIED", dispatcher.refresh(event).status)
        assertEquals("APPLIED", dispatcher.refresh(event).status)
        assertEquals(2, calls)
        assertNotNull(jdbc.queryForObject("SELECT applied_at FROM users.adaptation_event WHERE id=?", java.sql.Timestamp::class.java, event.id))
    }

    @Test fun `unchanged desired state publishes a newly preserved manual override version`() {
        sync.persist(name, desired())
        admin.updateRouteState(routeId(), RouteStateRequest(false))
        val manualVersion = version()
        val result = sync.persist(name, desired(manualVersion))
        assertEquals("PENDING_REFRESH", result.result.status)
        assertEquals(manualVersion, result.result.configurationVersion)
        assertEquals(0, result.result.counts["routesChanged"])
        assertEquals(1, result.result.counts["overridesPreserved"])
        assertTrue(result.expectedRoutes.isEmpty())
    }
}
