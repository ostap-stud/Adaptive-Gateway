package edu.ldubgd.adaptivemanager

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.web.server.ResponseStatusException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ObservationConcurrencyTests {
    @Test fun `manual trigger rejects a running poll and scheduler does not duplicate it`() {
        val discovery=mock(DiscoveryWatcher::class.java)
        val started=CountDownLatch(1)
        val release=CountDownLatch(1)
        `when`(discovery.discover()).thenAnswer {
            started.countDown()
            check(release.await(5,TimeUnit.SECONDS))
            emptyList<DiscoveryEntry>()
        }
        val settings=ManagerProperties()
        val observer=ObservationService(discovery,mock(OpenApiClient::class.java),mock(OpenApiAnalyzer::class.java),
            mock(AuthServiceClient::class.java),mock(DesiredConfigurationBuilder::class.java),settings,
            SyncCoordinator(settings,mock(AuthSyncClient::class.java)))
        val pool=Executors.newSingleThreadExecutor()
        try {
            val first=pool.submit<ObservationSnapshot> { observer.poll() }
            assertTrue(started.await(5,TimeUnit.SECONDS))
            assertEquals(409,assertThrows(ResponseStatusException::class.java) { observer.pollNow() }.statusCode.value())
            assertEquals(409,assertThrows(ResponseStatusException::class.java) { observer.changeMode("OBSERVE") }.statusCode.value())
            assertEquals("NOT_POLLED",observer.poll().registryStatus)
            release.countDown()
            assertEquals("AVAILABLE",first.get(5,TimeUnit.SECONDS).registryStatus)
            verify(discovery,times(1)).discover()
        } finally { release.countDown(); pool.shutdownNow() }
    }
}
