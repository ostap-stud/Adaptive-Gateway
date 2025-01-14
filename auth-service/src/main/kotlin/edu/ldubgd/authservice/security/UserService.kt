package edu.ldubgd.authservice.security

import edu.ldubgd.authservice.db.UserRoleRef
import edu.ldubgd.authservice.db.RoleRepository
import edu.ldubgd.authservice.db.UserRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class UserService : UserDetailsService {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var roleRepository: RoleRepository

    override fun loadUserByUsername(username: String?): UserDetails {
        val user = userRepository.findByLogin(username!!)
            ?: throw UsernameNotFoundException(
                String.format("Username %s not found", username)
            )
        val roles = roleRepository.findByIdIn(user.roles.map(UserRoleRef::roleId).toMutableList())
        return UserDetailsImpl.build(user, roles)
    }
}