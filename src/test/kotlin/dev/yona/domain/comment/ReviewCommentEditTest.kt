package dev.yona.domain.comment

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.Side
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.GitTestSupport
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 리뷰 댓글 고치기를 검증한다(추가 개발, GitHub 기준: 작성한 댓글은 제출 전후 모두 고칠 수 있고 "수정됨"으로 표시한다, `decisions.md` D-38 7번).
 * 임시 댓글을 고쳐도 임시로 남고, 빈 내용은 거절한다.
 */
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-review-edit"])
class ReviewCommentEditTest : AbstractIntegrationTest() {

    @Autowired private lateinit var comments: ReviewCommentService
    @Autowired private lateinit var reader: PendingReviewReader
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var repositories: dev.yona.repository.RepositoryService
    @PersistenceContext private lateinit var em: EntityManager

    private var sequence = 0

    private fun user() = users.save(User(loginId = "edt${++sequence}", name = "edt$sequence", email = "edt$sequence@example.com", state = UserState.ACTIVE))

    private fun project(): Project = Project(owner = "edto${++sequence}", name = "edtp$sequence", vcs = "GIT", projectScope = ProjectScope.PUBLIC).also {
        em.persist(it); repositories.createRepository(it)
        GitTestSupport.pushCommits(repositories, it, GitTestSupport.TestCommit("기반", mapOf("a.txt" to "1\n2\n3\n".toByteArray())))
    }

    private fun pullRequest(p: Project): PullRequest {
        val head = repositories.openRepository(p).use { it.resolve("refs/heads/master").name }
        return PullRequest(toProject = p, fromProject = p, number = (++sequence).toLong(), title = "요청", contributor = user(), state = State.OPEN, toBranch = "refs/heads/master", fromBranch = "refs/heads/f", mergedCommitIdFrom = head, mergedCommitIdTo = head).also { em.persist(it) }
    }

    private val range get() = CodeRange("a.txt", Side.B, 1, 0, Side.B, 1, 2)

    @Test
    fun `고치면 내용이 바뀌고 수정 시각이 남는다`() {
        val p = project(); val a = user(); val pr = pullRequest(p)
        val c = comments.commentOnPullRequest(p, pr, null, a, "처음", null, range)
        assertNull(c.editedDate)
        comments.edit(c, "고친 내용")
        assertEquals("고친 내용", c.contents); assertNotNull(c.editedDate)
    }

    @Test
    fun `빈 내용으로는 고칠 수 없다`() {
        val p = project(); val a = user(); val pr = pullRequest(p)
        val c = comments.commentOnPullRequest(p, pr, null, a, "처음", null, range)
        assertFailsWith<IllegalArgumentException> { comments.edit(c, "  ") }
        assertEquals("처음", c.contents)
    }

    @Test
    fun `임시 댓글은 작성자만 읽어 고칠 수 있고 고쳐도 임시로 남는다`() {
        val p = project(); val a = user(); val other = user(); val pr = pullRequest(p)
        val c = comments.commentOnPullRequest(p, pr, null, a, "임시", null, range, pending = true)
        em.flush(); em.clear()
        assertNull(reader.comment(c.id!!, other), "남의 임시 댓글은 찾을 수 없다")
        val mine = reader.comment(c.id!!, a)!!
        comments.edit(mine, "고친 임시")
        em.flush(); em.clear()
        assertEquals("고친 임시", reader.comment(c.id!!, a)!!.contents)
        assertTrue(reader.comment(c.id!!, a)!!.isPending)
        assertEquals(1L, reader.count(em.find(PullRequest::class.java, pr.id), a))
    }
}
