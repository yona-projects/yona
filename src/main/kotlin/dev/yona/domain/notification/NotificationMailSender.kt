package dev.yona.domain.notification

import dev.yona.access.GlobalResource
import dev.yona.access.Resource
import dev.yona.access.ResourceLookup
import dev.yona.config.YonaProperties
import dev.yona.access.asResource
import dev.yona.domain.comment.ReviewCommentRepository
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.isCreating
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.markdown.MarkdownRenderer
import dev.yona.markdown.ProjectMarkdownRenderer
import dev.yona.markdown.SiteHostname
import dev.yona.util.SiteUrls
import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.MessageSource
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.util.HtmlUtils
import org.thymeleaf.context.Context
import org.thymeleaf.spring6.SpringTemplateEngine
import java.util.Locale
import java.util.Properties

/**
 * 묶인 알림 한 건을 메일로 보낸다. v1.6 `NotificationMail.sendNotification`과 `sendMail`, `EventEmail`
 * (`app/models/NotificationMail.java:290-540`)에 해당한다.
 *
 * 받는 사람은 활성 사용자 중 허용 도메인에 드는 사용자이다. 선호 언어별로 나누고, 설정한 한도만큼씩 끊어 메일을 따로 보낸다.
 * 주소를 숨기는 설정(기본)이면 받는 사람(To)에 사이트의 보내는 주소를 쓰고 사용자는 숨은참조(Bcc)에 넣는다.
 *
 * 새로 만들어지는 알림(새 이슈, 새 글, 새 댓글)은 리소스로 만든 `Message-ID`를 가지고, 댓글처럼 컨테이너가 있는 알림은
 * 컨테이너의 식별자를 `References`에 달아 메일 프로그램이 한 대화로 묶게 한다. 수신 주소가 있으면 `Reply-To`를 달아 답장이 댓글이 되게 한다([MailboxAddress]).
 */
