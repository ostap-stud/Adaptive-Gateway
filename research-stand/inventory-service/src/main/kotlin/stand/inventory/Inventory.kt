package stand.inventory

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import stand.support.*
import java.util.UUID

data class Stock(val productId: Long, val onHand: Int, val reserved: Int) {
    val available get() = onHand - reserved
}
data class StockInput(val onHand: Int)
data class ReservationInput(val items: List<Item>)
data class Availability(val productId: Long, val quantity: Int, val available: Boolean)

@Repository
class InventoryRepository(private val db: JdbcTemplate) {
    fun stock(id: Long) = db.query("SELECT * FROM inventory.stock WHERE product_id=?", { rs, _ ->
        Stock(id, rs.getInt("on_hand"), rs.getInt("reserved"))
    }, id).singleOrNull()

    fun setStock(id: Long, amount: Int) = db.update("""
        INSERT INTO inventory.stock(product_id,on_hand) VALUES (?,?)
        ON CONFLICT(product_id) DO UPDATE SET on_hand=excluded.on_hand
        WHERE inventory.stock.reserved <= excluded.on_hand
    """.trimIndent(), id, amount)

    fun ensureReservation(id: UUID, released: Boolean) = db.update(
        "INSERT INTO inventory.reservation(order_id,released) VALUES (?,?) ON CONFLICT DO NOTHING", id, released)

    fun releasedForUpdate(id: UUID) = db.queryForObject(
        "SELECT released FROM inventory.reservation WHERE order_id=? FOR UPDATE", Boolean::class.java, id)!!

    fun items(id: UUID) = db.query("SELECT * FROM inventory.reservation_item WHERE order_id=? ORDER BY product_id", { rs, _ ->
        Item(rs.getLong("product_id"), rs.getInt("quantity"))
    }, id)

    fun hold(id: UUID, item: Item) {
        val changed = db.update("""
            UPDATE inventory.stock SET reserved=reserved+?
            WHERE product_id=? AND on_hand-reserved>=?
        """.trimIndent(), item.quantity, item.productId, item.quantity)
        if (changed == 0) conflict("Insufficient stock for product ${item.productId}")
        db.update("INSERT INTO inventory.reservation_item(order_id,product_id,quantity) VALUES (?,?,?)",
            id, item.productId, item.quantity)
    }

    fun release(item: Item) = db.update(
        "UPDATE inventory.stock SET reserved=reserved-? WHERE product_id=?", item.quantity, item.productId)

    fun markReleased(id: UUID) = db.update("UPDATE inventory.reservation SET released=true WHERE order_id=?", id)
}

@Service
class InventoryService(private val repo: InventoryRepository) {
    fun stock(id: Long) = repo.stock(id) ?: missing("Stock not found")

    fun availability(id: Long, quantity: Int): Availability {
        if (quantity <= 0) bad("Positive quantity required")
        return Availability(id, quantity, stock(id).available >= quantity)
    }

    @Transactional
    fun setStock(id: Long, input: StockInput): Stock {
        if (id <= 0 || input.onHand < 0) bad("Invalid stock")
        if (repo.setStock(id, input.onHand) == 0) conflict("Stock is already reserved")
        return stock(id)
    }

    @Transactional
    fun reserve(id: UUID, input: ReservationInput) {
        val items = normalized(input.items)
        val created = repo.ensureReservation(id, false) == 1
        if (repo.releasedForUpdate(id)) conflict("Reservation was released")
        if (created) items.forEach { repo.hold(id, it) }
        else if (repo.items(id) != items) conflict("Reservation ID already used with different items")
    }

    @Transactional
    fun release(id: UUID) {
        // A release may arrive before a timed-out reserve. The tombstone prevents a late hold.
        repo.ensureReservation(id, true)
        if (repo.releasedForUpdate(id)) return
        repo.items(id).forEach { repo.release(it) }
        repo.markReleased(id)
    }
}
