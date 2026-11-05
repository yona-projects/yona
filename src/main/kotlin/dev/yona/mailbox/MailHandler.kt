package dev.yona.mailbox

import dev.yona.access.AccessControl
import dev.yona.access.ResourceLookup
import dev.yona.access.asResource
import dev.yona.config.YonaProperties
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.notification.MailDispatcher
import dev.yona.domain.notification.NotificationMailSender
import dev.yona.domain.notification.OriginalEmail
import dev.yona.domain.notification.OriginalEmailRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserLookup
import jakarta.mail.Address
import jakarta.mail.MessagingException
import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import org.slf4j.LoggerFactory
import org.springframework.context.MessageSource
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.util.Date
import java.util.Locale
import java.util.Properties

/**
 * 받은 메일 한 통을 처리한다. v1.6 `mailbox.EmailHandler.handleMessage`(`app/mailbox/EmailHandler.java:140-420`)에 해당한다.
 *
 * 규칙은 v1.6 `docs/technical/mailbox.md`와 같다.
 * - 자동 응답 메일(`Auto-Submitted: auto-replied`, `X-Naver-Absent: yes`)과 이미 처리한 메일(같은 메시지 ID)은 건너뛴다.
 * - 보낸 사람은 `From` 주소로 사용자를 찾아 정한다. 사용자가 아닌 주소는 건너뛴다.
 * - 대상 프로젝트는 받는 사람 주소의 세부 부분(`yona+소유자/프로젝트@도메인`)으로 정하며 여러 곳일 수 있다. 읽을 수 없거나 없는 프로젝트는 오류이다.
 * - 이 메일이 알림 메일에 대한 답장이면(`In-Reply-To`, `References`의 메시지 ID, 또는 주소의 세부 부분 `소유자/프로젝트/종류/번호`) 그 리소스에
 *   댓글로 달고, 아니면 이슈로 만든다.
 * - 처리하지 못한 이유는 도움말과 함께 보낸 사람에게 회신한다.
 *
 * `From` 헤더를 그대로 믿고 인증하므로 IMAP 서버가 위조된 보낸 사람을 막아야 한다(v1.6 문서의 보안 주의와 같다).
 */
