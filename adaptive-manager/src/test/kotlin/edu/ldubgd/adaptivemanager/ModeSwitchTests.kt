package edu.ldubgd.adaptivemanager

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.web.server.ResponseStatusException

class ModeSwitchTests {
    private fun observer(settings: ManagerProperties, discovery: DiscoveryWatcher) = ObservationService(discovery,
        mock(OpenApiClient::class.java),mock(OpenApiAnalyzer::class.java),mock(AuthServiceClient::class.java),
        mock(DesiredConfigurationBuilder::class.java),settings,SyncCoordinator(settings,mock(AuthSyncClient::class.java)))

    @Test fun `mode switch changes shared write guards without starting a cycle and clears old results`() {
        val settings=ManagerProperties(authBaseUrl="http://localhost:8334",authKey="mode-test-key-at-least-32-characters",admissionMode="DISCOVERY")
        val discovery=mock(DiscoveryWatcher::class.java)
        `when`(discovery.discover()).thenReturn(listOf(DiscoveryEntry("demo","IGNORED")))
        val service=observer(settings,discovery)
        val first=service.poll()
        val applied=service.changeMode("APPLY")
        assertEquals("APPLY",settings.mode)
        assertEquals("APPLY",applied.mode)
        assertEquals(first.observedAt,applied.observedAt)
        assertEquals("STALE",applied.observations.single().status)
        assertNull(applied.observations.single().sync)
        assertNull(applied.observations.single().proposedRequest)
        verify(discovery,times(1)).discover()
        assertEquals("OBSERVE",service.changeMode("OBSERVE").mode)
        assertEquals("OBSERVE",settings.mode)
    }

    @Test fun `invalid or unsafe APPLY mode is rejected without changing current mode`() {
        val settings=ManagerProperties()
        val service=observer(settings,mock(DiscoveryWatcher::class.java))
        for(mode in listOf("APPLE","APPLY"))
            assertEquals(422,assertThrows(ResponseStatusException::class.java){service.changeMode(mode)}.statusCode.value())
        assertEquals("OBSERVE",settings.mode)
        assertEquals("OBSERVE",service.snapshot().mode)
    }
}
