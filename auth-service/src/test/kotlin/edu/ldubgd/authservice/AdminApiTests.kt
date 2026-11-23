package edu.ldubgd.authservice

import com.fasterxml.jackson.databind.ObjectMapper
import edu.ldubgd.authservice.admin.ManagerAdminClient
import edu.ldubgd.authservice.security.UserService
import edu.ldubgd.authservice.security.jwt.JwtUtil
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(properties=["eureka.client.enabled=false"])
@AutoConfigureMockMvc
@Transactional
class AdminApiTests {
    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var users: UserService
    @Autowired private lateinit var jwt: JwtUtil
    @Autowired private lateinit var json: ObjectMapper
    @MockBean private lateinit var manager: ManagerAdminClient
    private val prefix="/auth-service/admin"

    @BeforeEach fun fixture() {
        jdbc.update("INSERT INTO users.role(application,role_name) VALUES ('INFRA','ADMIN') ON CONFLICT(role_name,application) DO NOTHING")
        for (name in listOf("panel-admin","panel-user"))
            jdbc.update("INSERT INTO users.user_login(login,pass) VALUES (?, 'secret-hash-never-return')",name)
        jdbc.update("""INSERT INTO users.user_role(user_id,role_id) SELECT u.id,r.id FROM users.user_login u,users.role r
            WHERE u.login='panel-admin' AND r.role='ROLE_INFRA_ADMIN'""")
        `when`(manager.snapshot()).thenReturn(json.readTree("""{"mode":"OBSERVE","registryStatus":"NOT_POLLED","observations":[]}"""))
        `when`(manager.poll()).thenReturn(json.readTree("""{"mode":"OBSERVE","registryStatus":"AVAILABLE","observations":[]}"""))
    }
    private fun token(name: String): String {
        val user=users.loadUserByUsername(name)
        return jwt.generateToken(UsernamePasswordAuthenticationToken(user,null,user.authorities))
    }

    @Test fun `all panel endpoints require admin JWT and technical key is insufficient`() {
        for (path in listOf("services","routes","route-components","users","roles","permissions","adaptive","services/missing/operations")) {
            mvc.perform(get("$prefix/$path")).andExpect(status().isUnauthorized)
            mvc.perform(get("$prefix/$path").header("Authorization","Bearer ${token("panel-user")}"))
                .andExpect(status().isForbidden)
        }
        mvc.perform(post("$prefix/adaptive/poll")).andExpect(status().isUnauthorized)
        mvc.perform(post("$prefix/adaptive/poll").header("Authorization","Bearer ${token("panel-user")}"))
            .andExpect(status().isForbidden)
        mvc.perform(put("$prefix/adaptive/mode").contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("""{"mode":"APPLY"}"""))
            .andExpect(status().isUnauthorized)
        mvc.perform(put("$prefix/adaptive/mode").header("Authorization","Bearer ${token("panel-user")}")
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("""{"mode":"APPLY"}"""))
            .andExpect(status().isForbidden)
        mvc.perform(get("$prefix/users").header("X-Adaptive-Key","integration-test-key-32-characters-long"))
            .andExpect(status().isUnauthorized)
        verifyNoInteractions(manager)
    }

    @Test fun `admin reads users without password and triggers manager through Auth`() {
        val bearer="Bearer ${token("panel-admin")}"
        for (path in listOf("services","routes","route-components","roles","permissions"))
            mvc.perform(get("$prefix/$path").header("Authorization",bearer)).andExpect(status().isOk)
        val body=mvc.perform(get("$prefix/users").header("Authorization",bearer)).andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertFalse(body.contains("secret-hash")); assertFalse(body.contains("\"pass\""))
        val admin=json.readTree(body).single { it.path("login").asText()=="panel-admin" }
        assertEquals("ROLE_INFRA_ADMIN",admin.path("roles")[0].asText())
        mvc.perform(get("$prefix/adaptive").header("Authorization",bearer)).andExpect(status().isOk)
        mvc.perform(post("$prefix/adaptive/poll").header("Authorization",bearer)).andExpect(status().isOk)
        mvc.perform(get("$prefix/services/missing/operations").header("Authorization",bearer)).andExpect(status().isNotFound)
        verify(manager).poll()
    }

    @Test fun `admin mode request is delegated to the technical client`() {
        `when`(manager.mode("OBSERVE")).thenReturn(json.readTree("""{"mode":"OBSERVE","observations":[]}"""))
        mvc.perform(put("$prefix/adaptive/mode").header("Authorization","Bearer ${token("panel-admin")}")
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("""{"mode":"OBSERVE"}"""))
            .andExpect(status().isOk)
        verify(manager).mode("OBSERVE")
    }

    @Test fun `permissions include unmapped declarations and return role arrays`() {
        val sid=jdbc.queryForObject("INSERT INTO users.service(service_name) VALUES ('panel-permissions') RETURNING id",Int::class.java)!!
        val oid=jdbc.queryForObject("""INSERT INTO users.api_operation(service_id,backend_path,gateway_path,http_method)
            VALUES (?,'/panel-permissions/read','/panel-permissions/read','GET') RETURNING id""",Long::class.java,sid)!!
        jdbc.update("INSERT INTO users.operation_permission(operation_id,permission) VALUES (?,'panel.unmapped')",oid)
        val bearer="Bearer ${token("panel-admin")}"
        val body=mvc.perform(get("$prefix/permissions").header("Authorization",bearer)).andExpect(status().isOk)
            .andReturn().response.contentAsString
        val permission=json.readTree(body).single { it.path("permission").asText()=="panel.unmapped" }
        assertTrue(permission.path("roles").isArray); assertTrue(permission.path("roles").isEmpty)
        assertEquals(oid,permission.path("operations")[0].path("id").asLong())
        mvc.perform(get("$prefix/services/panel-permissions/operations").header("Authorization",bearer)).andExpect(status().isOk)
    }
}
