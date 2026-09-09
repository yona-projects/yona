package com.github.yonaprojects.yona.domain.twofactor

import org.springframework.data.jpa.repository.JpaRepository

interface TwoFactorTotpCredentialRepository : JpaRepository<TwoFactorTotpCredential, Long> {
    fun findByUserId(userId: Long): List<TwoFactorTotpCredential>
    fun findByUserIdAndEnabledTrue(userId: Long): List<TwoFactorTotpCredential>
}
