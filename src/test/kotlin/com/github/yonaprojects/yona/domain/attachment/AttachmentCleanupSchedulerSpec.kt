package com.github.yonaprojects.yona.domain.attachment

import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant

class AttachmentCleanupSchedulerSpec : DescribeSpec({
    val attachmentRepository = mockk<AttachmentRepository>()
    val attachmentService = mockk<AttachmentService>()
    val scheduler = AttachmentCleanupScheduler(attachmentRepository, attachmentService, 86400000L)

    beforeTest {
        clearMocks(attachmentRepository, attachmentService, answers = false)
    }

    describe("cleanupTemporaryFiles") {
        it("오래된 임시 첨부파일을 삭제해야 한다") {
            val att1 = Attachment(id = 1L)
            val att2 = Attachment(id = 2L)
            every { attachmentRepository.findByContainerTypeAndCreatedDateGreaterThanEqual(eq(ResourceType.USER), any()) } returns listOf(att1, att2)
            every { attachmentService.delete(any()) } returns Unit

            scheduler.cleanupTemporaryFiles()

            verify(exactly = 1) { attachmentService.delete(att1) }
            verify(exactly = 1) { attachmentService.delete(att2) }
        }

        it("삭제 중 예외가 발생해도 다른 파일 삭제를 계속 진행해야 한다") {
            val att1 = Attachment(id = 1L)
            val att2 = Attachment(id = 2L)
            every { attachmentRepository.findByContainerTypeAndCreatedDateGreaterThanEqual(eq(ResourceType.USER), any()) } returns listOf(att1, att2)
            every { attachmentService.delete(att1) } throws RuntimeException("Delete Failed")
            every { attachmentService.delete(att2) } returns Unit

            scheduler.cleanupTemporaryFiles()

            verify(exactly = 1) { attachmentService.delete(att1) }
            verify(exactly = 1) { attachmentService.delete(att2) }
        }
    }
})
