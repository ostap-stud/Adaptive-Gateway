package edu.ldubgd.authservice.adaptive

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronizationManager

/** A single transaction lock is sufficient for the first, single-manager deployment. */
@Component
class ConfigurationWriteLock(private val jdbc: JdbcTemplate) {
    fun lock() {
        check(TransactionSynchronizationManager.isActualTransactionActive())
        jdbc.execute("SELECT pg_advisory_xact_lock(718034202)")
    }
    fun route(id: Int) {
        lock()
        jdbc.update("""UPDATE users.service SET configuration_version = configuration_version + 1
            WHERE id = (SELECT service_id FROM users.route WHERE id = ?)""", id)
    }
    fun service(id: Int?) {
        lock()
        if (id != null) jdbc.update(
            "UPDATE users.service SET configuration_version = configuration_version + 1 WHERE id = ?", id)
    }
}
