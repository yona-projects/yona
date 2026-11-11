package dev.yona.web

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.enumeration.State
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
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertTrue

/**
 * Yona를 `https://서버/yona/`처럼 하위 경로에 올렸을 때(`yona.application-context`, v1.6에도 있던 기능) 화면의 링크가 접두어를 잃지 않는지 검증한다
 * (shell-F16, 선생님 결정 2026-11-11). 컨텍스트 경로 `/yona`로 여러 화면을 그려서, 접두어가 없는 루트 절대 경로(`href="/..."`, `src="/..."`, `action="/..."`,
 * `data-request-uri="/..."`)가 하나도 없어야 한다.
 */
@AutoConfigureMockMvc
@Transactional
@org.springframework.test.context.TestPropertySource(properties = ["yona.application-context=yona"])
class ContextPathLinksTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var events: dev.yona.domain.issue.IssueEventRepository
    @Autowired private lateinit var siteAdmins: dev.yona.domain.user.SiteAdminRepository

    private val context = "/yona"

    private fun fixture(): Pair<Project, MockHttpSession> {
        val salt = LegacyPasswordHasher.generateSalt()
        val user = users.save(User(loginId = "cp1", name = "cp1", email = "cp1@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        val project = projects.save(Project(owner = "cpo", name = "cpp", vcs = "GIT", projectScope = ProjectScope.PUBLIC, lastIssueNumber = 1))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(RoleType.MANAGER.id).get()))
        issues.save(Issue(project = project, number = 1, title = "이슈", state = State.OPEN, authorId = user.id, authorLoginId = user.loginId, authorName = user.name))
        events.save(dev.yona.domain.issue.IssueEvent(issue = issues.findAll().last(), senderLoginId = "cp1", eventType = dev.yona.domain.enumeration.EventType.ISSUE_ASSIGNEE_CHANGED, newValue = "cp1", created = java.util.Date()))
        val session = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", "cp1").param("password", "1234")) }
        return project to session
    }

    /** 접두어 없이 `/`로 시작하는 내부 주소를 모은다(`//`로 시작하는 프로토콜 상대 주소와 `#`, 빈 값은 제외). */
    private fun unprefixed(html: String): List<String> {
        val doc = Jsoup.parse(html)
        val attrs = listOf("href", "src", "action", "data-request-uri", "data-href", "data-url")
        val scriptUrls = doc.select("script:not([src])").flatMap { script ->
            Regex("""["'](/[A-Za-z0-9_][^"'\s<>]*)["']""").findAll(script.data().replace("\\/", "/")).map { it.groupValues[1] }.toList()
        }.filter { bad(it) && !it.startsWith("/\\") }.map { "script-string=$it" }
        // 인라인 스타일의 background-image:url(...)도 접두어가 있어야 한다(브라우저 확인에서 드러난 빈틈이다)
        val styleUrls = doc.select("[style]").flatMap { el -> Regex("""url\(['"]?(/[^'")]+)""").findAll(el.attr("style")).map { it.groupValues[1] }.toList() }
            .filter { bad(it) }.map { "style-url=$it" }
        return scriptUrls + styleUrls + attrs.flatMap { attr -> doc.select("[$attr]").filter { bad(it.attr(attr)) }.map { "$attr=" + it.attr(attr) + "  <" + it.tagName() + " class=" + it.className().take(40) + ">" } }
            .distinct()
    }

    @Test
    fun `이슈 이벤트 줄의 외부 아바타 주소에는 접두어를 붙이지 않는다`() {
        val (project, session) = fixture()
        val html = render("/${project.owner}/${project.name}/issue/1", session)
        assertTrue(!html.contains("$context/https:") && !html.contains("$context//"), "외부 주소에 접두어가 붙었다")
    }

    private fun bad(value: String) = value.startsWith("/") && !value.startsWith("//") && !value.startsWith("$context/") && value != context

    private fun render(path: String, session: MockHttpSession?): String =
        mockMvc.perform(get("$context$path").contextPath(context).apply { session?.let { session(it) } }).andReturn().response.contentAsString

    @Test
    fun `하위 경로에 올려도 주요 화면의 링크가 접두어를 잃지 않는다`() {
        val (project, session) = fixture()
        val base = "/${project.owner}/${project.name}"
        siteAdmins.save(dev.yona.domain.user.SiteAdmin(admin = users.findByLoginIdIgnoreCase("cp1")!!))
        val pages = listOf(
            "/", base, "$base/issues", "$base/issue/1", "$base/issueform", "$base/posts", "$base/postform", "$base/milestones", "$base/newMilestoneForm", "$base/members", "$base/settings",
            "$base/issue/labelsform", "$base/code", "$base/pullRequests", "$base/webhooks", "/cp1", "/notifications", "/help", "/projects", "/projectform", "/users/loginform", "/users/signupform",
            "/search?query=cp", "/user/editform", "/user/token", "/user/issues", "/organizations/new", "/organizations/cporg/members", "/sites/userList", "/sites/projectList", "/sites/setting", "/sites/security"
        )
        val violations = pages.associateWith { unprefixed(render(it, session)) }.filterValues { it.isNotEmpty() }
        assertTrue(violations.isEmpty(), "접두어 없는 주소가 있다: $violations")
    }
}
