package edu.ldubgd.alertSystem.gatewayserver.configuration

import edu.ldubgd.alertSystem.gatewayserver.data.ServiceRouteRepository
import edu.ldubgd.alertSystem.gatewayserver.data.ServiceRouteRepositoryImpl
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate

@Configuration
class GatewayConfiguration {

    @Bean
    fun serviceRouteRepository(jdbcTemplate: JdbcTemplate): ServiceRouteRepository {
        return ServiceRouteRepositoryImpl(jdbcTemplate)
    }

}