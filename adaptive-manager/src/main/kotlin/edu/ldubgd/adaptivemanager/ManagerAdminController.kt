package edu.ldubgd.adaptivemanager

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.*
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest

/** Called by Auth; the technical key is never sent to the frontend. */
@Component
class ManagerAdminAuthenticationFilter(private val settings: ManagerProperties) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest) = !request.servletPath.startsWith("/internal")

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val expected = settings.authKey.toByteArray(Charsets.UTF_8)
        val supplied = request.getHeader("X-Adaptive-Key").orEmpty().toByteArray(Charsets.UTF_8)
        if (expected.size < 32 || !MessageDigest.isEqual(expected, supplied)) {
            response.sendError(401)
            return
        }
        chain.doFilter(request, response)
    }
}

@RestController
@RequestMapping("/internal/admin")
class ManagerAdminController(private val observation: ObservationService) {
    data class ModeRequest(val mode: String)
    @GetMapping("/observations") fun observations() = observation.snapshot()
    // Respects the configured mode, admission rules, ownership and optimistic versions.
    @PostMapping("/poll") fun poll() = observation.pollNow()
    @PutMapping("/mode") fun mode(@RequestBody request: ModeRequest) = observation.changeMode(request.mode)
}
