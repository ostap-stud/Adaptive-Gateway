package edu.ldubgd.authservice.security.jwt

import edu.ldubgd.authservice.security.UserDetailsImpl
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import java.util.*


@Component
class JwtUtil {
    @Value("\${alertsystem.app.secret}")
    lateinit var secret: String

    @Value("\${alertsystem.app.expirationMs}")
    var expiration: Int? = null

    fun generateToken(authentication: Authentication): String {
        val userDetails = authentication.principal as UserDetailsImpl
        return Jwts.builder().subject(userDetails.username).claim("roles", userDetails.getRoles()/*userDetails.getRoleNames()*/)
            .expiration(Date(Date().time + expiration!!))
            .signWith(SignatureAlgorithm.HS256, secret)
            .compact()
    }

    /*fun validateToken(token: String, userDetails: UserDetailsImpl, requiredRoles: List<String>): Boolean {
        val username = getNameFromToken(token)
//        val roles = getRolesFromToken(token)
        val roles = userDetails.getRoleNames()
        return (username == userDetails.username && !isTokenExpired(token) && roles.stream().anyMatch { role -> requiredRoles.contains(role) })
    }*/

    /*fun validateToken(token: String, userDetails: UserDetailsImpl, serviceRoute: String): Boolean {
        val username = getNameFromToken(token)
        val roles = userDetails.getRoles()
        val accessedService = serviceRoute.substringAfter('/').substringBefore('/')
        return (
                username == userDetails.username && !isTokenExpired(token)
                        && roles.stream().anyMatch { role ->
                            role.substringAfter('_').substringBefore('_').equals(accessedService, ignoreCase = true)
                        }
                )
    }*/

    fun validateToken(token: String, userDetails: UserDetailsImpl, requiredRoles: List<String>): Boolean {
        val username = getNameFromToken(token)
        val roles = userDetails.getRoles()
        return (username == userDetails.username && !isTokenExpired(token) && roles.any { requiredRoles.contains(it) })
    }

    fun getNameFromToken(token: String): String? {
        /*val parser = Jwts.parser().setSigningKey(secret).build()
        return parser.parseSignedClaims(token).payload.subject*/
        return getClaimFromToken(token) { claims -> claims.subject }
    }

    fun getRolesFromToken(token: String): List<String> {
        return getClaimFromToken(token) { claims -> claims["roles"] as List<String>}
    }

    private fun isTokenExpired(token: String): Boolean {
        return getClaimFromToken(token) { claims -> claims.expiration }.before(Date())
    }

    // Get specified claim from token payload
    fun <T> getClaimFromToken(token: String, claimsResolver: (Claims) -> T): T {
        val claims = getAllClaimsFromToken(token)
        return claimsResolver.invoke(claims)
    }

    // Retrieve all payload (claims)
    private fun getAllClaimsFromToken(token: String?): Claims {
        val parser = Jwts.parser().setSigningKey(secret).build()
        return parser.parseSignedClaims(token).payload
    }

}