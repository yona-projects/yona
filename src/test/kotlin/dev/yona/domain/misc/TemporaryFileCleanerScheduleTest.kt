package dev.yona.domain.misc

import dev.yona.domain.enumeration.ResourceType
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.Date
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 임시 첨부 정리 작업의 **실제 실행 경로**(`scheduled()`)가 오래된 임시 첨부를 지우는지 검증한다. `cleanUp()`을 시험 트랜잭션 안에서 프록시를 거쳐 부르는 기존 시험
 * ([dev.yona.web.AttachmentUploadTest])은, `scheduled()`가 같은 객체의 `@Transactional cleanUp()`을 직접 부를 때 트랜잭션이 적용되지 않는 문제를 가린다.
 * OAuth 인가 정리 작업이 이 문제로 지우기에 실패했다(`OAuthAuthorizationPurgerScheduleTest`). 이 시험은 **트랜잭션 없이** 운영과 같은 경로로 부른다.
 */
class TemporaryFileCleanerScheduleTest : AbstractIntegrationTest() {

    @Autowired private lateinit var cleaner: TemporaryFileCleaner
    @Autowired private lateinit var attachments: AttachmentRepository

    private fun attachment(hash: String, ageDays: Long) = attachments.save(
        Attachment(name = hash, hash = hash, containerType = ResourceType.USER, containerId = 987_654_321L, createdDate = Date(System.currentTimeMillis() - ageDays * 86_400_000L))
    )

    @Test
    fun `정해진 시각의 실행은 오래된 임시 첨부를 실제로 지우고 최근 것은 남긴다`() {
        val old = attachment("sched-old-${System.nanoTime()}", ageDays = 40)
        val recent = attachment("sched-recent-${System.nanoTime()}", ageDays = 0)
        try {
            cleaner.scheduled()
            assertFalse(attachments.existsById(requireNotNull(old.id)), "정리 작업이 보관 기간이 지난 임시 첨부를 지워야 한다")
            assertTrue(attachments.existsById(requireNotNull(recent.id)), "최근 첨부는 남긴다")
        } finally {
            listOf(old, recent).forEach { it.id?.let { id -> if (attachments.existsById(id)) attachments.deleteById(id) } }
        }
    }
}
