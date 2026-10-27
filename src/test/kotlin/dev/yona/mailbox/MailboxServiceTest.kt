package dev.yona.mailbox

import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetup
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.PropertyName
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.misc.PropertyService
import dev.yona.domain.notification.MailDispatcher
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import jakarta.mail.Folder
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.util.Date
import java.util.Properties
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * IMAP 서버에 연결해 새 메일을 가져와 처리하는 서비스를 인메모리 IMAP 서버(GreenMail)로 검증한다.
 * 원본: `mailbox.MailboxService`(`app/mailbox/MailboxService.java`). 트랜잭션 없이 실제로 커밋하며 확인한다.
 */
@SpringBootTest(properties = ["spring.jpa.hibernate.ddl-auto=create-drop", "yona.notification-mail-enabled=false", "yona.background-async=false", "yona.update.interval=0", "yona.secret=test-secret", "yona.smtp.user=yona@mail.test", "yona.imap.user=yona@mail.test", "yona.imap.password=pw", "yona.imap.host=127.0.0.1"])
class MailboxServiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var boot: MailboxService
    @Autowired private lateinit var handler: MailHandler
    @Autowired private lateinit var diagnostics: dev.yona.domain.site.DiagnosticService
    @Autowired private lateinit var propertyRepository: dev.yona.domain.misc.PropertyRepository
    private lateinit var mailbox: MailboxService
    @Autowired private lateinit var properties: PropertyService
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var issues: IssueRepository
    @MockitoBean private lateinit var dispatcher: MailDispatcher

    companion object {
        private val greenMail = GreenMail(ServerSetup(0, "127.0.0.1", "imap")).also {
            it.setUser("yona@mail.test", "yona@mail.test", "pw")
            it.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun imap(registry: DynamicPropertyRegistry) {
            registry.add("yona.imap.port") { greenMail.imap.port }
        }

        @JvmStatic
        @AfterAll
        fun shutdown() = greenMail.stop()
    }

    private lateinit var sender: User
    private lateinit var project: Project

    @BeforeEach
    fun setUp() {
        // 서버가 준비되면 자동으로 시작한 서비스는 멈추고, 테스트마다 새 서비스를 쓴다.
        boot.stop()
        propertyRepository.deleteAll()
        mailbox = MailboxService(
            dev.yona.config.YonaProperties(
                backgroundAsync = false, mailboxPollingInterval = java.time.Duration.ofHours(1),
                imap = dev.yona.config.YonaProperties.Imap(host = "127.0.0.1", user = "yona@mail.test", password = "pw", port = greenMail.imap.port)
            ),
            properties, handler, users
        )
        greenMail.purgeEmailFromAllMailboxes()
        val tag = UUID.randomUUID().toString().take(8)
        val salt = LegacyPasswordHasher.generateSalt()
        sender = users.save(User(loginId = "mb$tag", name = "수신$tag", email = "mb$tag@corp.test", state = UserState.ACTIVE, createdDate = Date(), passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        project = projects.save(Project(owner = sender.loginId, name = "mailbox-$tag", vcs = "GIT", projectScope = ProjectScope.PRIVATE, createdDate = Date()))
        projectUsers.save(ProjectUser(user = sender, project = project, role = roles.findById(RoleType.MEMBER.id).get()))
    }

    @AfterEach
    fun tearDown() = mailbox.stop()

    private fun deliver(id: String, subject: String) {
        val mail = MailFixtures.mail("<$id@m>", sender.email!!, listOf("yona+${project.owner}/${project.name}@mail.test"), subject, "내용 $subject")
        greenMail.setUser("yona@mail.test", "yona@mail.test", "pw").deliver(mail)
    }

    private fun titles() = issues.findAll().filter { it.project?.id == project.id }.mapNotNull { it.title }.sorted()

    private fun awaitTitles(expected: List<String>) {
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            if (titles() == expected) return
            Thread.sleep(100)
        }
        assertEquals(expected, titles())
    }

    private fun uidOfFirstFolderState(): Pair<Long, Long> {
        val store = Session.getInstance(Properties()).getStore("imap")
        store.connect("127.0.0.1", greenMail.imap.port, "yona@mail.test", "pw")
        try {
            val folder = store.getFolder("inbox") as org.eclipse.angus.mail.imap.IMAPFolder
            folder.open(Folder.READ_ONLY)
            return folder.uidValidity to folder.uidNext
        } finally {
            store.close()
        }
    }

    // --- 시작 ---

    @Test
    fun `호스트를 설정하지 않으면 시작하지 않는다`() {
        val unconfigured = MailboxService(
            dev.yona.config.YonaProperties(), properties, org.mockito.Mockito.mock(MailHandler::class.java), users
        )
        unconfigured.start()
        assertFalse(unconfigured.running)
    }

    @Test
    fun `꺼 두면 시작하지 않는다`() {
        val off = MailboxService(
            dev.yona.config.YonaProperties(imap = dev.yona.config.YonaProperties.Imap(use = false, host = "127.0.0.1", user = "a", password = "b")),
            properties, org.mockito.Mockito.mock(MailHandler::class.java), users
        )
        off.start()
        assertFalse(off.running)
    }

    @Test
    fun `사용자나 비밀번호가 없으면 연결하지 못하고 시작하지 않는다`() {
        val missing = MailboxService(
            dev.yona.config.YonaProperties(imap = dev.yona.config.YonaProperties.Imap(host = "127.0.0.1")),
            properties, org.mockito.Mockito.mock(MailHandler::class.java), users
        )
        missing.start()
        assertFalse(missing.running)
    }

    @Test
    fun `처음 시작하면 UID 유효값만 기록하고 이미 있던 메일은 처리하지 않는다`() {
        deliver("old1", "예전 메일")
        mailbox.start()
        assertTrue(mailbox.running)
        assertEquals(uidOfFirstFolderState().first, properties.getLong(PropertyName.MAILBOX_LAST_UID_VALIDITY))
        assertNull(properties.getLong(PropertyName.MAILBOX_LAST_SEEN_UID))
        assertEquals(emptyList(), titles())
    }

    // --- 새 메일 ---

    @Test
    fun `시작한 뒤 도착한 메일은 바로 처리하고 마지막으로 본 UID를 갱신한다`() {
        mailbox.start()
        deliver("new1", "새 메일")
        awaitTitles(listOf("새 메일"))
        val (_, next) = uidOfFirstFolderState()
        val deadline = System.currentTimeMillis() + 5_000
        while (properties.getLong(PropertyName.MAILBOX_LAST_SEEN_UID) != next - 1 && System.currentTimeMillis() < deadline) Thread.sleep(50)
        assertEquals(next - 1, properties.getLong(PropertyName.MAILBOX_LAST_SEEN_UID))
    }

    @Test
    fun `멈춘 사이 도착한 메일은 다시 시작할 때 UID 순서대로 처리한다`() {
        deliver("seed", "먼저 본 메일")
        val (validity, next) = uidOfFirstFolderState()
        properties.set(PropertyName.MAILBOX_LAST_UID_VALIDITY, validity)
        properties.set(PropertyName.MAILBOX_LAST_SEEN_UID, next - 1)
        deliver("gap1", "사이 첫째")
        deliver("gap2", "사이 둘째")
        mailbox.start()
        awaitTitles(listOf("사이 둘째", "사이 첫째"))
        assertEquals(next + 1, properties.getLong(PropertyName.MAILBOX_LAST_SEEN_UID))
    }

    @Test
    fun `UID 유효값이 달라졌으면 지난 메일을 처리하지 않고 새 값을 기록한다`() {
        deliver("v1", "무시할 메일")
        properties.set(PropertyName.MAILBOX_LAST_UID_VALIDITY, 12345L)
        properties.set(PropertyName.MAILBOX_LAST_SEEN_UID, 0L)
        mailbox.start()
        assertEquals(emptyList(), titles())
        assertEquals(uidOfFirstFolderState().first, properties.getLong(PropertyName.MAILBOX_LAST_UID_VALIDITY))
    }

    @Test
    fun `마지막으로 본 UID는 줄어들지 않는다`() {
        properties.set(PropertyName.MAILBOX_LAST_SEEN_UID, 10L)
        mailbox.updateLastSeenUid(7)
        assertEquals(10L, properties.getLong(PropertyName.MAILBOX_LAST_SEEN_UID))
        mailbox.updateLastSeenUid(12)
        assertEquals(12L, properties.getLong(PropertyName.MAILBOX_LAST_SEEN_UID))
    }

    // --- 폴링 ---

    @Test
    fun `폴링은 새 메일을 가져와 처리하고 폴더를 닫는다`() {
        // 마지막으로 본 UID 기록이 없으면 폴링은 아무것도 처리하지 않는다(v1.6과 같다, R-61). 이미 쓰던 편지함으로 시작한다.
        val (validity, next) = uidOfFirstFolderState()
        properties.set(PropertyName.MAILBOX_LAST_UID_VALIDITY, validity)
        properties.set(PropertyName.MAILBOX_LAST_SEEN_UID, next - 1)
        mailbox.useIdle = false
        mailbox.start()
        deliver("poll1", "폴링 메일")
        mailbox.pollOnce()
        awaitTitles(listOf("폴링 메일"))
        // 폴링으로 받을 때는 수신 대기 스레드가 없어 진단이 초기화되지 않았다고 알린다(v1.6과 같다).
        assertEquals(listOf("The Email Receiver is not initialized"), mailbox.diagnose())
    }

    // --- 진단 ---

    @Test
    fun `기록이 없는 새 설치에서는 폴링이 새 메일을 처리하지 않는다`() {
        mailbox.useIdle = false
        mailbox.start()
        deliver("poll0", "처리되지 않는 메일")
        mailbox.pollOnce()
        assertEquals(emptyList(), titles())
    }

    @Test
    fun `수신 대기 중이면 문제가 없고 멈추면 알린다`() {
        assertEquals(emptyList(), mailbox.diagnose())
        mailbox.start()
        assertEquals(emptyList(), mailbox.diagnose())
        mailbox.stop()
        assertEquals(listOf("The Email Receiver is not running"), mailbox.diagnose())
    }

    @Test
    fun `수신 서버를 설정하지 않았으면 진단 점검이 없다`() {
        val unconfigured = MailboxService(dev.yona.config.YonaProperties(), properties, org.mockito.Mockito.mock(MailHandler::class.java), users)
        assertEquals(emptyList(), unconfigured.diagnose())
    }

    @Test
    fun `진단 화면의 점검에 수신 상태가 들어간다`() {
        // 서버가 준비될 때 자동으로 시작한 서비스를 setUp에서 멈췄다.
        assertTrue(diagnostics.checkAll().contains("The Email Receiver is not running"), diagnostics.checkAll().toString())
    }
}
