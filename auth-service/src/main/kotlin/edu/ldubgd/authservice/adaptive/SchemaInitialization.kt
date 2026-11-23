package edu.ldubgd.authservice.adaptive

import org.springframework.beans.factory.InitializingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import javax.sql.DataSource

@Configuration
class SchemaInitialization {
    @Bean("adaptiveSchema")
    @ConditionalOnProperty(name=["adaptive.initialize-schema"], havingValue="true", matchIfMissing=true)
    fun schema(dataSource: DataSource) = InitializingBean {
        dataSource.connection.use { connection ->
            // Serialize startup on one connection. Scripts have their own atomic transactions.
            connection.createStatement().use { it.execute("SELECT pg_advisory_lock(718034201)") }
            try {
                val present = connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT to_regclass('users.service') IS NOT NULL").use { it.next(); it.getBoolean(1) }
                }
                fun execute(resource: String) {
                    val sql = ClassPathResource("adaptive-db/$resource").inputStream.bufferedReader().use { it.readText() }
                    connection.createStatement().use { it.execute(sql) }
                }
                if (!present) {
                    connection.autoCommit = false
                    try { execute("000_empty_schema.sql"); connection.commit() }
                    catch (e: Exception) { connection.rollback(); throw e }
                    finally { connection.autoCommit = true }
                }
                listOf("001_baseline_compatibility", "002_adaptive_ownership", "003_operation_policy", "004_adaptation_event",
                    "005_permissions_and_policy_audit").forEach { execute("migrations/$it.sql") }
            } finally {
                connection.createStatement().use { it.execute("ROLLBACK"); it.execute("SELECT pg_advisory_unlock(718034201)") }
            }
        }
    }
}
