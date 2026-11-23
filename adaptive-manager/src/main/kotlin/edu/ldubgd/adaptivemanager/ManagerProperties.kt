package edu.ldubgd.adaptivemanager

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI

@ConfigurationProperties("manager")
data class ManagerProperties(
    @Volatile var mode: String = "OBSERVE",
    val pollingEnabled: Boolean = true,
    val pollDelayMs: Long = 30000,
    val initialDelayMs: Long = 1000,
    val eurekaUrl: String = "http://localhost:8761/eureka/",
    val timeoutMs: Long = 3000,
    val maxResponseBytes: Int = 2097152,
    val authBaseUrl: String = "",
    val authKey: String = "",
    val allowedServices: Set<String> = emptySet(),
    val admissionMode: String = "ALLOWLIST"
) {
    init {
        require(mode in setOf("OBSERVE", "APPLY")) { "Use OBSERVE or explicit APPLY mode" }
        require(allowedServices.all { it.matches(Regex("[a-z][a-z0-9-]{0,62}")) && it !in DiscoveryWatcher.RESERVED })
        require(admissionMode in setOf("ALLOWLIST", "DISCOVERY"))
        if (mode == "APPLY") validateApply()
        require(timeoutMs in 100..30000 && maxResponseBytes in 1024..16777216)
        require(pollDelayMs >= 1000 && initialDelayMs >= 0)
        endpoint(eurekaUrl)
        if (authBaseUrl.isNotEmpty()) {
            require(endpoint(authBaseUrl).path.orEmpty() in setOf("", "/"))
            require(authKey.toByteArray().size >= 32) { "Auth preflight requires a separate technical key" }
        }
    }
    fun admits(name: String) = name !in DiscoveryWatcher.RESERVED && (admissionMode == "DISCOVERY" || name in allowedServices)
    fun validateApply() {
        require((allowedServices.isNotEmpty() || admissionMode == "DISCOVERY") && authBaseUrl.isNotBlank() && authKey.toByteArray().size >= 32) {
            "APPLY requires configured Auth credentials and DISCOVERY or a non-empty ALLOWLIST"
        }
    }
    companion object {
        fun endpoint(value: String): URI = URI(value).also {
            require(it.scheme in setOf("http", "https") && it.host != null &&
                it.userInfo == null && it.query == null && it.fragment == null)
        }
    }
}
