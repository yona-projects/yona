package dev.yona.domain.notification

import dev.yona.config.YonaProperties
import dev.yona.domain.project.OrganizationRepository
import dev.yona.domain.project.ProjectTransfer
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.user.Email
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.domain.user.UserVerification
import dev.yona.markdown.MarkdownRenderer
import dev.yona.markdown.SiteHostname
import dev.yona.util.SiteUrls
import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import org.springframework.web.util.HtmlUtils
import java.util.Properties

/**
 * 알림 대기열을 거치지 않고 요청 즉시 보내는 시스템 메일을 만든다.
 *
 * - 비밀번호 재설정 (v1.6 `PasswordResetApp.sendPasswordResetMail`)
 * - 가입 인증과 계정 안내 (v1.6 `UserApp.sendMailAfterUserCreation`)
 * - 보조 이메일 확인 (v1.6 `actors.ValidationEmailSender`)
 * - 프로젝트 이전 요청 (v1.6 `ProjectApp.sendTransferRequestMail`)
 *
 * 문구는 요청한 사용자의 언어로 만든다. 만들고 보내는 방식은 v1.6을 따라 메일마다 다르다(D-19): 비밀번호 재설정과 프로젝트 이전 요청은
 * 요청 안에서 동기, 가입 인증은 요청 안에서 만들고 발송만 비동기, 보조 이메일 확인은 만들기와 보내기 모두 비동기이다.
 */
