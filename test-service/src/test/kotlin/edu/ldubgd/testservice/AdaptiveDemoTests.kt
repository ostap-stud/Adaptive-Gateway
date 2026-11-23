package edu.ldubgd.testservice

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(properties=["spring.profiles.active=adaptive-demo","eureka.client.enabled=false"])
@AutoConfigureMockMvc
class AdaptiveDemoTests {
    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var json: ObjectMapper
    @Test fun `demo publishes explicit public private and unsupported access declarations`() {
        val response=mvc.perform(get("/test-service/v3/api-docs")).andExpect(status().isOk).andReturn().response.contentAsString
        val paths=json.readTree(response).path("paths")
        assertTrue(paths.path("/test-service/demo/public").path("get").path("security").isArray)
        assertEquals(0,paths.path("/test-service/demo/public").path("get").path("security").size())
        assertTrue(paths.path("/test-service/demo/private").path("get").path("security")[0].has("bearerAuth"))
        assertEquals("INTERNAL", paths.path("/test-service/demo/blocked").path("get").path("x-access").path("type").asText())
        assertEquals("PERMISSION", paths.path("/test-service/demo/restricted").path("get").path("x-access").path("type").asText())
        mvc.perform(get("/test-service/demo/public")).andExpect(status().isOk)
    }
}
