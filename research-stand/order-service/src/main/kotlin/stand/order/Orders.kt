package stand.order

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import stand.support.*
import java.math.BigDecimal
import java.util.UUID

enum class Status { PENDING, RESERVED, CONFIRMED, CANCELLING, CANCELLED }
data class CreateOrder(val id: UUID, val items: List<Item>)
data class ChangeStatus(val status: Status)
data class Line(val productId: Long, val quantity: Int, val unitPrice: BigDecimal)
data class Purchase(val id: UUID, val status: Status, val items: List<Line>) {
    val total: BigDecimal get() = items.fold(BigDecimal.ZERO) { sum, item -> sum + item.unitPrice * item.quantity.toBigDecimal() }
}

@Repository
class OrderRepository(private val db: JdbcTemplate) {
    fun status(id: UUID, lock: Boolean = false): Status? {
        val suffix = if (lock) " FOR UPDATE" else ""
        return db.query("SELECT status FROM orders.purchase WHERE id=?$suffix",
            { row, _ -> Status.valueOf(row.getString(1)) }, id).singleOrNull()
    }

    fun load(id: UUID, lock: Boolean = false): Purchase {
        val state = status(id, lock) ?: missing("Order not found")
        val lines = db.query("SELECT * FROM orders.line WHERE order_id=? ORDER BY product_id", { row, _ ->
            Line(row.getLong("product_id"), row.getInt("quantity"), row.getBigDecimal("unit_price"))
        }, id)
        return Purchase(id, state, lines)
    }

    fun list(page: Int, size: Int): List<Purchase> =
        db.query("SELECT id FROM orders.purchase ORDER BY created_at DESC,id LIMIT ? OFFSET ?",
            { row, _ -> row.getObject(1, UUID::class.java) }, size, offset(page, size)).map { load(it) }

    @Transactional
    fun insert(id: UUID, lines: List<Line>) {
        val inserted = db.update("INSERT INTO orders.purchase(id,status) VALUES (?,'PENDING') ON CONFLICT DO NOTHING", id)
        if (inserted == 0) return
        for (line in lines) db.update(
            "INSERT INTO orders.line(order_id,product_id,quantity,unit_price) VALUES(?,?,?,?)",
            id, line.productId, line.quantity, line.unitPrice)
    }

    fun setStatus(id: UUID, status: Status) = db.update("UPDATE orders.purchase SET status=? WHERE id=?", status.name, id)
}

@Service
class OrderTransitions(private val repo: OrderRepository, private val inventory: InventoryClient) {
    @Transactional
    fun reserve(id: UUID, items: List<Item>): Purchase {
        val order = repo.load(id, lock = true)
        if (order.items.map { Item(it.productId, it.quantity) } != items) conflict("Order ID already used with different items")
        if (order.status in setOf(Status.CANCELLED, Status.CANCELLING)) conflict("Order is cancelled or cancellation is in progress")
        if (order.status != Status.PENDING) return order
        inventory.reserve(id, items)
        repo.setStatus(id, Status.RESERVED)
        return order.copy(status = Status.RESERVED)
    }

    @Transactional
    fun confirm(id: UUID): Purchase {
        val order = repo.load(id, lock = true)
        if (order.status !in setOf(Status.RESERVED, Status.CONFIRMED)) conflict("Only a reserved order may be confirmed")
        repo.setStatus(id, Status.CONFIRMED)
        return order.copy(status = Status.CONFIRMED)
    }

    @Transactional
    fun beginCancellation(id: UUID) {
        val state = repo.status(id, lock = true) ?: missing("Order not found")
        if (state != Status.CANCELLED) repo.setStatus(id, Status.CANCELLING)
    }

    @Transactional
    fun finishCancellation(id: UUID): Purchase {
        val order = repo.load(id, lock = true)
        if (order.status == Status.CANCELLED) return order
        inventory.release(id)
        repo.setStatus(id, Status.CANCELLED)
        return order.copy(status = Status.CANCELLED)
    }
}

@Service
class OrderService(private val repo: OrderRepository, private val catalog: CatalogClient, private val transitions: OrderTransitions) {
    fun create(input: CreateOrder): Purchase {
        val items = normalized(input.items)
        if (repo.status(input.id) == null) {
            val lines = items.map { Line(it.productId, it.quantity, catalog.price(it.productId)) }
            repo.insert(input.id, lines)
        }
        return transitions.reserve(input.id, items)
    }

    fun cancel(id: UUID): Purchase {
        transitions.beginCancellation(id)
        return transitions.finishCancellation(id)
    }

    fun change(id: UUID, change: ChangeStatus) = when (change.status) {
        Status.CONFIRMED -> transitions.confirm(id)
        Status.CANCELLED -> cancel(id)
        else -> bad("Requested transition is not supported")
    }

    fun get(id: UUID) = repo.load(id)
    fun list(page: Int, size: Int) = repo.list(page, size)
}
