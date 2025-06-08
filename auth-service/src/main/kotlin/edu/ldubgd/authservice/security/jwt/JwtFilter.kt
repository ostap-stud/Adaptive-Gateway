package edu.ldubgd.authservice.security.jwt

import edu.ldubgd.authservice.db.RoleRepository
import edu.ldubgd.authservice.db.ServiceRouteRepository
import edu.ldubgd.authservice.security.UserDetailsImpl
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
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
        val username: String?
        val userDetails: UserDetails?
        var authToken: UsernamePasswordAuthenticationToken? = null
        try {
            val headerAuth = request.getHeader("Authorization")
            if (headerAuth != null && headerAuth.startsWith("Bearer ")) {
                jwt = headerAuth.substring(7)
            }
            if (jwt != null) {
                username = jwtUtil.getNameFromToken(jwt)
                if (username != null && SecurityContextHolder.getContext().authentication == null) {
                    userDetails = userDetailsService.loadUserByUsername(username) as UserDetailsImpl
                    val rootRoute = "/${request.requestURI.substringAfter('/').substringBefore('/')}/**"
                    val serviceRoute =
                        routeRepository.findByRoute(request.requestURI) ?:
                        routeRepository.findByRoute(rootRoute)
                    if (serviceRoute != null){
                        val requiredRoles = roleRepository.findByIdIn(serviceRoute.getRolesId()).map { it.role!! }
                        if(jwtUtil.validateToken(jwt, userDetails, requiredRoles)) {
                            authToken = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
                        } else{
                            throw ResourceAccessException("User is not allowed to access this resource")
                        }
                    }
                    authToken?.let { SecurityContextHolder.getContext().authentication = it }
                }
            }
            filterChain.doFilter(request, response)
        }catch (e: Exception) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.message)
        }
    }

}