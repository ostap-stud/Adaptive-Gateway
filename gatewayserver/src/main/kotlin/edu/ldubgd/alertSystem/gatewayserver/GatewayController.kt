package edu.ldubgd.alertSystem.gatewayserver

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

@RestController
@RequestMapping("/gateway")
class GatewayController {
    @GetMapping("/test")
    fun userInfo(principal: Principal?): String {
        return principal?.name ?: "Not authenticated"
    }
}