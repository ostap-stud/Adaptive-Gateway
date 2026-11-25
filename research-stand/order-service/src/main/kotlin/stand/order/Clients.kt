package stand.order

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.*
import org.springframework.web.server.ResponseStatusException
import stand.support.*
import java.math.BigDecimal
import java.util.UUID

private fun client(url: String): RestClient {
    val factory = SimpleClientHttpRequestFactory().apply {
        setConnectTimeout(1500)
        setReadTimeout(3000)
    }
    return RestClient.builder().baseUrl(url).requestFactory(factory).build()
}

private fun <T> callBackend(service: String, block: () -> T): T = try {
    block()
} catch (error: HttpClientErrorException) {
    when (error.statusCode.value()) {
        404 -> missing("$service resource not found")
        409 -> conflict("$service rejected reservation")
        else -> throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "$service rejected internal request", error)
    }
} catch (error: RestClientException) {
    throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "$service unavailable; retry with the same order ID", error)
}

data class Quote(val price: BigDecimal)

@Component
class CatalogClient(@Value("\${stand.catalog-url}") url: String) {
    private val http = client(url)
    fun price(id: Long) = callBackend("Catalog") {
        http.get().uri("/catalog-service/products/{id}", id).retrieve().body(Quote::class.java)?.price
            ?: throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Missing product price")
    }
}

@Component
class InventoryClient(@Value("\${stand.inventory-url}") url: String) {
    private val http = client(url)
    fun reserve(id: UUID, items: List<Item>) = callBackend("Inventory") {
        http.put().uri("/inventory-service/reservations/{id}", id).body(mapOf("items" to items)).retrieve().toBodilessEntity()
    }
    fun release(id: UUID) = callBackend("Inventory") {
        http.delete().uri("/inventory-service/reservations/{id}", id).retrieve().toBodilessEntity()
    }
}
