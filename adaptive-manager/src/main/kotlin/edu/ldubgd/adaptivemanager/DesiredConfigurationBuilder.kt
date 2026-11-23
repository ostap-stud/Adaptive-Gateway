package edu.ldubgd.adaptivemanager

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.util.UUID

@Component
class DesiredConfigurationBuilder(private val json: ObjectMapper) {
    fun candidate(target: ServiceTarget, operations: List<DesiredOperation>): ConfigurationCandidate {
        val sorted = operations.sortedWith(compareBy<DesiredOperation> { it.gatewayPath }.thenBy { it.httpMethod })
        // Hash only effective routing/policy input. Formatting, descriptions and instance addresses do not matter.
        val content = linkedMapOf("openapiPath" to target.openapiPath,
            "route" to DesiredRoute("/${target.name}/**"), "operations" to sorted)
        val hash = MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(content)).joinToString("") { "%02x".format(it) }
        return ConfigurationCandidate(hash, target.openapiPath, DesiredRoute("/${target.name}/**"), sorted)
    }

    fun preview(candidate: ConfigurationCandidate, version: Long): DesiredServiceConfiguration {
        val requestId = UUID.nameUUIDFromBytes("${candidate.route.path}:${candidate.specHash}:$version".toByteArray(Charsets.UTF_8))
        return DesiredServiceConfiguration(requestId, version, candidate.specHash, candidate.openapiPath, candidate.route, candidate.operations)
    }
}
