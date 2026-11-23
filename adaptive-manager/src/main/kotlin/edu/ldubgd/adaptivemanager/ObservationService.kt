package edu.ldubgd.adaptivemanager

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

@Service
class ObservationService(
    private val discovery: DiscoveryWatcher, private val openApi: OpenApiClient,
    private val analyzer: OpenApiAnalyzer, private val auth: AuthServiceClient,
    private val builder: DesiredConfigurationBuilder,
    private val settings: ManagerProperties, private val sync: SyncCoordinator
) {
    private val current = AtomicReference(ObservationSnapshot(mode=settings.mode))
    private val logger = LoggerFactory.getLogger(javaClass)
    private val pollLock = ReentrantLock()
    fun snapshot(): ObservationSnapshot = current.get()

    fun poll(): ObservationSnapshot = executePoll(false)
    fun pollNow(): ObservationSnapshot = executePoll(true)

    fun changeMode(mode: String): ObservationSnapshot {
        if (mode !in setOf("OBSERVE", "APPLY"))
            throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Use OBSERVE or APPLY")
        if (!pollLock.tryLock()) throw ResponseStatusException(HttpStatus.CONFLICT, "Observation is already running")
        return try {
            if (mode == "APPLY") try { settings.validateApply() } catch (e: IllegalArgumentException) {
                throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.message)
            }
            if (settings.mode == mode) return current.get()
            val before = settings.mode
            settings.mode = mode
            // Historical results belong to the previous mode; changing mode does not run a cycle.
            val next = current.get().copy(mode=mode, message="Mode changed; run or await a new observation",
                observations=current.get().observations.map { it.copy(status="STALE", sync=null, proposedRequest=null,
                    message="Historical observation from previous Manager mode") })
            current.set(next)
            logger.info("Administrator changed Manager mode: {} -> {}", before, mode)
            next
        } finally { pollLock.unlock() }
    }

    private fun executePoll(manual: Boolean): ObservationSnapshot {
        if (!pollLock.tryLock()) {
            if (manual) throw ResponseStatusException(HttpStatus.CONFLICT, "Observation is already running")
            return current.get()
        }
        return try { observe() } finally { pollLock.unlock() }
    }

    private fun observe(): ObservationSnapshot {
        val previous = current.get()
        val now = Instant.now()
        val entries = try { discovery.discover() } catch (e: ObservationFailure) {
            val failed = previous.copy(registryStatus="UNAVAILABLE", observedAt=now, message=e.code,
                observations=previous.observations.map {
                    it.copy(status="STALE", proposedRequest=null, sync=null, message="Registry unavailable; candidate is historical")
                })
            if (previous.registryStatus != failed.registryStatus || previous.message != failed.message)
                logger.warn("Registry observation failed: {}", e.code)
            current.set(failed)
            return failed
        }
        val observed = entries.map { entry ->
            val target = entry.target
            if (target == null) ServiceObservation(entry.name, entry.status, now, message=entry.message)
            else try {
                val analysis = analyzer.analyze(target, openApi.fetch(target))
                val preflight = auth.inspect(entry.name)
                val proposed = when (preflight.status) {
                    "AUTOMATIC" -> builder.preview(analysis.candidate, preflight.expectedVersion!!)
                    "NOT_REGISTERED" -> builder.preview(analysis.candidate, 0)
                    else -> null
                }
                ServiceObservation(entry.name, if (analysis.candidate.operations.any { it.access == AccessType.BLOCKED })
                    "ANALYZED_WITH_BLOCKED_OPERATIONS" else "ANALYZED", now, target.instanceId, analysis.candidate,
                    analysis.warnings, preflight,
                    proposed, sync=sync.reconcile(entry.name, analysis.candidate, preflight, proposed))
            } catch (e: ObservationFailure) {
                ServiceObservation(entry.name, "OBSERVATION_FAILED", now, target.instanceId, message=e.code)
            }
        }.toMutableList()
        val names = entries.map { it.name }.toSet()
        previous.observations.filter { it.name !in names }.forEach {
            observed.add(ServiceObservation(it.name, "MISSING", now, message="Absent from current registry; no deactivation is requested"))
        }
        val next = ObservationSnapshot(mode=settings.mode, registryStatus="AVAILABLE", observedAt=now,
            lastSuccessfulPollAt=now, observations=observed.sortedBy { it.name })
        next.observations.forEach { item ->
            val before = previous.observations.find { it.name == item.name }
            if (before?.status != item.status || before.candidate?.specHash != item.candidate?.specHash ||
                before.auth != item.auth || before.message != item.message || before.sync != item.sync)
                logger.info("Observed service={} status={} hash={} auth={} sync={}", item.name, item.status, item.candidate?.specHash, item.auth?.status, item.sync?.status)
        }
        current.set(next)
        return next
    }
}
