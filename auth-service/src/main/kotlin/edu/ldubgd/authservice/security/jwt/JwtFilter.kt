package edu.ldubgd.authservice.security.jwt

import edu.ldubgd.authservice.db.RoleRepository
import edu.ldubgd.authservice.db.ServiceRouteRepository
import edu.ldubgd.authservice.security.UserDetailsImpl
import io.jsonwebtoken.ExpiredJwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtFilter : OncePerRequestFilter() {

    @Autowired
    private lateinit var jwtUtil: JwtUtil
    @Autowired
    private lateinit var userDetailsService: UserDetailsService
    @Autowired
    private lateinit var roleRepository: RoleRepository
    @Autowired
    private lateinit var routeRepository: ServiceRouteRepository

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        var jwt: String? = null
        var username: String? = null
        val userDetails: UserDetails?
        var authToken: UsernamePasswordAuthenticationToken? = null
        try {
            val headerAuth = request.getHeader("Authorization")
            if (headerAuth != null && headerAuth.startsWith("Bearer ")) {
                jwt = headerAuth.substring(7)
            }
            if (jwt != null) {
                try {
                    username = jwtUtil.getNameFromToken(jwt)
                } catch (e: ExpiredJwtException) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.message)
                }
                if (username != null && SecurityContextHolder.getContext().authentication == null) {
                    userDetails = userDetailsService.loadUserByUsername(username) as UserDetailsImpl
                    if(request.requestURI == "/auth/signup") {
                        if (authForSignUpAccess(request.requestURI, jwt, userDetails))
                            authToken = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
                    }else{
                        authToken = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
                    }
                    authToken?.let { SecurityContextHolder.getContext().authentication = it }
                }
            }
        }catch (e: Exception) {
            //
        }
        filterChain.doFilter(request, response)
    }

    private fun authForSignUpAccess(signUpRoute: String, jwt: String, userDetails: UserDetailsImpl): Boolean {
        val serviceRoute = routeRepository.findByRoute(signUpRoute)
        val requiredRoles = roleRepository.findByIdIn(serviceRoute?.getRolesId() ?: mutableListOf()).map { it.role }
        println("Service route: ${serviceRoute?.route}\n" +
                "Required roles: $requiredRoles")
        return jwtUtil.validateToken(jwt, userDetails, requiredRoles)

    }

}