@Service
class SystemMails(
    private val properties: YonaProperties,
    private val messages: MessageSource,
    private val siteUrls: SiteUrls,
    private val hostname: SiteHostname,
    private val dispatcher: MailDispatcher,
    private val markdown: MarkdownRenderer,
    private val organizations: OrganizationRepository,
    private val users: UserRepository,
    private val notificationMailSender: NotificationMailSender
) {


    private fun text(key: String, vararg args: Any?): String =
        messages.getMessage(key, arrayOf<Any?>(*args).map { it ?: "null" }.toTypedArray<Any>(), LocaleContextHolder.getLocale())

    private fun newMessage(): MimeMessageHelper = MimeMessageHelper(MimeMessage(Session.getInstance(Properties())), true, "UTF-8")

    private fun from(): String = requireNotNull(notificationMailSender.fromAddress()) { "보내는 메일 주소(yona.smtp.user)가 설정되지 않았다" }

    /** 비밀번호 재설정 주소를 일반 텍스트로 보낸다. 제목은 `[사이트이름] 제목`이다. */
    fun passwordReset(user: User, token: String): Boolean = syncInRequest("비밀번호 재설정 메일") {
        val helper = newMessage()
        helper.setFrom(from())
        helper.setSubject("[${properties.siteName}] " + text("site.resetPasswordEmail.title"))
        helper.addTo(requireNotNull(user.email))
        helper.setText(text("site.resetPasswordEmail.mailContents") + "\n\n" + siteUrls.absolute(siteUrls.path("/resetPassword?s=$token")), false)
        helper.mimeMessage
    }

    /**
     * 가입한 사용자에게 인증 링크와 계정 안내를 보낸다. 허용하지 않는 이메일 도메인이면 보내지 않는다.
     * 보내는 사람은 `yona.easymail`이고, 제목은 `가입 확인: 사이트 주소`이다.
     */
    fun signup(user: User, verification: UserVerification) = asyncSendInRequest("가입 인증 메일") {
        if (!MailRecipients.isAllowedDomain(user.email, properties.allowedSendingMailDomains)) return@asyncSendInRequest null
        val index = siteUrls.signupMailOrigin()
        val resetUrl = index + siteUrls.path("/lostPassword")
        val verifyUrl = index + siteUrls.path("/verify/${user.loginId}/${verification.verificationCode}")
        val html = StringBuilder().apply {
            append("<h1>").append(text("user.verification")).append("</h1>\n<hr />\n")
            append("<p><a href='").append(verifyUrl).append("'>").append(text("user.verification.link.click")).append("</a></p>\n<br />\n<br />\n")
            append("URL: <a href='").append(index).append("'>").append(index).append("</a><br/>\n")
            append("ID: ").append(user.loginId).append("<br/>\n").append("Email: ").append(user.email).append("<br/>\n<br/>\n")
            append("Password reset: <a href='").append(resetUrl).append("' target='_blank'>").append(resetUrl).append("</a><br/>\n")
        }
        val plain = StringBuilder().apply {
            append(text("user.verification")).append("\n--------------------------\n").append(verifyUrl).append("\n\n\n")
            append("URL: ").append(index).append("\n").append("ID: ").append(user.loginId).append("\n").append("Email: ").append(user.email).append("\n\n")
            append("Password reset: ").append(resetUrl).append("\n")
        }
        val helper = newMessage()
        val fromEmail = properties.easymail.fromEmail.ifEmpty { from() }
        helper.setFrom(InternetAddress(fromEmail, properties.easymail.fromName, "UTF-8"))
        helper.setSubject(text("user.verification.signup.confirm") + ": " + index)
        helper.addTo(InternetAddress(requireNotNull(user.email), user.name, "UTF-8"))
        helper.setText(plain.toString(), html.toString())
        helper.mimeMessage
    }

    /** 보조 이메일을 확인하는 링크를 HTML로 보낸다. */
    fun emailValidation(email: Email, confirmPath: String) = guarded("이메일 확인 메일") {
        val url = siteUrls.absolute(siteUrls.path(confirmPath))
        val helper = newMessage()
        helper.setFrom(InternetAddress(from(), properties.siteName, "UTF-8"))
        helper.addTo(InternetAddress(requireNotNull(email.email), email.user?.name, "UTF-8"))
        helper.setSubject(text("emails.validation.email.title", properties.siteName))
        helper.setText("<pre>" + text("emails.click.link") + "</pre><hr><a href=\"$url\">$url</a>", true)
        helper.mimeMessage
    }

    /**
     * 프로젝트 이전 요청을 받는 쪽에 알린다. 받는 사람이 사용자이면 그 사용자에게, 조직이면 조직 관리자 모두에게 숨은참조로 보낸다.
     * 수락 링크는 한 번만 쓸 수 있는 키가 담긴 주소이다.
     */
    fun projectTransfer(transfer: ProjectTransfer) {
        val bccUsers = LinkedHashSet<User>()
        transfer.destination?.let { users.findByLoginId(it) }?.let { bccUsers.add(it) }
        transfer.destination?.let { organizations.findByNameIgnoreCase(it) }?.let { org ->
            bccUsers.addAll(users.findUsersByOrganizationRole(requireNotNull(org.id), RoleType.ORG_ADMIN.id))
        }
        syncInRequest("프로젝트 이전 요청 메일") { transferMessage(transfer, bccUsers) }
    }

    private fun transferMessage(transfer: ProjectTransfer, bccUsers: Collection<User>): MimeMessage {
        val project = requireNotNull(transfer.project)
        val sender = requireNotNull(transfer.sender)
        val acceptUrl = siteUrls.absolute(siteUrls.path("/project/transfer/${transfer.id}/${transfer.confirmKey}"))
        val message = text("transfer.message.hello", transfer.destination) + "\n\n" +
            text("transfer.message.detail", project.name, transfer.newProjectName, project.owner, transfer.destination) + "\n" +
            text("transfer.message.link") + "\n\n" + acceptUrl + "\n\n" + text("transfer.message.deadline") + "\n\n" + text("transfer.message.thank")
        val helper = newMessage()
        helper.setFrom(InternetAddress(from(), sender.name, "UTF-8"))
        helper.addTo(InternetAddress(from(), "Yobi", "UTF-8"))
        bccUsers.forEach { helper.addBcc(InternetAddress(requireNotNull(it.email), it.name, "UTF-8")) }
        helper.setSubject("[${project.name}] @${sender.loginId} wants to transfer project")
        helper.setText(message, markdown.render(message))
        transfer.requested?.let { helper.setSentDate(it) }
        helper.mimeMessage.addHeader("References", "<$acceptUrl@${hostname.name()}>")
        return helper.mimeMessage
    }

    /** 요청 안에서 만들고 요청 스레드에서 바로 보낸다(v1.6 비밀번호 재설정, 프로젝트 이전 요청). [build]가 `null`이면 보내지 않는다. 보냈으면 `true`이다. */
    private fun syncInRequest(description: String, build: () -> MimeMessage?): Boolean {
        try {
            return build()?.let { dispatcher.sendNow(description, it) } ?: false
        } catch (e: Exception) {
            org.slf4j.LoggerFactory.getLogger(javaClass).warn("{}을 만들지 못했다", description, e)
            return false
        }
    }

    /** 요청 안에서 만들고 발송만 전용 스레드에 맡긴다(v1.6 가입 인증). [build]가 `null`이면 보내지 않는다. */
    private fun asyncSendInRequest(description: String, build: () -> MimeMessage?) {
        try {
            build()?.let { dispatcher.sendAsync(description, it) }
        } catch (e: Exception) {
            org.slf4j.LoggerFactory.getLogger(javaClass).warn("{}을 만들지 못했다", description, e)
        }
    }

    /**
     * 메일 만들기와 보내기를 모두 전용 스레드에 맡긴다(v1.6 보조 이메일 확인, D-19). 요청 스레드의 언어와 사이트 주소를 미리 구해 그 스레드에서 쓰게 한다.
     * 만들 필요가 없으면([build]가 `null`) 보내지 않는다. 요청을 실패시키지 않는다.
     */
    private fun guarded(description: String, build: () -> MimeMessage?) {
        val locale = LocaleContextHolder.getLocale()
        val origin = siteUrls.origin()
        dispatcher.dispatch(description) {
            LocaleContextHolder.setLocale(locale)
            try { siteUrls.withOrigin(origin, build) } finally { LocaleContextHolder.resetLocaleContext() }
        }
    }
}
