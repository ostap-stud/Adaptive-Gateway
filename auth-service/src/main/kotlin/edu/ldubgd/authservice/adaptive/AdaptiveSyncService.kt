package edu.ldubgd.authservice.adaptive

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.beans.factory.annotation.Value
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.annotation.Isolation
import org.springframework.util.AntPathMatcher
import org.springframework.web.server.ResponseStatusException
import java.security.MessageDigest
import java.util.UUID

@Service
class AdaptiveSyncService(
    private val jdbc: JdbcTemplate, private val json: ObjectMapper,
    private val locks: ConfigurationWriteLock, private val validator: DesiredConfigurationValidator,
    private val events: AdaptationEventRepository,
    @Value("\${adaptive.auto-register:true}") private val autoRegister: Boolean = true
) {
    @Transactional
    fun persist(name: String, input: DesiredServiceConfiguration): AdaptationEvent {
        val start = System.nanoTime()
        validator.validate(name, input)
        val desired = input.copy(operations = input.operations.sortedWith(
            compareBy<DesiredOperation> { it.gatewayPath }.thenBy { it.httpMethod }
        ).map { it.copy(roles = it.roles.toSortedSet(), permissions = it.permissions.toSortedSet()) })
        val hash = MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(desired))
            .joinToString("") { "%02x".format(it) }
        locks.lock()
        if (autoRegister && desired.expectedVersion == 0L) {
            jdbc.update("""INSERT INTO users.service(service_name,management_mode)
                VALUES (?,'AUTOMATIC') ON CONFLICT (service_name) DO NOTHING""", name)
        }
        val service = service(name)
        val sid = (service["id"] as Number).toInt()
        if (service["management_mode"] != "AUTOMATIC") conflict("Service is not AUTOMATIC")
        events.find(sid, desired.requestId)?.let {
            if (it.inputHash != hash) conflict("requestId was already used for another payload")
            return it
        }
        val version = (service["configuration_version"] as Number).toLong()
        if (desired.expectedVersion != version) conflict("Stale configuration version; read configuration again")
        val counts = linkedMapOf("routesChanged" to 0, "operationsChanged" to 0, "policiesChanged" to 0, "overridesPreserved" to 0)
        val existingRoutes = jdbc.queryForList("SELECT * FROM users.route WHERE service_id = ?", sid)
        val desiredRoute = desired.route
        if (desiredRoute != null) rejectShadowingRoutes(name, sid, desired.operations)
        // One stable ingress key for the first implementation. Never repurpose a manual row.
        val ingress = existingRoutes.singleOrNull { it["managed_key"] == "ingress" }
        if (ingress != null && ingress["source"] != "GENERATED") conflict("Managed key belongs to a manual route")
        if (desiredRoute != null) {
            if (ingress == null) {
                jdbc.update("""INSERT INTO users.route(route,service_id,"order",source,managed_key)
                    VALUES (?,?,?,'GENERATED','ingress')""", desiredRoute.path, sid, desiredRoute.order)
                counts["routesChanged"] = 1
            } else if (ingress["is_overridden"] == true) {
                counts["overridesPreserved"] = 1
            } else {
                counts["routesChanged"] = jdbc.update("""UPDATE users.route SET route = ?, "order" = ?, active = true
                    WHERE id = ? AND (route IS DISTINCT FROM ? OR "order" IS DISTINCT FROM ? OR NOT active)""",
                    desiredRoute.path, desiredRoute.order, ingress["id"], desiredRoute.path, desiredRoute.order)
            }
        }
        existingRoutes.filter { it["source"] == "GENERATED" &&
            (desiredRoute == null || it["managed_key"] != "ingress") }.forEach { route ->
            if (route["is_overridden"] == true) counts["overridesPreserved"] = counts.getValue("overridesPreserved") + 1
            else counts["routesChanged"] = counts.getValue("routesChanged") +
                jdbc.update("UPDATE users.route SET active = false WHERE id = ? AND active", route["id"])
        }

        val existingOps = jdbc.queryForList("SELECT * FROM users.api_operation WHERE service_id = ?", sid)
        val wanted = desired.operations.map { it.gatewayPath to it.httpMethod }.toSet()
        existingOps.filter { (it["gateway_path"] to it["http_method"]) !in wanted && it["source"] == "GENERATED" }
            .forEach {
                counts["operationsChanged"] = counts.getValue("operationsChanged") +
                    jdbc.update("UPDATE users.api_operation SET active = false WHERE id = ? AND active", it["id"])
            }
        desired.operations.forEach { op ->
            val existing = existingOps.singleOrNull { it["gateway_path"] == op.gatewayPath && it["http_method"] == op.httpMethod }
            if (existing != null && existing["source"] != "GENERATED") conflict("Operation belongs to manual configuration")
            val oid: Long
            if (existing == null) {
                oid = jdbc.queryForObject("""INSERT INTO users.api_operation
                    (service_id,backend_path,gateway_path,http_method,operation_id,spec_hash,source)
                    VALUES (?,?,?,?,?,?,'GENERATED') RETURNING id""", Long::class.java,
                    sid, op.backendPath, op.gatewayPath, op.httpMethod, op.operationId, desired.specHash)!!
                counts["operationsChanged"] = counts.getValue("operationsChanged") + 1
            } else {
                oid = (existing["id"] as Number).toLong()
                counts["operationsChanged"] = counts.getValue("operationsChanged") +
                    jdbc.update("""UPDATE users.api_operation SET backend_path=?, operation_id=?, spec_hash=?, active=true
                        WHERE id=? AND (backend_path IS DISTINCT FROM ? OR operation_id IS DISTINCT FROM ?
                        OR spec_hash IS DISTINCT FROM ? OR NOT active)""",
                        op.backendPath, op.operationId, desired.specHash, oid, op.backendPath, op.operationId, desired.specHash)
            }
            if (syncPolicy(oid, op)) counts["policiesChanged"] = counts.getValue("policiesChanged") + 1
        }
        val metadataChanged = service["openapi_hash"] != desired.specHash || service["openapi_path"] != desired.openapiPath
        val changed = metadataChanged || counts.filterKeys { it != "overridesPreserved" }.values.any { it > 0 }
        val next = version + if (changed) 1 else 0
        jdbc.update("""UPDATE users.service SET openapi_hash=?, openapi_path=?, last_synced_at=now(),
            configuration_version=? WHERE id=?""", desired.specHash, desired.openapiPath, next, sid)
        val expected = jdbc.queryForList(
            "SELECT id FROM users.route WHERE service_id=? AND active AND NOT is_internal", Int::class.java, sid)
            .map { "${name}_route-$it" }.toSet()
        // A manual route edit advances the version before sync. Even if the preserved override
        // makes this snapshot a database no-op, that new route version still needs publication.
        val needsRefresh = changed || jdbc.queryForObject("""SELECT count(*) FROM users.adaptation_event
            WHERE service_id=? AND configuration_version=? AND status IN ('APPLIED','PENDING_REFRESH','FAILED_REFRESH')""",
            Int::class.java, sid, next) == 0
        return events.insert(sid, desired.requestId, hash, next, needsRefresh, counts, expected,
            (System.nanoTime() - start) / 1_000_000)
    }

    private fun syncPolicy(oid: Long, op: DesiredOperation): Boolean {
        val previousPermissions = jdbc.queryForList("SELECT permission FROM users.operation_permission WHERE operation_id=?", String::class.java, oid).toSet()
        val permissionsChanged = previousPermissions != op.permissions
        if (permissionsChanged) {
            jdbc.update("DELETE FROM users.operation_permission WHERE operation_id=?", oid)
            op.permissions.forEach { jdbc.update("INSERT INTO users.operation_permission(operation_id,permission) VALUES (?,?)", oid, it) }
        }
        val roleIds = op.roles.map { role ->
            jdbc.queryForList("SELECT id FROM users.role WHERE role = ?", Int::class.java, role).singleOrNull()
                ?: throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Unknown or ambiguous role: $role")
        }.toSet()
        // Only the OPENAPI layer is owned by this protocol. CENTRAL and ADMIN remain untouched.
        val old = jdbc.queryForList("SELECT * FROM users.access_policy WHERE api_operation_id=? AND source='OPENAPI'", oid)
            .singleOrNull()
        if (old != null) {
            val oldRoles = jdbc.queryForList("SELECT role_id FROM users.access_policy_role WHERE policy_id=?",
                Int::class.java, old["id"]).toSet()
            if (old["access_type"] == op.access.name && oldRoles == roleIds) return permissionsChanged
        }
        val pid = if (old == null) jdbc.queryForObject("""INSERT INTO users.access_policy
            (api_operation_id,source,access_type) VALUES (?,'OPENAPI',?) RETURNING id""",
            Long::class.java, oid, op.access.name)!!
        else {
            jdbc.update("UPDATE users.access_policy SET access_type=? WHERE id=?", op.access.name, old["id"])
            (old["id"] as Number).toLong()
        }
        jdbc.update("DELETE FROM users.access_policy_role WHERE policy_id=?", pid)
        roleIds.forEach { jdbc.update("INSERT INTO users.access_policy_role(policy_id,role_id) VALUES (?,?)", pid, it) }
        return true
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun configuration(name: String): Map<String, Any?> {
        val service = service(name)
        val sid = service["id"]
        return mapOf("service" to service,
            "routes" to jdbc.queryForList("SELECT * FROM users.route WHERE service_id=? ORDER BY id", sid),
            "operations" to jdbc.queryForList("SELECT * FROM users.api_operation WHERE service_id=? ORDER BY id", sid),
            "policies" to jdbc.queryForList("""SELECT p.*, COALESCE(
                (SELECT jsonb_agg(r.role ORDER BY r.role) FROM users.access_policy_role pr
                    JOIN users.role r ON r.id=pr.role_id WHERE pr.policy_id=p.id), '[]'::jsonb)::text AS roles
                FROM users.access_policy p JOIN users.api_operation o ON o.id=p.api_operation_id
                WHERE o.service_id=? ORDER BY p.id""", sid),
            "latestEvent" to jdbc.queryForList("SELECT request_id,status,configuration_version,error FROM users.adaptation_event WHERE service_id=? ORDER BY id DESC LIMIT 1", sid).firstOrNull(),
            "pendingRefresh" to jdbc.queryForList("""SELECT request_id,status,configuration_version,error
                FROM users.adaptation_event WHERE service_id=? AND configuration_version=?
                AND status IN ('PENDING_REFRESH','FAILED_REFRESH') ORDER BY id""", sid, service["configuration_version"]))
    }

    fun event(name: String, requestId: UUID): AdaptationEvent {
        val sid = (service(name)["id"] as Number).toInt()
        return events.find(sid, requestId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found")
    }
    private fun service(name: String): Map<String, Any?> =
        jdbc.queryForList("SELECT * FROM users.service WHERE service_name=?", name).singleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Register the service through admin API first")

    private fun rejectShadowingRoutes(name: String, sid: Int, operations: List<DesiredOperation>) {
        val paths = listOf("/$name", "/$name/__adaptive_probe") + operations.map {
            it.gatewayPath.replace(Regex("\\{[^/]+}"), "__adaptive_probe")
        }
        val matcher = AntPathMatcher()
        val conflicts = jdbc.queryForList("""SELECT r.route FROM users.route r
            WHERE r.active AND NOT r.is_internal AND (r.service_id <> ? OR r.source = 'MANUAL')
            UNION ALL SELECT p.args->>'patterns' AS route FROM users.route r
            JOIN users.route_predicate rp ON rp.route_id=r.id JOIN users.predicate p ON p.id=rp.predicate_id
            WHERE r.active AND NOT r.is_internal AND p.name='Path'
            AND (r.service_id <> ? OR r.source = 'MANUAL')""", sid, sid)
        if (conflicts.any { row -> row["route"]?.toString().orEmpty().split(',').any { pattern ->
            pattern.trim().startsWith("/$name/") || paths.any { matcher.match(pattern.trim(), it) }
        } }) conflict("An existing route overlaps the generated service namespace")
    }
    private fun conflict(message: String): Nothing = throw ResponseStatusException(HttpStatus.CONFLICT, message)
}
