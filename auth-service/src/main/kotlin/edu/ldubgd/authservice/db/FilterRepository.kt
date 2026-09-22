package edu.ldubgd.authservice.db

import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface FilterRepository : CrudRepository<Filter, Int> {

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