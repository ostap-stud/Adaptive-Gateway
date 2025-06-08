package edu.ldubgd.authservice.db.controller

import edu.ldubgd.authservice.db.RoleRepository
import edu.ldubgd.authservice.db.dto.RoleDTO
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/\${spring.application.name}/role")
class RoleController {

    @Autowired
    private lateinit var roleRepository: RoleRepository

    @GetMapping("/")
    fun getRoles(): ResponseEntity<Any> {
        return ResponseEntity.ok(roleRepository.findAll())
    }

    @GetMapping("/{name}")
    fun getRole(@PathVariable name: String): ResponseEntity<Any> {
        roleRepository.findRoleByRole(name)?.let {
            return ResponseEntity.ok(it)
        }
        return ResponseEntity.notFound().build()
    }

    @PostMapping("/add")
    fun addRoles(@RequestBody roles: List<RoleDTO>): ResponseEntity<Any> {
        try {
            if (roles.isNotEmpty()) {
                roles.forEach { roleDTO ->
                    roleRepository.save(
                        name = roleDTO.roleName.uppercase(),
                        description = roleDTO.roleDescription,
                        application = roleDTO.application.uppercase()
                    )
                }
            }else {
                return ResponseEntity.status(HttpStatus.NO_CONTENT).body("No roles found in request body")
            }
        }catch (ex: Exception){
            ex.printStackTrace()
        }
        return ResponseEntity.status(HttpStatus.CREATED).body("Successfully added ${roles.size} roles")
    }

    @PutMapping("/{id}")
    fun updateRole(@PathVariable id: Int, @RequestBody role: RoleDTO): ResponseEntity<Any> {
        val current = roleRepository.findById(id)
        if (current.isPresent) {
            roleRepository.update(id, role.roleName, role.roleDescription, role.application)
            return ResponseEntity.status(HttpStatus.OK).body("Successfully updated Role (id: $id)")
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Not found")
    }

    @DeleteMapping("/{id}")
    fun deleteRole(@PathVariable id: Int): ResponseEntity<Any> {
        val deleteRole = roleRepository.findById(id)
        if (deleteRole.isPresent) {
            roleRepository.deleteById(id)
            return ResponseEntity.ok("Successfully deleted Role (id: $id)")
        }
        return ResponseEntity.notFound().build()
    }

}