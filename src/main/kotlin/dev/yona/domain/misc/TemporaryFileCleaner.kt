package dev.yona.domain.misc

import dev.yona.config.YonaProperties
import dev.yona.domain.enumeration.ResourceType
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.Date

/**
 * 사용자가 올린 임시 첨부를 정리한다. v1.6 `Attachment.cleanupTemporaryUploadFilesWithSchedule`에 해당한다.
 *
 * 서버가 뜬 뒤 보관 기간(`yona.temporary-files-keep-up-time`, 기본 하루)이 지나면 처음 실행하고, 이후 같은 주기로 반복한다.
 *
 * v1.6의 조회 조건 `createdDate >= (오늘 0시 - 보관 기간)`을 그대로 따른다. 오래된 첨부가 아니라 **최근 보관 기간 안에 올린** 임시 첨부를
 * 지운다([refactoring-backlog.md] R-20).
 */
@Component
class TemporaryFileCleaner(
    private val attachments: AttachmentRepository,
    private val attachmentService: AttachmentService,
    private val properties: YonaProperties,
    private val jobs: dev.yona.observability.JobMetrics
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        initialDelayString = "#{@schedulingDelays.temporaryFilesKeepUpMillis}",
        fixedDelayString = "#{@schedulingDelays.temporaryFilesKeepUpMillis}"
    )
    fun scheduled() {
        try {
            log.info("User uploaded temporary files are cleaned up... {}", jobs.run("temporary_files_cleanup") { cleanUp() })
        } catch (e: Exception) {
            log.warn("Failed!! User uploaded temporary files clean-up action failed!", e)
        }
    }

    /** [now]가 속한 날의 0시이다 (v1.6 `JodaDateUtil.today()`는 `LocalDate.now()`, 즉 오늘 0시이다). */
    private fun startOfDay(now: Long): Long =
        java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** 임시 첨부를 지우고 지운 개수를 돌려준다. */
    @Transactional
    fun cleanUp(now: Long = System.currentTimeMillis()): Int {
        val targets = attachments.findByContainerTypeAndCreatedDateGreaterThanEqual(
            ResourceType.USER, Date(startOfDay(now) - properties.temporaryFilesKeepUpTime.toMillis())
        )
        targets.forEach { attachmentService.delete(it) }
        return targets.size
    }
}
