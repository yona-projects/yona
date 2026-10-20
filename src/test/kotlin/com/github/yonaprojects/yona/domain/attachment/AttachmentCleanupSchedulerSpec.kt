package com.github.yonaprojects.yona.domain.attachment

import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
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

    // v1.6 Attachment.cleanupTemporaryUploadFilesWithSchedule()은 24시간마다 한 번 실행됐다. 비교 방향이 "최근 업로드"를
    // 지우는 레거시 동작이라, 실행 주기가 1시간으로 줄면 작성 중인 임시 업로드가 최대 1시간 안에 삭제된다.
    describe("실행 주기") {
        it("v1.6과 같이 하루에 한 번 실행되어야 한다") {
            val scheduled = AttachmentCleanupScheduler::class.java.getMethod("cleanupTemporaryFiles")
                .getAnnotation(org.springframework.scheduling.annotation.Scheduled::class.java)
            scheduled.cron shouldBe "0 0 0 * * *"
        }
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
