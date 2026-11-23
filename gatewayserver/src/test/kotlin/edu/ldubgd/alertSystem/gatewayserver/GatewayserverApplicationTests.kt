package edu.ldubgd.alertSystem.gatewayserver

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import edu.ldubgd.alertSystem.gatewayserver.data.ServiceRouteRepository
import org.junit.jupiter.api.Assertions.*

@SpringBootTest
class GatewayserverApplicationTests {
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var repository: ServiceRouteRepository

    @Test
    @Transactional
    fun `inactive external routes are hidden while internal endpoints remain guarded`() {
        val serviceId = jdbc.queryForObject("INSERT INTO users.service(service_name) VALUES ('gateway-reader-test') RETURNING id", Int::class.java)!!
        jdbc.update("INSERT INTO users.route(route, service_id, active) VALUES ('/reader-inactive/**', ?, false)", serviceId)
        jdbc.update("INSERT INTO users.route(route, is_internal, active) VALUES ('/internal-inactive/**', true, false)")
        assertFalse(repository.findAllRoutes().any { it.route == "/reader-inactive/**" })
        // The WebFilter must still call Auth, whose active check denies this resource.
        assertTrue(repository.findAllInternalRoutes().any { it.route == "/internal-inactive/**" })
    }

	@Test
	fun contextLoads() {
	}

}
