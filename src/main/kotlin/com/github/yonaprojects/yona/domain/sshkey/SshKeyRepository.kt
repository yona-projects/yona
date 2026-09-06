package com.github.yonaprojects.yona.domain.sshkey

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface SshKeyRepository : JpaRepository<SshKey, Long> {
    fun findByUserId(userId: Long): List<SshKey>
    fun findByFingerprint(fingerprint: String): Optional<SshKey>
}
