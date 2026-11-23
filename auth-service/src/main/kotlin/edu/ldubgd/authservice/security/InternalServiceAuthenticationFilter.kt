package edu.ldubgd.authservice.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest

/** Created only inside the dedicated SecurityFilterChain; never a servlet-wide bean. */
class InternalServiceAuthenticationFilter(secret: String) : OncePerRequestFilter() {
    private val expected = secret.toByteArray(Charsets.UTF_8)
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val supplied = request.getHeader("X-Adaptive-Key").orEmpty().toByteArray(Charsets.UTF_8)
        if (expected.size < 32 || !MessageDigest.isEqual(expected, supplied)) {
            response.status = 401
            return
        }
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            "adaptive-manager", null, listOf(SimpleGrantedAuthority("ADAPTIVE_SYNC")))
        chain.doFilter(request, response)
    }
}
