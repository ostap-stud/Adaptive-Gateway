package edu.ldubgd.authservice

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(properties = ["eureka.client.enabled=false"])
@AutoConfigureMockMvc
@Transactional
class AuthSecurityBaselineTests {
    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var encoder: PasswordEncoder

    @BeforeEach
    fun fixture() {
        val role = jdbc.queryForObject("INSERT INTO users.role(application, role_name) VALUES ('SECURITYTEST', 'USER') RETURNING id", Int::class.java)!!
        val user = jdbc.queryForObject("INSERT INTO users.user_login(login, pass) VALUES ('baseline-user', ?) RETURNING id", Int::class.java, encoder.encode("baseline-test-password"))!!
        jdbc.update("INSERT INTO users.user_role(user_id, role_id) VALUES (?, ?)", user, role)
        val route = jdbc.queryForObject("INSERT INTO users.route(route, is_internal) VALUES ('/baseline-protected/**', true) RETURNING id", Int::class.java)!!
        jdbc.update("INSERT INTO users.route_role(route_id, role_id) VALUES (?, ?)", route, role)
    }

    private fun login(): String = mvc.perform(post("/auth-service/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"login":"baseline-user","password":"baseline-test-password"}"""))
        .andExpect(status().isOk).andReturn().response.contentAsString

    @Test
    fun `login token grants permitted route but not administrative access`() {
        val token = login()
        mvc.perform(post("/auth-service/validate").header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"routePath":"/baseline-protected/item","method":"GET"}"""))
            .andExpect(status().isOk)
        mvc.perform(get("/auth-service/service/").header("Authorization", "Bearer $token"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `missing token and wrong password are rejected`() {
        mvc.perform(post("/auth-service/validate").contentType(MediaType.APPLICATION_JSON)
            .content("""{"routePath":"/baseline-protected/item","method":"GET"}"""))
            .andExpect(status().isUnauthorized)
        mvc.perform(post("/auth-service/login").contentType(MediaType.APPLICATION_JSON)
            .content("""{"login":"baseline-user","password":"wrong"}"""))
            .andExpect(status().isUnauthorized)
    }
}
