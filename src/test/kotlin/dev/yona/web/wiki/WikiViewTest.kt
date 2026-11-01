package dev.yona.web.wiki

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
import dev.yona.support.selectFirst
import dev.yona.support.AbstractIntegrationTest
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 프로젝트 위키 화면(로드맵 X-17 W4, `decisions.md` D-60)을 검증한다: 보기, 만들기, 고치기와 이름 바꾸기, 지우기, 이력과 두 리비전 비교. 화면은 GitHub 위키 모양이다
 * (오른쪽 `Pages` 사이드바, `Edit`/`New Page`, 편집 메시지와 `Save Page`, `Revisions`의 두 리비전 비교). 읽기는 프로젝트를 읽을 수 있으면 되고 쓰기는 구성원이다.
 */
@AutoConfigureMockMvc
@Transactional
class WikiViewTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var roles: RoleRepository

    private var sequence = 0
    private val stamp = System.nanoTime()

    private fun user(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "wv$n-$stamp", name = "위키$n", email = "wv$n-$stamp@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234")) }

    private fun project(scope: ProjectScope = ProjectScope.PUBLIC): Project {
        val n = ++sequence
        val p = projects.save(Project(owner = "wvo$n-$stamp", name = "wvp$n", vcs = "GIT", projectScope = scope, lastIssueNumber = 0, lastPostingNumber = 0))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        return p
    }

    private fun member(p: Project): MockHttpSession = login(user().also { projectUsers.save(ProjectUser(user = it, project = p, role = roles.findById(RoleType.MEMBER.id).get())) })

    private fun outsider(): MockHttpSession = login(user())

    private fun base(p: Project) = "/${p.owner}/${p.name}/wiki"

    private fun getPage(session: MockHttpSession, uri: String, vararg vars: Any): ResultActions = mockMvc.perform(get(uri, *vars).session(session))

    private fun doc(result: ResultActions): Document = Jsoup.parse(result.andReturn().response.contentAsString)

    private fun postForm(session: MockHttpSession, uri: String, vararg vars: Any, params: Map<String, String>): ResultActions =
        mockMvc.perform(post(uri, *vars).session(session).apply { params.forEach { (k, v) -> param(k, v) } })

    private fun create(session: MockHttpSession, p: Project, title: String, content: String, message: String = "") =
        postForm(session, base(p), params = mapOf("title" to title, "content" to content, "message" to message))

    // --- 비어 있는 위키와 메뉴 ---

    @Test
    fun `비어 있는 위키는 안내를 보이고 구성원에게만 첫 페이지 만들기를 보인다`() {
        val p = project(); val writer = member(p); val reader = outsider()
        val asWriter = doc(getPage(writer, base(p)).andExpect(status().isOk))
        assertNotNull(asWriter.selectFirst("a.wiki-create-first[href^=${base(p)}/_new]"))
        val asReader = doc(getPage(reader, base(p)).andExpect(status().isOk))
        assertNull(asReader.selectFirst("a.wiki-create-first"))
        assertTrue(asReader.select(".wiki-empty").isNotEmpty())
    }

    @Test
    fun `프로젝트 메뉴에 위키 탭이 있다`() {
        val p = project()
        val menu = doc(getPage(outsider(), "/${p.owner}/${p.name}").andExpect(status().isOk))
        assertNotNull(menu.selectFirst(".project-menu-nav a[href=${base(p)}]"))
    }

    // --- 만들기와 보기 ---

    @Test
    fun `페이지를 만들면 그 페이지로 이동하고 마크다운이 렌더링된다`() {
        val p = project(); val writer = member(p)
        create(writer, p, "Home", "# 안녕하세요\n\n본문 **굵게**", "첫 글").andExpect(status().isSeeOther).andExpect { assertEquals("${base(p)}/Home", it.response.getHeader("Location")) }
        val page = doc(getPage(writer, "${base(p)}/Home").andExpect(status().isOk))
        assertEquals("안녕하세요", page.selectFirst(".wiki-body h1")?.text()?.removeSuffix("#"))
        assertEquals("굵게", page.selectFirst(".wiki-body strong")?.text())
        assertEquals("Home", page.selectFirst(".wiki-page-title")?.text())
        assertTrue(page.selectFirst(".wiki-last-edited")!!.text().contains("위키"))
    }

    @Test
    fun `홈 페이지가 있으면 위키 첫 화면이 그 페이지이다`() {
        val p = project(); val writer = member(p)
        create(writer, p, "Home", "홈 본문")
        val home = doc(getPage(writer, base(p)).andExpect(status().isOk))
        assertEquals("Home", home.selectFirst(".wiki-page-title")?.text())
        assertTrue(home.selectFirst(".wiki-body")!!.text().contains("홈 본문"))
    }

    @Test
    fun `한글과 공백 제목은 하이픈 이름의 주소로 이동한다`() {
        val p = project(); val writer = member(p)
        val location = create(writer, p, "시작 하기", "내용").andExpect(status().isSeeOther).andReturn().response.getHeader("Location")
        assertEquals("${base(p)}/%EC%8B%9C%EC%9E%91-%ED%95%98%EA%B8%B0", location)
        val page = doc(getPage(writer, "${base(p)}/{name}", "시작-하기").andExpect(status().isOk))
        assertEquals("시작 하기", page.selectFirst(".wiki-page-title")?.text())
    }

    @Test
    fun `없는 페이지는 404이다`() {
        val p = project()
        getPage(member(p), "${base(p)}/Nope").andExpect(status().isNotFound)
    }

    @Test
    fun `쓰기 단추는 구성원에게만 보이고 이력 단추는 모두에게 보인다`() {
        val p = project(); val writer = member(p); val reader = outsider()
        create(writer, p, "Home", "x")
        val asWriter = doc(getPage(writer, "${base(p)}/Home"))
        assertNotNull(asWriter.selectFirst("a.wiki-edit[href=${base(p)}/Home/_edit]"))
        assertNotNull(asWriter.selectFirst("a.wiki-new[href=${base(p)}/_new]"))
        assertNotNull(asWriter.selectFirst("a.wiki-history[href=${base(p)}/Home/_history]"))
        val asReader = doc(getPage(reader, "${base(p)}/Home"))
        assertNull(asReader.selectFirst("a.wiki-edit"))
        assertNull(asReader.selectFirst("a.wiki-new"))
        assertNotNull(asReader.selectFirst("a.wiki-history"))
    }

    // --- 사이드바와 푸터 ---

    @Test
    fun `사이드바에 페이지 목록이 제목순으로 나오고 현재 페이지가 강조되며 특수 페이지는 따로 보인다`() {
        val p = project(); val writer = member(p)
        listOf("banana", "Apple").forEach { create(writer, p, it, "x") }
        create(writer, p, "_Sidebar", "**사이드 메뉴**")
        create(writer, p, "_Footer", "푸터 글")
        val page = doc(getPage(writer, "${base(p)}/Apple"))
        assertEquals(listOf("Apple", "banana"), page.select(".wiki-pages-list a").map { it.text() })
        assertEquals("Apple", page.selectFirst(".wiki-pages-list a.active")?.text())
        assertNotNull(page.selectFirst(".wiki-pages-filter"))
        assertEquals("사이드 메뉴", page.selectFirst(".wiki-custom-sidebar strong")?.text())
        assertTrue(page.selectFirst(".wiki-custom-footer")!!.text().contains("푸터 글"))
    }

    // --- 만들기 화면과 오류 ---

    @Test
    fun `새 페이지 화면은 구성원에게만 열린다`() {
        val p = project()
        val form = doc(getPage(member(p), "${base(p)}/_new").andExpect(status().isOk))
        assertNotNull(form.selectFirst("form[action=${base(p)}] input[name=title]"))
        assertNotNull(form.selectFirst("form textarea[name=content]"))
        assertNotNull(form.selectFirst("form input[name=message]"))
        assertNotNull(form.selectFirst("form button.wiki-save"))
        getPage(outsider(), "${base(p)}/_new").andExpect(status().isForbidden)
    }

    @Test
    fun `잘못된 제목과 중복 제목은 폼을 다시 보여 주고 입력을 보존한다`() {
        val p = project(); val writer = member(p)
        create(writer, p, "A", "1")
        val invalid = doc(create(writer, p, "a/b", "내용 보존").andExpect(status().isBadRequest))
        assertNotNull(invalid.selectFirst(".wiki-error"))
        assertEquals("a/b", invalid.selectFirst("input[name=title]")?.attr("value"))
        assertTrue(invalid.selectFirst("textarea[name=content]")!!.text().contains("내용 보존"))
        val duplicate = doc(create(writer, p, "A", "2").andExpect(status().isConflict))
        assertNotNull(duplicate.selectFirst(".wiki-error"))
    }

    @Test
    fun `구성원이 아니면 만들 수 없다`() {
        val p = project()
        create(outsider(), p, "A", "1").andExpect(status().isForbidden)
    }

    // --- 고치기와 이름 바꾸기와 지우기 ---

    @Test
    fun `고치기 화면은 현재 내용을 채우고 저장하면 반영된다`() {
        val p = project(); val writer = member(p)
        create(writer, p, "Home", "v1")
        val form = doc(getPage(writer, "${base(p)}/Home/_edit").andExpect(status().isOk))
        assertEquals("Home", form.selectFirst("input[name=title]")?.attr("value")?.replace(" ", "-"))
        assertTrue(form.selectFirst("textarea[name=content]")!!.text().contains("v1"))
        assertNotNull(form.selectFirst("form[action=${base(p)}/Home/_edit]"))
        assertNotNull(form.selectFirst("form[action=${base(p)}/Home/_delete]"))
        postForm(writer, "${base(p)}/Home/_edit", params = mapOf("title" to "Home", "content" to "v2", "message" to "고침")).andExpect(status().isSeeOther)
        assertTrue(doc(getPage(writer, "${base(p)}/Home")).selectFirst(".wiki-body")!!.text().contains("v2"))
    }

    @Test
    fun `제목을 바꾸면 새 이름의 주소로 이동한다`() {
        val p = project(); val writer = member(p)
        create(writer, p, "Old", "body")
        val location = postForm(writer, "${base(p)}/Old/_edit", params = mapOf("title" to "New Name", "content" to "body", "message" to "")).andExpect(status().isSeeOther).andReturn().response.getHeader("Location")
        assertEquals("${base(p)}/New-Name", location)
        getPage(writer, "${base(p)}/Old").andExpect(status().isNotFound)
        getPage(writer, "${base(p)}/New-Name").andExpect(status().isOk)
    }

    @Test
    fun `이미 있는 이름으로 바꾸면 고치기 폼을 다시 보여 준다`() {
        val p = project(); val writer = member(p)
        create(writer, p, "A", "1"); create(writer, p, "B", "2")
        val form = doc(postForm(writer, "${base(p)}/A/_edit", params = mapOf("title" to "B", "content" to "1", "message" to "")).andExpect(status().isConflict))
        assertNotNull(form.selectFirst(".wiki-error"))
    }

    @Test
    fun `구성원이 아니면 고치거나 지울 수 없다`() {
        val p = project(); val writer = member(p); val reader = outsider()
        create(writer, p, "Home", "x")
        getPage(reader, "${base(p)}/Home/_edit").andExpect(status().isForbidden)
        postForm(reader, "${base(p)}/Home/_edit", params = mapOf("title" to "Home", "content" to "y", "message" to "")).andExpect(status().isForbidden)
        postForm(reader, "${base(p)}/Home/_delete", params = emptyMap()).andExpect(status().isForbidden)
    }

    @Test
    fun `지우면 위키 첫 화면으로 돌아가고 페이지는 사라진다`() {
        val p = project(); val writer = member(p)
        create(writer, p, "A", "1"); create(writer, p, "B", "2")
        val location = postForm(writer, "${base(p)}/A/_delete", params = mapOf("message" to "정리")).andExpect(status().isSeeOther).andReturn().response.getHeader("Location")
        assertEquals(base(p), location)
        getPage(writer, "${base(p)}/A").andExpect(status().isNotFound)
        assertEquals(listOf("B"), doc(getPage(writer, "${base(p)}/B")).select(".wiki-pages-list a").map { it.text() })
    }

    // --- 이력과 비교 ---

    @Test
    fun `이력은 최신순이고 두 리비전을 골라 비교한다`() {
        val p = project(); val writer = member(p)
        create(writer, p, "Home", "a\n", "첫 글")
        postForm(writer, "${base(p)}/Home/_edit", params = mapOf("title" to "Home", "content" to "b\n", "message" to "둘째"))
        postForm(writer, "${base(p)}/Home/_edit", params = mapOf("title" to "Home", "content" to "c\n", "message" to "셋째"))
        val history = doc(getPage(outsider(), "${base(p)}/Home/_history").andExpect(status().isOk))
        assertEquals(listOf("셋째", "둘째", "첫 글"), history.select("tr.wiki-revision .wiki-revision-message").map { it.text() })
        val ids = history.select("tr.wiki-revision input[name=from]").map { it.attr("value") }
        assertEquals(3, ids.size)
        assertNotNull(history.selectFirst("form.wiki-compare-form[action=${base(p)}/Home/_compare] button"))
        val compare = doc(getPage(outsider(), "${base(p)}/Home/_compare?from=${ids[2]}&to=${ids[0]}").andExpect(status().isOk))
        assertTrue(compare.select(".wiki-diff .diff-add").any { it.text().contains("c") })
        assertTrue(compare.select(".wiki-diff .diff-del").any { it.text().contains("a") })
        val single = doc(getPage(outsider(), "${base(p)}/Home/_compare?commit=${ids[1]}").andExpect(status().isOk))
        assertTrue(single.select(".wiki-diff .diff-add").any { it.text().contains("b") })
    }

    @Test
    fun `비교에 필요한 값이 없거나 리비전이 틀리면 오류이다`() {
        val p = project(); val writer = member(p)
        create(writer, p, "Home", "a")
        getPage(writer, "${base(p)}/Home/_compare").andExpect(status().isBadRequest)
        getPage(writer, "${base(p)}/Home/_compare?commit=${"0".repeat(40)}").andExpect(status().isNotFound)
    }

    // --- 접근 ---

    @Test
    fun `비공개 프로젝트의 위키는 구성원만 본다`() {
        val p = project(ProjectScope.PRIVATE); val writer = member(p)
        create(writer, p, "Home", "x")
        getPage(writer, base(p)).andExpect(status().isOk)
        assertFalse(getPage(outsider(), base(p)).andReturn().response.status == 200)
        assertFalse(getPage(outsider(), "${base(p)}/Home").andReturn().response.status == 200)
    }

    @Test
    fun `이름이 점 wiki 인 프로젝트와 겹치는 위키는 쓸 수 없다고 알린다`() {
        val p = project(); val writer = member(p)
        projects.save(Project(owner = p.owner, name = "${p.name}.wiki", vcs = "GIT", projectScope = ProjectScope.PUBLIC, lastIssueNumber = 0, lastPostingNumber = 0))
        create(writer, p, "Home", "x").andExpect(status().isConflict)
    }
}
