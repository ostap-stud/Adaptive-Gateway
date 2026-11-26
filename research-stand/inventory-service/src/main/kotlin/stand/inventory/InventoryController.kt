package stand.inventory

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import stand.support.Access
import stand.support.Kind.*
import java.util.*

@RestController
@RequestMapping("/inventory-service")
class InventoryController(private val inventory: InventoryService) {
    @GetMapping("/stock/{id}") @Access(PERMISSION,"inventory.manage")
    fun stock(@PathVariable id: Long) = inventory.stock(id)

    @GetMapping("/stock/{id}/availability") @Access(AUTHENTICATED)
    fun availability(@PathVariable id: Long, @RequestParam quantity: Int) = inventory.availability(id, quantity)

    @PutMapping("/stock/{id}") @Access(PERMISSION,"inventory.manage")
    fun update(@PathVariable id: Long, @RequestBody input: StockInput) = inventory.setStock(id, input)

    @PutMapping("/reservations/{id}") @Access(INTERNAL) @ResponseStatus(HttpStatus.NO_CONTENT)
    fun reserve(@PathVariable id: UUID, @RequestBody input: ReservationInput) = inventory.reserve(id, input)

    @DeleteMapping("/reservations/{id}") @Access(INTERNAL) @ResponseStatus(HttpStatus.NO_CONTENT)
    fun release(@PathVariable id: UUID) = inventory.release(id)
}
