package dev.yona.mailbox

import dev.yona.domain.board.Posting
import dev.yona.domain.board.PostingCommentRepository
import dev.yona.domain.board.PostingRepository
import dev.yona.domain.comment.IssueCommentRepository
import dev.yona.domain.comment.NonRangedCodeCommentThread
import dev.yona.domain.comment.CommentThreadRepository
import dev.yona.domain.comment.ReviewCommentRepository
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.ThreadState
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.misc.AttachmentRepository
import dev.yona.domain.misc.Watch
import dev.yona.domain.misc.WatchRepository
import dev.yona.domain.notification.NotificationEventRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.access.ResourceLookup
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 받은 메일에서 이슈, 댓글, 리뷰 댓글을 만드는 부분을 검증한다. 원본: `mailbox.CreationViaEmail`(`app/mailbox/CreationViaEmail.java`).
 */
@Transactional
class MailResourceCreatorTest : AbstractIntegrationTest() {

    @Autowired private lateinit var creator: MailResourceCreator
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var issueComments: IssueCommentRepository
    @Autowired private lateinit var postings: PostingRepository
    @Autowired private lateinit var postingComments: PostingCommentRepository
    @Autowired private lateinit var threads: CommentThreadRepository
    @Autowired private lateinit var reviewComments: ReviewCommentRepository
    @Autowired private lateinit var attachments: AttachmentRepository
    @Autowired private lateinit var events: NotificationEventRepository
    @Autowired private lateinit var watches: WatchRepository
    @Autowired private lateinit var lookup: ResourceLookup

    private var sequence = 0
    private var number = 0L

