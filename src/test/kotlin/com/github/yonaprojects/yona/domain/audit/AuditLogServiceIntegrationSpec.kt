package com.github.yonaprojects.yona.domain.audit

import com.github.yonaprojects.yona.AbstractIntegrationTest
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired

// 법적 컴플라이언스 감사 #6 대응 — record()로 남긴 행이 실제 DB에 정확한 필드로
// 저장/조회되는지 실제 Spring 컨텍스트(H2/MariaDB 등)에서 검증한다.
class AuditLogServiceIntegrationSpec @Autowired constructor(
    private val auditLogService: AuditLogService,
    private val auditLogRepository: AuditLogRepository
) : AbstractIntegrationTest() {

    init {
        beforeEach {
            auditLogRepository.deleteAll()
        }

        describe("AuditLogService.record") {
            it("행위자/대상/액션/사유/시각이 정확히 저장되어야 한다") {
                auditLogService.record("admin", "target-user", AuditAction.USER_STATE_CHANGED, "state=LOCKED")

                val saved = auditLogRepository.findAllByOrderByCreatedAtDesc()
                saved.size shouldBe 1
                saved[0].actorLoginId shouldBe "admin"
                saved[0].targetLoginId shouldBe "target-user"
                saved[0].action shouldBe AuditAction.USER_STATE_CHANGED
                saved[0].reason shouldBe "state=LOCKED"
            }
        }

        describe("AuditLogService.findByTarget") {
            it("대상 loginId로 조회 시 최신순으로 반환해야 한다") {
                auditLogService.record("admin", "target-user", AuditAction.TWO_FACTOR_DISABLED_BY_ADMIN)
                Thread.sleep(5) // createdAt(Instant.now()) 동시 저장 시 정렬이 모호해지는 것을 방지
                auditLogService.record("admin", "target-user", AuditAction.ACCOUNT_LOCK_RELEASED)
                auditLogService.record("admin", "other-user", AuditAction.USER_STATE_CHANGED)

                val logs = auditLogService.findByTarget("target-user")

                logs.size shouldBe 2
                logs.map { it.action } shouldBe listOf(AuditAction.ACCOUNT_LOCK_RELEASED, AuditAction.TWO_FACTOR_DISABLED_BY_ADMIN)
            }
        }
    }
}
