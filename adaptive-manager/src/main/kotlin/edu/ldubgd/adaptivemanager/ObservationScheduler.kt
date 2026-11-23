package edu.ldubgd.adaptivemanager

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix="manager", name=["polling-enabled"], havingValue="true", matchIfMissing=true)
class ObservationScheduler(private val observation: ObservationService) {
    @Scheduled(fixedDelayString="\${manager.poll-delay-ms:30000}", initialDelayString="\${manager.initial-delay-ms:1000}")
    fun poll() { observation.poll() }
}
