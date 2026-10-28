package dev.yona.web.project

import dev.yona.domain.board.Posting
import dev.yona.domain.board.PostingRepository
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.Milestone
import dev.yona.domain.issue.MilestoneRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.RepositoryService
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.jsoup.Jsoup
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.transaction.annotation.Transactional
import java.io.File
import kotlin.test.assertTrue

/**
 * 한글이나 공백이 든 소유자와 프로젝트 이름이 화면의 주소(링크, 폼 동작, 스타일시트 등)에 퍼센트 인코딩되어 나오는지 검증한다.
 *
 * v1.6의 역방향 라우트는 경로 변수를 모두 인코딩하므로 `/tester/%ED%95%9C...`처럼 나오고, 날것의 한글이 속성에 남으면 공식 릴리스와 달라진다.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-korean-links"])
class KoreanNameLinksTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var postings: PostingRepository
    @Autowired private lateinit var milestones: MilestoneRepository
    @Autowired private lateinit var issues: IssueRepository

    @AfterEach
    fun cleanRepositories() {
        File("build/tmp/it-korean-links").deleteRecursively()
    }

    private val urlAttributes = listOf("href", "action", "src", "data-action", "data-href", "data-request-uri", "data-url", "data-uri")

    private fun rawNames(html: String): List<String> {
        // 엑셀 내려받기 링크는 요청 주소를 그대로 쓴다(MockMvc는 요청 주소를 인코딩하지 않고 일부만 인코딩해 둔다)
        val doc = Jsoup.parse(html)
        return doc.allElements.flatMap { element -> urlAttributes.mapNotNull { attribute -> element.attr(attribute).takeIf { it.startsWith("/") && !it.contains("format=xls") && it.any { c -> c.code > 127 || c == ' ' } }?.let { "<${element.tagName()} $attribute=\"$it\">" } } }
    }

    @Test
    fun `한글 소유자와 한글 프로젝트의 화면에는 날것의 한글이 든 주소가 없다`() {
        val salt = LegacyPasswordHasher.generateSalt()
        val user = users.save(User(loginId = "kl1", name = "kl1", email = "kl1@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        val session = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", "kl1").param("password", "1234")) }
        val project = projects.save(Project(owner = "한글조직", name = "한글 앱", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        repositories.createRepository(project)
        projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(RoleType.MANAGER.id).get()))
        val milestone = milestones.save(Milestone(title = "마일스톤", state = State.OPEN, project = project))
        issues.save(Issue(project = project, milestone = milestone, state = State.OPEN, number = 1, title = "이슈", authorId = user.id, authorLoginId = "kl1", authorName = "kl1"))
        postings.save(Posting(project = project, title = "글", body = "본문", number = 1))
        // MockMvc가 경로를 인코딩하므로 날것의 이름으로 요청한다
        // 사용자 화면의 포크 원본 링크도 인코딩한다
        val fork = projects.save(Project(owner = "kl1", name = "fork1", vcs = "GIT", projectScope = ProjectScope.PUBLIC, originalProject = project))
        menuSettings.save(ProjectMenuSetting(project = fork, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        projectUsers.save(ProjectUser(user = user, project = fork, role = roles.findById(RoleType.MANAGER.id).get()))
        val base = "/${project.owner}/${project.name}"
        val pages = listOf(base, "$base/issues", "$base/issue/1", "$base/posts", "$base/post/1", "$base/milestones", "$base/milestone/${milestone.id}", "$base/members", "$base/pullRequests",
            "$base/code", "$base/changeVCS", "$base/settingform", "$base/issueform", "$base/postform", "$base/transfer", "$base/deleteform", "$base/statistics", "$base/search?keyword=x&searchType=issue", "/projects", "/", "/kl1")
        val problems = pages.flatMap { page ->
            val response = mockMvc.perform(get(page).session(session)).andReturn().response
            // 화면이 제대로 열렸는지 먼저 본다(오류 화면이면 검사가 의미 없다)
            if (response.status != 200) listOf("$page: 상태 ${response.status}") else rawNames(response.contentAsString).distinct().map { "$page: $it" }
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

}
