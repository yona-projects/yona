package dev.yona.domain.comment

import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.Side
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.notification.NotificationEventRepository
import dev.yona.domain.project.Project
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 임시 저장 리뷰의 줄 댓글을 검증한다(로드맵 X-26, `decisions.md` D-38): 임시로 달기(새 스레드, 공개 스레드의 답글, 내 임시 스레드의 답글), 알림이 없는 것,
 * 본인만 읽는 것, 임시 댓글 지우기와 리뷰 버리기, 제출(공개)하기.
 */
@Transactional
@org.springframework.test.context.TestPropertySource(properties = ["yona.data-dir=build/tmp/it-pending-review"])
class PendingReviewServiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var comments: ReviewCommentService
    @Autowired private lateinit var pending: PendingReviewService
    @Autowired private lateinit var reader: PendingReviewReader
    @Autowired private lateinit var threads: CommentThreadRepository
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var notifications: NotificationEventRepository
    @Autowired private lateinit var repositories: dev.yona.repository.RepositoryService
    @PersistenceContext private lateinit var em: EntityManager

    private var sequence = 0

    private fun user() = users.save(User(loginId = "pdg${++sequence}", name = "pdg$sequence", email = "pdg$sequence@example.com", state = UserState.ACTIVE))

    /** 댓글 스레드는 커밋 작성자를 찾으려고 저장소를 열기 때문에 커밋이 하나 있는 실제 저장소가 필요하다. */
    private fun project(): Project = Project(owner = "pdgo${++sequence}", name = "pdgp$sequence", vcs = "GIT", projectScope = ProjectScope.PUBLIC).also {
        em.persist(it); repositories.createRepository(it)
        dev.yona.support.GitTestSupport.pushCommits(repositories, it, dev.yona.support.GitTestSupport.TestCommit("기반", mapOf("a.txt" to "1\n2\n3\n4\n".toByteArray())))
    }

    private fun head(p: Project): String = repositories.openRepository(p).use { it.resolve("refs/heads/master").name }

    private fun pullRequest(p: Project, contributor: User) =
        PullRequest(
            toProject = p, fromProject = p, number = (++sequence).toLong(), title = "요청", contributor = contributor, state = State.OPEN, toBranch = "refs/heads/master", fromBranch = "refs/heads/feature",
            mergedCommitIdFrom = head(p), mergedCommitIdTo = head(p)
        ).also { em.persist(it) }

    private val range get() = CodeRange("a.txt", Side.B, 3, 0, Side.B, 3, 5)

    private fun reviewNotes() = notifications.findAll().filter { it.eventType == EventType.NEW_REVIEW_COMMENT }

    private fun natively(sql: String): Long { em.flush(); return (em.createNativeQuery(sql).singleResult as Number).toLong() }

    @Test
    fun `임시로 달면 새 스레드와 댓글이 임시이고 알림이 없으며 공개 읽기에는 보이지 않는다`() {
        val p = project(); val author = user(); val pr = pullRequest(p, user())
        val c = comments.commentOnPullRequest(p, pr, null, author, "임시 내용", null, range, pending = true)
        assertNotNull(c.pendingDate); assertNotNull(c.thread!!.pendingDate)
        assertTrue(reviewNotes().isEmpty(), "임시 댓글은 알림을 보내지 않는다")
        em.flush(); em.clear()
        assertTrue(threads.findByPullRequestId(pr.id!!).isEmpty())
        assertEquals(1L, reader.count(pr, author))
        assertEquals(0L, reader.count(pr, user()), "다른 사람에게는 내 임시 댓글이 없다")
    }

    @Test
    fun `공개 스레드에 임시 답글을 달 수 있고 스레드에는 공개 댓글만 보인다`() {
        val p = project(); val author = user(); val other = user(); val pr = pullRequest(p, user())
        val first = comments.commentOnPullRequest(p, pr, null, other, "공개 댓글", null, range)
        val reply = comments.commentOnPullRequest(p, pr, null, author, "임시 답글", first.thread!!.id, null, pending = true)
        assertEquals(first.thread!!.id, reply.thread!!.id); assertNotNull(reply.pendingDate); assertNull(reply.thread!!.pendingDate)
        em.flush(); em.clear()
        assertEquals(listOf("공개 댓글"), threads.findById(first.thread!!.id!!).get().reviewComments.map { it.contents })
        assertEquals(listOf("임시 답글"), reader.replies(pr, author).map { it.contents })
        assertTrue(reviewNotes().none { it.newValue == "임시 답글" })
    }

    @Test
    fun `내 임시 스레드에 이어 달면 같은 스레드에 임시로 붙고 남의 임시 스레드는 찾을 수 없다`() {
        val p = project(); val author = user(); val other = user(); val pr = pullRequest(p, user())
        val first = comments.commentOnPullRequest(p, pr, null, author, "임시 1", null, range, pending = true)
        val threadId = first.thread!!.id!!
        em.flush(); em.clear()
        val second = comments.commentOnPullRequest(p, pr, null, author, "임시 2", threadId, null, pending = false)
        assertEquals(threadId, second.thread!!.id, "임시 스레드에 다는 답글은 같은 스레드이다")
        assertNotNull(second.pendingDate, "임시 스레드의 답글은 공개로 달 수 없다")
        assertEquals(2L, reader.count(pr, author))
        em.flush(); em.clear() // 요청마다 영속성 컨텍스트가 따로이므로 다른 사용자의 요청으로 넘어간다
        assertFailsWith<IllegalArgumentException> { comments.commentOnPullRequest(p, pr, null, other, "남의 스레드", threadId, null, pending = true) }
    }

    @Test
    fun `임시 스레드의 읽기 경로는 스레드와 댓글을 함께 돌려준다`() {
        val p = project(); val author = user(); val pr = pullRequest(p, user())
        comments.commentOnPullRequest(p, pr, null, author, "하나", null, range, pending = true)
        em.flush(); em.clear()
        val loaded = reader.threads(pr, author).single()
        assertEquals("a.txt", (loaded as CodeCommentThread).path)
        assertEquals(listOf("하나"), reader.threadComments(loaded).map { it.contents })
        assertTrue(reader.threads(pr, user()).isEmpty())
    }

    @Test
    fun `임시 댓글을 지우면 마지막 댓글일 때 임시 스레드도 지우고 남의 댓글과 공개 댓글은 지우지 못한다`() {
        val p = project(); val author = user(); val other = user(); val pr = pullRequest(p, user())
        val pub = comments.commentOnPullRequest(p, pr, null, other, "공개", null, range)
        val a = comments.commentOnPullRequest(p, pr, null, author, "임시 A", null, range, pending = true)
        val b = comments.commentOnPullRequest(p, pr, null, author, "임시 B", a.thread!!.id, null, pending = true)
        em.flush(); em.clear()
        assertFalse(pending.deleteComment(pr, other, a.id!!), "남의 임시 댓글")
        assertFalse(pending.deleteComment(pr, author, pub.id!!), "공개 댓글은 이 경로로 지우지 못한다")
        assertTrue(pending.deleteComment(pr, author, b.id!!))
        assertEquals(1L, natively("select count(*) from comment_thread where id = ${a.thread!!.id}"), "댓글이 남아 있는 임시 스레드는 그대로이다")
        assertTrue(pending.deleteComment(pr, author, a.id!!))
        assertEquals(0L, natively("select count(*) from comment_thread where id = ${a.thread!!.id}"), "마지막 댓글을 지우면 임시 스레드도 지운다")
        assertEquals(1L, natively("select count(*) from review_comment where id = ${pub.id}"))
    }

    @Test
    fun `리뷰를 버리면 내 임시 댓글과 임시 스레드만 모두 지운다`() {
        val p = project(); val author = user(); val other = user(); val pr = pullRequest(p, user())
        val pub = comments.commentOnPullRequest(p, pr, null, other, "공개", null, range)
        comments.commentOnPullRequest(p, pr, null, author, "임시 스레드", null, range, pending = true)
        comments.commentOnPullRequest(p, pr, null, author, "임시 답글", pub.thread!!.id, null, pending = true)
        val others = comments.commentOnPullRequest(p, pr, null, other, "남의 임시", null, range, pending = true)
        em.flush(); em.clear()
        assertEquals(2, pending.discard(pr, author))
        assertEquals(0L, reader.count(pr, author))
        assertEquals(1L, reader.count(pr, other), "다른 사람의 임시 리뷰는 그대로이다")
        assertEquals(1L, natively("select count(*) from review_comment where id = ${pub.id}"))
        assertNotNull(others.id)
    }

    @Test
    fun `제출하면 내 임시 댓글이 모두 공개되고 번호를 돌려주며 개수는 0이 된다`() {
        val p = project(); val author = user(); val other = user(); val pr = pullRequest(p, user())
        val pub = comments.commentOnPullRequest(p, pr, null, other, "공개", null, range)
        val mine = comments.commentOnPullRequest(p, pr, null, author, "임시 스레드", null, range, pending = true)
        val reply = comments.commentOnPullRequest(p, pr, null, author, "임시 답글", pub.thread!!.id, null, pending = true)
        comments.commentOnPullRequest(p, pr, null, other, "남의 임시", null, range, pending = true)
        em.flush(); em.clear()
        val published = pending.publish(pr, author)
        assertEquals(setOf(mine.id, reply.id), published.toSet())
        em.flush(); em.clear()
        assertEquals(0L, reader.count(pr, author)); assertEquals(1L, reader.count(pr, other))
        assertEquals(2, threads.findByPullRequestId(pr.id!!).size, "내 임시 스레드가 공개되었다(남의 임시 스레드는 아직 숨겨져 있다)")
        assertEquals(listOf("공개", "임시 답글"), threads.findById(pub.thread!!.id!!).get().reviewComments.sortedBy { it.createdDate }.map { it.contents }, "답글은 제출 시각으로 맨 뒤에 붙는다")
    }

    @Test
    fun `임시 댓글이 없으면 제출은 빈 목록이다`() {
        val p = project(); val author = user(); val pr = pullRequest(p, user())
        assertEquals(emptyList(), pending.publish(pr, author))
        assertEquals(0, pending.discard(pr, author))
    }
}
