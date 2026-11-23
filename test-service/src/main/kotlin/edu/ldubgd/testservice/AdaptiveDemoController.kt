package edu.ldubgd.testservice

import io.swagger.v3.oas.models.security.SecurityRequirement
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Opt-in demonstration endpoints; absent from normal BKR test-service deployments. */
@Profile("adaptive-demo")
@RestController
@RequestMapping("/\${spring.application.name}/demo")
class AdaptiveDemoController {
    @GetMapping("/public") fun publicOperation() = "demo public reached"
    @GetMapping("/private") fun privateOperation() = "demo authenticated reached"
    @GetMapping("/blocked") fun blockedOperation() = "backend reached without Gateway policy"
    @GetMapping("/restricted") fun restrictedOperation() = "demo permission granted"
}

@Profile("adaptive-demo")
@Configuration
class AdaptiveDemoOpenApi {
    @Bean
    fun demoPolicy(
        @Value("\${spring.application.name}") name: String,
        @Value("\${demo.public-access:PUBLIC}") publicAccess: String
    ): OpenApiCustomizer {
        require(publicAccess in setOf("PUBLIC", "AUTHENTICATED"))
        return OpenApiCustomizer { api ->
            api.paths["/$name/demo/public"]!!.get.security =
                if (publicAccess == "PUBLIC") emptyList() else listOf(SecurityRequirement().addList("bearerAuth"))
            api.paths["/$name/demo/private"]!!.get.security = listOf(SecurityRequirement().addList("bearerAuth"))
            api.paths["/$name/demo/blocked"]!!.get.addExtension("x-access", mapOf("type" to "INTERNAL"))
            api.paths["/$name/demo/restricted"]!!.get.addExtension("x-access", mapOf("type" to "PERMISSION", "permissions" to listOf("demo.read")))
        }
    }
}
