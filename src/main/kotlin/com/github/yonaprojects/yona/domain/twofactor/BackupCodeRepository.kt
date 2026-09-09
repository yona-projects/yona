package com.github.yonaprojects.yona.domain.twofactor

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface BackupCodeRepository : JpaRepository<BackupCode, Long> {
    fun findByUserId(userId: Long): List<BackupCode>
    fun findByUserIdAndUsedAtIsNull(userId: Long): List<BackupCode>

    @Modifying
    @Query("delete from BackupCode b where b.user.id = :userId")
    fun deleteByUserId(@Param("userId") userId: Long)
}
