package edu.ldubgd.alertSystem.gatewayserver.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

@RestController
@RequestMapping("/api")
class UserLoginController {
    @GetMapping("/user")
    fun userInfo(principal: Principal?): String {
        return principal?.name ?: "null"
    }
}