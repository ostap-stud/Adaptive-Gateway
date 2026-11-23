package edu.ldubgd.authservice.adaptive

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate

@Service
class AdaptiveRefreshService(
    private val jdbc: JdbcTemplate, private val events: AdaptationEventRepository,
    private val locks: ConfigurationWriteLock, private val gateway: GatewayRefreshClient,
    transactions: PlatformTransactionManager
) {
    private val transaction = TransactionTemplate(transactions)

    /** Invoked by the controller only after persist() committed. No HTTP inside the write transaction. */
    fun refresh(event: AdaptationEvent): AdaptiveSyncResult {
        check(!TransactionSynchronizationManager.isActualTransactionActive())
        val before = transaction.execute {
            locks.lock()
            val current = events.find(event.serviceId, event.result.requestId)!!
            if (!isCurrent(current)) events.status(current, "SUPERSEDED", "A newer configuration or management mode replaced this event")
            events.find(event.serviceId, event.result.requestId)!!
        }!!
        if (before.result.status !in setOf("PENDING_REFRESH", "FAILED_REFRESH")) return before.result
        val outcome = gateway.refresh(before)
        return transaction.execute {
            locks.lock()
            if (isCurrent(before)) events.status(before, outcome.status, outcome.error)
            else events.status(before, "SUPERSEDED", "Configuration changed during Gateway refresh")
            events.find(event.serviceId, event.result.requestId)!!.result
        }!!
    }

    private fun isCurrent(event: AdaptationEvent): Boolean = jdbc.queryForObject(
        "SELECT count(*) FROM users.service WHERE id=? AND configuration_version=? AND management_mode='AUTOMATIC'",
        Int::class.java, event.serviceId, event.result.configurationVersion) == 1
}
