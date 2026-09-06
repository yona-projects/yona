package com.github.yonaprojects.yona.domain.deploykey

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface DeployKeyRepository : JpaRepository<DeployKey, Long> {
    fun findByProjectId(projectId: Long): List<DeployKey>
    fun findByHttpsTokenHash(httpsTokenHash: String): Optional<DeployKey>
    fun findByFingerprint(fingerprint: String): Optional<DeployKey>
}
