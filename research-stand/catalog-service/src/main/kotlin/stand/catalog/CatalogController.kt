package stand.catalog

import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import stand.support.Access
import stand.support.Kind.PERMISSION
import stand.support.Kind.PUBLIC
import java.math.BigDecimal

@RestController
@RequestMapping("/catalog-service")
class CatalogController(private val catalog: CatalogService) {
    @GetMapping("/products") @Access(PUBLIC)
    fun products(@RequestParam categoryId: Long?, @RequestParam minPrice: BigDecimal?, @RequestParam maxPrice: BigDecimal?,
                 @RequestParam q: String?, @RequestParam(defaultValue="0") page: Int,
                 @RequestParam(defaultValue="20") size: Int) = catalog.list(categoryId, minPrice, maxPrice, q, page, size)

    @GetMapping("/products/{id}") @Access(PUBLIC)
    fun product(@PathVariable id: Long) = catalog.get(id)

    @GetMapping("/categories") @Access(PUBLIC)
    fun categories() = catalog.categories()

    @PostMapping("/products") @Access(PERMISSION, "catalog.write") @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody product: ProductInput) = catalog.save(null, product)

    @PutMapping("/products/{id}") @Access(PERMISSION, "catalog.write")
    fun update(@PathVariable id: Long, @RequestBody product: ProductInput) = catalog.save(id, product)

    @DeleteMapping("/products/{id}") @Access(PERMISSION, "catalog.write") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deactivate(@PathVariable id: Long) = catalog.deactivate(id)
}

@RestController
@Profile("expanded", "authenticated", "permission")
class SelectionController(private val catalog: CatalogService) {
    @GetMapping("/catalog-service/selection") @Access(PUBLIC)
    fun selection() = catalog.list(null, null, BigDecimal("1000"), null, 0, 5)
}

@Configuration
class ExperimentConfiguration {
    private fun selectionPolicy(policy: Map<String, Any>) = OpenApiCustomizer { api ->
        api.paths.getValue("/catalog-service/selection").get.addExtension("x-access", policy)
    }

    @Bean @Profile("authenticated")
    fun authenticatedSelection() = selectionPolicy(mapOf("type" to "AUTHENTICATED"))

    @Bean @Profile("permission")
    fun restrictedSelection() = selectionPolicy(mapOf("type" to "PERMISSION", "permissions" to listOf("catalog.selection")))
}
