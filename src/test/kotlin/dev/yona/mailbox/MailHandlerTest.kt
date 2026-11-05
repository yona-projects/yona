package dev.yona.mailbox

import dev.yona.domain.board.Posting
import dev.yona.domain.board.PostingRepository
import dev.yona.domain.comment.CommentThreadRepository
import dev.yona.domain.comment.IssueCommentRepository
import dev.yona.domain.comment.NonRangedCodeCommentThread
import dev.yona.domain.comment.ReviewCommentRepository
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.ThreadState
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.notification.MailDispatcher
import dev.yona.domain.notification.OriginalEmail
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 받은 메일 한 통을 처리하는 흐름을 검증한다. 원본: `mailbox.EmailHandler.handleMessage`(`app/mailbox/EmailHandler.java`).
 */
@SpringBootTest(properties = ["spring.jpa.hibernate.ddl-auto=create-drop", "yona.notification-mail-enabled=false", "yona.background-async=false", "yona.update.interval=0", "yona.mailbox-require-sender-authentication=false", "yona.secret=test-secret", "yona.smtp.user=yona@mail.test", "yona.imap.user=yona", "yona.imap.domain=mail.test"])
@Transactional
class MailHandlerTest : AbstractIntegrationTest() {

    @Autowired private lateinit var handler: MailHandler
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var issueComments: IssueCommentRepository
    @Autowired private lateinit var postings: PostingRepository
    @Autowired private lateinit var threads: CommentThreadRepository
    @Autowired private lateinit var reviewComments: ReviewCommentRepository
    @Autowired private lateinit var originalEmails: OriginalEmailRepository
    @MockitoBean private lateinit var dispatcher: MailDispatcher

    private var sequence = 0
    private var number = 0L

    private fun user(name: String? = null): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "mh$n", name = name ?: "처리$n", email = "mh$n@corp.test", state = UserState.ACTIVE, createdDate = Date(), passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun project(owner: User, scope: ProjectScope = ProjectScope.PRIVATE) =
        projects.save(Project(owner = owner.loginId, name = "hproj${++sequence}", vcs = "GIT", projectScope = scope, createdDate = Date()))

