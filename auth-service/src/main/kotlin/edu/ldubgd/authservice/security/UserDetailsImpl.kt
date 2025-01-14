package edu.ldubgd.authservice.security

import edu.ldubgd.authservice.db.Role
import edu.ldubgd.authservice.db.User
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import java.util.stream.Collectors

class UserDetailsImpl() : UserDetails {

    private var id: Int? = null
    private var authority: String? = null
    private lateinit var login: String
    private lateinit var password: String
    private lateinit var roles: List<Role>

    constructor(
        id: Int?,
        login: String,
        password: String,
        authority: String?,
        roles: List<Role> = listOf(
            Role(
                id = 0, roleName = "USER", application = "HEAD", role = "ROLE_HEAD_USER"
            )
        )
    ) : this() {
        this.id = id
        this.login = login
        this.password = password
        this.authority = authority
        this.roles = roles
    }

    override fun getAuthorities(): MutableCollection<out GrantedAuthority> {
        val grantedAuthorities = HashSet<GrantedAuthority>()
        if (this.authority != null) {
            grantedAuthorities.add(SimpleGrantedAuthority(authority))
        } else{
            roles.forEach { role ->
                grantedAuthorities.add(SimpleGrantedAuthority(role.roleName))
            }
        }
        return grantedAuthorities
    }

    override fun getPassword(): String {
        return password
    }

    override fun getUsername(): String {
        return login
    }

    fun getRoleNames(): Set<String>{
        return roles.stream().map(Role::roleName).collect(Collectors.toSet())
    }

    fun getRoles(): Set<String>{
        return roles.stream().map(Role::role).collect(Collectors.toSet())
    }

    companion object {
        fun build(user: User, roles: List<Role>): UserDetailsImpl {
            return UserDetailsImpl(
                user.id,
                user.login,
                user.pass,
                user.authority,
                roles
            )
        }
    }

}