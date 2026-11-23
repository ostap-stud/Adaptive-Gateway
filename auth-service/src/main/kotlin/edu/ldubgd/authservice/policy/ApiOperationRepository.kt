package edu.ldubgd.authservice.policy

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class ApiOperationRepository(private val jdbc: JdbcTemplate) {
    // Include inactive operations: a retired static path must not fall through to a public template.
    fun findByService(serviceId: Int): List<ApiOperation> = jdbc.query(
        "SELECT id, service_id, backend_path, gateway_path, http_method, active FROM users.api_operation WHERE service_id = ?",
        { rs, _ -> ApiOperation(rs.getLong("id"), rs.getInt("service_id"), rs.getString("backend_path"),
            rs.getString("gateway_path"), rs.getString("http_method"), rs.getBoolean("active")) }, serviceId
    )
}
