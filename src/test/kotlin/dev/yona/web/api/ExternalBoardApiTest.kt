package dev.yona.web.api

import dev.yona.domain.board.PostingCommentRepository
import dev.yona.domain.board.PostingRepository
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 외부용 게시판 API를 검증한다: 글 여러 개 등록, 댓글 등록.
 *
 * 원본: `app/controllers/api/BoardApi.java`(`newPostings`, `newPostingComment`).
 */
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
class ExternalBoardApiTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var postings: PostingRepository
    @Autowired private lateinit var comments: PostingCommentRepository
    @Autowired private lateinit var events: ApplicationEvents
    @Autowired private lateinit var entityManager: EntityManager

    private var sequence = 0

    private fun newUser(token: String? = null): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "xb$n", name = "xb$n", email = "xb$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt), token = token))
    }

    private fun member(scope: ProjectScope = ProjectScope.PUBLIC): Pair<User, Project> {
        val n = ++sequence
        val user = newUser("xbtk$n")
        val project = projects.save(Project(owner = "xbo$n", name = "xbp$n", vcs = "GIT", projectScope = scope, lastIssueNumber = 0, lastPostingNumber = 0))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(RoleType.MEMBER.id).get()))
        return user to project
    }

    private fun api(project: Project, path: String) = "/-_-api/v1/owners/${project.owner}/projects/${project.name}/$path"

    private fun json(builder: org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder, token: String?, body: String?) =
        mockMvc.perform(builder.apply { token?.let { header("Authorization", "token $it") } }.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).apply { body?.let { content(it) } })

    private val dateFormat get() = SimpleDateFormat("yyyy-MM-dd a hh:mm:ss Z", Locale.ENGLISH)

    @Test
    fun `글 여러 개를 번호와 작성자, 시각과 함께 등록한다`() {
        val (user, project) = member()
        val author = newUser()
        val created = dateFormat.format(Date(1_600_000_000_000L))
        val body = """{"posts":[{"title":"가져온 글","body":"내용 @${user.loginId}","author":{"email":"${author.email}"},"createdAt":"$created","updatedAt":"$created","number":5},{"title":"다음 글"}]}"""
        json(post(api(project, "posts")), user.token, body).andExpect(status().isCreated)
            .andExpect(jsonPath("$[0].status").value(201)).andExpect(jsonPath("$[0].location").value("/${project.owner}/${project.name}/post/5"))
            .andExpect(jsonPath("$[1].location").value("/${project.owner}/${project.name}/post/1"))
        entityManager.flush(); entityManager.clear()
        val saved = postings.findAll().filter { it.project?.id == project.id }.associateBy { it.number }
        assertEquals("가져온 글", saved[5]!!.title); assertEquals(author.loginId, saved[5]!!.authorLoginId); assertEquals(1_600_000_000_000L / 1000, saved[5]!!.createdDate!!.time / 1000)
        assertEquals(user.loginId, saved[1]!!.authorLoginId)
        assertEquals(0, events.stream(dev.yona.domain.board.PostingCreatedActivity::class.java).count())
    }

    @Test
    fun `글 등록의 오류와 권한`() {
        val (user, project) = member()
        json(post(api(project, "posts")), user.token, """{"nope":1}""").andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("No posts key exists or value wasn't array!"))
        mockMvc.perform(post(api(project, "posts")).header("Authorization", "token ${user.token}").contentType(MediaType.TEXT_PLAIN).content("x")).andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("Expecting Json data"))
        val (_, secret) = member(ProjectScope.PRIVATE)
        json(post(api(secret, "posts")), user.token, """{"posts":[{"title":"t"}]}""").andExpect(status().isForbidden)
    }

    @Test
    fun `댓글을 달면 작성자와 시각을 정하고 글의 댓글 수가 맞는다`() {
        val (user, project) = member()
        val author = newUser()
        json(post(api(project, "posts")), user.token, """{"posts":[{"title":"글"}]}""").andExpect(status().isCreated)
        val created = dateFormat.format(Date(1_600_000_000_000L))
        json(post(api(project, "posts/1/comments")), user.token, """{"body":"댓글","createdAt":"$created","author":{"email":"${author.email}"}}""").andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value(201)).andExpect(jsonPath("$.location").value(org.hamcrest.Matchers.startsWith("/${project.owner}/${project.name}/post/1#comment-")))
        entityManager.flush(); entityManager.clear()
        val saved = comments.findAll().single()
        assertEquals(author.loginId, saved.authorLoginId); assertEquals(1_600_000_000_000L / 1000, saved.createdDate!!.time / 1000)
        assertEquals(1, postings.findAll().single { it.project?.id == project.id }.numOfComments)
    }

    @Test
    fun `댓글의 오류와 권한`() {
        val (user, project) = member()
        json(post(api(project, "posts")), user.token, """{"posts":[{"title":"글"}]}""")
        mockMvc.perform(post(api(project, "posts/1/comments")).header("Authorization", "token ${user.token}").contentType(MediaType.TEXT_PLAIN).content("x")).andExpect(status().isBadRequest)
        json(post(api(project, "posts/9/comments")), user.token, """{"body":"x"}""").andExpect(status().isNotFound)
        val (owner, secret) = member(ProjectScope.PRIVATE)
        json(post(api(secret, "posts")), owner.token, """{"posts":[{"title":"비밀"}]}""").andExpect(status().isCreated)
        json(post(api(secret, "posts/1/comments")), user.token, """{"body":"x"}""").andExpect(status().isForbidden)
        assertTrue(true)
    }
}
