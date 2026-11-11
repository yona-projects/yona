package dev.yona.web.shell

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
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 프로젝트 화면이 새 셸의 사이드바로 그려지는지 검증한다(D-140, D-141). 지금의 가로 메뉴와 같은 이름과 순서의 평평한 목록이고,
 * 설정은 설정 전용 사이드바(맨 위에 프로젝트로 돌아가기)이다. 단축키 등록도 같은 항목에서 파생된다.
 */
@AutoConfigureMockMvc
@Transactional
class ProjectShellTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository

    private var sequence = 0

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "ps$n", name = "ps$n", email = "ps$n@example.com", state = UserState.ACTIVE, createdDate = Date(), passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun project(owner: User, issue: Boolean = true, wiki: Boolean = false): Project {
        val project = projects.save(Project(owner = owner.loginId, name = "shellproj${++sequence}", vcs = "GIT", projectScope = ProjectScope.PUBLIC, createdDate = Date()))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = issue, pullRequest = true, review = true, milestone = true, board = true, wiki = wiki))
        projectUsers.save(ProjectUser(user = owner, project = project, role = roles.findById(RoleType.MANAGER.id).get()))
        return project
    }

    private fun page(path: String, session: MockHttpSession? = null): Document =
        Jsoup.parse(mockMvc.perform(get(path).apply { session?.let { session(it) } }).andExpect(status().isOk).andReturn().response.contentAsString)

    private fun base(p: Project) = "/${p.owner}/${p.name}"

    @Test
    fun `메인 사이드바는 지금의 가로 메뉴와 같은 평평한 목록이고 관리자에게 설정이 있다`() {
        val owner = newUser(); val p = project(owner)
        val nav = page(base(p), login(owner)).selectFirst("nav.shell-sidebar")
        assertNotNull(nav)
        assertEquals(
            listOf("", "/code", "/issues", "/pullRequests", "/reviews", "/milestones", "/posts", "/settingform").map { base(p) + it },
            nav.select("a.nav-link").map { it.attr("href") }
        )
        assertNull(nav.selectFirst(".shell-sidebar-back"))
    }

    @Test
    fun `비로그인은 설정 항목이 없다`() {
        val owner = newUser(); val p = project(owner)
        val hrefs = page(base(p)).select("nav.shell-sidebar a.nav-link").map { it.attr("href") }
        assertTrue(hrefs.none { it.endsWith("/settingform") }, hrefs.toString())
    }

    @Test
    fun `이슈 메뉴를 끄면 항목도 사라진다`() {
        val owner = newUser(); val p = project(owner, issue = false)
        assertTrue(page(base(p), login(owner)).select("nav.shell-sidebar a.nav-link").none { it.attr("href").endsWith("/issues") })
    }

    @Test
    fun `현재 화면의 항목만 활성이다`() {
        val owner = newUser(); val p = project(owner); val s = login(owner)
        listOf("" to "", "/milestones" to "/milestones", "/posts" to "/posts").forEach { (suffix, hrefSuffix) ->
            val active = page(base(p) + suffix, s).select("nav.shell-sidebar a.nav-link.active")
            assertEquals(1, active.size, suffix)
            assertEquals(base(p) + hrefSuffix, active.first()!!.attr("href"))
        }
    }

    @Test
    fun `설정 화면은 설정 전용 사이드바이고 맨 위에 프로젝트로 돌아가는 단추가 있다`() {
        val owner = newUser(); val p = project(owner)
        val doc = page(base(p) + "/members", login(owner))
        val nav = doc.selectFirst("nav.shell-sidebar")!!
        assertEquals(base(p), nav.selectFirst("a.shell-sidebar-back")!!.attr("href"))
        val hrefs = nav.select("a.nav-link").map { it.attr("href") }
        assertTrue(hrefs.containsAll(listOf("/settingform", "/members", "/webhooks", "/issue/labelsform", "/transfer", "/deleteform").map { base(p) + it }), hrefs.toString())
        assertEquals(base(p) + "/members", nav.selectFirst("a.nav-link.active")!!.attr("href"))
        assertTrue(doc.select(".shell-main ul.nav-tabs a[href\$=/settingform]").isEmpty(), "옛 설정 탭 줄이 없어야 한다")
    }

    @Test
    fun `사이드바와 본문 래퍼가 나란히 놓이고 옛 가로 메뉴는 없다`() {
        val owner = newUser(); val p = project(owner)
        val doc = page(base(p) + "/issues", login(owner))
        assertNotNull(doc.selectFirst(".shell > nav.shell-sidebar"))
        assertNotNull(doc.selectFirst(".shell > .shell-main"))
        assertNull(doc.selectFirst(".project-menu-outer"))
    }

    @Test
    fun `단축키 등록은 사이드바와 같은 항목에서 만들어진다`() {
        val owner = newUser(); val p = project(owner, issue = false)
        val script = page(base(p), login(owner)).select("script").joinToString("\n") { it.data() }
        assertTrue("htKeyMap" in script)
        assertTrue("\"H\"" in script && "\"M\"" in script && "\"B\"" in script)
        assertTrue("\"I\"" !in script, "이슈 메뉴를 끄면 I 키도 없어야 한다")
    }

    @Test
    fun `스크립트가 없는 프로젝트 화면만 swap-safe 표지를 갖는다(D-136)`() {
        val owner = newUser(); val s = login(owner); val p = project(owner)
        assertNotNull(page("${base(p)}/watchers", s).selectFirst(".shell-main [data-swap-safe]"))
        assertNotNull(page("${base(p)}/setting/rulesets", s).selectFirst(".shell-main [data-swap-safe]"))
        assertNotNull(page("${base(p)}/setting/deploykeys", s).selectFirst(".shell-main [data-swap-safe]"))
        assertNotNull(page("${base(p)}/setting/lfs", s).selectFirst(".shell-main [data-swap-safe]"))
        assertNull(page(base(p), s).selectFirst("[data-swap-safe]"), "프로젝트 홈은 일반 이동이다")
    }

    @Test
    fun `모듈을 불러오는 설정 화면은 스크립트를 본문 안에 두고 swap-safe 표지를 갖는다(D-136)`() {
        val owner = newUser(); val s = login(owner); val p = project(owner)
        listOf("/members", "/changeVCS", "/deleteform", "/transfer", "/webhooks", "/issue/labelsform", "/reviews").forEach { sub ->
            val doc = page("${base(p)}$sub", s)
            assertNotNull(doc.selectFirst(".shell-main [data-swap-safe]"), sub)
            assertTrue(doc.select(".shell-main script").any { it.data().contains("loadModule") }, "$sub 의 초기화 스크립트는 본문 안에 있어야 교체 때 다시 돈다")
            assertNull(doc.select("body > script, div.shell ~ script").firstOrNull { it.data().contains("loadModule") }, "$sub 본문 밖에 남은 초기화 스크립트")
        }
    }

    @Test
    fun `마일스톤 목록은 전역 단축키를 쓰므로 정리 규약을 등록하고 본문 안에 스크립트를 둔다(D-136, A-475)`() {
        val owner = newUser(); val s = login(owner); val p = project(owner)
        val doc = page("${base(p)}/milestones", s)
        assertNotNull(doc.selectFirst(".shell-main [data-swap-safe]"))
        val script = doc.select(".shell-main script").first { it.data().contains("setKeymapLink") }
        assertTrue(script.data().contains("onPageLeave"), "화면을 떠날 때 N 단축키를 풀어야 한다")
        assertEquals("module", script.attr("type"))
    }

    @Test
    fun `게시판 목록은 select2 조각과 초기화 스크립트를 본문 안에 두고 떠날 때 N 단축키를 푼다(D-136, A-475)`() {
        val owner = newUser(); val s = login(owner); val p = project(owner)
        val doc = page("${base(p)}/posts", s)
        assertNotNull(doc.selectFirst(".shell-main [data-swap-safe]"))
        assertEquals(1, doc.select("script[src*=lib/select2/select2.js]").size, "select2 라이브러리는 한 번만 싣는다")
        assertEquals(1, doc.select("head script[src*=lib/select2/select2.js]").size, "라이브러리는 레이아웃 head에 있어야 본문 교체 때 순서가 깨지지 않는다")
        assertEquals(1, doc.select("head script[type=module][src*=esm/ui/Select2.js]").size, "select2 항목 모양 모듈도 head에서 한 번 싣는다(본문 교체 때 인라인 초기화보다 늦게 실행되지 않게)")
        assertEquals(1, doc.select("script[type=module][src*=esm/ui/Select2.js]").size)
        assertNotNull(doc.selectFirst(".shell-main script#tplSelect2FormatUser"))
        val init = doc.select(".shell-main script").first { it.data().contains("board.List") }
        assertEquals("module", init.attr("type"))
        assertTrue(init.data().contains("onPageLeave"))
    }

    @Test
    fun `이슈 목록은 스크립트와 조각과 라벨 스타일을 본문 안에 두고 떠날 때 N 단축키와 문서 처리기를 푼다(D-136, A-475)`() {
        val owner = newUser(); val s = login(owner); val p = project(owner)
        val doc = page("${base(p)}/issues", s)
        assertNotNull(doc.selectFirst(".shell-main [data-swap-safe]"))
        assertNotNull(doc.selectFirst(".shell-main link[href$=/issue/labels.css]"), "라벨 색 스타일은 본문 안에 있어야 이동 뒤에도 적용된다")
        val init = doc.select(".shell-main script").first { it.data().contains("issue.List") }
        assertEquals("module", init.attr("type"))
        assertTrue(init.data().contains("onPageLeave") && init.data().contains("htmx:afterSwap.issueList"))
    }

    @Test
    fun `코드 주고받기 목록과 위키 화면도 본문 안에 스크립트를 두고 swap-safe 표지를 갖는다(D-136)`() {
        val owner = newUser(); val s = login(owner); val p = project(owner, wiki = true)
        val pr = page("${base(p)}/pullRequests", s)
        assertNotNull(pr.selectFirst(".shell-main [data-swap-safe]"), "pullRequests")
        assertTrue(pr.select(".shell-main script[type=module]").any { it.data().contains("_initTwoColumnMode") }, "초기화 스크립트는 본문 안")
        assertNotNull(page("${base(p)}/wiki", s).selectFirst(".shell-main [data-swap-safe]"), "wiki")
    }

    @Test
    fun `리뷰 목록은 다른 화면처럼 바깥 여백 래퍼 안에 있어 오른쪽이 화면 끝에 붙지 않는다`() {
        val owner = newUser(); val s = login(owner); val p = project(owner)
        assertNotNull(page("${base(p)}/reviews", s).selectFirst(".page-wrap-outer > .shell"))
    }
}
