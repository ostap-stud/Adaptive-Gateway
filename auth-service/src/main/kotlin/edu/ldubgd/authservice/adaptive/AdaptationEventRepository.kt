package edu.ldubgd.authservice.adaptive

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

data class AdaptationEvent(val id: Long, val serviceId: Int, val inputHash: String,
    val expectedRoutes: Set<String>, val result: AdaptiveSyncResult)

@Repository
class AdaptationEventRepository(private val jdbc: JdbcTemplate, private val json: ObjectMapper) {
    fun find(serviceId: Int, requestId: UUID): AdaptationEvent? = jdbc.query(
        "SELECT * FROM users.adaptation_event WHERE service_id = ? AND request_id = ?", { rs, _ ->
            AdaptationEvent(rs.getLong("id"), serviceId, rs.getString("input_hash"),
                json.readValue(rs.getString("expected_route_ids")),
                AdaptiveSyncResult(requestId, rs.getLong("configuration_version"), rs.getString("status"),
                    json.readValue(rs.getString("counts")), rs.getString("error")))
        }, serviceId, requestId).singleOrNull()

    fun insert(serviceId: Int, requestId: UUID, hash: String, version: Long, changed: Boolean,
               counts: Map<String, Int>, routeIds: Set<String>, duration: Long): AdaptationEvent {
        jdbc.update("""INSERT INTO users.adaptation_event
            (service_id,request_id,input_hash,configuration_version,status,counts,expected_route_ids,duration_ms)
            VALUES (?,?,?,?,?,?::jsonb,?::jsonb,?)""",
            serviceId, requestId, hash, version, if (changed) "PENDING_REFRESH" else "NO_CHANGE",
            json.writeValueAsString(counts), json.writeValueAsString(routeIds), duration)
        return find(serviceId, requestId)!!
    }

    fun status(event: AdaptationEvent, status: String, error: String?) {
        jdbc.update("""UPDATE users.adaptation_event SET status = ?, error = ?,
            applied_at = CASE WHEN ? = 'APPLIED' THEN now() ELSE NULL END
            WHERE id = ? AND status IN ('PENDING_REFRESH','FAILED_REFRESH')""",
            status, error, status, event.id)
    }
}
