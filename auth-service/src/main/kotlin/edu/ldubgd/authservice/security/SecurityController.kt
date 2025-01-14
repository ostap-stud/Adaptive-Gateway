package edu.ldubgd.authservice.security

import edu.ldubgd.authservice.db.RoleRepository
import edu.ldubgd.authservice.db.ServiceRouteRepository
import edu.ldubgd.authservice.db.User
import edu.ldubgd.authservice.db.UserRepository
import edu.ldubgd.authservice.security.jwt.JwtUtil
import edu.ldubgd.authservice.security.requests.LogInRequest
import edu.ldubgd.authservice.security.requests.SignUpRequest
import edu.ldubgd.authservice.security.requests.ValidateTokenRequest
import io.jsonwebtoken.ExpiredJwtException
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/auth")
class SecurityController {

    @Autowired
    private lateinit var userRepository: UserRepository
    @Autowired
    private lateinit var roleRepository: RoleRepository
    @Autowired
    private lateinit var routeRepository: ServiceRouteRepository
    @Autowired
    private lateinit var userDetailsService: UserDetailsService
    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder
    @Autowired
    private lateinit var authenticationManager: AuthenticationManager
    @Autowired
    private lateinit var jwtUtil: JwtUtil

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

    @PostMapping("/signup")
    fun signup(@RequestBody signUpRequest: SignUpRequest): ResponseEntity<Any> {
        if(userRepository.existsByLogin(signUpRequest.login)){
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
        return ResponseEntity.status(HttpStatus.CREATED).body("Success")
    }

    @GetMapping("/test")
    fun test(): ResponseEntity<Any> {
        return ResponseEntity.status(HttpStatus.OK).body("YOU REACHED THE AUTH SERVICE")
    }

    @PostMapping("/validate")
    fun validate(@RequestHeader("Authorization") authorization: String?,
                 @RequestBody validateTokenRequest: ValidateTokenRequest
    ): ResponseEntity<Any>{
        var jwt: String? = null
        var username: String? = null
        val userDetails: UserDetailsImpl?
//        var authToken: UsernamePasswordAuthenticationToken? = null
        try {
            if (authorization != null && authorization.startsWith("Bearer ")) {
                jwt = authorization.substring(7)
            }
            if (jwt != null) {
                try {
                    username = jwtUtil.getNameFromToken(jwt)
                } catch (e: ExpiredJwtException) {
                    ResponseEntity.status(HttpServletResponse.SC_UNAUTHORIZED)
                        .body("Authentication failed. " + e.message)
                }
                if (username != null /*&& SecurityContextHolder.getContext().authentication == null*/) {
                    userDetails = userDetailsService.loadUserByUsername(username) as UserDetailsImpl
                    /*if (jwtUtil.validateToken(jwt, userDetails, validateTokenRequest.roles))
                        return ResponseEntity.status(HttpStatus.OK).body("Token is valid!")*/
                    val serviceRoute = routeRepository.findByRoute(validateTokenRequest.routePath)
                    serviceRoute ?: return ResponseEntity.status(HttpStatus.OK).body("Route is public or doesn't exist")
                    val requiredRoles = roleRepository.findByIdIn(serviceRoute.getRolesId()).map { it.role }
                    /*println("Service route: ${serviceRoute.route}\n" +
                            "Required roles: $requiredRoles")*/
                    if (jwtUtil.validateToken(jwt, userDetails, requiredRoles))
                        return ResponseEntity.status(HttpStatus.OK).body("Token is valid!")
                    /*authToken = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
                    SecurityContextHolder.getContext().authentication = authToken*/
                }
            }
        }catch (e: Exception) {
            e.printStackTrace()
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body("Token is invalid or something went wrong.")
    }

}