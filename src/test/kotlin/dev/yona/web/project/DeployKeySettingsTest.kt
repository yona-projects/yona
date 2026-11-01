package dev.yona.web.project

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.sshkey.DeployKeyRepository
import dev.yona.domain.sshkey.DeployKeyService
import dev.yona.domain.sshkey.TestSshKey
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.RepositoryService
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.selectFirst
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
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
 * 프로젝트 설정의 배포 키 화면을 검증한다(X-3 3단계, `decisions.md` D-66). 구성은 SSH 키·GPG 키 화면과 같다. 목록 화면(`.../setting/deploykeys`)과 추가 화면(`.../new`) 두 개이고,
 * 목록의 행 동작은 삭제 버튼 하나뿐이다. 프로젝트 관리자만 쓸 수 있다.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-deploy-key"])
class DeployKeySettingsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var service: DeployKeyService
    @Autowired private lateinit var keys: DeployKeyRepository

    private var sequence = 0

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "dks$n", name = "dks$n", email = "dks$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234")) }

    private fun newProject(vcs: String = "GIT"): Project {
        val n = ++sequence
        val project = projects.save(Project(owner = "dko$n", name = "dkp$n", vcs = vcs, projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        if (vcs == "GIT") repositories.createRepository(project)
        return project
    }

    private fun member(project: Project, role: RoleType): User = newUser().also { projectUsers.save(ProjectUser(user = it, project = project, role = roles.findById(role.id).get())) }

    private fun url(project: Project, tail: String = "") = "/${project.owner}/${project.name}/setting/deploykeys$tail"

    private fun page(project: Project, session: MockHttpSession, tail: String = ""): Document =
        Jsoup.parse(mockMvc.perform(get(url(project, tail)).session(session).header("Accept-Language", "ko-KR")).andExpect(status().isOk).andReturn().response.contentAsString)

    private fun add(project: Project, session: MockHttpSession?, name: String, key: String, writable: Boolean = false) =
        post(url(project)).also { r -> session?.let { r.session(it) }; r.param("name", name).param("publicKey", key); if (writable) r.param("writable", "true") }

    private fun manager(project: Project) = login(member(project, RoleType.MANAGER))

    @Test
    fun `프로젝트 관리자가 아니면 쓸 수 없다`() {
        val project = newProject(); val plain = login(member(project, RoleType.MEMBER))
        mockMvc.perform(get(url(project)).session(plain)).andExpect(status().isForbidden)
        mockMvc.perform(get(url(project, "/new")).session(plain)).andExpect(status().isForbidden)
        mockMvc.perform(add(project, plain, "a", TestSshKey.generate().publicLine)).andExpect(status().isForbidden)
        mockMvc.perform(add(project, null, "a", TestSshKey.generate().publicLine)).andExpect(status().is4xxClientError)
        assertEquals(0L, keys.count())
    }

    @Test
    fun `Git이 아닌 프로젝트에는 배포 키 화면이 없다`() {
        val project = newProject("SVN")
        mockMvc.perform(get(url(project)).session(manager(project))).andExpect(status().isBadRequest)
    }

    @Test
    fun `설정 메뉴에 배포 키 탭이 있고 목록 화면에서 활성이다`() {
        val project = newProject(); val s = manager(project)
        val tab = page(project, s).select("ul.nav-tabs a[href$=/setting/deploykeys]").single()
        assertTrue(tab.hasClass("active"))
        assertNotNull(Jsoup.parse(mockMvc.perform(get("/${project.owner}/${project.name}/settingform").session(s)).andReturn().response.contentAsString).selectFirst("ul.nav-tabs a[href$=/setting/deploykeys]"))
    }

    @Test
    fun `키가 없으면 빈 목록 안내와 추가 링크만 보인다`() {
        val project = newProject(); val list = page(project, manager(project))
        assertNull(list.selectFirst("table#deploy-key-list"))
        assertNotNull(list.selectFirst("p.empty"))
        assertEquals(url(project, "/new"), list.selectFirst("a#deploy-key-new")!!.attr("href"))
        assertNull(list.selectFirst("form#deploy-key-create"), "목록 화면에는 추가 폼이 없다")
    }

    @Test
    fun `추가 화면은 이름과 공개키와 쓰기 허용 체크와 취소 링크를 가진다`() {
        val project = newProject(); val form = page(project, manager(project), "/new").selectFirst("form#deploy-key-create")!!
        assertEquals(url(project), form.attr("action"))
        assertEquals("post", form.attr("method"))
        assertNotNull(form.selectFirst("input[name=name]"))
        assertNotNull(form.selectFirst("textarea[name=publicKey]"))
        val writable = form.selectFirst("input[type=checkbox][name=writable]")!!
        assertFalse(writable.hasAttr("checked"), "기본은 읽기 전용이다")
        assertEquals(url(project), form.selectFirst("a.cancel")!!.attr("href"))
    }

    @Test
    fun `키를 추가하면 목록에 이름과 지문과 읽기 전용 여부가 보인다`() {
        val project = newProject(); val s = manager(project)
        val ro = TestSshKey.generate("ci@build"); val rw = TestSshKey.generate()
        mockMvc.perform(add(project, s, "빌드 서버", ro.publicLine)).andExpect(status().is3xxRedirection)
        mockMvc.perform(add(project, s, "배포 서버", rw.publicLine, writable = true)).andExpect(status().is3xxRedirection)
        val rows = page(project, s).select("table#deploy-key-list tbody tr.deploy-key-row")
        assertEquals(listOf("배포 서버", "빌드 서버"), rows.map { it.selectFirst(".deploy-key-name")!!.text() })
        assertEquals(listOf(rw.fingerprint, ro.fingerprint), rows.map { it.selectFirst(".deploy-key-fingerprint")!!.text() })
        assertEquals(listOf(false, true), rows.map { it.hasClass("deploy-key-read-only") })
    }

    @Test
    fun `목록의 행 동작은 삭제 버튼 하나뿐이다`() {
        val project = newProject(); val s = manager(project)
        mockMvc.perform(add(project, s, "a", TestSshKey.generate().publicLine))
        val row = page(project, s).select("tr.deploy-key-row").single()
        assertEquals(1, row.select("button, input[type=submit]").size)
        assertEquals(0, row.select("a").size, "편집이나 상세로 가는 링크가 없다")
        val form = row.selectFirst("form")!!
        assertTrue(form.attr("action").matches(Regex("/dko\\d+/dkp\\d+/setting/deploykeys/\\d+/delete")))
        assertEquals("post", form.attr("method"))
    }

    @Test
    fun `받을 수 없는 키와 같은 키는 400과 이유 메시지를 추가 화면에 보인다`() {
        val project = newProject(); val s = manager(project)
        val doc = Jsoup.parse(mockMvc.perform(add(project, s, "x", "not a key")).andExpect(status().isBadRequest).andReturn().response.contentAsString)
        assertNotNull(doc.selectFirst("form#deploy-key-create"))
        val alert = doc.selectFirst(".alert-danger")!!
        assertTrue(alert.text().isNotBlank() && !alert.text().contains("deployKey.error"), alert.text())
        assertEquals("not a key", doc.selectFirst("textarea[name=publicKey]")!!.text().trim(), "입력한 값을 되돌려 준다")
        val key = TestSshKey.generate().publicLine
        mockMvc.perform(add(project, s, "a", key)).andExpect(status().is3xxRedirection)
        mockMvc.perform(add(project, s, "b", key)).andExpect(status().isBadRequest)
    }

    @Test
    fun `삭제하면 목록에서 사라지고 다른 저장소의 키는 404이다`() {
        val project = newProject(); val other = newProject(); val s = manager(project)
        mockMvc.perform(add(project, s, "a", TestSshKey.generate().publicLine))
        val id = service.list(project).single().id!!
        mockMvc.perform(post(url(other, "/$id/delete")).session(manager(other))).andExpect(status().isNotFound)
        assertEquals(1L, keys.count())
        mockMvc.perform(post(url(project, "/$id/delete")).session(s)).andExpect(status().is3xxRedirection)
        assertEquals(0L, keys.count())
        assertNull(page(project, s).selectFirst("table#deploy-key-list"))
    }

    @Test
    fun `이름에 든 태그는 이스케이프되어 보인다`() {
        val project = newProject(); val s = manager(project)
        mockMvc.perform(add(project, s, "<script>alert(1)</script>", TestSshKey.generate().publicLine))
        val html = mockMvc.perform(get(url(project)).session(s)).andReturn().response.contentAsString
        assertFalse(html.contains("<script>alert(1)</script>"))
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"))
    }
}
