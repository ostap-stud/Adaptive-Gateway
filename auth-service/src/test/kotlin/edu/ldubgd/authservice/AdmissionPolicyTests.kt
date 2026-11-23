package edu.ldubgd.authservice

import edu.ldubgd.authservice.adaptive.*
import edu.ldubgd.authservice.policy.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@SpringBootTest(properties=["eureka.client.enabled=false"])
@Transactional
class AdmissionPolicyTests {
    @Autowired private lateinit var sync: AdaptiveSyncService
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var admin: PolicyAdministration
    @Autowired private lateinit var policies: AccessPolicyRepository
    @Autowired private lateinit var resolver: PolicyResolver
    private val name="admission-policy-test"
    private fun desired(version: Long=0) = DesiredServiceConfiguration(UUID.randomUUID(),version,"a".repeat(64),
        "/$name/v3/api-docs",DesiredRoute("/$name/**"),listOf(DesiredOperation("/$name/read","/$name/read","GET",
            permissions=setOf("admission.read","admission.export"))))
    private fun operation() = jdbc.queryForObject("SELECT o.id FROM users.api_operation o JOIN users.service s ON s.id=o.service_id WHERE s.service_name=?",Long::class.java,name)!!
    private fun effective() = resolver.resolve(policies.findByOperation(operation()))

    @Test fun `first valid snapshot creates an automatic service atomically and repeated request is idempotent`() {
        val input=desired()
        val event=sync.persist(name,input)
        assertEquals("AUTOMATIC",sync.configuration(name)["service"].let { (it as Map<*,*>)["management_mode"] })
        assertEquals(event,sync.persist(name,input))
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM users.service WHERE service_name=?",Int::class.java,name))
    }
    @Test fun `permission mappings are live require every permission and remain centrally owned`() {
        sync.persist(name,desired())
        assertEquals(AccessType.BLOCKED,effective().accessType)
        jdbc.update("INSERT INTO users.role(application,role_name) VALUES ('ADMISSION','READER'),('ADMISSION','EXPORTER')")
        admin.mapping("admission.read",PermissionMappingRequest(setOf("ROLE_ADMISSION_READER"),"Test mapping"),"operator")
        assertEquals(AccessType.BLOCKED,effective().accessType)
        admin.mapping("admission.export",PermissionMappingRequest(setOf("ROLE_ADMISSION_EXPORTER"),"Second requirement"),"operator")
        assertEquals(listOf(setOf("ROLE_ADMISSION_EXPORTER"),setOf("ROLE_ADMISSION_READER")),effective().roleGroups)
        val service=sync.configuration(name)["service"] as Map<*,*>
        sync.persist(name,desired((service["configuration_version"] as Number).toLong()))
        assertEquals(AccessType.ROLES,effective().accessType)
        admin.mapping("admission.read",PermissionMappingRequest(emptySet(),"Revoke mapping"),"operator")
        assertEquals(AccessType.BLOCKED,effective().accessType)
    }
    @Test fun `normal admin policy cannot weaken unresolved permission but audited force can and survives sync`() {
        sync.persist(name,desired())
        val id=operation()
        admin.change(id,PolicySource.ADMIN,PolicyChangeRequest(AccessType.PUBLIC,reason="Normal override"),"operator")
        assertEquals(AccessType.BLOCKED,effective().accessType)
        admin.change(id,PolicySource.ADMIN,PolicyChangeRequest(AccessType.PUBLIC,forceOverride=true,reason="Explicit exception"),"operator")
        assertEquals(AccessType.PUBLIC,effective().accessType)
        val service=sync.configuration(name)["service"] as Map<*,*>
        sync.persist(name,desired((service["configuration_version"] as Number).toLong()))
        assertEquals(AccessType.PUBLIC,effective().accessType)
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM users.policy_audit WHERE target=?",Int::class.java,"$id/ADMIN"))
        admin.remove(id,PolicySource.ADMIN,PolicyRemovalRequest("Restore declaration"),"operator")
        assertEquals(AccessType.BLOCKED,effective().accessType)
    }
    @Test fun `invalid initial snapshot does not register service and central force is rejected`() {
        assertThrows(ResponseStatusException::class.java) { sync.persist(name,desired().copy(specHash="invalid")) }
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM users.service WHERE service_name=?",Int::class.java,name))
        sync.persist(name,desired())
        assertThrows(ResponseStatusException::class.java) {
            admin.change(operation(),PolicySource.CENTRAL,PolicyChangeRequest(AccessType.PUBLIC,forceOverride=true,reason="Wrong source"),"operator")
        }
    }
}
