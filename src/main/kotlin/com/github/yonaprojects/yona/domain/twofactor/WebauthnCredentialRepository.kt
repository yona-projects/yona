package com.github.yonaprojects.yona.domain.twofactor

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface WebauthnCredentialRepository : JpaRepository<WebauthnCredential, Long> {
    fun findByUserId(userId: Long): List<WebauthnCredential>
    fun findByCredentialId(credentialId: String): Optional<WebauthnCredential>
}
