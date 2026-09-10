package com.github.yonaprojects.yona.domain.audit

import org.springframework.stereotype.Service

// 실제 문자열 값을 여기 한 곳에 모아 호출부/조회부가 오타로 어긋나는 것을 막는다.
object AuditAction {
    const val USER_STATE_CHANGED = "USER_STATE_CHANGED"
    const val TWO_FACTOR_DISABLED_BY_ADMIN = "TWO_FACTOR_DISABLED_BY_ADMIN"
    const val ACCOUNT_LOCK_RELEASED = "ACCOUNT_LOCK_RELEASED"
}

@Service
class AuditLogService(
    private val auditLogRepository: AuditLogRepository
) {
    fun record(actorLoginId: String, targetLoginId: String, action: String, reason: String? = null) {
        auditLogRepository.save(
            AuditLog(actorLoginId = actorLoginId, targetLoginId = targetLoginId, action = action, reason = reason)
        )
    }

    fun findAll(): List<AuditLog> = auditLogRepository.findAllByOrderByCreatedAtDesc()

    fun findByTarget(targetLoginId: String): List<AuditLog> =
        auditLogRepository.findByTargetLoginIdOrderByCreatedAtDesc(targetLoginId)
}
