package edu.ldubgd.alertSystem.gatewayserver.data

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import edu.ldubgd.alertSystem.gatewayserver.CustomRouteDefinitionRepository
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate

class ServiceRouteRepositoryImpl(
    private val jdbcTemplate: JdbcTemplate
) : ServiceRouteRepository {

    private val mapper = jacksonObjectMapper()
    private val logger = LoggerFactory.getLogger(CustomRouteDefinitionRepository::class.java)

    override fun findAllRoutes(): List<ServiceRouteDTO> {
        val sql =
            """
                SELECT
                    r.id,
                    r.route,
                    r.order,
                    s.service_name AS serviceName,
                    -- Aggregate route's filters to array
                    (
                        SELECT COALESCE(jsonb_agg(jsonb_build_object('name', f.name, 'args', f.args)), '[]')
                        FROM users.route_filter rf
                        JOIN users.filter f ON f.id = rf.filter_id
                        WHERE rf.route_id = r.id
                    ) AS filters,
                    -- Aggregate route's predicates to array
                    (
                        SELECT COALESCE(jsonb_agg(jsonb_build_object('name', p.name, 'args', p.args)), '[]')
                        FROM users.route_predicate rp
                        JOIN users.predicate p ON p.id = rp.predicate_id
                        WHERE rp.route_id = r.id
                    ) AS predicates
                FROM users.route AS r
                JOIN users.service AS s ON r.service_id = s.id
                WHERE r.is_internal = false
            """
        val query = jdbcTemplate.query(sql) { result, _ ->
            ServiceRouteDTO(
                id = result.getInt("id"),
                route = result.getString("route"),
                order = result.getInt("order"),
                serviceName = result.getString("serviceName"),
                filters = mapper.readValue(result.getString("filters")),
                predicates = mapper.readValue(result.getString("predicates"))
            )
        }
        logger.info("$query")
        return query
    }

    override fun findAllInternalRoutes(): List<ServiceRoutePathDTO> {
        val sql =
            """
                SELECT r.route FROM route AS r 
                WHERE r.is_internal = true
            """
        return jdbcTemplate.query(sql) { result, _ ->
            ServiceRoutePathDTO(
                route = result.getString("route")
            )
        }
    }

}