@Service
class NotificationMailSender(
    private val mailboxAddress: dev.yona.mailbox.MailboxAddress,
    private val mailSenderProvider: ObjectProvider<JavaMailSender>,
    private val properties: YonaProperties,
    private val notificationMessages: NotificationMessages,
    private val templates: SpringTemplateEngine,
    private val projectMarkdown: ProjectMarkdownRenderer,
    private val markdown: MarkdownRenderer,
    private val mailHtml: MailHtml,
    private val lookup: ResourceLookup,
    private val siteUrls: SiteUrls,
    private val hostname: SiteHostname,
    private val messageSource: MessageSource,
    private val users: UserRepository,
    private val reviewComments: ReviewCommentRepository,
    private val metrics: dev.yona.observability.CollaborationMetrics
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val mailOut = LoggerFactory.getLogger("mail.out")

    /** 보낼 메일 한 통의 받는 사람이다. */
    private data class Recipient(val email: String, val name: String?)

    /** 사이트의 보내는 주소이다 (v1.6 `Config.getEmailFromSmtp`). 설정하지 않았으면 `null`이다. */
    fun fromAddress(): String? {
        val user = properties.smtp.user
        if (user.isEmpty()) return null
        return if (user.contains("@")) user else user + "@" + properties.smtp.domain.ifEmpty { hostname.name() }
    }

    /**
     * [notification]을 받을 사용자에게 보낸다. 보낼 사람이 없거나 메일 서버를 설정하지 않았으면 아무것도 하지 않는다.
     * 메일 한 통을 보내다 실패하면 경고를 남기고 다음 메일을 계속 보낸다.
     */
    @Transactional(readOnly = true)
    fun send(notification: MergedNotification) {
        val receivers = MailRecipients.eligible(notification.receivers, properties.allowedSendingMailDomains)
        if (receivers.isEmpty()) return
        val size = MailRecipients.partialSize(receivers.size, properties.notificationMailRecipientLimit, properties.notificationMailHideAddress)
        if (size <= 0) return
        for ((lang, byLang) in MailRecipients.byLanguage(receivers)) {
            for (group in byLang.chunked(size)) {
                val to = if (properties.notificationMailHideAddress) setOf(Recipient(fromAddress().orEmpty(), properties.siteName)) else recipients(group)
                val bcc = if (properties.notificationMailHideAddress) recipients(group) else emptySet()
                sendMail(notification, to, bcc, lang)
            }
        }
    }

    private fun recipients(users: List<User>): Set<Recipient> = users.mapTo(LinkedHashSet()) { Recipient(it.email.orEmpty(), it.name) }

    private fun sendMail(notification: MergedNotification, to: Set<Recipient>, bcc: Set<Recipient>, lang: String) {
        if (to.isEmpty()) return
        val sender = mailSenderProvider.ifAvailable
        if (sender == null) {
            metrics.mail("skipped")
            log.warn("메일 서버(spring.mail.host)가 설정되지 않아 알림 메일을 보내지 않았다: {}", notification.main.title)
            return
        }
        val main = notification.main
        try {
            val message = buildMessage(notification, to, bcc, lang) ?: return
            sender.send(message)
            metrics.mail("success")
            val title = (main.title ?: "").replace("\"", "\\\"")
            // v1.6는 받는 사람, 참조, 숨은 참조의 InternetAddress 집합을 그대로 기록한다("이름 <주소>")
            mailOut.info("\"{}\" {}", title, message.allRecipients.orEmpty().toCollection(LinkedHashSet()))
        } catch (e: Exception) {
            metrics.mail("failure")
            log.warn("알림 메일을 보내지 못했다: {}", main.title, e)
        }
    }

    private fun buildMessage(notification: MergedNotification, to: Set<Recipient>, bcc: Set<Recipient>, lang: String): MimeMessage? {
        val main = notification.main
        val resource = lookup.get(requireNotNull(main.resourceType), requireNotNull(main.resourceId))
        val message = EventMimeMessage(Session.getInstance(Properties()), if (main.eventType!!.isCreating) messageId(resource) else null)
        val htmlText = notification.sources.map { notificationMessages.message(it, lang) }.let { if (it.any { m -> m == null }) return null else it.filterNotNull() }
        val plainText = notification.sources.map { notificationMessages.plainMessage(it, lang) }.let { if (it.any { m -> m == null }) return null else it.filterNotNull() }
        val urlToView = notificationMessages.urlToView(main)

        // 이슈 댓글은 이슈를 기준으로 프로젝트와 구독 해제 링크를 정한다.
        val base = if (resource.type == ResourceType.ISSUE_COMMENT) requireNotNull(resource.container) else resource
        val helper = MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED, "UTF-8")
        helper.setFrom(InternetAddress(requireNotNull(fromAddress()) { "보내는 메일 주소(yona.smtp.user)가 설정되지 않았다" }, senderName(main), "UTF-8"))
        to.forEach { helper.addTo(InternetAddress(it.email, it.name, "UTF-8")) }
        bcc.forEach { helper.addBcc(InternetAddress(it.email, it.name, "UTF-8")) }
        helper.setSubject(main.title.orEmpty())
        // 수신 주소가 있으면 답장이 곧 댓글이 되도록 Reply-To를 단다 (v1.6 `getReplyTo`). 있으면 본문 안내도 "회신하거나 보기"가 된다.
        val replyTo = mailboxAddress.replyTo(resource)
        if (replyTo != null) helper.setReplyTo(InternetAddress(replyTo))
        val acceptsReply = replyTo != null

        val joined = htmlText.joinToString(SEPARATOR)
        val html = if (main.eventType == EventType.ISSUE_BODY_CHANGED || main.eventType == EventType.POSTING_BODY_CHANGED) {
            wrap(lang, joined, urlToView, base, acceptsReply)
        } else {
            wrap(lang, renderMarkdown(joined, base, lang), urlToView, base, acceptsReply)
        }
        helper.setText(plainText.joinToString(SEPARATOR) + PLAIN_FOOTER, html)
        main.created?.let { helper.setSentDate(it) }
        references(resource)?.let { message.addHeader("References", it) }
        return message
    }

    /** 보낸 사용자의 이름이다. 알림을 만든 뒤 사용자가 지워졌으면 `null`이다. */
    private fun senderName(event: NotificationEvent): String? = event.senderId?.let { users.findById(it).orElse(null)?.name }

    private fun renderMarkdown(source: String, resource: Resource, lang: String): String {
        if (resource.type == ResourceType.ORGANIZATION || resource is GlobalResource) return markdown.render(source)
        return projectMarkdown.render(source, requireNotNull(resource.project), lang)
    }

    private fun wrap(lang: String, message: String, urlToView: String?, resource: Resource, acceptsReply: Boolean): String {
        val locale = Locale.forLanguageTag(lang)
        val unwatch = siteUrls.path("/unwatch?resource.type=${resource.type.resource}&resource.id=${resource.id}")
        val context = Context(locale).apply {
            setVariable("message", message)
            setVariable("urlToView", urlToView)
            setVariable("siteName", properties.siteName)
            setVariable("acceptsReply", acceptsReply)
            setVariable("unwatchLink", footerLink(unwatch, messageSource.getMessage("notification.unwatch", null, locale)))
            setVariable("settingsLink", footerLink(siteUrls.path("/user/editform/notifications"), messageSource.getMessage("userinfo.changeNotifications", null, locale)))
        }
        return mailHtml.finish(templates.process("common/notification-mail", context))
    }

    private fun footerLink(link: String, text: String) =
        "<a href=\"${HtmlUtils.htmlEscape(link)}\" target=\"_blank\" style=\"color:#4399e2; text-decoration:underline;\">${HtmlUtils.htmlEscape(text)}</a>"

    /** 리소스로 만든 메시지 식별자이다 (v1.6 `Resource.getMessageId`). */
    private fun messageId(resource: Resource) = "<${resource.type.resource}/${resource.id}@${hostname.name()}>"

    /**
     * 컨테이너가 있으면 그 식별자를 `References`로 쓴다. 컨테이너가 코드 리뷰 스레드이면 스레드의 첫 리뷰 댓글의 식별자를 쓴다
     * (스레드 자체의 식별자를 `Message-ID`로 가진 메일은 없다. v1.6 `NotificationMail`). 댓글이 없는 스레드는 `null`이다.
     */
    private fun references(resource: Resource): String? {
        val container = resource.container ?: return null
        if (container.type != ResourceType.COMMENT_THREAD) return messageId(container)
        val first = reviewComments.findFirstByThreadIdOrderByCreatedDateAscIdAsc(container.id.toLong()) ?: return null
        return messageId(first.asResource())
    }

    /**
     * 새로 만들어지는 알림의 `Message-ID`를 고정하는 메시지이다. 보내기 직전의 저장 과정(`saveChanges`)이 식별자를 새로 만들므로
     * 식별자를 만드는 메서드를 바꾼다 (v1.6 `EventEmail.createMimeMessage`).
     */
    private class EventMimeMessage(session: Session, private val fixedId: String?) : MimeMessage(session) {
        override fun updateMessageID() {
            if (fixedId != null) setHeader("Message-ID", fixedId) else super.updateMessageID()
        }
    }

    private companion object {
        const val SEPARATOR = "\n\n---\n\n"
        const val PLAIN_FOOTER = "\n\n Yona 에서 자세히 보거나 혹은 이 메일에 직접 회신하실 수도 있습니다."
    }
}
