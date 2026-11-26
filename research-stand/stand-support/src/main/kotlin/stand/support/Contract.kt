package stand.support

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Access(val type: Kind, val permission: String = "")

enum class Kind { PUBLIC, AUTHENTICATED, PERMISSION, INTERNAL }

@Configuration
class ContractConfiguration {
//    @Bean
//    fun openApi() = OpenAPI().servers(listOf(Server().url("/")))

    @Bean
    fun configureOpenAPI(
        @Value("\${openapi.service.title}") title: String,
        @Value("\${openapi.service.version}") version: String,
        @Value("\${openapi.service.url}") url: String,
    ): OpenAPI {
        val securityScheme = "bearerAuth"
        return OpenAPI()
            .addServersItem(Server().url(url))
            .info(Info().title(title).version(version))
            .addSecurityItem(SecurityRequirement().addList(securityScheme))
            .components(Components().addSecuritySchemes(
                securityScheme,
                SecurityScheme()
                    .name(securityScheme)
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
            ))
    }

    @Bean
    fun accessContract() = OperationCustomizer { operation, method ->
        val rule = method.getMethodAnnotation(Access::class.java)
        if (rule != null) {
            val policy = mutableMapOf<String, Any>("type" to rule.type.name)
            if (rule.type == Kind.PERMISSION) {
                require(rule.permission.isNotBlank())
                policy["permissions"] = listOf(rule.permission)
            }
            operation.addExtension("x-access", policy)
        }
        operation
    }
}

fun bad(message: String): Nothing = throw ResponseStatusException(HttpStatus.BAD_REQUEST, message)
fun missing(message: String): Nothing = throw ResponseStatusException(HttpStatus.NOT_FOUND, message)
fun conflict(message: String): Nothing = throw ResponseStatusException(HttpStatus.CONFLICT, message)

data class Item(val productId: Long, val quantity: Int)
fun normalized(items: List<Item>): List<Item> {
    if (items.size !in 1..100 || items.any { it.productId <= 0 || it.quantity !in 1..100000 }) {
        bad("Supply 1–100 products with positive IDs and quantities up to 100000")
    }
    if (items.distinctBy { it.productId }.size != items.size) bad("Duplicate product ID")
    return items.sortedBy { it.productId }
}

fun offset(page: Int, size: Int): Long {
    if (page < 0 || size !in 1..100) bad("page must be non-negative and size must be 1–100")
    return page.toLong() * size
}
