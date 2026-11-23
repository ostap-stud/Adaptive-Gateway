package edu.ldubgd.authservice.adaptive

import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/\${spring.application.name}/internal/adaptive/services/{name}")
class AdaptiveConfigurationController(
    private val sync: AdaptiveSyncService, private val refresh: AdaptiveRefreshService
) {
    @GetMapping("/configuration")
    fun configuration(@PathVariable name: String) = sync.configuration(name)

    @PutMapping("/configuration")
    fun synchronize(@PathVariable name: String, @RequestBody desired: DesiredServiceConfiguration): AdaptiveSyncResult =
        refresh.refresh(sync.persist(name, desired))

    @PostMapping("/events/{requestId}/refresh")
    fun retry(@PathVariable name: String, @PathVariable requestId: UUID): AdaptiveSyncResult =
        refresh.refresh(sync.event(name, requestId))
}
