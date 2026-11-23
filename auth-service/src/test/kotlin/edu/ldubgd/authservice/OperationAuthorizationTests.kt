package edu.ldubgd.authservice

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import edu.ldubgd.authservice.security.jwt.JwtUtil
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import java.util.Date
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(properties = ["eureka.client.enabled=false"])
@AutoConfigureMockMvc
@Transactional
class OperationAuthorizationTests {
    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var encoder: PasswordEncoder
    @Autowired private lateinit var jwt: JwtUtil
    private var serviceId = 0
    private var routeId = 0
    private var roleId = 0
    private lateinit var token: String

    @BeforeEach fun fixture() {
        serviceId = jdbc.queryForObject("INSERT INTO users.service(service_name) VALUES ('operation-test') RETURNING id", Int::class.java)!!
        routeId = jdbc.queryForObject("INSERT INTO users.route(route, service_id, source, managed_key) VALUES ('/op/**', ?, 'GENERATED', 'main') RETURNING id", Int::class.java, serviceId)!!
        roleId = jdbc.queryForObject("INSERT INTO users.role(application, role_name) VALUES ('OPERATION', 'USER') RETURNING id", Int::class.java)!!
        val userId = jdbc.queryForObject("INSERT INTO users.user_login(login, pass) VALUES ('operation-user', ?) RETURNING id", Int::class.java, encoder.encode("operation-password"))!!
        jdbc.update("INSERT INTO users.user_role(user_id, role_id) VALUES (?, ?)", userId, roleId)
        token = mvc.perform(post("/auth-service/login").contentType(MediaType.APPLICATION_JSON)
            .content("""{"login":"operation-user","password":"operation-password"}"""))
            .andExpect(status().isOk).andReturn().response.contentAsString
    }

    private fun operation(path: String, type: String? = null): Long {
        val id = jdbc.queryForObject("INSERT INTO users.api_operation(service_id, backend_path, gateway_path, http_method) VALUES (?, ?, ?, 'GET') RETURNING id", Long::class.java, serviceId, path, path)!!
        if (type != null) policy(id, "OPENAPI", type)
        return id
    }

    private fun policy(operation: Long, source: String, type: String, force: Boolean = false): Long =
        jdbc.queryForObject("INSERT INTO users.access_policy(api_operation_id, source, access_type, force_override) VALUES (?, ?, ?, ?) RETURNING id", Long::class.java, operation, source, type, force)!!

    private fun check(path: String, expected: Int, authenticated: Boolean = false, id: Int? = routeId, method: String = "GET", rawToken: String? = null) {
        val request = post("/auth-service/validate").contentType(MediaType.APPLICATION_JSON)
            .content("""{"routePath":"$path","method":"$method","routeId":${id ?: "null"}}""")
        if (authenticated || rawToken != null) request.header("Authorization", "Bearer ${rawToken ?: token}")
        mvc.perform(request).andExpect(status().`is`(expected))
    }

    @Test fun `public authentication and blocked operations produce distinct decisions`() {
        operation("/op/public", "PUBLIC"); operation("/op/private"); operation("/op/blocked", "BLOCKED")
        check("/op/public", 200)
        check("/op/private", 401)
        check("/op/private", 200, true)
        check("/op/private", 401, rawToken = "malformed")
        check("/op/blocked", 403, true)
    }

    @Test fun `role policy requires live database membership`() {
        val id = operation("/op/roles")
        val rule = policy(id, "OPENAPI", "ROLES")
        check("/op/roles", 403, true)
        jdbc.update("INSERT INTO users.access_policy_role(policy_id, role_id) VALUES (?, ?)", rule, roleId)
        check("/op/roles", 200, true)
        jdbc.update("DELETE FROM users.user_role WHERE role_id = ?", roleId)
        check("/op/roles", 403, true)
        check("/op/roles", 401)
    }

    @Test fun `expired and non expiring tokens cannot authenticate an operation`() {
        operation("/op/private")
        val expired = Jwts.builder().subject("operation-user").expiration(Date(0))
            .signWith(SignatureAlgorithm.HS256, jwt.secret).compact()
        val noExpiry = Jwts.builder().subject("operation-user")
            .signWith(SignatureAlgorithm.HS256, jwt.secret).compact()
        check("/op/private", 401, rawToken = expired)
        check("/op/private", 401, rawToken = noExpiry)
    }

    @Test fun `unknown operation wrong method and missing or mismatched route ID do not fall back`() {
        operation("/op/public", "PUBLIC")
        jdbc.update("INSERT INTO users.route(route, service_id, \"order\") VALUES ('/op/**', ?, 5)", serviceId)
        check("/op/missing", 404, true)
        check("/op/public", 404, true, method = "POST")
        check("/op/public", 404, true, id = null)
        check("/op/public", 404, true, id = Int.MAX_VALUE)
        check("/outside", 404, true)
        jdbc.update("UPDATE users.route SET active = false WHERE id = ?", routeId)
        check("/op/public", 404, true)
    }

    @Test fun `inactive and ambiguous operations do not fall through to a public template`() {
        operation("/op/{id}", "PUBLIC")
        val retired = operation("/op/retired", "PUBLIC")
        jdbc.update("UPDATE users.api_operation SET active = false WHERE id = ?", retired)
        check("/op/retired", 404)
        check("/op/123", 200)
        operation("/op/{name}", "PUBLIC")
        check("/op/123", 404)
    }

    @Test fun `removing explicit override restores the service declaration`() {
        val id = operation("/op/override", "BLOCKED")
        val admin = policy(id, "ADMIN", "PUBLIC")
        check("/op/override", 403, true)
        jdbc.update("UPDATE users.access_policy SET force_override = true WHERE id = ?", admin)
        check("/op/override", 200)
        jdbc.update("DELETE FROM users.access_policy WHERE id = ?", admin)
        check("/op/override", 403, true)
    }
}
