package edu.ldubgd.authservice.security

import edu.ldubgd.authservice.db.RoleRepository
import edu.ldubgd.authservice.db.User
import edu.ldubgd.authservice.db.UserRepository
import edu.ldubgd.authservice.security.jwt.JwtUtil
import edu.ldubgd.authservice.security.requests.LogInRequest
import edu.ldubgd.authservice.security.requests.SignUpRequest
import edu.ldubgd.authservice.security.requests.ValidateTokenRequest
import edu.ldubgd.authservice.policy.AuthorizationDecisionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/\${spring.application.name}")
@Tag(name = "Client & Validation API", description = "Операції реєстрації, входу та валідації клієнтів системи")
class SecurityController {

    @Autowired
    private lateinit var userRepository: UserRepository
    @Autowired
    private lateinit var roleRepository: RoleRepository
    @Autowired
    private lateinit var authorizationDecisions: AuthorizationDecisionService
    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder
    @Autowired
    private lateinit var authenticationManager: AuthenticationManager
    @Autowired
    private lateinit var jwtUtil: JwtUtil

    @Operation(
        summary = "[Публічний] Вхід клієнта за вказаними ідентифікаційними та автентифікаційними даними для отримання токена доступу",
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "Дані користувача",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = LogInRequest::class),
                    examples = [
                        ExampleObject(
                            name = "Example of user's id and auth data",
                            value = """
                            {
                              "login": "User321",
                              "password": "Secret"
                            }
                        """
                        )
                    ]
                )
            ]
        ),
        responses = [
            ApiResponse(responseCode = "200", description = "Успішний вхід, токен згенеровано та повернено"),
            ApiResponse(responseCode = "401", description = "Невалідні дані користувача")
        ]
    )
    @PostMapping("/login")
    fun login(@RequestBody logInRequest: LogInRequest): ResponseEntity<Any> {
        val authentication: Authentication
        try {
            authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken(logInRequest.login.lowercase(), logInRequest.password)
            )
        }catch (e: Exception) {
            return ResponseEntity(HttpStatus.UNAUTHORIZED)
        }
        SecurityContextHolder.getContext().authentication = authentication
        val jwt = jwtUtil.generateToken(authentication)
        return ResponseEntity.ok(jwt)
    }

    @Operation(
        summary = "[Обмежений] Реєстрація клієнта за вказаними даними, враховуючи ролі доступу",
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "Дані користувача",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = SignUpRequest::class),
                    examples = [
                        ExampleObject(
                            name = "Example of user's id and auth data",
                            value = """
                            {
                              "login": "User321",
                              "password": "Secret",
                              "contactId": "432",
                              "roles": ["ROLE_TEST_USER"]
                            }
                        """
                        )
                    ]
                )
            ]
        ),
        responses = [
            ApiResponse(responseCode = "201", description = "Успішна реэстрація клієнта в системі"),
            ApiResponse(responseCode = "400", description = "Некоректні дані для реєстрації")
        ]
    )
    @PostMapping("/signup")
    fun signup(@RequestBody signUpRequest: SignUpRequest): ResponseEntity<Any> {
        if(userRepository.existsByLogin(signUpRequest.login.lowercase())){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("This name is already taken")
        }
        if(userRepository.existsByContactId(signUpRequest.contactId)){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("This contact_id is already taken")
        }
        val userRoles = roleRepository.findRolesByRoleIn(signUpRequest.roles)
        val user = User(
            id = 0,
            login = signUpRequest.login.lowercase(),
            pass = passwordEncoder.encode(signUpRequest.password),
            contactId = signUpRequest.contactId
        )
        userRoles.forEach {
            user.addRole(it)
        }
        userRepository.save(user)
        return ResponseEntity.status(HttpStatus.CREATED).body("User is successfully registered!")
    }

    @Operation(
        summary = "[Обмежений] Призначення ролей клієнту",
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "Список ролей для призначення користувачу",
            content = [
                Content(
                    mediaType = "application/json",
                    examples = [
                        ExampleObject(
                            name = "Example list of roles",
                            value = """
                            [
                              "ROLE_TEST_USER"
                            ]
                        """
                        )
                    ]
                )
            ]
        ),
        responses = [
            ApiResponse(responseCode = "200", description = "Ролі успішно призначено"),
            ApiResponse(responseCode = "400", description = "Некоректні дані")
        ]
    )
    @PostMapping("/add-roles/{username}")
    fun addRoles(@PathVariable username: String, @RequestBody roles: List<String>): ResponseEntity<Any> {
        val user = userRepository.findByLogin(username.lowercase()) ?:
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("User doesn't exists")
        val newRoles = roleRepository.findRolesByRoleIn(roles)
        newRoles.forEach {
            user.addRole(it)
        }
        userRepository.save(user)
        return ResponseEntity.status(HttpStatus.OK).body("Roles added successfully!")
    }

    @Operation(
        summary = "[Обмежений] Видалення ролей клієнта",
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "Список ролей користувача для видалення",
            content = [
                Content(
                    mediaType = "application/json",
                    examples = [
                        ExampleObject(
                            name = "Example list of roles",
                            value = """
                            [
                              "ROLE_TEST_USER"
                            ]
                        """
                        )
                    ]
                )
            ]
        ),
        responses = [
            ApiResponse(responseCode = "200", description = "Ролі успішно знято"),
            ApiResponse(responseCode = "400", description = "Некоректні дані")
        ]
    )
    @DeleteMapping("/remove-roles/{username}")
    fun removeRoles(@PathVariable username: String, @RequestBody roles: List<String>): ResponseEntity<Any> {
        val user = userRepository.findByLogin(username.lowercase()) ?:
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("User doesn't exists")
        val removeRoles = roleRepository.findRolesByRoleIn(roles)
        removeRoles.forEach { removeRole ->
            user.roles.removeIf { roleRef ->
                roleRef.roleId == removeRole.id
            }
        }
        userRepository.save(user)
        return ResponseEntity.status(HttpStatus.OK).body("Roles removed successfully!")
    }

    @Operation(
        summary = "[Публічний] Валідація токена доступу, враховуючи ролі користувача та маршрут запиту",
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "Дані HTTP-запиту",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ValidateTokenRequest::class),
                    examples = [
                        ExampleObject(
                            name = "Example of user's id and auth data",
                            value = """
                            {
                              "routePath": "/test-service/services/add",
                              "method": "POST"
                            }
                        """
                        )
                    ]
                )
            ]
        ),
        responses = [
            ApiResponse(responseCode = "200", description = "Токен валідний"),
            ApiResponse(responseCode = "401", description = "Помилка автентифікації або відмова legacy-маршруту"),
            ApiResponse(responseCode = "403", description = "Відмова політики API-операції"),
            ApiResponse(responseCode = "404", description = "Даний маршрут відсутній в системі")
        ]
    )
    @PostMapping("/validate")
    fun validate(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestBody request: ValidateTokenRequest
    ): ResponseEntity<Any> {
        val decision = authorizationDecisions.decide(authorization, request)
        return ResponseEntity.status(decision.status).body(decision.message)
    }
}
