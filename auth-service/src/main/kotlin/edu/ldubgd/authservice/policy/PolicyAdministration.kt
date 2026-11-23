package edu.ldubgd.authservice.policy

import com.fasterxml.jackson.databind.ObjectMapper
import edu.ldubgd.authservice.adaptive.ConfigurationWriteLock
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.security.Principal

data class PermissionMappingRequest(val roles: Set<String>, val reason: String)
data class PolicyChangeRequest(val accessType: AccessType, val roles: Set<String> = emptySet(),
    val forceOverride: Boolean = false, val reason: String)
data class PolicyRemovalRequest(val reason: String)

@Service
class PolicyAdministration(private val jdbc: JdbcTemplate, private val json: ObjectMapper,
    private val lock: ConfigurationWriteLock, private val policies: AccessPolicyRepository, private val resolver: PolicyResolver) {
    fun operations(name: String): List<Map<String, Any?>> = jdbc.queryForList(
        "SELECT o.* FROM users.api_operation o JOIN users.service s ON s.id=o.service_id WHERE s.service_name=? ORDER BY o.gateway_path,o.http_method", name
    ).map { row ->
        val id=(row["id"] as Number).toLong()
        row + mapOf("permissions" to jdbc.queryForList("SELECT permission FROM users.operation_permission WHERE operation_id=? ORDER BY permission",String::class.java,id),
            "policies" to policies.findByOperation(id), "effectivePolicy" to resolver.resolve(policies.findByOperation(id)))
    }
    fun mappings() = jdbc.queryForList("""SELECT pr.permission, jsonb_agg(r.role ORDER BY r.role)::text AS roles
        FROM users.permission_role pr JOIN users.role r ON r.id=pr.role_id GROUP BY pr.permission ORDER BY pr.permission""")
    fun audit() = jdbc.queryForList("SELECT * FROM users.policy_audit ORDER BY id DESC LIMIT 100")

    @Transactional
    fun mapping(permission: String, request: PermissionMappingRequest, actor: String) {
        valid(permission.matches(Regex("[a-zA-Z][a-zA-Z0-9_.:-]{0,127}")), "Invalid permission")
        reason(request.reason)
        lock.lock()
        val ids=roleIds(request.roles)
        val before=jdbc.queryForList("SELECT role_id FROM users.permission_role WHERE permission=? ORDER BY role_id",permission)
        jdbc.update("DELETE FROM users.permission_role WHERE permission=?",permission)
        ids.forEach { jdbc.update("INSERT INTO users.permission_role(permission,role_id) VALUES (?,?)",permission,it) }
        jdbc.update("""UPDATE users.service SET configuration_version=configuration_version+1 WHERE id IN (
            SELECT o.service_id FROM users.api_operation o JOIN users.operation_permission p ON p.operation_id=o.id WHERE p.permission=?)""",permission)
        record(actor,"PERMISSION_MAPPING",permission,request.reason,before,request)
    }
    @Transactional
    fun change(id: Long, source: PolicySource, request: PolicyChangeRequest, actor: String) {
        valid(source in setOf(PolicySource.CENTRAL,PolicySource.ADMIN),"Only CENTRAL and ADMIN can be edited")
        valid(!request.forceOverride || source==PolicySource.ADMIN,"Force override requires ADMIN source")
        valid(if(request.accessType==AccessType.ROLES) request.roles.isNotEmpty() else request.roles.isEmpty(),"ROLES requires roles; other types reject roles")
        reason(request.reason)
        lock.lock()
        val sid=serviceFor(id)
        val ids=roleIds(request.roles)
        val before=policies.findByOperation(id)
        val pid=jdbc.queryForObject("""INSERT INTO users.access_policy(api_operation_id,source,access_type,force_override,reason)
            VALUES (?,?,?,?,?) ON CONFLICT(api_operation_id,source) DO UPDATE
            SET access_type=EXCLUDED.access_type,force_override=EXCLUDED.force_override,reason=EXCLUDED.reason RETURNING id""",
            Long::class.java,id,source.name,request.accessType.name,request.forceOverride,request.reason)!!
        jdbc.update("DELETE FROM users.access_policy_role WHERE policy_id=?",pid)
        ids.forEach { jdbc.update("INSERT INTO users.access_policy_role(policy_id,role_id) VALUES (?,?)",pid,it) }
        lock.service(sid)
        record(actor,"POLICY_PUT","$id/${source.name}",request.reason,before,policies.findByOperation(id))
    }
    @Transactional
    fun remove(id: Long, source: PolicySource, request: PolicyRemovalRequest, actor: String) {
        valid(source in setOf(PolicySource.CENTRAL,PolicySource.ADMIN),"Only CENTRAL and ADMIN can be removed")
        reason(request.reason)
        lock.lock()
        val sid=serviceFor(id)
        val before=policies.findByOperation(id)
        jdbc.update("DELETE FROM users.access_policy WHERE api_operation_id=? AND source=?",id,source.name)
        lock.service(sid)
        record(actor,"POLICY_DELETE","$id/${source.name}",request.reason,before,policies.findByOperation(id))
    }
    private fun serviceFor(id: Long) = jdbc.queryForList("SELECT service_id FROM users.api_operation WHERE id=?",Int::class.java,id)
        .singleOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND,"Operation not found")
    private fun roleIds(roles: Set<String>): List<Int> {
        valid(roles.size<=100,"Too many roles")
        return roles.map { role -> jdbc.queryForList("SELECT id FROM users.role WHERE role=?",Int::class.java,role).singleOrNull()
            ?: throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Unknown or ambiguous role: $role") }
    }
    private fun reason(value: String) = valid(value.isNotBlank() && value.length<=2000,"A reason of 1..2000 characters is required")
    private fun record(actor: String, action: String, target: String, reason: String, before: Any, after: Any) {
        jdbc.update("INSERT INTO users.policy_audit(actor,action,target,reason,before_state,after_state) VALUES (?,?,?,?,?::jsonb,?::jsonb)",
            actor,action,target,reason,json.writeValueAsString(before),json.writeValueAsString(after))
    }
    private fun valid(value: Boolean, message: String) { if(!value) throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,message) }
}

@RestController
@RequestMapping("/\${spring.application.name}/policy")
class PolicyAdminController(private val administration: PolicyAdministration) {
    @GetMapping("/services/{name}/operations") fun operations(@PathVariable name: String) = administration.operations(name)
    @GetMapping("/permissions") fun mappings() = administration.mappings()
    @PutMapping("/permissions/{permission}") fun mapping(@PathVariable permission: String,
        @RequestBody request: PermissionMappingRequest, principal: Principal) = administration.mapping(permission,request,principal.name)
    @PutMapping("/operations/{id}/{source}") fun change(@PathVariable id: Long,@PathVariable source: PolicySource,
        @RequestBody request: PolicyChangeRequest,principal: Principal) = administration.change(id,source,request,principal.name)
    @DeleteMapping("/operations/{id}/{source}") fun remove(@PathVariable id: Long,@PathVariable source: PolicySource,
        @RequestBody request: PolicyRemovalRequest,principal: Principal) = administration.remove(id,source,request,principal.name)
    @GetMapping("/audit") fun audit() = administration.audit()
}
