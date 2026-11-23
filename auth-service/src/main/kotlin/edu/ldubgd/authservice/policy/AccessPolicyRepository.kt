package edu.ldubgd.authservice.policy

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class AccessPolicyRepository(private val jdbc: JdbcTemplate) {
    fun findByOperation(operationId: Long): List<AccessPolicy> = jdbc.query(
        """SELECT p.id, p.source, p.access_type, p.force_override, r.role
           FROM users.access_policy p
           LEFT JOIN users.access_policy_role pr ON pr.policy_id = p.id
           LEFT JOIN users.role r ON r.id = pr.role_id
           WHERE p.api_operation_id = ? ORDER BY p.id""",
        { rs, _ -> AccessPolicy(rs.getLong("id"), PolicySource.valueOf(rs.getString("source")),
            AccessType.valueOf(rs.getString("access_type")), rs.getBoolean("force_override"),
            rs.getString("role")?.let { setOf(it) } ?: emptySet()) }, operationId
    ).groupBy { it.id }.values.map { rows ->
        val first = rows.first()
        val groups = if (first.source == PolicySource.OPENAPI) jdbc.queryForList(
            "SELECT permission FROM users.operation_permission WHERE operation_id=? ORDER BY permission", String::class.java, operationId
        ).map { permission -> jdbc.queryForList("""SELECT r.role FROM users.permission_role pr
            JOIN users.role r ON r.id=pr.role_id WHERE pr.permission=?""", String::class.java, permission).toSet() } else emptyList()
        first.copy(roles = rows.flatMap { it.roles }.toSet(), permissionGroups=groups)
    }
}
