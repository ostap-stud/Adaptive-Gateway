package edu.ldubgd.authservice.db

import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : CrudRepository<User, Int> {
    fun findByLogin(login: String): User?
    fun existsByLogin(login: String): Boolean
    fun existsByContactId(contactId: Int): Boolean
}