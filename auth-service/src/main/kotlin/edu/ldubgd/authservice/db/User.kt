package edu.ldubgd.authservice.db

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.MappedCollection
import org.springframework.data.relational.core.mapping.Table

@Table("user_login")
data class User(
    @Id
    val id: Int,
    val login: String,
    val pass: String,
    val authority: String? = null,
    val loginDesc: String? = null,
    val contactId: Int? = null,
    @MappedCollection(idColumn = "user_id", keyColumn = "role_id")
    val roles: MutableSet<UserRoleRef> = HashSet()
){
    fun addRole(role: Role){
        roles.add(createRoleRef(role))
    }

    private fun createRoleRef(role: Role): UserRoleRef {
        return UserRoleRef(role.id)
    }
}
