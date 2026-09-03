package com.github.yonaprojects.yona.domain.attachment

import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class AttachmentCleanupScheduler(
    private val attachmentRepository: AttachmentRepository,
    private val attachmentService: AttachmentService,
    @Value("\${yona.attachment.temporary-keep-alive-ms:86400000}")
    private val keepAliveMillis: Long
) {
    private val log = LoggerFactory.getLogger(AttachmentCleanupScheduler::class.java)

    @Scheduled(cron = "0 0 * * * *")
    fun cleanupTemporaryFiles() {
        log.info("Starting cleanup of temporary attachment files...")
        // yona Attachment.java:438-477 cleanupTemporaryUploadFilesWithSchedule() 대응 (P2-26). [GL-models_Attachment-035]
        // 원본은 "오래된" 파일이 아니라 .ge("createdDate", now-keepAlive) — 즉 keepAlive 이내에
        // "최근" 업로드된 파일을 정리 대상으로 삼는다. 스케줄러의 취지(방치된 임시파일 정리)와
        // 반대로 보이는 비교 방향이라 yona 자체의 버그로 의심되지만, 사용자 지시에 따라 레거시
        // 동작을 그대로 포팅한다 — 수정 여부 판단은 백로그 P2-26 TODO로 남겨둔다.
        val threshold = Instant.now().minusMillis(keepAliveMillis)
        val temporaryAttachments = attachmentRepository.findByContainerTypeAndCreatedDateGreaterThanEqual(
            ResourceType.USER,
            threshold
        )
        
        var deletedCount = 0
        for (attachment in temporaryAttachments) {
            try {
                attachmentService.delete(attachment)
                deletedCount++
            } catch (e: Exception) {
                log.error("Failed to delete temporary attachment: ${attachment.id}", e)
            }
        }
        log.info("Temporary attachment cleanup completed. Deleted $deletedCount of ${temporaryAttachments.size} files.")
    }
}
