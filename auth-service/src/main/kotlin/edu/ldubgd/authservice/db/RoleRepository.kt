package edu.ldubgd.authservice.db

import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface RoleRepository : CrudRepository<Role, Int> {
    fun findByIdIn(id: MutableCollection<Int>): List<Role>
//    fun findRolesByRoleNameIn(roleName: List<String>): List<Role>
    fun findRolesByRoleIn(role: List<String>): List<Role>
    fun findRoleByRole(role: String): Role?

    @Query("insert into users.role(id, role_name, role_desc, application, role) values (default, :role_name, :role_desc, :application, default) returning *")
    fun save(
        @Param("role_name") name: String,
        @Param("role_desc") description: String?,
        @Param("application") application: String,
    ): Role

    @Query("update users.role set role_name = :role_name, role_desc = :role_desc, application = :application where id = :id returning *")
    fun update(
        @Param("id") id: Int,
        @Param("role_name") name: String,
        @Param("role_desc") description: String?,
        @Param("application") application: String,
    ): Role

}