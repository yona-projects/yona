package com.github.yonaprojects.yona.domain.audit

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify

class AuditLogServiceSpec : DescribeSpec({
    val auditLogRepository = mockk<AuditLogRepository>()
    val service = AuditLogService(auditLogRepository)

    describe("record") {
        it("행위자/대상/액션/사유를 그대로 담아 저장해야 한다") {
            val slot = slot<AuditLog>()
            every { auditLogRepository.save(capture(slot)) } answers { firstArg() }

            service.record("admin", "target-user", AuditAction.USER_STATE_CHANGED, "LOCKED로 변경")

            slot.captured.actorLoginId shouldBe "admin"
            slot.captured.targetLoginId shouldBe "target-user"
            slot.captured.action shouldBe AuditAction.USER_STATE_CHANGED
            slot.captured.reason shouldBe "LOCKED로 변경"
        }

        it("사유 없이도 기록할 수 있어야 한다") {
            val slot = slot<AuditLog>()
            every { auditLogRepository.save(capture(slot)) } answers { firstArg() }

            service.record("admin", "target-user", AuditAction.TWO_FACTOR_DISABLED_BY_ADMIN)

            slot.captured.reason shouldBe null
        }
    }

    describe("findAll / findByTarget") {
        it("findAll은 저장소의 최신순 조회 결과를 그대로 반환해야 한다") {
            val logs = listOf(AuditLog(actorLoginId = "a", targetLoginId = "b", action = "X"))
            every { auditLogRepository.findAllByOrderByCreatedAtDesc() } returns logs

            service.findAll() shouldBe logs
        }

        it("findByTarget은 대상 loginId로 최신순 조회해야 한다") {
            val logs = listOf(AuditLog(actorLoginId = "a", targetLoginId = "target", action = "X"))
            every { auditLogRepository.findByTargetLoginIdOrderByCreatedAtDesc("target") } returns logs

            service.findByTarget("target") shouldBe logs
            verify(exactly = 1) { auditLogRepository.findByTargetLoginIdOrderByCreatedAtDesc("target") }
        }
    }
})
