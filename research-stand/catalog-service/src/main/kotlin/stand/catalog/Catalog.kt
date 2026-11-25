package stand.catalog

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import stand.support.*
import java.math.BigDecimal
import java.sql.ResultSet

data class Category(val id: Long, val name: String)
data class Product(val id: Long, val name: String, val categoryId: Long, val price: BigDecimal)
data class ProductInput(val name: String, val categoryId: Long, val price: BigDecimal)
data class ProductPage(val page: Int, val size: Int, val products: List<Product>)

@Repository
class CatalogRepository(private val db: NamedParameterJdbcTemplate) {
    private fun product(row: ResultSet, index: Int) =
        Product(row.getLong("id"), row.getString("name"), row.getLong("category_id"), row.getBigDecimal("price"))

    fun categories() = db.query("SELECT * FROM catalog.category ORDER BY id") { row, _ ->
        Category(row.getLong("id"), row.getString("name"))
    }

    fun find(id: Long) = db.query("SELECT * FROM catalog.product WHERE id=:id AND active", mapOf("id" to id), ::product).singleOrNull()

    fun search(categoryId: Long?, min: BigDecimal?, max: BigDecimal?, q: String?, page: Int, size: Int): List<Product> {
        val args = mutableMapOf<String, Any>("size" to size, "offset" to offset(page, size))
        val filters = mutableListOf("active")
        fun filter(name: String, value: Any?, expression: String) {
            if (value != null) {
                args[name] = value
                filters += expression
            }
        }
        filter("category", categoryId, "category_id=:category")
        filter("min", min, "price>=:min")
        filter("max", max, "price<=:max")
        filter("q", q, "strpos(lower(name), lower(:q))>0")
        val where = filters.joinToString(" AND ")
        return db.query("SELECT * FROM catalog.product WHERE $where ORDER BY id LIMIT :size OFFSET :offset", args, ::product)
    }

    fun save(id: Long?, input: ProductInput): Product? {
        val args = mapOf("id" to id, "name" to input.name.trim(), "category" to input.categoryId, "price" to input.price)
        val sql = if (id == null)
            "INSERT INTO catalog.product(name,category_id,price) VALUES (:name,:category,:price)"
        else "UPDATE catalog.product SET name=:name, category_id=:category, price=:price WHERE id=:id AND active"
        return db.query("$sql RETURNING *", args, ::product).singleOrNull()
    }

    fun deactivate(id: Long) = db.update("UPDATE catalog.product SET active=false WHERE id=:id AND active", mapOf("id" to id))
}

@Service
class CatalogService(private val repo: CatalogRepository) {
    fun save(id: Long?, input: ProductInput): Product {
        if (input.name.isBlank() || input.name.trim().length > 160) bad("Invalid name")
        if (input.price.signum() <= 0 || input.price.scale() > 2 || input.price >= BigDecimal("1000000000000"))
            bad("Price must be positive, with at most two decimal places")
        if (repo.categories().none { it.id == input.categoryId }) bad("Unknown category")
        return repo.save(id, input) ?: missing("Product not found")
    }

    fun list(categoryId: Long?, min: BigDecimal?, max: BigDecimal?, q: String?, page: Int, size: Int): ProductPage {
        if (listOfNotNull(min,max).any { it.signum() < 0 } || (min != null && max != null && min > max)) bad("Invalid price range")
        return ProductPage(page, size, repo.search(categoryId, min, max, q, page, size))
    }

    fun categories() = repo.categories()
    fun get(id: Long) = repo.find(id) ?: missing("Product not found")
    fun deactivate(id: Long) { if (repo.deactivate(id) == 0) missing("Product not found") }
}
