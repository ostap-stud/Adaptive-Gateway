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
                SELECT r.id, r.route, s.service_name AS serviceName, 
                COALESCE(jsonb_agg(jsonb_build_object('name', f.name, 'args', f.args)) FILTER ( WHERE f.id IS NOT NULL ), '[]') AS filters, 
                COALESCE(jsonb_agg(jsonb_build_object('name', p.name, 'args', p.args)) FILTER ( WHERE p.id IS NOT NULL ), '[]') AS predicates 
                FROM users.route AS r
                JOIN users.service AS s ON r.service_id = s.id 
                LEFT JOIN users.route_filter AS rt ON rt.route_id = r.id 
                LEFT JOIN users.filter AS f ON rt.filter_id = f.id 
                LEFT JOIN users.route_predicate AS rp ON rp.route_id = r.id 
                LEFT JOIN users.predicate AS p ON rp.predicate_id = p.id 
                WHERE r.is_internal = false
                GROUP BY r.id, r.route, s.service_name
            """
        val query = jdbcTemplate.query(sql) { result, _ ->
            ServiceRouteDTO(
                id = result.getInt("id"),
                route = result.getString("route"),
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