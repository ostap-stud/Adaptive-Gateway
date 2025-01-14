package edu.ldubgd.authservice.security

import edu.ldubgd.authservice.security.jwt.JwtFilter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration

@Configuration
@EnableWebSecurity
class SecurityConfiguration{

    @Autowired
    private lateinit var jwtFilter: JwtFilter

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }

    @Bean
    fun authenticationManager(
        authenticationConfiguration: AuthenticationConfiguration
    ): AuthenticationManager = authenticationConfiguration.authenticationManager

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            csrf { disable() }
            cors { CorsConfiguration().applyPermitDefaultValues() }
            exceptionHandling { authenticationEntryPoint = HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED) }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            authorizeRequests {
                authorize("/error", permitAll)
                authorize("/auth/login", permitAll)
                authorize("/auth/signup", authenticated)
                authorize("/api/**", authenticated)
                authorize(anyRequest, permitAll)
            }
            addFilterBefore<UsernamePasswordAuthenticationFilter>(jwtFilter)
        }
        return http.build()
    }

}