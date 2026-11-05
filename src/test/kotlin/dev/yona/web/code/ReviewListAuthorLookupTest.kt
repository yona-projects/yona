package dev.yona.web.code

import dev.yona.support.selectFirst
import dev.yona.domain.comment.CodeCommentThread
import dev.yona.domain.comment.CommentThread
import dev.yona.domain.comment.CommentThreadRepository
import dev.yona.domain.comment.NonRangedCodeCommentThread
import dev.yona.domain.comment.ReviewComment
import dev.yona.domain.comment.ReviewCommentRepository
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ThreadState
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import jxl.Workbook
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.mockito.Mockito
import java.io.ByteArrayInputStream
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 리뷰 목록이 스레드마다 첫 댓글 작성자를 번호로 하나씩 다시 조회하지 않고, 쪽에 나온 작성자를 한 번에 조회함을 검증한다 (R-65-4).
 * v1.6은 `User.find.byId`를 스레드마다 불렀다. 화면은 같고 쿼리 수만 줄어든다.
 */
@AutoConfigureMockMvc
@Transactional
class ReviewListAuthorLookupTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @MockitoSpyBean private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var threads: CommentThreadRepository
    @Autowired private lateinit var reviewComments: ReviewCommentRepository
    @Autowired private lateinit var entityManager: jakarta.persistence.EntityManager

    private var sequence = 0

    private fun user(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "ra$n", name = "작성자$n", email = "ra$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    @Test
    fun `첫 댓글 작성자를 스레드마다 번호로 조회하지 않는다`() {
        val viewer = user()
        val n = ++sequence
        val project = projects.save(Project(owner = "rao$n", name = "rap$n", vcs = "GIT", projectScope = ProjectScope.PUBLIC)).also {
            menuSettings.save(ProjectMenuSetting(project = it, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        }
        val authors = (1..4).map { user() }
        authors.forEachIndexed { i, author ->
            val thread = threads.save(CodeCommentThread(commitId = "abcdef12345678$i", path = "src/A$i.kt").also {
                it.project = project; it.state = ThreadState.OPEN; it.createdDate = Date(1_700_000_000_000L + i * 60_000L); it.authorId = author.id; it.authorLoginId = author.loginId; it.authorName = author.name
            })
            thread.reviewComments.add(reviewComments.save(ReviewComment(contents = "내용$i", createdDate = Date(1_700_000_000_000L + i * 60_000L), authorId = author.id, authorLoginId = author.loginId, authorName = author.name, thread = thread)))
        }
        val session = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", viewer.loginId!!).param("password", "1234")) }
        entityManager.flush(); entityManager.clear()
        Mockito.clearInvocations(users)

        val html = mockMvc.perform(get("/${project.owner}/${project.name}/reviews").session(session)).andExpect(status().isOk).andReturn().response.contentAsString

        authors.forEach { assertTrue(html.contains(it.loginId!!), "${it.loginId} 작성자가 목록에 있어야 한다") }
        authors.forEach { Mockito.verify(users, Mockito.never()).findById(it.id!!) }
    }
}
