package edu.ldubgd.authservice.admin

import edu.ldubgd.authservice.db.ServiceRepository
import edu.ldubgd.authservice.db.ServiceRouteRepository
import edu.ldubgd.authservice.db.RoleRepository
import edu.ldubgd.authservice.db.FilterRepository
import edu.ldubgd.authservice.db.PredicateRepository
import edu.ldubgd.authservice.policy.PolicyAdministration
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

/** Stable read API for the admin panel; existing mutation APIs remain authoritative. */
@RestController
@RequestMapping("/\${spring.application.name}/admin")
class AdminController(private val services: ServiceRepository, private val routes: ServiceRouteRepository,
    private val roles: RoleRepository, private val jdbc: JdbcTemplate,
    private val policies: PolicyAdministration, private val manager: ManagerAdminClient,
    private val filters: FilterRepository, private val predicates: PredicateRepository) {
    @GetMapping("/services") fun services() = services.findAll().sortedBy { it.serviceName }
    @GetMapping("/routes") fun routes() = routes.findAll().sortedBy { it.id }
    @GetMapping("/roles") fun roles() = roles.findAll().sortedBy { it.id }
    @GetMapping("/route-components") fun routeComponents() = mapOf(
        "filters" to filters.findAll().sortedBy { it.id }, "predicates" to predicates.findAll().sortedBy { it.id })

    @GetMapping("/services/{name}/operations") fun operations(@PathVariable name: String): List<Map<String, Any?>> {
        if (services.findByServiceName(name) == null) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found")
        return policies.operations(name)
    }

    @GetMapping("/users") fun users() = jdbc.queryForList(
        "SELECT id,login,login_desc,contact_id FROM users.user_login ORDER BY id"
    ).map { user -> user + ("roles" to jdbc.queryForList("""SELECT r.role FROM users.user_role ur
        JOIN users.role r ON r.id=ur.role_id WHERE ur.user_id=? ORDER BY r.role""", String::class.java, user["id"])) }

    // Include unresolved declarations, mapping-only permissions and historical usages.
    // Arrays are real JSON arrays, rather than the legacy endpoint's JSON-encoded strings.
    @GetMapping("/permissions") fun permissions() = jdbc.queryForList("""SELECT permission FROM users.operation_permission
        UNION SELECT permission FROM users.permission_role ORDER BY permission""", String::class.java).map { permission ->
        mapOf("permission" to permission,
            "roles" to jdbc.queryForList("""SELECT r.role FROM users.permission_role pr
                JOIN users.role r ON r.id=pr.role_id WHERE pr.permission=? ORDER BY r.role""", String::class.java, permission),
            "operations" to jdbc.queryForList("""SELECT o.id,s.service_name,o.gateway_path,o.http_method,o.active
                FROM users.operation_permission p JOIN users.api_operation o ON o.id=p.operation_id
                JOIN users.service s ON s.id=o.service_id WHERE p.permission=? ORDER BY o.id""", permission))
    }

    @GetMapping("/adaptive") fun adaptive() = manager.snapshot()
    @PostMapping("/adaptive/poll") fun poll() = manager.poll()
    data class ModeRequest(val mode: String)
    @PutMapping("/adaptive/mode") fun mode(@RequestBody request: ModeRequest) = manager.mode(request.mode)
}
