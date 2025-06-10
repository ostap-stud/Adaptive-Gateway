package edu.ldubgd.authservice.security.jwt

import edu.ldubgd.authservice.db.RoleRepository
import edu.ldubgd.authservice.db.service.RouteService
import edu.ldubgd.authservice.security.UserDetailsImpl
import io.jsonwebtoken.ExpiredJwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.access.AccessDeniedException
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
    private lateinit var routeService: RouteService

    @Value("\${spring.application.name}")
    private lateinit var appName: String

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
            if (!request.requestURI.equals("/$appName/validate")){
                val headerAuth = request.getHeader("Authorization")
                if (headerAuth != null && headerAuth.startsWith("Bearer ")) {
                    jwt = headerAuth.substring(7)
                }
                if (jwt != null) {
                    username = jwtUtil.getNameFromToken(jwt)
                    if (username != null && SecurityContextHolder.getContext().authentication == null) {
                        userDetails = userDetailsService.loadUserByUsername(username) as UserDetailsImpl
                        val requestPathRoutes = routeService.findRoutesByRequestPath(request.requestURI)
                        if (requestPathRoutes.isNotEmpty()) {
                            val routeToDirect =
                                routeService.findRouteByRequestMethod(requestPathRoutes, request.method)
                            if (routeToDirect != null) {
                                val requiredRoles = roleRepository.findByIdIn(routeToDirect.getRolesId()).map { it.role!! }
                                if (jwtUtil.validateToken(jwt, userDetails, requiredRoles))
                                    authToken = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
                                else
                                    throw AccessDeniedException("Access denied to [${request.requestURI}] with [${request.method}]")
                            }
                        }
                        authToken ?: throw IllegalArgumentException("Route not found [${request.requestURI}] with [${request.method}]")
                        SecurityContextHolder.getContext().authentication = authToken
                    }
                }
            }
            filterChain.doFilter(request, response)
        }catch (ex: Exception) {
            when (ex){
                is AccessDeniedException, is ExpiredJwtException -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED, ex.message)
                is IllegalArgumentException -> response.sendError(HttpServletResponse.SC_NOT_FOUND, ex.message)
                else -> response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex.message)
            }
        }
    }

}