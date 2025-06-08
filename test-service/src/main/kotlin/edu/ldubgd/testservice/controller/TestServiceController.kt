package edu.ldubgd.testservice.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

@RestController
@RequestMapping("/\${spring.application.name}")
class TestServiceController {

    @GetMapping("/get")
    fun testGet(): String = "Successfully reached the endpoint!"

    @GetMapping("/user")
    fun user(principal: Principal?): String{
        return principal?.name ?: "Not authenticated"
    }
}