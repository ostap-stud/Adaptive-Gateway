package edu.ldubgd.authservice.db

import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface RoleRepository : CrudRepository<Role, Int> {
    fun findByIdIn(id: MutableCollection<Int>): List<Role>
//    fun findRolesByRoleNameIn(roleName: List<String>): List<Role>
    fun findRolesByRoleIn(role: List<String>): List<Role>
}