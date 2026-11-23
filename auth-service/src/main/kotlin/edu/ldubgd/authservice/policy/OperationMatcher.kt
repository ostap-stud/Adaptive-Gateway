package edu.ldubgd.authservice.policy

import org.springframework.http.server.PathContainer
import org.springframework.stereotype.Component
import org.springframework.web.util.pattern.PathPattern
import org.springframework.web.util.pattern.PathPatternParser
import java.util.Locale

@Component
class OperationMatcher {
    fun find(operations: List<ApiOperation>, path: String, method: String): ApiOperation? {
        val parser = PathPatternParser()
        val candidates = try {
            operations.filter { it.httpMethod == method.uppercase(Locale.ROOT) }.map { operation ->
                // MVP accepts literal segments and {parameter}, not regex or catch-all operations.
                require(operation.gatewayPath.split('/').all { segment ->
                    (!segment.contains('{') && !segment.contains('}') && !segment.contains('*') && !segment.contains('?')) ||
                        segment.matches(Regex("\\{[A-Za-z_][A-Za-z0-9_]*}"))
                })
                operation to parser.parse(operation.gatewayPath)
            }.filter { it.second.matches(PathContainer.parsePath(path)) }
                .sortedWith { a, b -> PathPattern.SPECIFICITY_COMPARATOR.compare(a.second, b.second) }
        } catch (_: IllegalArgumentException) { return null }
        if (candidates.isEmpty()) return null
        if (candidates.size > 1 && PathPattern.SPECIFICITY_COMPARATOR.compare(candidates[0].second, candidates[1].second) == 0) return null
        return candidates.first().first.takeIf { it.active }
    }
}
