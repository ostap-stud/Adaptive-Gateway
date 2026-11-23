package edu.ldubgd.authservice

import edu.ldubgd.authservice.db.*
import edu.ldubgd.authservice.db.controller.ServiceController
import edu.ldubgd.authservice.db.dto.*
import edu.ldubgd.authservice.db.service.RouteService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import java.util.Optional

@SpringBootTest(properties = ["eureka.client.enabled=false"])
@Transactional
class AdaptiveOwnershipTests {
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var services: ServiceRepository
    @Autowired private lateinit var routes: ServiceRouteRepository
    @Autowired private lateinit var metadata: AdaptiveServiceMetadataRepository
    @Autowired private lateinit var controller: ServiceController
    @Autowired private lateinit var routeService: RouteService
    private var serviceId = 0
    private var routeId = 0

    @BeforeEach
    fun fixture() {
        serviceId = jdbc.queryForObject(
            "INSERT INTO users.service(service_name) VALUES ('ownership-test') RETURNING id", Int::class.java)!!
        routeId = jdbc.queryForObject(
            "INSERT INTO users.route(route, service_id, source, managed_key) VALUES ('/ownership/**', ?, 'GENERATED', 'main') RETURNING id",
            Int::class.java, serviceId)!!
    }

    @Test
    fun `metadata changes retain child route and its permissions`() {
        val role = jdbc.queryForObject(
            "INSERT INTO users.role(application, role_name) VALUES ('OWNERSHIP', 'USER') RETURNING id", Int::class.java)!!
        jdbc.update("INSERT INTO users.route_role(route_id, role_id) VALUES (?, ?)", routeId, role)
        val linkId = jdbc.queryForObject("SELECT id FROM users.route_role WHERE route_id = ?", Int::class.java, routeId)
        assertTrue(metadata.updateManagementMode(serviceId, ManagementMode.AUTOMATIC))
        assertTrue(metadata.updateDetails(serviceId, "ownership-test", "Updated description"))
        val loaded = services.findById(serviceId).get()
        assertEquals(ManagementMode.AUTOMATIC, loaded.managementMode)
        assertEquals(2L, loaded.configurationVersion)
        assertEquals(routeId, loaded.routes.single().id)
        assertEquals(linkId, jdbc.queryForObject("SELECT id FROM users.route_role WHERE route_id = ?", Int::class.java, routeId))
    }

    @Test
    fun `manual deletion of generated route retains inactive override`() {
        controller.deleteServiceRoute(routeId)
        val route = routes.findById(routeId).get()
        assertFalse(route.active)
        assertTrue(route.isOverridden)
        assertEquals("main", route.managedKey)
        assertTrue(routeService.findRoutesByRequestPath("/ownership/item").isEmpty())
        assertNull(routeService.findRouteByRequestMethod(listOf(route), "GET"))
    }

    @Test
    fun `legacy payload creates a manual active route and full update retains ownership`() {
        val dto = ServiceRouteDTO("/legacy/**", null, "ownership-test")
        val manual = routeService.insertRoute(dto)
        assertEquals(RouteSource.MANUAL, manual.source)
        assertTrue(manual.active)
        val updated = routeService.updateRoute(Optional.of(routes.findById(routeId).get()), dto)
        assertEquals(RouteSource.GENERATED, updated.source)
        assertEquals("main", updated.managedKey)
        assertTrue(updated.isOverridden)
        controller.deleteServiceRoute(manual.id)
        assertFalse(routes.existsById(manual.id))
    }

    @Test
    fun `manual filter edit marks generated route as overridden`() {
        controller.addServiceRouteFilters(routeId, listOf(FilterDTO("AddResponseHeader", mapOf("name" to "X-Test", "value" to "yes"))))
        val loaded = routes.findById(routeId).get()
        assertTrue(loaded.isOverridden)
        assertEquals(1, loaded.filters.size)
    }
}
