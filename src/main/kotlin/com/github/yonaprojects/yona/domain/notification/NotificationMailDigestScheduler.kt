package com.github.yonaprojects.yona.domain.notification

import com.github.yonaprojects.yona.domain.board.PostingCommentRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.issue.IssueCommentRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.mail.EmailAddressDetail
import com.github.yonaprojects.yona.domain.mail.MailRecipient
import com.github.yonaprojects.yona.domain.mail.MailService
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommentThreadRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.support.MarkdownService
import com.github.yonaprojects.yona.domain.user.EmailDomainValidator
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.UserState
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Date
import java.util.Locale

/**
 * yona `models/NotificationMail.startSchedule()`/`sendMail()`/`sendNotification()` 대응.
 * Akka 스케줄러 대신 Spring `@Scheduled`를 쓴다(사용자 지시 — 이 부분만 Spring/Kotlin 방식으로
 * 재설계). 나머지 알고리즘(지연 발송, 이벤트 병합, 도메인 제한, 수신자 제한 분할, 언어별 그룹핑,
 * Message-ID/References 스레딩)은 legacy 그대로 옮긴다.
 */
@Component
class NotificationMailDigestScheduler(
    private val notificationMailRepository: NotificationMailRepository,
    private val notificationEventMerger: NotificationEventMerger,
    private val messageResolver: NotificationMessageResolver,
    private val urlResolver: NotificationUrlResolver,
    private val mailRenderer: NotificationMailRenderer,
    private val markdownService: MarkdownService,
    private val mailService: MailService,
    private val userRepository: UserRepository,
    private val issueRepository: IssueRepository,
    private val postingRepository: PostingRepository,
    private val issueCommentRepository: IssueCommentRepository,
    private val postingCommentRepository: PostingCommentRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val commitCommentRepository: CommitCommentRepository,
    private val reviewCommentRepository: ReviewCommentRepository,
    private val commentThreadRepository: CommentThreadRepository,
    private val projectRepository: ProjectRepository,
    private val organizationRepository: OrganizationRepository,
    @Value("\${yona.notification.bymail.enabled:true}") private val enabled: Boolean,
    @Value("\${yona.notification.bymail.hide-address:true}") private val hideAddress: Boolean,
    @Value("\${yona.notification.bymail.recipient-limit:0}") private val recipientLimit: Int,
    @Value("\${yona.notification.bymail.delay-ms:180000}") private val delayMs: Long,
    @Value("\${yona.notification.bymail.allowed-domains:}") private val allowedDomains: String,
    @Value("\${yona.mailbox.imap.address:}") private val imapAddress: String,
    @Value("\${yona.hostname:localhost}") private val hostname: String,
    @Value("\${yona.site-name:Yona}") private val siteName: String,
    private val meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(NotificationMailDigestScheduler::class.java)

    init {
        // "대기 큐 적체" 게이지 — 아직 발송되지 않은 NotificationMail 마커 수를 스크랩 시점마다 그대로 조회한다.
        meterRegistry.gauge(
            "yona.notification.digest.queue_backlog",
            notificationMailRepository
        ) { it.count().toDouble() }
    }

    @Scheduled(
        initialDelayString = "\${yona.notification.bymail.initial-delay-ms:5000}",
        fixedDelayString = "\${yona.notification.bymail.interval-ms:60000}"
    )
    fun sendDueNotificationMails() {
        if (!enabled) {
            return
        }
        try {
            sendMail()
        } catch (e: Exception) {
            logger.warn("Error occurred while sending notification mails", e)
        }
    }

    @Transactional
    fun sendMail() {
        val sample = Timer.start(meterRegistry)
        try {
            val createdUntil = Instant.now().minusMillis(delayMs)
            val mails = notificationMailRepository.findByNotificationEvent_CreatedBeforeOrderByNotificationEvent_CreatedAsc(createdUntil)

            val events = extractEventsAndDelete(mails)

            val merged = try {
                notificationEventMerger.mergeEvents(events)
            } catch (e: Exception) {
                logger.warn("Failed to group events", e)
                events.map { MergedNotificationEvent(it) }
            }
            // 병합률 — N개의 원본 이벤트가 M(<=N)개의 발송 단위로 줄어든 만큼이 이번 배치에서 병합된 건수다.
            meterRegistry.counter("yona.notification.digest.merged").increment((events.size - merged.size).toDouble())

            for (event in merged) {
                try {
                    if (resourceExists(event)) {
                        sendNotification(event)
                        meterRegistry.counter("yona.notification.digest.sent").increment()
                    }
                } catch (e: Exception) {
                    logger.warn("Error occurred while sending a notification mail", e)
                    meterRegistry.counter("yona.notification.digest.failed").increment()
                }
            }
        } finally {
            sample.stop(meterRegistry.timer("yona.notification.digest.duration"))
        }
    }

    private fun extractEventsAndDelete(mails: List<NotificationMail>): List<NotificationEvent> {
        val events = mutableListOf<NotificationEvent>()
        for (mail in mails) {
            try {
                val event = mail.notificationEvent ?: continue
                notificationMailRepository.delete(mail)
                events.add(event)
            } catch (e: Exception) {
                logger.warn("Error occurred while collecting notification events", e)
            }
        }
        return events
    }

    // yona INotificationEvent.resourceExists() 대응 — NotificationEvent/MergedNotificationEvent
    // 둘 다 받을 수 있도록 인터페이스 타입으로 받는다(병합 이벤트는 main의 resourceType/Id로 위임).
    private fun resourceExists(event: INotificationEvent): Boolean {
        val id = event.resourceId.toLongOrNull() ?: return true
        return when (event.resourceType) {
            ResourceType.ISSUE_POST -> issueRepository.existsById(id)
            ResourceType.BOARD_POST -> postingRepository.existsById(id)
            ResourceType.ISSUE_COMMENT -> issueCommentRepository.existsById(id)
            ResourceType.NONISSUE_COMMENT -> postingCommentRepository.existsById(id)
            ResourceType.PULL_REQUEST -> pullRequestRepository.existsById(id)
            ResourceType.COMMIT_COMMENT -> commitCommentRepository.existsById(id)
            ResourceType.REVIEW_COMMENT -> reviewCommentRepository.existsById(id)
            ResourceType.COMMENT_THREAD -> commentThreadRepository.existsById(id)
            ResourceType.PROJECT -> projectRepository.existsById(id)
            ResourceType.ORGANIZATION -> organizationRepository.existsById(id)
            else -> true
        }
    }

    // yona sendNotification(event) 대응 — 수신자 정리(비활성 사용자/도메인 제한 제외), 언어별 그룹핑,
    // 수신자 제한(recipientLimit) 분할까지 담당한다.
    private fun sendNotification(event: MergedNotificationEvent) {
        val receivers = event.receivers.filterTo(mutableSetOf()) { it.state == UserState.ACTIVE }

        if (allowedDomains.isNotBlank()) {
            receivers.removeIf { !EmailDomainValidator.isAllowed(it.email, allowedDomains) }
        }

        if (receivers.isEmpty()) {
            return
        }

        val partialRecipientSize = getPartialRecipientSize(receivers.size)
        if (partialRecipientSize <= 0) {
            return
        }

        val usersByLang = receivers.groupBy { it.getPreferredLanguage() }

        for ((langCode, users) in usersByLang) {
            for (chunk in users.chunked(partialRecipientSize)) {
                val toList = getToList(chunk)
                val bccList = getBccList(chunk)
                sendMail(event, toList, bccList, Locale.forLanguageTag(langCode))
            }
        }
    }

    private fun getPartialRecipientSize(receiverCount: Int): Int {
        if (recipientLimit <= 0) {
            return receiverCount
        }
        return if (hideAddress) recipientLimit - 1 else recipientLimit
    }

    private fun getToList(users: List<User>): List<MailRecipient> {
        return if (hideAddress) listOf(MailRecipient("no-reply@yona.io", siteName)) else users.map { MailRecipient(it.email, it.name) }
    }

    private fun getBccList(users: List<User>): List<MailRecipient> {
        return if (hideAddress) users.map { MailRecipient(it.email, it.name) } else emptyList()
    }

    // yona sendMail(event, toList, bccList, langCode) 대응.
    private fun sendMail(event: MergedNotificationEvent, toList: List<MailRecipient>, bccList: List<MailRecipient>, locale: Locale) {
        if (toList.isEmpty()) {
            return
        }

        val main = event.main
        val sender = main.senderId?.let { userRepository.findById(it).orElse(null) }

        val message = messageResolver.getMessage(event, locale)
        val plainMessage = messageResolver.getPlainMessage(event, locale)
        val urlToView = urlResolver.getUrlToView(main)

        // 댓글(ISSUE_COMMENT)에 대한 unwatch 링크는 댓글이 아니라 이슈 자체를 대상으로 해야 한다
        // (legacy sendMail()의 ISSUE_COMMENT -> issue 리소스 치환과 동일).
        val (unwatchResourceType, unwatchResourceId) = unwatchTarget(main)

        val acceptsReply = getReplyTo(main)
        val htmlMessage = if (main.eventType == EventType.ISSUE_BODY_CHANGED || main.eventType == EventType.POSTING_BODY_CHANGED) {
            message
        } else {
            // yona Markdown.render(source, project, lang) 대응 — 이 스케줄러는 HTTP 요청 스레드가
            // 아니라 LocaleContextHolder로 수신자의 언어를 알 수 없다. 이미 계산해둔 수신자 배치의 locale을
            // 명시적으로 넘겨 @멘션 표시 이름이 발신자가 아니라 수신자의 언어로 렌더링되게 한다.
            markdownService.render(message, true, projectOf(main), locale.language)
        }

        val htmlBody = mailRenderer.render(htmlMessage, urlToView, unwatchResourceType, unwatchResourceId, acceptsReply != null, locale)
        val plainBody = mailRenderer.renderPlain(plainMessage)

        val messageId = if (main.eventType.isCreating()) computeMessageId(main.resourceType, main.resourceId) else null
        val references = computeReferences(main)

        mailService.sendNotificationMail(
            toList = toList,
            bccList = bccList,
            fromName = sender?.name ?: siteName,
            subject = main.title,
            htmlBody = htmlBody,
            plainBody = plainBody,
            replyTo = acceptsReply,
            messageId = messageId,
            references = references,
            sentDate = Date.from(main.created ?: Instant.now())
        )
    }

    private fun unwatchTarget(event: NotificationEvent): Pair<ResourceType, String> {
        if (event.resourceType == ResourceType.ISSUE_COMMENT) {
            val comment = event.resourceId.toLongOrNull()?.let { issueCommentRepository.findById(it).orElse(null) }
            if (comment != null) {
                return ResourceType.ISSUE_POST to comment.issue.id.toString()
            }
        }
        return event.resourceType to event.resourceId
    }

    private fun projectOf(event: NotificationEvent): Project? {
        return when (event.resourceType) {
            ResourceType.ISSUE_POST -> event.resourceId.toLongOrNull()?.let { issueRepository.findById(it).orElse(null)?.project }
            ResourceType.BOARD_POST -> event.resourceId.toLongOrNull()?.let { postingRepository.findById(it).orElse(null)?.project }
            ResourceType.ISSUE_COMMENT -> event.resourceId.toLongOrNull()?.let { issueCommentRepository.findById(it).orElse(null)?.issue?.project }
            ResourceType.NONISSUE_COMMENT -> event.resourceId.toLongOrNull()?.let { postingCommentRepository.findById(it).orElse(null)?.posting?.project }
            ResourceType.PULL_REQUEST -> event.resourceId.toLongOrNull()?.let { pullRequestRepository.findById(it).orElse(null)?.toProject }
            ResourceType.PROJECT -> event.resourceId.toLongOrNull()?.let { projectRepository.findById(it).orElse(null) }
            else -> null
        }
    }

    // yona NotificationMail.getReplyTo() 대응 — comment 계열은 컨테이너(이슈/게시글)의 상세 주소로,
    // post 계열은 자기 자신의 상세 주소로 회신을 라우팅한다. IncomingMailProcessingService의
    // "owner/project/<resourceType>/<resourceId>" detail 파싱과 짝을 이룬다.
    private fun getReplyTo(event: NotificationEvent): String? {
        if (imapAddress.isBlank()) return null

        val (containerType, containerId, project) = when (event.resourceType) {
            ResourceType.ISSUE_COMMENT -> {
                val comment = event.resourceId.toLongOrNull()?.let { issueCommentRepository.findById(it).orElse(null) } ?: return null
                Triple(ResourceType.ISSUE_POST, comment.issue.id.toString(), comment.issue.project)
            }
            ResourceType.NONISSUE_COMMENT -> {
                val comment = event.resourceId.toLongOrNull()?.let { postingCommentRepository.findById(it).orElse(null) } ?: return null
                Triple(ResourceType.BOARD_POST, comment.posting.id.toString(), comment.posting.project)
            }
            ResourceType.REVIEW_COMMENT -> {
                val comment = event.resourceId.toLongOrNull()?.let { reviewCommentRepository.findById(it).orElse(null) } ?: return null
                val thread = comment.thread ?: return null
                Triple(ResourceType.COMMENT_THREAD, thread.id.toString(), thread.project)
            }
            ResourceType.COMMIT_COMMENT -> {
                val comment = event.resourceId.toLongOrNull()?.let { commitCommentRepository.findById(it).orElse(null) } ?: return null
                Triple(ResourceType.COMMIT_COMMENT, comment.id.toString(), comment.project)
            }
            ResourceType.ISSUE_POST -> {
                val issue = event.resourceId.toLongOrNull()?.let { issueRepository.findById(it).orElse(null) } ?: return null
                Triple(ResourceType.ISSUE_POST, issue.id.toString(), issue.project)
            }
            ResourceType.BOARD_POST -> {
                val posting = event.resourceId.toLongOrNull()?.let { postingRepository.findById(it).orElse(null) } ?: return null
                Triple(ResourceType.BOARD_POST, posting.id.toString(), posting.project)
            }
            else -> return null
        }

        val owner = project?.owner ?: return null
        val detail = "$owner/${project.name}/${containerType.resource()}/$containerId"

        val base = runCatching { EmailAddressDetail.of(imapAddress) }.getOrNull() ?: return null
        return EmailAddressDetail(base.user, detail, base.domain).toString()
    }

    // yona Resource.getMessageId() 대응 — "<{resourceType}/{resourceId}@{hostname}>" 결정론적 포맷.
    private fun computeMessageId(resourceType: ResourceType, resourceId: String): String {
        return "<${resourceType.resource()}/$resourceId@$hostname>"
    }

    // yona EventEmail.addReferences() 대응. resource.getContainer()가 COMMENT_THREAD면 그 스레드의
    // "첫 리뷰 댓글" Message-ID를 참조하고(legacy 특수 케이스), 그 외 컨테이너가 있으면 컨테이너 자체의
    // Message-ID를 참조한다(default 케이스). REVIEW_COMMENT의 컨테이너는 COMMENT_THREAD, COMMIT_COMMENT의
    // 컨테이너는 COMMIT이다(NEW_REVIEW_COMMENT/NEW_COMMENT/REVIEW_THREAD_STATE_CHANGED 생산이
    // 실제로 배선된 뒤 이 References 매핑도 함께 갱신해야 한다). COMMENT_THREAD 자신(REVIEW_THREAD_STATE_CHANGED)은
    // legacy에서도 컨테이너가 없어(CommentThread.asResource()가 getContainer()를 오버라이드하지 않음)
    // References를 채우지 않는다.
    private fun computeReferences(event: NotificationEvent): String? {
        return when (event.resourceType) {
            ResourceType.ISSUE_COMMENT -> {
                val comment = event.resourceId.toLongOrNull()?.let { issueCommentRepository.findById(it).orElse(null) } ?: return null
                computeMessageId(ResourceType.ISSUE_POST, comment.issue.id.toString())
            }
            ResourceType.NONISSUE_COMMENT -> {
                val comment = event.resourceId.toLongOrNull()?.let { postingCommentRepository.findById(it).orElse(null) } ?: return null
                computeMessageId(ResourceType.BOARD_POST, comment.posting.id.toString())
            }
            ResourceType.REVIEW_COMMENT -> {
                val comment = event.resourceId.toLongOrNull()?.let { reviewCommentRepository.findById(it).orElse(null) } ?: return null
                val threadId = comment.thread?.id ?: return null
                val firstComment = reviewCommentRepository.findByThreadIdOrderByCreatedDateAsc(threadId).firstOrNull() ?: return null
                computeMessageId(ResourceType.REVIEW_COMMENT, firstComment.id.toString())
            }
            ResourceType.COMMIT_COMMENT -> {
                val comment = event.resourceId.toLongOrNull()?.let { commitCommentRepository.findById(it).orElse(null) } ?: return null
                val projectId = comment.project?.id ?: return null
                computeMessageId(ResourceType.COMMIT, "$projectId:${comment.commitId}")
            }
            else -> null
        }
    }
}
