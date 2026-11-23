package edu.ldubgd.adaptivemanager

import org.springframework.stereotype.Component
import java.net.URI

@Component
class OpenApiClient(private val http: ReadOnlyJsonClient) {
    // No Auth technical key or caller-supplied server URL is sent to a discovered service.
    fun fetch(target: ServiceTarget) = http.get(URI(target.origin + target.openapiPath))
}
