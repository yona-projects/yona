package dev.yona.domain.site

import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.notification.MailDispatcher
import dev.yona.domain.notification.NotificationMailSender
import dev.yona.domain.project.ProjectService
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.UserRepository
import jakarta.mail.Session
import jakarta.mail.internet.AddressException
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import org.springframework.core.env.Environment
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Properties

/**
 * 사이트 관리자가 쓰는 메일이다. v1.6 `SiteApp.writeMail`, `sendMail`, `mailList`(`app/controllers/SiteApp.java:87-123, 277-313`)에 해당한다.
 *
 * 메일은 일반 텍스트 한 통이며 문자 집합은 UTF-8이다. 보내는 일은 [MailDispatcher.sendNow]에 맡긴다. 메일 서버가 없거나 보내지 못하면
 * 경고만 남기고 호출한 쪽은 성공한 것처럼 진행한다(v1.6도 실패를 알리는 경로가 없다, R-54).
 */
@Service
class SiteMailService(
    private val dispatcher: MailDispatcher,
    private val notificationMailSender: NotificationMailSender,
    private val environment: Environment,
    private val users: UserRepository,
    private val projects: ProjectService,
    private val projectUsers: ProjectUserRepository
) {

    /**
     * 설정되지 않은 메일 서버 항목의 이름이다 (v1.6은 `smtp.host`, `smtp.user`, `smtp.password`를 확인한다. v2는 `spring.mail.*`이다, A-90).
     */
    fun notConfiguredItems(): List<String> = listOf("spring.mail.host", "spring.mail.username", "spring.mail.password").filter { environment.getProperty(it) == null }

    /** 메일 쓰기 화면에 미리 채울 보내는 주소이다. 설정하지 않았으면 `null`이다 (v1.6 `Config.getEmailFromSmtp`). */
    fun defaultSender(): String? = notificationMailSender.fromAddress()

    /**
     * 메일을 보낸다.
     *
     * @throws IllegalArgumentException 보내는 주소나 받는 주소가 올바르지 않거나 본문이 비었을 때(v1.6은 commons-email의 `EmailException`으로 500이다)
     */
    fun send(from: String, to: String, subject: String, body: String) {
        require(body.isNotEmpty()) { "본문이 비었습니다." }
        val message = MimeMessage(Session.getInstance(Properties()))
        val helper = MimeMessageHelper(message, false, "UTF-8")
        helper.setFrom(address(from))
        helper.setTo(address(to))
        helper.setSubject(subject)
        helper.setText(body, false)
        dispatcher.sendNow("사이트 관리자 메일", message)
    }

    private fun address(text: String): InternetAddress = try {
        InternetAddress.parse(text, true).single()
    } catch (e: AddressException) {
        throw IllegalArgumentException("메일 주소가 올바르지 않습니다: $text", e)
    }

    /** 모든 사용자의 이메일이다. 삭제되었거나 잠긴 사용자도 담는다. */
    @Transactional(readOnly = true)
    fun allEmails(): Set<String> = users.findAll().mapNotNull { it.email }.toSet()

    /**
     * `소유자/이름` 형식의 프로젝트들의 구성원 이메일이다. 사이트 관리자 역할 행은 뺀다.
     *
     * @throws IllegalArgumentException 형식이 다르거나 프로젝트가 없을 때(v1.6은 배열 범위 오류와 `NullPointerException`으로 500이다)
     */
    @Transactional(readOnly = true)
    fun projectEmails(names: Collection<String>): Set<String> {
        val emails = LinkedHashSet<String>()
        for (name in names) {
            val parts = name.split("/")
            require(parts.size >= 2) { "프로젝트 이름의 형식이 올바르지 않습니다: $name" }
            val project = requireNotNull(projects.findByOwnerAndProjectName(parts[0], parts[1])) { "프로젝트가 없습니다: $name" }
            projectUsers.findMembers(requireNotNull(project.id), RoleType.SITEMANAGER.id).mapNotNullTo(emails) { it.user?.email }
        }
        return emails
    }
}
