package com.github.yonaprojects.yona.domain.audit

import org.springframework.data.jpa.repository.JpaRepository

interface AuditLogRepository : JpaRepository<AuditLog, Long> {
    fun findAllByOrderByCreatedAtDesc(): List<AuditLog>
    fun findByTargetLoginIdOrderByCreatedAtDesc(targetLoginId: String): List<AuditLog>
}
