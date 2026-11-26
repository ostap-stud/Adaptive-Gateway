package stand.order

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import stand.support.Access
import stand.support.Kind.AUTHENTICATED
import stand.support.Kind.PERMISSION
import java.util.*

@RestController
@RequestMapping("/order-service/orders")
class OrderController(private val orders: OrderService) {
    @PostMapping @Access(AUTHENTICATED) @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody input: CreateOrder) = orders.create(input)

    @GetMapping("/{id}") @Access(PERMISSION, "orders.read")
    fun get(@PathVariable id: UUID) = orders.get(id)

    @GetMapping @Access(PERMISSION, "orders.read")
    fun list(@RequestParam(defaultValue="0") page: Int, @RequestParam(defaultValue="20") size: Int) = orders.list(page, size)

    @PostMapping("/{id}/cancel") @Access(PERMISSION, "orders.manage")
    fun cancel(@PathVariable id: UUID) = orders.cancel(id)

    @PatchMapping("/{id}/status") @Access(PERMISSION, "orders.manage")
    fun change(@PathVariable id: UUID, @RequestBody input: ChangeStatus) = orders.change(id, input)
}