@Service
class MailHandler(
    private val creator: MailResourceCreator,
    private val userLookup: UserLookup,
    private val projectService: ProjectService,
    private val accessControl: AccessControl,
    private val resources: ResourceLookup,
    private val originalEmails: OriginalEmailRepository,
    private val dispatcher: MailDispatcher,
    private val notificationMailSender: NotificationMailSender,
    private val properties: YonaProperties,
    private val messages: MessageSource,
    private val mailboxAddress: MailboxAddress,
    private val transactions: TransactionTemplate
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val mailIn = LoggerFactory.getLogger("mail.in")

    /** 댓글 대상 후보이다. 프로젝트는 처음 쓸 때 구하며 구하지 못하면(전역 리소스 등) 그때 예외이다. */
    private class ReplyTarget(val type: ResourceType, val id: String, val projectId: Result<Long?>)

    /**
     * 메일 한 통을 처리한다.
     *
     * @param onProcessed 처리한 메일마다 부른다. 호출한 쪽이 마지막으로 본 UID를 갱신하는 데 쓴다. 자동 응답, 중복, 알 수 없는 보낸 사람처럼
     *   건너뛴 메일에도 한 번 부른다. v1.6은 건너뛴 메일의 UID를 갱신하지 않아 다음 시작 때마다 다시 가져왔다(R-61-7)
     */
    fun handle(message: MimeMessage, onProcessed: () -> Unit = {}) {
        var notified = false
        process(message) { notified = true; onProcessed() }
        if (!notified) onProcessed()
    }

    /** [handle]의 본문이다. 보낸 사람별로 처리를 마칠 때마다 [onProcessed]를 부른다. */
    private fun process(message: MimeMessage, onProcessed: () -> Unit) {
        try {
            if (isAutoReplied(message)) return
        } catch (e: MessagingException) {
            log.warn("Failed to determine whether the email is auto-replied or not: {}", message, e)
        }
        try {
            originalEmails.findByMessageId(message.messageID.orEmpty()).firstOrNull()?.let { same ->
                // 처리한 지 한 시간이 지났으면 새 메일일 수 있어 경고한다.
                if (same.handledDate?.before(Date(System.currentTimeMillis() - 3_600_000L)) == true) {
                    log.warn("This email '{}' is ignored because an email with the same id '{}' was already handled at '{}'", message, same.messageId, same.handledDate)
                }
                return
            }
        } catch (e: MessagingException) {
            log.warn("Failed to determine whether the email is duplicated or not: {}", message, e)
        }

        val senders = try {
            message.from?.map { it as InternetAddress }
        } catch (e: Exception) {
            log.error("Failed to get senders from an email", e)
            return
        }
        if (senders.isNullOrEmpty()) {
            log.warn("This email has no sender: {}", message)
            return
        }

        val startTime = System.currentTimeMillis()
        for (sender in senders) {
            val author = userLookup.findByEmail(sender.address) ?: continue
            // 받는 서버가 인증하지 못한 보낸 사람은 처리하지 않는다. 회신하면 위조된 주소로 되돌려 보내게 되므로 조용히 건너뛴다(R-61-10)
            if (properties.mailboxRequireSenderAuthentication && !SenderAuthentication.verified(message, sender.address)) {
                log.warn("This email '{}' is ignored because its sender {} is not authenticated (Authentication-Results)", message, sender.address)
                continue
            }
            val errors = mutableListOf<String>()
            var failure: Exception? = null
            try {
                createResources(message, author, errors)
            } catch (e: MailHandlerException) {
                failure = e
                errors += e.message.orEmpty()
            } catch (e: Exception) {
                failure = e
                log.warn("Failed to process an email: {}", describe(message), e)
                errors += "Unexpected error occurs"
            }
            if (errors.isNotEmpty()) reply(message, sender.personal, sender.address, helpMessage(locale(author), sender.personal?.takeIf { it.isNotBlank() } ?: sender.address, errors))
            logRequest(message, startTime, failure)
            onProcessed()
        }
    }

    private fun isAutoReplied(message: MimeMessage): Boolean = headerContains(message, "Auto-Submitted", "auto-replied") || headerContains(message, "X-Naver-Absent", "yes")

    /** 헤더 값에서 `;` 앞 부분이 [expected]와 같으면(대소문자 무시) `true`이다. */
    private fun headerContains(message: MimeMessage, name: String, expected: String): Boolean =
        message.getHeader(name)?.any { it.substringBefore(';').trim().equals(expected, ignoreCase = true) } ?: false

    private fun createResources(message: MimeMessage, sender: User, errors: MutableList<String>) {
        val threads = findThreads(message)
        // 한 메일이 여러 리소스를 만들 수 있다.
        for (project in findProjects(message, sender, errors)) {
            var commented = false
            for (thread in threads) {
                if (thread.projectId.getOrThrow() != project.id) continue
                when (thread.type) {
                    ResourceType.COMMENT_THREAD -> {
                        val comment = transactions.execute { creator.saveReviewComment(message, resources.get(thread.type, thread.id)) }
                        remember(message, ResourceType.REVIEW_COMMENT, requireNotNull(comment?.id))
                    }
                    ResourceType.ISSUE_POST, ResourceType.BOARD_POST -> {
                        val comment = requireNotNull(transactions.execute { creator.saveComment(message, resources.get(thread.type, thread.id)) })
                        remember(message, comment.type, requireNotNull(comment.id))
                    }
                    else -> Unit
                }
                commented = true
            }
            // 이어 달 대상이 없으면 새 이슈로 만든다.
            if (!commented) {
                val issue = transactions.execute { creator.saveIssue(message, requireNotNull(projectService.findByOwnerAndProjectName(project.owner.orEmpty(), project.name.orEmpty()))) }
                remember(message, ResourceType.ISSUE_POST, requireNotNull(issue?.id))
            }
        }
    }

    /**
     * 만든 리소스를 원본 메일의 메시지 ID와 함께 기록한다. 만드는 일이 끝난 뒤에 따로 기록하므로, 한 메일이 프로젝트 여러 곳에 이슈를 만들면
     * 메시지 ID의 유일 제약 때문에 두 번째부터는 이슈만 남고 이 기록이 실패한다(v1.6과 같다, R-61).
     */
    private fun remember(message: MimeMessage, type: ResourceType, id: Long) {
        originalEmails.save(OriginalEmail(messageId = message.messageID, resourceType = type, resourceId = id.toString(), handledDate = Date()))
    }

    private fun yonaAddress(): String = mailboxAddress.address().orEmpty()

    private fun toYona(addresses: Array<Address>?): List<EmailAddressWithDetail> =
        addresses.orEmpty().map { EmailAddressWithDetail((it as InternetAddress).address) }.filter { it.isToYona(yonaAddress()) }

    private fun findProjects(message: MimeMessage, sender: User, errors: MutableList<String>): Set<Project> {
        val projects = LinkedHashSet<Project>()
        for (address in toYona(message.allRecipients)) {
            val detail = address.detail
            if (detail.isEmpty()) continue
            val lang = locale(sender)
            if (detail.equals("help", ignoreCase = true)) {
                reply(message, sender.name, requireNotNull(sender.email), helpMessage(lang, sender.name, null))
                continue
            }
            val segments = detail.split("/")
            if (segments.size < 2) {
                errors += messages.getMessage("viaEmail.error.email", arrayOf<Any>(address.toString()), lang)
                continue
            }
            val project = transactions.execute { projectService.findByOwnerAndProjectName(segments[0], segments[1])?.takeIf { accessControl.isAllowed(sender, it.asResource(), Operation.READ) } }
            if (project == null) {
                errors += messages.getMessage("viaEmail.error.forbidden.or.notfound", arrayOf<Any>(address.toString()), lang)
                continue
            }
            projects += project
        }
        return projects
    }

    /** 이 메일이 답하는 리소스를 찾는다. `In-Reply-To`와 `References`의 메시지 ID, 받는 주소의 세부 부분이 단서이다. */
    private fun findThreads(message: MimeMessage): List<ReplyTarget> {
        val messageIds = LinkedHashSet<String>()
        message.getHeader("In-Reply-To", null)?.let { messageIds += MessageIds.parse(it) }
        message.getHeader("References")?.forEach { messageIds += MessageIds.parse(it) }

        val found = LinkedHashMap<Pair<ResourceType, String>, ReplyTarget>()
        fun add(type: ResourceType, id: String) {
            found.putIfAbsent(type to id, ReplyTarget(type, id, runCatching { transactions.execute { resources.get(type, id).project?.id } }))
        }
        for (messageId in messageIds) {
            for ((type, id) in resourcesByMessageId(messageId)) {
                when (type) {
                    ResourceType.COMMENT_THREAD, ResourceType.ISSUE_POST, ResourceType.BOARD_POST -> add(type, id)
                    // 리뷰 댓글에 대한 답장은 그 댓글이 속한 스레드에 단다.
                    ResourceType.REVIEW_COMMENT -> transactions.execute { resources.get(type, id).container }?.let { add(it.type, it.id) }
                    else -> log.info("Cannot comment a resource of unknown type: {}/{}", type, id)
                }
            }
        }
        for (address in toYona(message.allRecipients)) resourceFromDetail(address.detail)?.let { add(it.first, it.second) }
        return found.values.toList()
    }

    /** 메시지 ID로 리소스를 찾는다. 원본 메일로 기록된 것이 있으면 그것이고, 없으면 메시지 ID의 왼쪽 부분을 경로로 본다. */
    private fun resourcesByMessageId(messageId: String): Set<Pair<ResourceType, String>> {
        val recorded = originalEmails.findByMessageId(messageId)
        if (recorded.isNotEmpty()) return recorded.mapNotNull { e -> e.resourceType?.let { it to e.resourceId.orEmpty() } }.toSet()
        return try {
            pathResource(MessageIds.idLeft(messageId))?.let { setOf(it) } ?: emptySet()
        } catch (e: Exception) {
            log.info("Error while finding a resource by message-id '{}'", messageId, e)
            emptySet()
        }
    }

    /** 세부 부분 `소유자/프로젝트/종류/번호`의 리소스이다. 경로가 모자라면 `null`이다. */
    private fun resourceFromDetail(detail: String): Pair<ResourceType, String>? {
        val segments = detail.split("/", limit = 3)
        if (segments.size < 3) return null
        return pathResource(commitPathWithProject(segments[0], segments[1], segments[2]))
    }

    /**
     * `commit/해시` 경로에 프로젝트 번호를 붙여 `commit/프로젝트번호:해시`로 만든다. 커밋 리소스의 번호는 `프로젝트번호:해시`인데
     * 해시만 있으면 번호를 읽다가 `NumberFormatException`이 났다(v1.6, R-124-4). 해시만 있는 주소는 주소의 프로젝트에 속한 커밋으로 본다.
     * 프로젝트를 찾지 못하면 경로를 그대로 두어, 없는 리소스를 가리킨 다른 주소처럼 오류 회신이 나간다.
     */
    private fun commitPathWithProject(owner: String, projectName: String, path: String): String {
        val segments = path.split("/")
        if (segments.size != 2 || segments[0] != ResourceType.COMMIT.resource || segments[1].contains(':')) return path
        val project = transactions.execute { projectService.findByOwnerAndProjectName(owner, projectName) } ?: return path
        return "${segments[0]}/${project.id}:${segments[1]}"
    }

    /** `종류/번호` 경로의 리소스이다 (v1.6 `Resource.findByPath`). 없는 번호이면 v1.6처럼 오류이다. */
    private fun pathResource(path: String): Pair<ResourceType, String>? {
        val segments = path.split("/")
        if (segments.size < 2) return null
        val type = ResourceType.entries.firstOrNull { it.resource == segments[0] } ?: return null
        transactions.execute { resources.get(type, segments[1]) }
        return type to segments[1]
    }

    private fun locale(user: User): Locale = Locale.forLanguageTag(user.lang ?: Locale.getDefault().language)

    /** 도움말이다. [errors]가 있으면 앞에 이유를 붙인다. */
    private fun helpMessage(locale: Locale, username: String?, errors: List<String>?): String {
        val site = properties.siteName
        val address = EmailAddressWithDetail(yonaAddress()).also { it.detail = "dlab/hive/issue" }
        fun text(key: String, vararg args: Any?) = messages.getMessage(key, args.map { it ?: "null" }.toTypedArray(), locale)
        return buildString {
            append(text("viaEmail.help.hello", username))
            if (!errors.isNullOrEmpty()) {
                append("\n\n")
                if (errors.size > 1) append(text("viaEmail.help.errorMultiLine", site, "\n* " + errors.joinToString("\n* ")))
                else append(text("viaEmail.help.errorSingleLine", site, errors[0]))
            }
            append("\n\n").append(text("viaEmail.help.intro", site))
            append("\n\n").append(text("viaEmail.help.description", "dlab/hive", address.toString()))
            append("\n\n").append(text("viaEmail.help.bye", site))
        }
    }

    /** 원본 메일에 답하는 메일을 보낸다. 보내지 못해도 경고만 남긴다. */
    private fun reply(origin: MimeMessage, username: String?, emailAddress: String, body: String) {
        try {
            val subject = requireNotNull(origin.subject) { "원본 메일에 제목이 없다" }
            val helper = MimeMessageHelper(MimeMessage(Session.getInstance(Properties())), false, "UTF-8")
            helper.setFrom(requireNotNull(notificationMailSender.fromAddress()), properties.siteName)
            helper.setTo(InternetAddress(emailAddress, username, "UTF-8"))
            helper.setSubject(if (subject.lowercase().startsWith("re:")) subject else "Re: $subject")
            helper.setText(body, false)
            helper.setSentDate(Date())
            helper.mimeMessage.setHeader("In-Reply-To", origin.messageID)
            helper.mimeMessage.setHeader("References", origin.messageID)
            dispatcher.sendNow("메일 수신 오류 회신", helper.mimeMessage)
        } catch (e: Exception) {
            log.warn("Failed to send an email: {}", emailAddress, e)
        }
    }

    private fun describe(message: MimeMessage): String = try {
        "{Subject: ${message.subject}, From: ${message.from?.contentToString()}, To: ${message.allRecipients?.contentToString()}}"
    } catch (e: MessagingException) {
        message.toString()
    }

    private fun logRequest(message: MimeMessage, startTime: Long, failure: Exception?) {
        try {
            val entry = "${describe(message)} ${if (failure == null) "SUCCESS" else "FAILED"} ${System.currentTimeMillis() - startTime}ms"
            if (failure != null && failure !is MailHandlerException) mailIn.error(entry, failure) else mailIn.info(entry)
        } catch (e: MessagingException) {
            log.warn("Failed to log mail request", e)
        }
    }
}
