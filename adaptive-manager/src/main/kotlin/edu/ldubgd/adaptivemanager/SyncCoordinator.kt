package edu.ldubgd.adaptivemanager

import org.springframework.stereotype.Component

@Component
class SyncCoordinator(private val settings: ManagerProperties, private val client: AuthSyncClient) {
    fun reconcile(name: String, candidate: ConfigurationCandidate, auth: AuthPreflight,
                  proposed: DesiredServiceConfiguration?): SyncOutcome? {
        if (settings.mode == "OBSERVE") return null
        if (!settings.admits(name)) return SyncOutcome("SKIP","NOT_ALLOWLISTED")
        if (auth.status !in setOf("AUTOMATIC", "NOT_REGISTERED") || proposed == null) return SyncOutcome("SKIP",auth.status)
        if (auth.specHash == candidate.specHash) {
            // Read durable state after each restart or uncertain response. Never trust a local success cache.
            auth.pendingRefresh.lastOrNull()?.let { return client.retry(name,it) }
            val latest = auth.latestEvent
            if (latest != null && latest.configurationVersion == auth.expectedVersion &&
                latest.status in setOf("APPLIED","NO_CHANGE"))
                return SyncOutcome("SKIP","UNCHANGED",latest.requestId,latest.configurationVersion)
        }
        // At most one write per service per poll. Conflicts cause a fresh GET on the next poll.
        return client.apply(name,proposed)
    }
}
