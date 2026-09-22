package edu.ldubgd.authservice.db.controller

import edu.ldubgd.authservice.db.RoleRepository
import edu.ldubgd.authservice.db.dto.RoleDTO
import edu.ldubgd.authservice.db.dto.ServiceDTO
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/\${spring.application.name}/role")
@Tag(name = "Role Management", description = "Керування ролями в системі")
class RoleController {

    @Autowired
    private lateinit var roleRepository: RoleRepository

    @Operation(
        summary = "Отримання існуючих в системі ролей",
        responses = [
            ApiResponse(responseCode = "200")
        ]
    )
    @GetMapping("/")
    fun getRoles(): ResponseEntity<Any> {
        return ResponseEntity.ok(roleRepository.findAll())
    }

    @Operation(
        summary = "Отримання інформації про роль за її ПОВНОЮ назвою",
        responses = [
            ApiResponse(responseCode = "200"),
            ApiResponse(responseCode = "404", description = "Роль не знайдено")
        ]
    )
    @GetMapping("/{name}")
    fun getRole(@PathVariable name: String): ResponseEntity<Any> {
        roleRepository.findRoleByRole(name)?.let {
            return ResponseEntity.ok(it)
        }
        return ResponseEntity.notFound().build()
    }

    @Operation(
        summary = "Створення та збереження ролі",
        description = "Вказується назва ролі + сервіс застосування. Роль автоматично сформується в БД як ROLE_[СЕРВІС]_[НАЗВА]",
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "Список ролей для збереження",
            content = [
                Content(
                    mediaType = "application/json",
                    array = ArraySchema(schema = Schema(implementation = RoleDTO::class)),
                    examples = [
                        ExampleObject(
                            name = "Example of adding roles",
                            value = """
                            [
                              {
                                "roleName": "HEAD",
                                "application": "SERVICE",
                                "roleDescription": "about role"
                              }
                            ]
                        """
                        )
                    ]
                )
            ]
        ),
        responses = [
            ApiResponse(responseCode = "201", description = "Ролі успішно створено"),
            ApiResponse(responseCode = "204", description = "Тіло запиту порожнє"),
            ApiResponse(responseCode = "400", description = "Помилка обробки запиту")
        ]
    )
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
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.message)
        }
        return ResponseEntity.status(HttpStatus.CREATED).body("Successfully added ${roles.size} roles")
    }

    @Operation(
        summary = "Оновлення ролі",
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "Роль з оновленими даними",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ServiceDTO::class),
                    examples = [
                        ExampleObject(
                            name = "Example of updating role",
                            value = """
                            {
                              "roleName": "HEAD",
                              "application": "SERVICE",
                              "roleDescription": "about role"
                            }
                        """
                        )
                    ]
                )
            ]
        ),
        responses = [
            ApiResponse(responseCode = "200", description = "Роль успішно оновлено"),
            ApiResponse(responseCode = "404", description = "Роль не знайдено")
        ]
    )
    @PutMapping("/{id}")
    fun updateRole(@PathVariable id: Int, @RequestBody role: RoleDTO): ResponseEntity<Any> {
        val current = roleRepository.findById(id)
        if (current.isPresent) {
            roleRepository.update(id, role.roleName, role.roleDescription, role.application)
            return ResponseEntity.status(HttpStatus.OK).body("Successfully updated Role (id: $id)")
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Not found")
    }

    @Operation(
        summary = "Видалення ролі",
        responses = [
            ApiResponse(responseCode = "200", description = "Роль успішно видалено"),
            ApiResponse(responseCode = "404", description = "Роль не знайдено")
        ]
    )
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