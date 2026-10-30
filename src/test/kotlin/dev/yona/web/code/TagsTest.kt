package dev.yona.web.code

import dev.yona.support.selectFirst
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
import dev.yona.repository.RepositoryService
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.GitTestSupport
import dev.yona.support.GitTestSupport.TestCommit
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Git 태그 목록과 삭제를 검증한다. v1.6에 없는 전용 화면이다(로드맵 X-18, `decisions.md` D-30).
 * 브랜치 화면과 선택 목록에서 태그를 다루는 방식은 [BranchesTest], [CodeBrowserWebTest]가 검증한다.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-code-browser"])
class TagsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var repositories: RepositoryService

    private var sequence = 0

    private fun user(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "tg$n", name = "tg$n", email = "tg$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234")) }

    private fun member(p: Project, role: RoleType = RoleType.MANAGER): User = user().also { projectUsers.save(ProjectUser(user = it, project = p, role = roles.findById(role.id).get())) }

    private fun project(vcs: String = "GIT"): Project {
        val n = ++sequence
        val p = projects.save(Project(owner = "tgo$n", name = "tgp$n", vcs = vcs, projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        if (vcs == "GIT") repositories.createRepository(p)
        return p
    }

    /** 경량 태그 `light-0.1`과 `release/1.0`, 주석 태그 `v1.0`(9월)과 `v2.0`(10월), 커밋이 아닌 대상을 가리키는 `tree-tag`가 있는 프로젝트이다. */
    private fun tagged(): Project = project().also {
        GitTestSupport.pushCommits(repositories, it, TestCommit("첫 커밋", mapOf("a.txt" to "a".toByteArray())))
        GitTestSupport.createRef(repositories, it, "refs/tags/light-0.1")
        GitTestSupport.createRef(repositories, it, "refs/tags/release/1.0")
        GitTestSupport.createRef(repositories, it, "refs/tags/tree-tag", toTree = true)
        GitTestSupport.createAnnotatedTag(repositories, it, "v1.0", "첫 릴리스\n\n자세한 설명입니다", "릴리서", 1_788_000_000_000L)
        GitTestSupport.createAnnotatedTag(repositories, it, "v2.0", "두 번째", "릴리서", 1_790_000_000_000L)
    }

    private fun page(path: String, session: MockHttpSession? = null): Document =
        Jsoup.parse(mockMvc.perform(get(path).apply { session?.let { session(it) } }).andExpect(status().isOk).andReturn().response.contentAsString)

    private fun rows(p: Project, session: MockHttpSession? = null) = page("/${p.owner}/${p.name}/tags", session).select("table.tag-list-wrap tbody tr")

    @Test
    fun `태그는 태그 날짜가 최근인 순서이고 커밋이 아닌 대상을 가리키는 태그는 맨 뒤에 있어도 500이 아니다`() {
        val p = tagged()
        // 주석 태그는 태그 단 시각, 경량 태그는 커밋 시각이 기준이다. 같으면 이름순이다
        assertEquals(listOf("v2.0", "v1.0", "light-0.1", "release/1.0", "tree-tag"), rows(p).map { it.selectFirst("td.tagName .tag-name")!!.text() })
    }

    @Test
    fun `주석 태그는 메시지와 태그를 단 사람을, 경량 태그는 종류만 보여 주고 커밋이 아닌 태그는 그렇게 표시한다`() {
        val p = tagged()
        val byName = rows(p).associateBy { it.selectFirst("td.tagName .tag-name")!!.text() }
        val annotated = byName.getValue("v1.0")
        assertNotNull(annotated.selectFirst(".tag-type.annotated"))
        assertEquals("첫 릴리스", annotated.selectFirst(".tag-message")!!.text())
        assertTrue(annotated.selectFirst(".tag-message")!!.attr("title").contains("자세한 설명입니다"))
        assertTrue(annotated.selectFirst(".tagger")!!.text().contains("릴리서"))
        val light = byName.getValue("light-0.1")
        assertNotNull(light.selectFirst(".tag-type.lightweight")); assertNull(light.selectFirst(".tag-message")); assertNull(light.selectFirst(".tagger"))
        assertEquals(7, light.selectFirst("td.commit a.commitId")!!.text().length)
        assertTrue(light.selectFirst("td.commit a.commitId")!!.attr("href").startsWith("/${p.owner}/${p.name}/commit/"))
        val notCommit = byName.getValue("tree-tag")
        assertNotNull(notCommit.selectFirst(".not-commit")); assertNull(notCommit.selectFirst("td.commit a.commitId"))
        // 열면 404가 되는 링크는 걸지 않는다. 이름은 글자로만 보인다
        assertEquals("span", notCommit.selectFirst("td.tagName .tag-name")!!.tagName()); assertNull(notCommit.selectFirst("td.tagName a"))
        assertEquals("a", light.selectFirst("td.tagName .tag-name")!!.tagName())
    }

    @Test
    fun `태그 이름에 슬래시가 있어도 두 번 인코딩한 주소로 코드를 연다`() {
        val p = tagged()
        val link = rows(p).first { it.selectFirst("td.tagName .tag-name")!!.text() == "release/1.0" }.selectFirst("td.tagName .tag-name")!!
        assertEquals("/${p.owner}/${p.name}/code/refs%252Ftags%252Frelease%252F1.0", link.attr("href"))
        // MockMvc는 실서버(Tomcat)와 달리 퍼센트 인코딩을 한 번 풀어 주지 않으므로, 서버가 한 번 푼 뒤의 형태(`%2F`)로 요청한다
        mockMvc.perform(get("/${p.owner}/${p.name}/code/refs%2Ftags%2Frelease%2F1.0")).andExpect(status().isOk)
    }

    @Test
    fun `탭은 파일, 커밋, 브랜치, 태그이고 태그가 활성이다`() {
        val p = tagged()
        val tabs = page("/${p.owner}/${p.name}/tags").select(".code-browse-wrap ul.nav-tabs li")
        assertEquals(listOf("Files", "Commit", "Branches", "Tags"), tabs.map { it.text() }); assertTrue(tabs[3].selectFirst("a")!!.hasClass("active"))
    }

    @Test
    fun `태그가 없으면 없다고 안내한다`() {
        val p = project().also { GitTestSupport.pushCommits(repositories, it, TestCommit("첫 커밋", mapOf("a.txt" to "a".toByteArray()))) }
        val doc = page("/${p.owner}/${p.name}/tags")
        assertEquals(0, doc.select("table.tag-list-wrap tbody tr").size); assertNotNull(doc.selectFirst(".no-tags"))
    }

    @Test
    fun `삭제 단추는 매니저에게만 나오고 삭제 주소는 슬래시를 두 번 인코딩한다`() {
        val p = tagged(); val manager = member(p); val plain = member(p, RoleType.MEMBER)
        assertNull(page("/${p.owner}/${p.name}/tags").selectFirst("a[data-request-method=delete]"))
        assertNull(page("/${p.owner}/${p.name}/tags", login(plain)).selectFirst("a[data-request-method=delete]"))
        val buttons = page("/${p.owner}/${p.name}/tags", login(manager)).select("a[data-request-method=delete]")
        assertEquals(5, buttons.size)
        assertTrue(buttons.any { it.attr("href") == "/${p.owner}/${p.name}/tags/release%252F1.0" })
        assertTrue(buttons.all { it.hasAttr("data-confirm") })
    }

    @Test
    fun `태그를 지우면 태그 목록으로 돌아가고 슬래시가 든 이름도 지운다`() {
        val p = tagged(); val manager = member(p)
        mockMvc.perform(delete("/${p.owner}/${p.name}/tags/v1.0").session(login(manager))).andExpect(status().isSeeOther).andExpect(redirectedUrl("/${p.owner}/${p.name}/tags"))
        mockMvc.perform(delete("/${p.owner}/${p.name}/tags/release%2F1.0").session(login(manager))).andExpect(status().isSeeOther)
        assertTrue("refs/tags/v1.0" !in repositories.refNames(p)); assertTrue("refs/tags/release/1.0" !in repositories.refNames(p))
        assertTrue("refs/tags/v2.0" in repositories.refNames(p))
    }

    @Test
    fun `없는 태그나 태그가 아닌 이름은 404이고 브랜치는 지워지지 않는다`() {
        val p = tagged(); val manager = member(p)
        mockMvc.perform(delete("/${p.owner}/${p.name}/tags/nope").session(login(manager))).andExpect(status().isNotFound)
        mockMvc.perform(delete("/${p.owner}/${p.name}/tags/master").session(login(manager))).andExpect(status().isNotFound)
        mockMvc.perform(delete("/${p.owner}/${p.name}/tags/refs%2Fheads%2Fmaster").session(login(manager))).andExpect(status().isNotFound)
        assertTrue("refs/heads/master" in repositories.refNames(p))
    }

    @Test
    fun `일반 구성원과 익명은 지울 수 없다`() {
        val p = tagged(); val plain = member(p, RoleType.MEMBER)
        mockMvc.perform(delete("/${p.owner}/${p.name}/tags/v1.0").session(login(plain))).andExpect(status().isForbidden)
        // 익명도 403이다(v1.6 브랜치 삭제와 같다)
        mockMvc.perform(delete("/${p.owner}/${p.name}/tags/v1.0")).andExpect(status().isForbidden)
        assertTrue("refs/tags/v1.0" in repositories.refNames(p))
    }

    @Test
    fun `Subversion 프로젝트는 400이다`() {
        val p = project(vcs = "SUBVERSION")
        mockMvc.perform(get("/${p.owner}/${p.name}/tags")).andExpect(status().isBadRequest)
    }
}
