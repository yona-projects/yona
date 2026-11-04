package dev.yona.mailbox

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.notification.MailDispatcher
import dev.yona.domain.notification.OriginalEmailRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import jakarta.mail.internet.MimeMessage
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.util.Date
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 한 메일을 프로젝트 여러 곳으로 보냈을 때의 결과이다. 원본 메일 기록의 메시지 ID에 유일 제약이 있어 두 번째 프로젝트부터는 이슈만 남고
 * 기록이 실패해 오류를 회신한다. v1.6과 같은 결과이다(R-61). 트랜잭션 없이 실제로 커밋하며 확인한다.
 */
@SpringBootTest(properties = ["spring.jpa.hibernate.ddl-auto=create-drop", "yona.notification-mail-enabled=false", "yona.background-async=false", "yona.update.interval=0", "yona.mailbox-require-sender-authentication=false", "yona.secret=test-secret", "yona.smtp.user=yona@mail.test", "yona.imap.user=yona", "yona.imap.domain=mail.test"])
class MailHandlerMultiProjectTest : AbstractIntegrationTest() {

    @Autowired private lateinit var handler: MailHandler
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var originalEmails: OriginalEmailRepository
    @MockitoBean private lateinit var dispatcher: MailDispatcher

    @Test
    fun `두 번째 프로젝트부터는 이슈만 남고 오류를 회신한다`() {
        val tag = UUID.randomUUID().toString().take(8)
        val salt = LegacyPasswordHasher.generateSalt()
        val sender = users.save(User(loginId = "mp$tag", name = "다중", email = "mp$tag@corp.test", state = UserState.ACTIVE, createdDate = Date(), passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        val a = projects.save(Project(owner = sender.loginId, name = "multi-a-$tag", vcs = "GIT", projectScope = ProjectScope.PRIVATE, createdDate = Date()))
        val b = projects.save(Project(owner = sender.loginId, name = "multi-b-$tag", vcs = "GIT", projectScope = ProjectScope.PRIVATE, createdDate = Date()))
        listOf(a, b).forEach { projectUsers.save(ProjectUser(user = sender, project = it, role = roles.findById(RoleType.MEMBER.id).get())) }

        val sent = mutableListOf<MimeMessage>()
        Mockito.doAnswer { sent += it.getArgument<MimeMessage>(1); null }.`when`(dispatcher).sendNow(Mockito.anyString(), Mockito.any(MimeMessage::class.java) ?: MimeMessage(jakarta.mail.Session.getInstance(java.util.Properties())))
        var processed = 0
        handler.handle(MailFixtures.mail("<multi-$tag@m>", sender.email!!, listOf("yona+${a.owner}/${a.name}@mail.test", "yona+${b.owner}/${b.name}@mail.test"), "두 곳")) { processed++ }

        // 이슈는 두 프로젝트 모두 만들어지지만 원본 메일 기록은 하나뿐이다.
        assertEquals(2, issues.findAll().count { it.project?.id == a.id || it.project?.id == b.id })
        assertEquals(1, originalEmails.findByMessageId("<multi-$tag@m>").size)
        assertEquals(1, sent.size)
        assertTrue((sent.single().content as String).contains("Unexpected error occurs"))
        assertEquals(1, processed)
    }
}