    private fun user(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "mc$n", name = "메일$n", email = "mc$n@mail.test", state = UserState.ACTIVE, createdDate = Date(), passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun project(owner: User, scope: ProjectScope = ProjectScope.PRIVATE) =
        projects.save(Project(owner = owner.loginId, name = "mailproj${++sequence}", vcs = "GIT", projectScope = scope, createdDate = Date()))

    private fun member(user: User, project: Project, watching: Boolean = true) {
        projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(RoleType.MEMBER.id).get()))
        if (watching) watches.save(Watch(user = user, resourceType = ResourceType.PROJECT, resourceId = project.id.toString()))
    }

    private fun issue(project: Project, author: User) = issues.save(
        Issue(project = project, number = ++number, title = "기존 이슈", body = "본문", state = State.OPEN, authorId = author.id, authorLoginId = author.loginId, authorName = author.name, numOfComments = 0, weight = 0, createdDate = Date(), updatedDate = Date())
    )

    private fun to(project: Project) = "yona+${project.owner}/${project.name}@mail.test"

    // --- 이슈 ---

    @Test
    fun `메일로 이슈를 만든다`() {
        val sender = user(); val p = project(sender); member(sender, p)
        val mail = MailFixtures.mail("<m-issue-1@mail>", sender.email!!, listOf(to(p)), "메일 제목", "메일 본문")
        val issue = creator.saveIssue(mail, p)
        assertEquals("메일 제목", issue.title)
        assertEquals("메일 본문", issue.body)
        assertEquals(sender.loginId, issue.authorLoginId)
        assertEquals(1L, issue.number)
    }

    @Test
    fun `권한이 없으면 만들 수 없다는 예외이다`() {
        val owner = user(); val outsider = user(); val p = project(owner)
        val mail = MailFixtures.mail("<m-denied@mail>", outsider.email!!, listOf(to(p)), "제목")
        val e = assertFailsWith<PermissionDenied> { creator.saveIssue(mail, p) }
        // v1.6는 메시지가 {1}부터인데 {0}부터 인자를 넘겨 문구가 어긋난다(R-61). 같은 문구를 그대로 낸다.
        assertEquals("You (issue) don't have the permission to create ${p.owner}/${p.name} in project {3}.", e.message)
    }

    @Test
    fun `알림은 보낸 사람과 메일을 직접 받은 사람을 뺀 구독자에게 간다`() {
        val sender = user(); val direct = user(); val other = user(); val cc = user(); val p = project(sender)
        listOf(sender, direct, other, cc).forEach { member(it, p) }
        val mail = MailFixtures.mail("<m-event@mail>", sender.email!!, listOf(to(p), direct.email!!), "알림 제목", cc = listOf(cc.email!!))
        val issue = creator.saveIssue(mail, p)
        val event = events.findByResourceTypeAndResourceId(ResourceType.ISSUE_POST, issue.id.toString()).single()
        assertEquals(EventType.NEW_ISSUE, event.eventType)
        assertEquals(setOf(other.loginId), event.receivers.map { it.loginId }.toSet())
    }

    @Test
    fun `일반 텍스트 첨부는 이슈에 붙는다`() {
        val sender = user(); val p = project(sender); member(sender, p)
        val body = MailFixtures.multipart("mixed", MimeBodyPartText("본문 글"), MailFixtures.file("report.txt", "리포트 내용"))
        val issue = creator.saveIssue(MailFixtures.mail("<m-att@mail>", sender.email!!, listOf(to(p)), "첨부 메일", body), p)
        assertEquals("본문 글", issue.body)
        val stored = attachments.findAll().filter { it.containerType == ResourceType.ISSUE_POST && it.containerId == issue.id }
        assertEquals(listOf("report.txt"), stored.map { it.name })
    }

    @Test
    fun `HTML 메일은 cid를 첨부 주소로 바꾸고 이슈 본문의 개행을 줄이며 알림 본문도 같은 내용이다`() {
        val sender = user(); val other = user(); val p = project(sender); member(sender, p); member(other, p)
        val html = MailFixtures.html("<html><body>\n<p>그림</p>\n<img src=\"cid:pic1\">\n<a href=\"cid:none\">x</a></body></html>")
        val related = MailFixtures.multipart("related", html, MailFixtures.file("pic.png", "PNG", "<pic1>"))
        val issue = creator.saveIssue(MailFixtures.mail("<m-html@mail>", sender.email!!, listOf(to(p)), "HTML 메일", related), p)
        val stored = attachments.findAll().single { it.containerType == ResourceType.ISSUE_POST && it.containerId == issue.id }
        // 첨부 주소로 바뀌고 알 수 없는 cid는 그대로이며 태그 사이 개행은 공백 한 칸이 된다(HtmlCompressor 기본 설정).
        assertEquals("<p>그림</p> <img src=\"/files/${stored.id}\"> <a href=\"cid:none\">x</a>", issue.body)
        val event = events.findByResourceTypeAndResourceId(ResourceType.ISSUE_POST, issue.id.toString()).single()
        assertEquals(issue.body, event.newValue)
    }

    @Test
    fun `파일 이름이 없는 related 첨부도 오류 없이 저장한다 (v1_6은 decodeText(null)의 NPE)`() {
        // JavaMail 1.4.1, 1.4.5, 1.5.2 모두 MimeUtility.decodeText(null)이 NullPointerException을 던진다(직접 실행해 확인).
        // v1.6 CreationViaEmail.saveAttachment는 파일 이름을 검사하지 않고 넘기므로 이 메일은 v1.6에서 500이다.
        val sender = user(); val p = project(sender); member(sender, p)
        val html = MailFixtures.html("<html><body><p>그림</p><img src=\"cid:pic9\"></body></html>")
        val related = MailFixtures.multipart("related", html, MailFixtures.inline("<pic9>", "PNG"))
        val issue = creator.saveIssue(MailFixtures.mail("<m-noname@mail>", sender.email!!, listOf(to(p)), "이름 없는 첨부", related), p)
        val stored = attachments.findAll().single { it.containerType == ResourceType.ISSUE_POST && it.containerId == issue.id }
        assertTrue(!stored.name.isNullOrBlank(), "이름이 없으면 시각으로 이름을 만든다")
        assertTrue(issue.body!!.contains("/files/${stored.id}"), issue.body)
    }

    // --- 댓글 ---

    @Test
    fun `이슈에 메일로 댓글을 단다`() {
        val sender = user(); val other = user(); val p = project(sender); member(sender, p); member(other, p)
        val issue = issue(p, other)
        val target = lookup.get(ResourceType.ISSUE_POST, issue.id.toString())
        val comment = creator.saveComment(MailFixtures.mail("<m-c1@mail>", sender.email!!, listOf(to(p)), "Re: 기존 이슈", "댓글 본문"), target)
        assertEquals("댓글 본문", comment.contents)
        assertEquals(sender.loginId, comment.authorLoginId)
        assertEquals(1, issueComments.findAll().count { it.issue?.id == issue.id })
        assertEquals(ResourceType.ISSUE_COMMENT, comment.type)
    }

    @Test
    fun `게시글에 메일로 댓글을 단다`() {
        val sender = user(); val p = project(sender); member(sender, p)
        val posting = postings.save(Posting(project = p, number = ++number, title = "글", body = "본문", authorId = sender.id, authorLoginId = sender.loginId, authorName = sender.name, createdDate = Date(), updatedDate = Date()))
        val target = lookup.get(ResourceType.BOARD_POST, posting.id.toString())
        val comment = creator.saveComment(MailFixtures.mail("<m-c2@mail>", sender.email!!, listOf(to(p)), "Re: 글", "게시글 댓글"), target)
        assertEquals("게시글 댓글", comment.contents)
        assertEquals(1, postingComments.findAll().count { it.posting?.id == posting.id })
        assertEquals(ResourceType.NONISSUE_COMMENT, comment.type)
    }

    @Test
    fun `댓글 권한이 없으면 예외이다`() {
        val owner = user(); val outsider = user(); val p = project(owner)
        val target = lookup.get(ResourceType.ISSUE_POST, issue(p, owner).id.toString())
        assertFailsWith<PermissionDenied> { creator.saveComment(MailFixtures.mail("<m-c3@mail>", outsider.email!!, listOf(to(p)), "Re: x"), target) }
    }

    @Test
    fun `댓글 첨부는 댓글에 붙는다`() {
        val sender = user(); val p = project(sender); member(sender, p)
        val target = lookup.get(ResourceType.ISSUE_POST, issue(p, sender).id.toString())
        val content = MailFixtures.multipart("mixed", MimeBodyPartText("본문"), MailFixtures.file("c.txt", "내용"))
        val comment = creator.saveComment(MailFixtures.mail("<m-c4@mail>", sender.email!!, listOf(to(p)), "Re: x", content), target)
        assertEquals(listOf("c.txt"), attachments.findAll().filter { it.containerType == ResourceType.ISSUE_COMMENT && it.containerId == comment.id }.map { it.name })
    }

    // --- 리뷰 댓글 ---

    @Test
    fun `리뷰 스레드에 메일로 댓글을 단다`() {
        val sender = user(); val p = project(sender); member(sender, p)
        val thread = threads.save(NonRangedCodeCommentThread(prevCommitId = null, commitId = "abc123", path = "a.txt").also { it.project = p; it.state = ThreadState.OPEN; it.createdDate = Date() })
        val target = lookup.get(ResourceType.COMMENT_THREAD, thread.id.toString())
        val comment = creator.saveReviewComment(MailFixtures.mail("<m-r1@mail>", sender.email!!, listOf(to(p)), "Re: 리뷰", "리뷰 댓글"), target)
        assertEquals("리뷰 댓글", comment.contents)
        assertEquals(thread.id, comment.thread?.id)
        assertEquals(sender.loginId, comment.authorLoginId)
    }

    @Test
    fun `리뷰 댓글 권한이 없으면 예외이다`() {
        val owner = user(); val outsider = user(); val p = project(owner)
        val thread = threads.save(NonRangedCodeCommentThread(commitId = "abc123", path = "a.txt").also { it.project = p; it.state = ThreadState.OPEN })
        val target = lookup.get(ResourceType.COMMENT_THREAD, thread.id.toString())
        assertFailsWith<PermissionDenied> { creator.saveReviewComment(MailFixtures.mail("<m-r2@mail>", outsider.email!!, listOf(to(p)), "Re: x"), target) }
    }

    private fun MimeBodyPartText(text: String) = jakarta.mail.internet.MimeBodyPart().apply { setText(text, "utf-8") }
}
