package edu.ldubgd.authservice.adaptive

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** Opt-in fresh installation; never resets an existing password or service mode. */
@Component
@ConditionalOnProperty(name=["adaptive.bootstrap.enabled"], havingValue="true")
class LocalBootstrap(private val jdbc: JdbcTemplate, private val encoder: PasswordEncoder,
    @Value("\${adaptive.bootstrap.login:admin}") private val login: String,
    @Value("\${adaptive.bootstrap.password}") private val password: String) : ApplicationRunner {
    @Transactional
    override fun run(args: ApplicationArguments) {
        require(login.matches(Regex("[a-z][a-z0-9-]{0,62}")) && password.length >= 12) { "Bootstrap requires a lowercase login and a password of at least 12 characters" }
        jdbc.execute("SELECT pg_advisory_xact_lock(718034202)")
        jdbc.update("INSERT INTO users.user_login(login,pass) VALUES (?,?) ON CONFLICT(login) DO NOTHING", login, encoder.encode(password))
        jdbc.update("""INSERT INTO users.role(application,role_name,role_desc) VALUES ('INFRA','ADMIN','Infrastructure administrator')
            ON CONFLICT(role_name,application) DO NOTHING""")
        val uid = jdbc.queryForObject("SELECT id FROM users.user_login WHERE login=?", Int::class.java, login)!!
        val rid = jdbc.queryForObject("SELECT id FROM users.role WHERE role='ROLE_INFRA_ADMIN'", Int::class.java)!!
        if (jdbc.queryForObject("SELECT count(*) FROM users.user_role WHERE user_id=? AND role_id=?", Int::class.java, uid,rid)==0)
            jdbc.update("INSERT INTO users.user_role(user_id,role_id) VALUES (?,?)",uid,rid)
        jdbc.update("INSERT INTO users.service(service_name) VALUES ('auth-service') ON CONFLICT(service_name) DO NOTHING")
        val sid = jdbc.queryForObject("SELECT id FROM users.service WHERE service_name='auth-service'",Int::class.java)!!
        for ((path,internal) in listOf("/auth-service/**" to false, "/actuator/**" to true)) {
            if (jdbc.queryForObject("SELECT count(*) FROM users.route WHERE route=?",Int::class.java,path)==0) {
                val route = jdbc.queryForObject("INSERT INTO users.route(route,service_id,is_internal) VALUES (?,?,?) RETURNING id",
                    Int::class.java,path,if(internal) null else sid,internal)!!
                jdbc.update("INSERT INTO users.route_role(route_id,role_id) VALUES (?,?)",route,rid)
            }
        }
    }
}
