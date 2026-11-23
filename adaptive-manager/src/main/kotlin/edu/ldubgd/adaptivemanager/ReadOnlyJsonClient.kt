package edu.ldubgd.adaptivemanager

import org.springframework.stereotype.Component
import java.net.URI

/** Discovery and OpenAPI consumers retain a GET-only interface. */
@Component
class ReadOnlyJsonClient(private val transport: JsonHttpTransport) {
    fun get(uri: URI, headers: Map<String, String> = emptyMap()) = transport.request("GET", uri, headers)
}
