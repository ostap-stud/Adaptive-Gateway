package stand.catalog

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal

class CatalogTests {
    private val repo = mock(CatalogRepository::class.java)
    private val catalog = CatalogService(repo)

    @Test fun rejectsInvalidMoneyAndUnknownCategory() {
        val valid = ProductInput("SSD", 1, BigDecimal("29.99"))
        for (price in listOf("0", "-1", "12.345", "1000000000000")) {
            assertEquals(400, assertThrows(ResponseStatusException::class.java) {
                catalog.save(null, valid.copy(price = BigDecimal(price)))
            }.statusCode.value())
        }
        assertThrows(ResponseStatusException::class.java) { catalog.save(null, valid) }
    }

    @Test fun rejectsReversedPriceRange() {
        assertThrows(ResponseStatusException::class.java) {
            catalog.list(null, BigDecimal.TEN, BigDecimal.ONE, null, 0, 20)
        }
    }
}
