package dev.yona.domain.notification

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationRepository
import dev.yona.domain.project.OrganizationUser
import dev.yona.domain.project.OrganizationUserRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectTransfer
import dev.yona.domain.user.Email
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.domain.user.UserVerification
import dev.yona.markdown.SiteHostname
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import jakarta.mail.Message
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import org.awaitility.Awaitility
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 알림과 별개로 요청 즉시 보내는 시스템 메일(비밀번호 재설정, 가입 인증, 보조 이메일 확인, 프로젝트 이전)을 검증한다.
 *
 * 원본: `PasswordResetApp.sendPasswordResetMail`, `UserApp.sendMailAfterUserCreation`, `ValidationEmailSender`, `ProjectApp.sendTransferRequestMail`.
 */
@Transactional
class SystemMailsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var publisher: ApplicationEventPublisher
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var organizations: OrganizationRepository
    @Autowired private lateinit var organizationUsers: OrganizationUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var messages: MessageSource
    @Autowired private lateinit var hostname: SiteHostname
    @Autowired private lateinit var siteUrls: dev.yona.util.SiteUrls
    @MockitoBean private lateinit var mailSender: JavaMailSender

    private var sequence = 0

    @BeforeEach
    fun clear() { Mockito.clearInvocations(mailSender) }

    private fun msg(key: String, vararg args: Any) = messages.getMessage(key, args, LocaleContextHolder.getLocale())

    private fun user(name: String = "sm${++sequence}"): User {
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = name, name = name, email = "$name@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    /** 비동기로 보내므로 한 통이 나갈 때까지 기다렸다가 보낸 메일을 돌려준다. */
    private fun sentMails(expected: Int = 1): List<MimeMessage> {
        Awaitility.await().atMost(Duration.ofSeconds(5)).until { collected().size >= expected }
        return collected()
    }

    private fun collected(): List<MimeMessage> = Mockito.mockingDetails(mailSender).invocations.filter { it.method.name == "send" }
        .flatMap { inv -> inv.arguments.flatMap { a -> if (a is Array<*>) a.toList() else listOf(a) } }.filterIsInstance<MimeMessage>().onEach { it.saveChanges() }

    private fun texts(message: MimeMessage): List<String> {
        val parts = mutableListOf<String>()
        fun walk(p: jakarta.mail.Part) {
            if (p.isMimeType("multipart/*")) { val m = p.content as MimeMultipart; for (i in 0 until m.count) walk(m.getBodyPart(i)) }
            else if (p.isMimeType("text/*")) parts += p.content as String
        }
        walk(message)
        return parts
    }

    private fun addresses(message: MimeMessage, type: Message.RecipientType) = (message.getRecipients(type) ?: emptyArray()).map { (it as InternetAddress).address }

    @Test
    fun `비밀번호 재설정 메일은 재설정 주소를 일반 텍스트로 보낸다`() {
        val u = user()
        publisher.publishEvent(PasswordResetRequestedEvent(u, "tokenvalue"))
        val mail = sentMails().single()
        assertEquals("[Yona] " + msg("site.resetPasswordEmail.title"), mail.subject)
        assertEquals(listOf(u.email), addresses(mail, Message.RecipientType.TO))
        val body = texts(mail).single()
        assertTrue(body.startsWith(msg("site.resetPasswordEmail.mailContents") + "\n\n"), body)
        assertTrue(body.endsWith("/resetPassword?s=tokenvalue"), body)
        assertTrue(body.contains("://"), body)
    }

    @Test
    fun `비밀번호 재설정 메일은 요청 안에서 동기로 보내 기다리지 않아도 나가 있다`() {
        val u = user()
        publisher.publishEvent(PasswordResetRequestedEvent(u, "sync-token"))
        assertEquals(1, collected().size)
    }

    @Test
    fun `가입 인증 메일은 인증 링크와 계정 안내를 담는다`() {
        val u = user()
        publisher.publishEvent(UserVerificationRequestedEvent(u, UserVerification(user = u, loginId = u.loginId, verificationCode = "code-1", timestamp = 1L)))
        val mail = sentMails().single()
        assertTrue(mail.subject.startsWith(msg("user.verification.signup.confirm") + ": http"), mail.subject)
        assertEquals(listOf(u.email), addresses(mail, Message.RecipientType.TO))
        val html = texts(mail).first { it.contains("<") }
        val text = texts(mail).first { !it.contains("<") }
        assertTrue(html.contains("/verify/${u.loginId}/code-1"), html)
        assertTrue(html.contains(msg("user.verification.link.click")), html)
        assertTrue(text.contains("ID: ${u.loginId}\nEmail: ${u.email}\n\nPassword reset: "), text)
        assertTrue(text.contains(msg("user.verification") + "\n--------------------------\n"), text)
    }

    @Test
    fun `보조 이메일 확인 메일은 확인 주소를 링크로 보낸다`() {
        val u = user()
        val email = Email(user = u, email = "extra@example.com", valid = false, token = "t1")
        publisher.publishEvent(EmailValidationRequestedEvent(email, "/user/email/confirm/5/t1"))
        val mail = sentMails().single()
        assertEquals(msg("emails.validation.email.title", "Yona"), mail.subject)
        assertEquals(listOf("extra@example.com"), addresses(mail, Message.RecipientType.TO))
        val html = texts(mail).single()
        assertTrue(html.startsWith("<pre>" + msg("emails.click.link") + "</pre><hr><a href=\""), html)
        assertTrue(html.contains("/user/email/confirm/5/t1\">http"), html)
    }

    @Test
    fun `프로젝트 이전 요청 메일은 받는 사용자에게 숨은참조로 수락 링크를 보낸다`() {
        val sender = user(); val dest = user()
        val p = projects.save(Project(owner = sender.loginId, name = "mv${++sequence}", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        val transfer = ProjectTransfer(id = 9, sender = sender, destination = dest.loginId, project = p, requested = Date(), confirmKey = "key9", newProjectName = "moved")
        publisher.publishEvent(ProjectTransferRequestedEvent(transfer))
        val mail = sentMails().single()
        assertEquals("[${p.name}] @${sender.loginId} wants to transfer project", mail.subject)
        assertEquals(listOf(dest.email), addresses(mail, Message.RecipientType.BCC))
        assertTrue(texts(mail).first { !it.contains("<") }.contains("/project/transfer/9/key9"))
        assertEquals("<${siteUrls.absolute("/project/transfer/9/key9")}@${hostname.name()}>", mail.getHeader("References").single())
    }

    @Test
    fun `프로젝트 이전 요청이 조직으로 가면 조직 관리자에게 보낸다`() {
        val sender = user(); val admin = user(); val member = user()
        val org = organizations.save(Organization(name = "smorg${++sequence}", descr = "d", created = Date()))
        organizationUsers.save(OrganizationUser(user = admin, organization = org, role = roles.findById(RoleType.ORG_ADMIN.id).get()))
        organizationUsers.save(OrganizationUser(user = member, organization = org, role = roles.findById(RoleType.ORG_MEMBER.id).get()))
        val p = projects.save(Project(owner = sender.loginId, name = "mv${++sequence}", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        publisher.publishEvent(ProjectTransferRequestedEvent(ProjectTransfer(id = 10, sender = sender, destination = org.name, project = p, requested = Date(), confirmKey = "k10", newProjectName = "x")))
        assertEquals(listOf(admin.email), addresses(sentMails().single(), Message.RecipientType.BCC))
    }
}
