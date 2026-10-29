package dev.yona.domain.comment

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.ThreadState
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectDeletionService
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 임시 저장 리뷰의 댓글(`pending_date`)이 기본으로 숨겨지는지 검증한다(로드맵 X-26, `decisions.md` D-38). 엔티티에 항상 적용되는 조건 때문에 스레드와 댓글을 읽는
 * 어떤 경로(아이디 조회, 질의, 파생 질의, 연관 컬렉션)도 임시 행을 보지 못하고, 프로젝트를 지우면 임시 행도 함께 지워져야 한다.
 */
@Transactional
class PendingReviewStorageTest : AbstractIntegrationTest() {

    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var threads: CommentThreadRepository
    @Autowired private lateinit var reviewComments: ReviewCommentRepository
    @Autowired private lateinit var projectDeletion: ProjectDeletionService
    @PersistenceContext private lateinit var em: EntityManager

    private var sequence = 0

    private fun user() = users.save(User(loginId = "pnd${++sequence}", name = "pnd$sequence", email = "pnd$sequence@example.com", state = UserState.ACTIVE))

    private fun project() = Project(owner = "pndo${++sequence}", name = "pndp$sequence", vcs = "GIT", projectScope = ProjectScope.PUBLIC).also { em.persist(it) }

    private fun pullRequest(p: Project, contributor: User) =
        PullRequest(toProject = p, fromProject = p, number = (++sequence).toLong(), title = "요청", contributor = contributor, state = State.OPEN, toBranch = "refs/heads/master", fromBranch = "refs/heads/feature").also { em.persist(it) }

    private fun thread(p: Project, pr: PullRequest, author: User, pending: Boolean): CodeCommentThread =
        CodeCommentThread(commitId = "abc${++sequence}", path = "a.txt", startSide = dev.yona.domain.enumeration.Side.B, startLine = 1, endSide = dev.yona.domain.enumeration.Side.B, endLine = 1).also {
            it.project = p; it.pullRequest = pr; it.state = ThreadState.OPEN; it.authorId = author.id; it.authorLoginId = author.loginId; it.createdDate = Date(); if (pending) it.pendingDate = Date()
            em.persist(it)
        }

    private fun comment(t: CommentThread, author: User, text: String, pending: Boolean) =
        ReviewComment(contents = text, createdDate = Date(), authorId = author.id, authorLoginId = author.loginId, thread = t, pendingDate = if (pending) Date() else null).also { em.persist(it); t.reviewComments.add(it) }

    private fun natively(sql: String): Long { em.flush(); return (em.createNativeQuery(sql).singleResult as Number).toLong() }

    @Test
    fun `임시 스레드와 임시 댓글은 어떤 읽기 경로에도 보이지 않는다`() {
        val p = project(); val author = user(); val pr = pullRequest(p, user())
        val visible = thread(p, pr, author, pending = false); comment(visible, author, "공개 댓글", pending = false)
        val hidden = thread(p, pr, author, pending = true); val hiddenComment = comment(hidden, author, "임시 댓글", pending = true)
        val reply = comment(visible, author, "임시 답글", pending = true)
        em.flush(); em.clear()

        assertNull(em.find(CommentThread::class.java, hidden.id), "아이디로도 읽지 못한다")
        assertNull(em.find(ReviewComment::class.java, hiddenComment.id))
        assertEquals(listOf(visible.id), threads.findByPullRequestId(pr.id!!).map { it.id })
        assertEquals(1, threads.countByPullRequestId(pr.id!!))
        assertEquals(1, threads.countByPullRequestIdAndState(pr.id!!, ThreadState.OPEN))
        assertEquals(1, threads.countByProjectIdAndState(p.id!!, ThreadState.OPEN))
        assertEquals(1, reviewComments.countByThreadId(visible.id!!), "공개 스레드의 임시 답글은 세지 않는다")
        assertEquals(listOf("공개 댓글"), em.find(CommentThread::class.java, visible.id).reviewComments.map { it.contents }, "연관 컬렉션도 임시 답글을 싣지 않는다")
        assertEquals(1, em.createQuery("select count(c) from ReviewComment c where c.thread.pullRequest.id = :id", java.lang.Long::class.java).setParameter("id", pr.id).singleResult.toLong())
        assertEquals(1, em.createQuery("select count(t) from CommentThread t where t.pullRequest.id = :id", java.lang.Long::class.java).setParameter("id", pr.id).singleResult.toLong())
        // 숨겨졌을 뿐 지워진 것은 아니다
        assertEquals(2, natively("select count(*) from comment_thread where pull_request_id = ${pr.id}"))
        assertEquals(3, natively("select count(*) from review_comment where thread_id in (${visible.id}, ${hidden.id})"))
        assertTrue(reply.id != null)
    }

    @Test
    fun `새 컬럼의 기본값은 공개이고 옛 행은 그대로 보인다`() {
        val p = project(); val a = user(); val pr = pullRequest(p, user())
        val t = CodeCommentThread(commitId = "old", path = "b.txt").also { it.project = p; it.pullRequest = pr; it.state = ThreadState.OPEN; it.authorId = a.id; it.createdDate = Date(); em.persist(it) }
        em.flush(); em.clear()
        assertEquals(false, em.find(CommentThread::class.java, t.id).isPending)
        assertEquals(1, natively("select count(*) from comment_thread where id = ${t.id} and pending_date is null"))
    }

    @Test
    fun `프로젝트를 지우면 임시 스레드와 임시 댓글도 함께 지워진다`() {
        val p = project(); val other = project(); val a = user()
        val pr = pullRequest(p, user()); val otherPr = pullRequest(other, user())
        val mine = thread(p, pr, a, pending = true); comment(mine, a, "임시", pending = true)
        val visible = thread(p, pr, a, pending = false); comment(visible, a, "공개 스레드의 임시 답글", pending = true)
        val others = thread(other, otherPr, a, pending = true); comment(others, a, "다른 프로젝트의 임시", pending = true)
        em.flush()
        projectDeletion.delete(p)
        em.flush(); em.clear()
        assertEquals(0, natively("select count(*) from comment_thread where project_id = ${p.id}"), "임시 스레드가 남지 않는다")
        assertEquals(0, natively("select count(*) from review_comment where thread_id in (${mine.id}, ${visible.id})"), "임시 댓글이 남지 않는다")
        assertEquals(1, natively("select count(*) from comment_thread where id = ${others.id}"), "다른 프로젝트의 임시 행은 그대로이다")
    }
}
