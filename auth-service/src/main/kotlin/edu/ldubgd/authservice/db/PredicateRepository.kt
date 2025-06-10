package edu.ldubgd.authservice.db

import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface PredicateRepository : CrudRepository<Predicate, Int> {
    /*@Query(
        """
        SELECT * FROM users.predicate AS p 
        WHERE p.name = :name AND p.args = to_jsonb(:args::json)
        """
    )
    fun findPredicateByNameAndArgs(@Param("name") name: String, @Param("args") args: String): Predicate?*/
    fun findPredicatesByName(name: String): List<Predicate>
    fun findPredicatesByNameIn(name: Collection<String>): List<Predicate>

    @Query(
        """
        INSERT INTO users.predicate (name, args)
        VALUES (:name, to_jsonb(:args::json))
        RETURNING *
        """,
    )
    fun saveWithArgs(@Param("name") name: String,@Param("args") args: String): Predicate
}