    private fun member(user: User, project: Project) {
        projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(RoleType.MEMBER.id).get()))
    }

    private fun yona(detail: String? = null) = if (detail == null) "yona@mail.test" else "yona+$detail@mail.test"

    private fun toProject(p: Project) = yona("${p.owner}/${p.name}")

    private fun issue(project: Project, author: User) = issues.save(
        Issue(project = project, number = ++number, title = "기존", body = "본문", state = State.OPEN, authorId = author.id, authorLoginId = author.loginId, authorName = author.name, numOfComments = 0, weight = 0, createdDate = Date(), updatedDate = Date())
    )

    /** 처리하고 처리 완료 알림이 몇 번 불렸는지 돌려준다. */
    private fun handle(mail: MimeMessage): Int {
        var processed = 0
        handler.handle(mail) { processed++ }
        return processed
    }

    private val sent = mutableListOf<MimeMessage>()

    @BeforeEach
    fun recordReplies() {
        sent.clear()
        Mockito.doAnswer { sent += it.getArgument<MimeMessage>(1); null }.`when`(dispatcher).sendNow(Mockito.anyString(), Mockito.any(MimeMessage::class.java) ?: MimeMessage(jakarta.mail.Session.getInstance(java.util.Properties())))
    }

    private fun replies(): List<MimeMessage> = sent.toList()

    private fun textOf(message: MimeMessage) = message.content as String

    // --- 무시하는 메일 ---

    @Test
    fun `자동 응답 메일은 처리하지 않는다`() {
        val sender = user(); val p = project(sender); member(sender, p)
        assertEquals(1, handle(MailFixtures.mail("<h-auto1@m>", sender.email!!, listOf(toProject(p)), "부재", headers = mapOf("Auto-Submitted" to "auto-replied; owner-email=\"x\""))))
        assertEquals(1, handle(MailFixtures.mail("<h-auto2@m>", sender.email!!, listOf(toProject(p)), "부재", headers = mapOf("X-Naver-Absent" to "yes"))))
        assertEquals(0, issues.findAll().count { it.project?.id == p.id })
    }

    @Test
    fun `이미 처리한 메시지 ID는 처리하지 않는다`() {
        val sender = user(); val p = project(sender); member(sender, p)
        // 다른 테스트가 커밋한 기록과 (종류, 번호)가 겹치지 않게 고유한 번호를 쓴다.
        originalEmails.save(OriginalEmail(messageId = "<h-dup@m>", resourceType = ResourceType.ISSUE_POST, resourceId = "9${System.nanoTime()}", handledDate = Date()))
        assertEquals(1, handle(MailFixtures.mail("<h-dup@m>", sender.email!!, listOf(toProject(p)), "중복")))
        assertEquals(0, issues.findAll().count { it.project?.id == p.id })
    }

    /** 건너뛴 메일도 처리 완료로 알려야 마지막으로 본 UID가 앞서 가서 다음 시작 때 다시 가져오지 않는다 (R-61-7). */
    @Test
    fun `사용자가 아닌 사람의 메일은 건너뛴다`() {
        val owner = user(); val p = project(owner, ProjectScope.PUBLIC)
        assertEquals(1, handle(MailFixtures.mail("<h-unknown@m>", "stranger@elsewhere.test", listOf(toProject(p)), "모르는 사람")))
        assertEquals(0, issues.findAll().count { it.project?.id == p.id })
        assertTrue(replies().isEmpty())
    }

    @Test
    fun `Yona로 온 주소가 아니면 만들지 않는다`() {
        val sender = user(); val p = project(sender); member(sender, p)
        assertEquals(1, handle(MailFixtures.mail("<h-other@m>", sender.email!!, listOf("someone+${p.owner}/${p.name}@other.test"), "다른 곳")))
        assertEquals(0, issues.findAll().count { it.project?.id == p.id })
    }

    @Test
    fun `세부 부분이 없는 주소는 아무것도 하지 않지만 처리한 것으로 센다`() {
        val sender = user()
        assertEquals(1, handle(MailFixtures.mail("<h-nodetail@m>", sender.email!!, listOf(yona()), "세부 없음")))
        assertTrue(replies().isEmpty())
    }

    // --- 이슈 만들기 ---

    @Test
    fun `프로젝트 주소로 보내면 이슈를 만든다`() {
        val sender = user(); val p = project(sender); member(sender, p)
        assertEquals(1, handle(MailFixtures.mail("<h-new@m>", sender.email!!, listOf(toProject(p)), "새 이슈", "내용")))
        val created = issues.findAll().single { it.project?.id == p.id }
        assertEquals("새 이슈", created.title)
        assertEquals("내용", created.body)
        assertTrue(replies().isEmpty())
    }

    @Test
    fun `만든 이슈는 원본 메일의 메시지 ID와 함께 기록한다`() {
        val sender = user(); val p = project(sender); member(sender, p)
        handle(MailFixtures.mail("<h-orig@m>", sender.email!!, listOf(toProject(p)), "기록"))
        val created = issues.findAll().single { it.project?.id == p.id }
        val original = originalEmails.findByMessageId("<h-orig@m>").single()
        assertEquals(ResourceType.ISSUE_POST, original.resourceType)
        assertEquals(created.id.toString(), original.resourceId)
        assertTrue(original.handledDate != null)
    }

    // --- 오류 회신 ---

    @Test
    fun `없는 프로젝트나 읽을 수 없는 프로젝트는 오류를 회신한다`() {
        val owner = user(); val outsider = user(name = "외부인"); val p = project(owner)
        handle(MailFixtures.mail("<h-err1@m>", outsider.email!!, listOf(toProject(p)), "비공개", fromName = "외부 사람"))
        val reply = replies().single()
        assertEquals("Re: 비공개", reply.subject)
        assertEquals(outsider.email, (reply.allRecipients.single() as jakarta.mail.internet.InternetAddress).address)
        assertEquals("<h-err1@m>", reply.getHeader("In-Reply-To")!!.single())
        assertEquals("<h-err1@m>", reply.getHeader("References")!!.single())
        val body = textOf(reply)
        assertTrue(body.startsWith("Hello, 외부 사람"), body)
        assertTrue(body.contains("The project which matches with the address <${toProject(p)}> does not exist or access is not allowed."), body)
        assertTrue(body.contains("Best Regards,"), body)
    }

    @Test
    fun `이미 Re로 시작하는 제목은 그대로 쓴다`() {
        val sender = user()
        handle(MailFixtures.mail("<h-re@m>", sender.email!!, listOf(yona("nope/nothing")), "RE: 이미"))
        assertEquals("RE: 이미", replies().single().subject)
    }

    @Test
    fun `오류가 둘 이상이면 목록으로 알린다`() {
        val sender = user()
        handle(MailFixtures.mail("<h-err2@m>", sender.email!!, listOf(yona("nope/one"), yona("nope/two"), yona("justone")), "여러 오류"))
        val body = textOf(replies().single())
        // 보낸 사람 헤더에 표시 이름이 없으면 v1.6은 인사말이 `Hello, null`이었다(R-61-4). 보낸 주소로 인사한다
        assertTrue(body.startsWith("Hello, ${sender.email}"), body)
        assertFalse(body.startsWith("Hello, null"), body)
        assertTrue(body.contains("failed to process your email. The reasons are as follows:\n\n* "), body)
        assertEquals(3, Regex("\n\\* ").findAll(body).count(), body)
        assertTrue(body.contains("We could not understand the address <${yona("justone").replace("yona+", "yona+")}> in the recipient list."), body)
    }

    @Test
    fun `help 주소로 보내면 도움말을 회신한다`() {
        val sender = user(name = "도움")
        assertEquals(1, handle(MailFixtures.mail("<h-help@m>", sender.email!!, listOf(yona("HELP")), "도움말")))
        val body = textOf(replies().single())
        assertTrue(body.startsWith("Hello, 도움"), body)
        assertFalse(body.contains("failed to process"), body)
        assertTrue(body.contains("yona+dlab/hive/issue@mail.test"), body)
    }

    @Test
    fun `댓글 권한이 없으면 권한 오류를 회신한다`() {
        val owner = user(); val reader = user(); val p = project(owner, ProjectScope.PUBLIC)
        member(owner, p)
        val target = issue(p, owner)
        handle(MailFixtures.mail("<h-denied@m>", reader.email!!, listOf(yona("${p.owner}/${p.name}/issue_post/${target.id}")), "Re: 기존", "댓글"))
        // 공개 프로젝트의 로그인 사용자는 댓글을 달 수 있다. 비공개일 때만 막힌다.
        assertEquals(1, issueComments.findAll().count { it.issue?.id == target.id })
    }

    @Test
    fun `알 수 없는 리소스를 가리키면 예상하지 못한 오류를 회신한다`() {
        val sender = user(); val p = project(sender); member(sender, p)
        handle(MailFixtures.mail("<h-boom@m>", sender.email!!, listOf(yona("${p.owner}/${p.name}/issue_post/999999")), "없는 이슈"))
        assertTrue(textOf(replies().single()).contains("Unexpected error occurs"))
    }

    // --- 댓글 달기 ---

    @Test
    fun `원본 메일에 대한 답장은 그 이슈에 댓글로 단다`() {
        val sender = user(); val p = project(sender); member(sender, p)
        val target = issue(p, sender)
        originalEmails.save(OriginalEmail(messageId = "<orig-issue@m>", resourceType = ResourceType.ISSUE_POST, resourceId = target.id.toString(), handledDate = Date()))
        handle(MailFixtures.mail("<h-reply@m>", sender.email!!, listOf(toProject(p)), "Re: 기존", "답장", headers = mapOf("In-Reply-To" to "<orig-issue@m>")))
        assertEquals(listOf("답장"), issueComments.findAll().filter { it.issue?.id == target.id }.map { it.contents })
        assertEquals(1, issues.findAll().count { it.project?.id == p.id })
    }

    @Test
    fun `알림 메일의 메시지 ID에서 리소스를 찾아 References로도 댓글을 단다`() {
        val sender = user(); val p = project(sender); member(sender, p)
        val target = issue(p, sender)
        handle(MailFixtures.mail("<h-ref@m>", sender.email!!, listOf(toProject(p)), "Re: 기존", "참조 답장", headers = mapOf("References" to "<x@y> <issue_post/${target.id}@yona.example>")))
        assertEquals(listOf("참조 답장"), issueComments.findAll().filter { it.issue?.id == target.id }.map { it.contents })
        assertEquals(1, issues.findAll().count { it.project?.id == p.id })
    }

    @Test
    fun `받는 주소의 세부 부분이 가리키는 리소스에 댓글을 단다`() {
        val sender = user(); val p = project(sender); member(sender, p)
        val target = issue(p, sender)
        handle(MailFixtures.mail("<h-detail@m>", sender.email!!, listOf(yona("${p.owner}/${p.name}/issue_post/${target.id}")), "Re: 기존", "주소로 댓글"))
        assertEquals(listOf("주소로 댓글"), issueComments.findAll().filter { it.issue?.id == target.id }.map { it.contents })
        assertEquals(1, issues.findAll().count { it.project?.id == p.id })
    }

    @Test
    fun `게시글에도 댓글을 단다`() {
        val sender = user(); val p = project(sender); member(sender, p)
        val posting = postings.save(Posting(project = p, number = ++number, title = "글", body = "본문", authorId = sender.id, authorLoginId = sender.loginId, authorName = sender.name, createdDate = Date(), updatedDate = Date()))
        handle(MailFixtures.mail("<h-post@m>", sender.email!!, listOf(yona("${p.owner}/${p.name}/board_post/${posting.id}")), "Re: 글", "글 댓글"))
        assertEquals(ResourceType.NONISSUE_COMMENT, originalEmails.findByMessageId("<h-post@m>").single().resourceType)
    }

    @Test
    fun `리뷰 스레드에는 리뷰 댓글을 단다`() {
        val sender = user(); val p = project(sender); member(sender, p)
        val thread = threads.save(NonRangedCodeCommentThread(commitId = "abc", path = "a.txt").also { it.project = p; it.state = ThreadState.OPEN; it.createdDate = Date() })
        handle(MailFixtures.mail("<h-thread@m>", sender.email!!, listOf(yona("${p.owner}/${p.name}/comment_thread/${thread.id}")), "Re: 리뷰", "리뷰 답장"))
        assertEquals(listOf("리뷰 답장"), reviewComments.findAll().filter { it.thread?.id == thread.id }.map { it.contents })
    }

    @Test
    fun `다른 프로젝트의 리소스는 댓글 대상이 아니라 새 이슈가 된다`() {
        val sender = user(); val a = project(sender); val b = project(sender); member(sender, a); member(sender, b)
        val inB = issue(b, sender)
        handle(MailFixtures.mail("<h-cross@m>", sender.email!!, listOf(toProject(a)), "Re: 다른 곳", "내용", headers = mapOf("References" to "<issue_post/${inB.id}@yona.example>")))
        assertEquals(1, issues.findAll().count { it.project?.id == a.id })
        assertEquals(0, issueComments.findAll().count { it.issue?.id == inB.id })
    }

    @Test
    fun `커밋 주소로 온 답장은 해시만 있어도 오류 없이 건너뛴다 (R-124-4)`() {
        val sender = user(); val p = project(sender); member(sender, p)
        // 알림이 만드는 주소는 `commit/프로젝트번호:해시`이다. 해시만 있는 주소는 주소의 프로젝트로 보고 같은 주소로 다룬다.
        handle(MailFixtures.mail("<h-commit-hash@m>", sender.email!!, listOf(yona("${p.owner}/${p.name}/commit/abc123")), "Re: 커밋", "답장"))
        assertTrue(replies().isEmpty(), "오류 회신이 없어야 한다")
        handle(MailFixtures.mail("<h-commit-full@m>", sender.email!!, listOf(yona("${p.owner}/${p.name}/commit/${p.id}%3Aabc123")), "Re: 커밋", "답장"))
        assertTrue(replies().isEmpty(), "오류 회신이 없어야 한다")
        assertEquals(0, issues.findAll().count { it.project?.id == p.id }, "커밋에는 댓글을 달지 않으므로 새 이슈도 만들지 않는다")
    }

    @Test
    fun `종류가 다른 리소스를 가리키면 아무것도 만들지 않는다`() {
        val sender = user(); val p = project(sender); member(sender, p)
        handle(MailFixtures.mail("<h-miles@m>", sender.email!!, listOf(yona("${p.owner}/${p.name}/user/${sender.id}")), "이상한 대상"))
        assertEquals(0, issues.findAll().count { it.project?.id == p.id })
    }
}
