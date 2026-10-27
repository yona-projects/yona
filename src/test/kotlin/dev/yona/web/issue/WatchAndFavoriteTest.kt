package dev.yona.web.issue

import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Assignee
import dev.yona.domain.issue.AssigneeRepository
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueEvent
import dev.yona.domain.issue.IssueLabel
import dev.yona.domain.issue.IssueLabelCategory
import dev.yona.domain.issue.IssueLabelCategoryRepository
import dev.yona.domain.issue.IssueLabelRepository
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.Milestone
import dev.yona.domain.issue.MilestoneRepository
import dev.yona.domain.misc.Attachment
import dev.yona.domain.misc.AttachmentRepository
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
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 구독(`POST /watch`, `/unwatch`)과 즐겨찾기 토글(`POST /-_-api/v1/favoriteIssues/:id`)을 검증한다.
 *
 * 원본: `app/controllers/WatchApp.java`, `app/models/Watch.java`, `app/controllers/api/UserApi.java:92-126`, `app/models/User.java:1072-1100`.
 */
@AutoConfigureMockMvc
@Transactional
class WatchAndFavoriteTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var watches: dev.yona.domain.misc.WatchRepository
    @Autowired private lateinit var unwatches: dev.yona.domain.misc.UnwatchRepository
    @Autowired private lateinit var favorites: dev.yona.domain.misc.FavoriteIssueRepository
    @Autowired private lateinit var entityManager: EntityManager
    @Autowired private lateinit var postings: dev.yona.domain.board.PostingRepository

    private var sequence = 0

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "wf$n", name = "wf$n", email = "wf$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun newProject(scope: ProjectScope = ProjectScope.PUBLIC): Project {
        val n = ++sequence
        val project = projects.save(Project(owner = "wo$n", name = "wp$n", vcs = "GIT", projectScope = scope, lastIssueNumber = 1))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        return project
    }

    private fun issue(project: Project, title: String = "제목"): Issue = issues.save(
        Issue(project = project, number = 1, title = title, body = "본문", state = State.OPEN, numOfComments = 0, weight = 0, createdDate = Date(), updatedDate = Date())
    )

    // --- 구독 ---

    @Test
    fun `구독하면 기록하고 이전의 구독 해제는 지운다`() {
        val project = newProject(); val i = issue(project); val user = newUser()
        val session = login(user)
        mockMvc.perform(post("/unwatch").session(session).queryParam("resource.type", "issue_post").queryParam("resource.id", i.id.toString()).header("Accept", "application/json")).andExpect(status().isOk)
        assertEquals(1, unwatches.count())
        mockMvc.perform(post("/watch").session(session).queryParam("resource.type", "issue_post").queryParam("resource.id", i.id.toString())).andExpect(status().isOk)
        assertEquals(1, watches.count()); assertEquals(0, unwatches.count())
        mockMvc.perform(post("/watch").session(session).queryParam("resource.type", "issue_post").queryParam("resource.id", i.id.toString())).andExpect(status().isOk)
        assertEquals(1, watches.count())
    }

    @Test
    fun `화면이 보내는 주소의 앰퍼샌드 amp 세미콜론 구분도 질의 구분자로 받는다 (v1_6 Netty 질의 해석)`() {
        val project = newProject(); val i = issue(project); val user = newUser()
        val session = login(user)
        mockMvc.perform(post("/watch?resource.type=issue_post&amp;resource.id=${i.id}").session(session)).andExpect(status().isOk)
        assertEquals(1, watches.count())
        mockMvc.perform(post("/unwatch?resource.type=issue_post&amp;resource.id=${i.id}").session(session).header("Accept", "application/json")).andExpect(status().isOk)
        assertEquals(0, watches.count())
    }

    @Test
    fun `익명이거나 읽을 수 없으면 구독은 403이다`() {
        val project = newProject(ProjectScope.PRIVATE); val i = issue(project)
        mockMvc.perform(post("/watch").queryParam("resource.type", "issue_post").queryParam("resource.id", i.id.toString())).andExpect(status().isForbidden).andExpect(content().string("Anonymous cannot watch it."))
        mockMvc.perform(post("/watch").session(login(newUser())).queryParam("resource.type", "issue_post").queryParam("resource.id", i.id.toString()))
            .andExpect(status().isForbidden).andExpect(content().string("You have no permission to watch it."))
    }

    @Test
    fun `구독 해제는 JSON이면 200이고 아니면 리소스로 돌아가며 처음이면 안내를 남긴다`() {
        val project = newProject(); val i = issue(project); val user = newUser()
        val session = login(user)
        mockMvc.perform(post("/unwatch").session(session).queryParam("resource.type", "issue_post").queryParam("resource.id", i.id.toString()))
            .andExpect(status().isSeeOther).andExpect(header().string("Location", "/${project.owner}/${project.name}/issue/1"))
        // 이미 해제한 뒤에는 안내가 없다.
        mockMvc.perform(get("/unwatch").session(session).queryParam("resource.type", "issue_post").queryParam("resource.id", i.id.toString())).andExpect(status().isSeeOther)
        assertEquals(1, unwatches.count())
    }

    @Test
    fun `프로젝트 구독 해제는 프로젝트 홈으로 돌아간다 (v1_6 Project_asResource의 getProject)`() {
        val project = newProject(); val user = newUser()
        mockMvc.perform(post("/unwatch").session(login(user)).queryParam("resource.type", "project").queryParam("resource.id", project.id.toString()))
            .andExpect(status().isSeeOther).andExpect(header().string("Location", "/${project.owner}/${project.name}"))
    }

    @Test
    fun `게시글 댓글과 커밋 코멘트 구독 해제는 v1_6 RouteUtil의 주소로 돌아간다`() {
        val project = newProject(); val user = newUser(); val session = login(user)
        val posting = postings.save(dev.yona.domain.board.Posting(project = project, number = 1, title = "글", body = "본문", authorLoginId = user.loginId, createdDate = Date()))
        val comment = entityManager.let { em -> dev.yona.domain.board.PostingComment(contents = "c", createdDate = Date(), authorId = user.id, authorLoginId = user.loginId, authorName = user.name, posting = posting, projectId = project.id!!).also { em.persist(it); em.flush() } }
        mockMvc.perform(post("/unwatch").session(session).queryParam("resource.type", "nonissue_comment").queryParam("resource.id", comment.id.toString()))
            .andExpect(status().isSeeOther).andExpect(header().string("Location", "/${project.owner}/${project.name}/post/1#comment-${comment.id}"))
        val commit = dev.yona.domain.comment.CommitComment(project = project, commitId = "abc", contents = "c", createdDate = Date(), authorId = user.id, authorLoginId = user.loginId, authorName = user.name)
            .also { entityManager.persist(it); entityManager.flush() }
        mockMvc.perform(post("/unwatch").session(session).queryParam("resource.type", "code_comment").queryParam("resource.id", commit.id.toString()))
            .andExpect(status().isSeeOther).andExpect(header().string("Location", "/${project.owner}/${project.name}/commit/abc#comment-${commit.id}"))
    }

    @Test
    fun `구독 해제를 익명이 하면 403 화면이다`() {
        val project = newProject(); val i = issue(project)
        mockMvc.perform(post("/unwatch").queryParam("resource.type", "issue_post").queryParam("resource.id", i.id.toString())).andExpect(status().isForbidden)
    }

    @Test
    fun `알 수 없는 리소스는 400이다`() {
        mockMvc.perform(post("/watch").session(login(newUser())).queryParam("resource.type", "nothing").queryParam("resource.id", "1")).andExpect(status().isBadRequest)
        mockMvc.perform(post("/watch").session(login(newUser()))).andExpect(status().isBadRequest)
    }

    // --- 즐겨찾기 ---

    @Test
    fun `즐겨찾기를 켜고 끈다`() {
        val project = newProject(); val i = issue(project); val user = newUser()
        val session = login(user)
        mockMvc.perform(post("/-_-api/v1/favoriteIssues/${i.id}").session(session)).andExpect(status().isOk)
            .andExpect(jsonPath("$.issueId").value(i.id.toString())).andExpect(jsonPath("$.favored").value(true)).andExpect(jsonPath("$.message").value("Added as a favorite issue. See it on the My Issues page"))
        assertEquals(1, favorites.count())
        mockMvc.perform(post("/-_-api/v1/favoriteIssues/${i.id}").session(session)).andExpect(jsonPath("$.favored").value(false)).andExpect(jsonPath("$.message").value("Removed from favorite issues"))
        assertEquals(0, favorites.count())
    }

    @Test
    fun `즐겨찾기 목록을 돌려준다`() {
        val project = newProject(); val a = issue(project, "첫 이슈"); val user = newUser()
        val session = login(user)
        mockMvc.perform(post("/-_-api/v1/favoriteIssues/${a.id}").session(session))
        mockMvc.perform(get("/-_-api/v1/favoriteIssues").session(session)).andExpect(status().isOk)
            .andExpect(jsonPath("$.projectIds[0]").value(a.id)).andExpect(jsonPath("$.projects[0].issueTitle").value("첫 이슈")).andExpect(jsonPath("$.projects[0].issueId").value(a.id))
    }

    @Test
    fun `숫자가 아닌 번호는 400이다`() {
        mockMvc.perform(post("/-_-api/v1/favoriteIssues/abc").session(login(newUser()))).andExpect(status().isBadRequest)
    }
}
