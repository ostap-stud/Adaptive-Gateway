package edu.ldubgd.authservice.db

import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface FilterRepository : CrudRepository<Filter, Int> {
    /*@Query(
        """
        SELECT * FROM users.filter AS f 
        WHERE f.name = :name AND f.args = to_jsonb(:args::json)
        """
    )
    fun findFilterByNameAndArgs(@Param("name") name: String,@Param("args") args: String): Filter?*/
    fun findFiltersByName(name: String): List<Filter>
    fun findFiltersByNameIn(name: Collection<String>): List<Filter>

    @Query(
        """
        INSERT INTO users.filter (name, args)
        VALUES (:name, to_jsonb(:args::json))
        RETURNING *
        """,
    )
    fun saveWithArgs(@Param("name") name: String, @Param("args") args: String): Filter
}