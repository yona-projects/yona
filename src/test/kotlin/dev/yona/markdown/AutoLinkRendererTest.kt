package dev.yona.markdown

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.RepositoryService
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.GitTestSupport
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 렌더링한 HTML 안의 `#이슈`, 커밋 SHA, `@사용자` 참조를 링크로 바꾸는 동작을 검증한다.
 *
 * 원본: `app/utils/AutoLinkRenderer.java`.
 */
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-autolink"])
class AutoLinkRendererTest : AbstractIntegrationTest() {

    @Autowired private lateinit var renderer: AutoLinkRenderer
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var organizations: OrganizationRepository
    @Autowired private lateinit var repositories: RepositoryService

    private var sequence = 0

    @BeforeEach
    fun useEnglish() {
        LocaleContextHolder.setLocale(Locale.ENGLISH)
    }

    @AfterEach
    fun cleanRepositories() {
        File("build/tmp/it-autolink").deleteRecursively()
        LocaleContextHolder.resetLocaleContext()
    }

    private fun project(owner: String = "al${++sequence}", name: String = "pj$sequence", code: Boolean = true, vcs: String = "GIT"): Project {
        val project = projects.save(Project(owner = owner, name = name, vcs = vcs, projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = project, code = code, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        if (vcs == "GIT") repositories.createRepository(project)
        return project
    }

    private fun issue(project: Project, number: Long, title: String = "버그 수정", state: State = State.OPEN) =
        issues.save(Issue(project = project, number = number, title = title, state = state))

    private fun user(loginId: String, name: String = loginId, english: String? = null) =
        users.save(User(loginId = loginId, name = name, englishName = english, email = "$loginId@example.com", state = UserState.ACTIVE, lang = "ko-KR"))

    private fun render(body: String, project: Project, lang: String? = null) = renderer.render(body, project, lang)

    // --- 이슈 ---

    @Test
    fun `샵 번호를 이슈 링크로 바꾼다`() {
        val project = project()
        issue(project, 3)
        assertEquals(
            "<p><a href=\"/${project.owner}/${project.name}/issue/3\" class=\"issueLink\">#3.버그 수정<span class=\"issue-state open\">Open</span></a></p>",
            render("<p>#3</p>", project).replace("'", "\"")
        )
    }

    @Test
    fun `제목에 달러와 역슬래시가 있어도 오류 없이 그대로 보인다 (v1_6은 appendReplacement 예외, 오류는 v2가 처리)`() {
        val project = project()
        issue(project, 5, title = "Cost \$5 and C:\\dir")
        val rendered = render("<p>#5</p>", project)
        assertTrue(rendered.contains("Cost \$5 and C:\\dir"), rendered)
    }

    @Test
    fun `없는 이슈 번호는 그대로 둔다`() {
        val project = project()
        assertEquals("<p>#99</p>", render("<p>#99</p>", project))
    }

    @Test
    fun `닫힌 이슈는 닫힘 상태를 보여 준다`() {
        val project = project()
        issue(project, 1, state = State.CLOSED)
        assertTrue(render("<p>#1</p>", project).contains("<span class='issue-state closed'>Closed</span>") ||
            render("<p>#1</p>", project).contains("<span class=\"issue-state closed\">Closed</span>"))
    }

    @Test
    fun `다른 프로젝트의 이슈는 소유자 이름 샵 번호로 참조한다 (접두어가 중복되는 v1 6 결함 재현)`() {
        val project = project()
        val other = project("bob${++sequence}", "tools")
        issue(other, 2, "다른 곳")
        val html = render("<p>${other.owner}/tools#2</p>", project)
        // v1.6은 링크 문구에 접두어를 두 번 붙인다 (refactoring-backlog R-15).
        assertTrue(html.contains(">${other.owner}/tools#2.다른 곳${other.owner}/tools#2.다른 곳<"), html)
        assertTrue(html.contains("href=\"/${other.owner}/tools/issue/2\"") || html.contains("href='/${other.owner}/tools/issue/2'"), html)
    }

    @Test
    fun `소유자와 같은 이름의 프로젝트는 이름을 생략할 수 있다`() {
        val project = project()
        val same = project(owner = "carol${++sequence}", name = project.name!!)
        issue(same, 5)
        assertTrue(render("<p>${same.owner}#5</p>", project).contains("/issue/5"))
    }

    @Test
    fun `코드와 링크 안의 참조는 바꾸지 않는다`() {
        val project = project()
        issue(project, 1)
        assertEquals("<p><code>#1</code></p>", render("<p><code>#1</code></p>", project))
        assertEquals("<p><a href=\"/x\">#1</a></p>", render("<p><a href=\"/x\">#1</a></p>", project))
    }

    @Test
    fun `단어 문자에 붙어 있는 참조는 바꾸지 않는다`() {
        val project = project()
        issue(project, 1)
        assertEquals("<p>abc#1 #1x</p>", render("<p>abc#1 #1x</p>", project))
    }

    // --- 커밋 ---

    @Test
    fun `커밋 SHA를 짧은 SHA 링크로 바꾼다`() {
        val project = project()
        GitTestSupport.commitFiles(repositories, project, mapOf("a.txt" to "a".toByteArray()))
        val full = repositories.openRepository(project).use { it.resolve("HEAD").name }
        val html = render("<p>${full.substring(0, 10)}</p>", project)
        assertTrue(html.contains(">${full.substring(0, 7)}</a>"), html)
        assertTrue(html.contains("/${project.owner}/${project.name}/commit/$full"), html)
    }

    @Test
    fun `골뱅이를 붙인 SHA도 링크로 바꾼다`() {
        val project = project()
        GitTestSupport.commitFiles(repositories, project, mapOf("a.txt" to "a".toByteArray()))
        val full = repositories.openRepository(project).use { it.resolve("HEAD").name }
        assertTrue(render("<p>@${full.substring(0, 8)}</p>", project).contains(">${full.substring(0, 7)}</a>"))
    }

    @Test
    fun `없는 SHA는 그대로 두고 코드 메뉴가 꺼져 있으면 바꾸지 않는다`() {
        val project = project()
        assertEquals("<p>abcdef1234</p>", render("<p>abcdef1234</p>", project))
        val noCode = project(code = false)
        GitTestSupport.commitFiles(repositories, noCode, mapOf("a.txt" to "a".toByteArray()))
        val full = repositories.openRepository(noCode).use { it.resolve("HEAD").name }
        assertEquals("<p>${full.substring(0, 9)}</p>", render("<p>${full.substring(0, 9)}</p>", noCode))
    }

    // --- 사용자, 조직, 프로젝트 ---

    @Test
    fun `골뱅이 사용자를 사용자 링크로 바꾼다`() {
        val project = project()
        user("dave", "데이브 (개발팀)")
        val html = render("<p>@dave</p>", project)
        assertTrue(html.contains("href='/dave'") || html.contains("href=\"/dave\""), html)
        assertTrue(html.contains("no-text-decoration user-link"), html)
        assertTrue(html.contains(">@데이브</span>"), html)
        assertTrue(html.contains("data-toggle=\"popover\"") || html.contains("data-toggle='popover'"), html)
    }

    @Test
    fun `영어 사용자에게는 영문 이름을 보여 준다`() {
        val project = project()
        user("erin", "에린", "Erin")
        assertTrue(render("<p>@erin</p>", project, "en-US").contains(">@Erin</span>"))
        assertTrue(render("<p>@erin</p>", project, "ko-KR").contains(">@에린</span>"))
    }

    @Test
    fun `조직 이름은 조직 링크로 바꾼다`() {
        val project = project()
        organizations.save(Organization(name = "yonaorg"))
        val html = render("<p>@yonaorg</p>", project)
        assertTrue(html.contains("/organizations/yonaorg"), html)
        assertTrue(html.contains("<span class=\"org-link\">@yonaorg</span>") || html.contains("<span class='org-link'>@yonaorg</span>"), html)
    }

    @Test
    fun `없는 사용자는 그대로 두고 이메일 주소는 건드리지 않는다`() {
        val project = project()
        assertEquals("<p>@nobody1234 a@b.com</p>", render("<p>@nobody1234 a@b.com</p>", project))
    }

    @Test
    fun `골뱅이 소유자 슬래시 프로젝트는 프로젝트 링크로 바꾼다`() {
        val project = project()
        val other = project("frank${++sequence}", "lib")
        val html = render("<p>@${other.owner}/lib</p>", project)
        assertTrue(html.contains("/${other.owner}/lib"), html)
        assertTrue(html.contains("project-link"), html)
        assertTrue(html.contains("@${other.owner}/lib</span>"), html)
    }

    @Test
    fun `이슈 상태 문구는 현재 언어를 따른다`() {
        val project = project()
        issue(project, 7, state = State.CLOSED)
        LocaleContextHolder.setLocale(Locale.KOREAN)
        assertTrue(render("<p>#7</p>", project).contains("issue-state closed"))
    }
}
