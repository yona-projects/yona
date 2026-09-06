package com.github.yonaprojects.yona.domain.gpgkey

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface GpgKeyRepository : JpaRepository<GpgKey, Long> {
    fun findByUserId(userId: Long): List<GpgKey>
    fun findByFingerprint(fingerprint: String): Optional<GpgKey>
    fun findByAssociatedKeyIdsContaining(keyId: String): List<GpgKey>
}
