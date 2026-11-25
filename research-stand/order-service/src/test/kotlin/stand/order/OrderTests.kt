package stand.order

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import stand.support.Item
import stand.support.normalized
import java.math.BigDecimal
import java.util.UUID

class OrderTests {
    @Test fun calculatesTotalFromPriceSnapshots() {
        val order = Purchase(UUID.randomUUID(), Status.RESERVED,
            listOf(Line(1, 3, BigDecimal("10.19")), Line(2, 2, BigDecimal("12.95"))))
        assertEquals(BigDecimal("56.47"), order.total)
    }

    @Test fun rejectsDuplicateProductAndEmptyBasket() {
        for (items in listOf(emptyList(), listOf(Item(1,1), Item(1,2)), listOf(Item(1,0)))) {
            assertThrows(ResponseStatusException::class.java) { normalized(items) }
        }
    }
}
