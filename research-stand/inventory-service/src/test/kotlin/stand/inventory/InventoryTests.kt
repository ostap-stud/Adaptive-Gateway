package stand.inventory

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.web.server.ResponseStatusException

class InventoryTests {
    private val repo = mock(InventoryRepository::class.java)
    private val inventory = InventoryService(repo)

    @Test fun availabilityExcludesExistingReservations() {
        `when`(repo.stock(1)).thenReturn(Stock(1, 10, 4))
        assertTrue(inventory.availability(1, 6).available)
        assertFalse(inventory.availability(1, 7).available)
    }

    @Test fun rejectsStockReductionBelowHeldQuantity() {
        `when`(repo.setStock(1, 1)).thenReturn(0)
        val error = assertThrows(ResponseStatusException::class.java) { inventory.setStock(1, StockInput(1)) }
        assertEquals(409, error.statusCode.value())
    }
}
