package edu.ldubgd.authservice.db

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import edu.ldubgd.authservice.adaptive.ConfigurationWriteLock

/** Update the service row without re-saving its routes aggregate. */
@Repository
@Transactional
class AdaptiveServiceMetadataRepository(private val jdbc: JdbcTemplate, private val locks: ConfigurationWriteLock) {
    fun updateDetails(id: Int, name: String, description: String?): Boolean {
        locks.lock()
        return jdbc.update(
        "UPDATE users.service SET service_name = ?, service_desc = ?, configuration_version = configuration_version + 1 WHERE id = ?",
        name, description, id
        ) == 1
    }

    fun updateManagementMode(id: Int, mode: ManagementMode): Boolean {
        locks.lock()
        return jdbc.update(
        "UPDATE users.service SET management_mode = ?, configuration_version = configuration_version + 1 WHERE id = ?",
        mode.name, id
        ) == 1
    }
}
