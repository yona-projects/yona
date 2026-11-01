package dev.yona.web.project

import dev.yona.support.selectFirst
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.misc.WatchRepository
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationRepository
import dev.yona.domain.project.OrganizationUser
import dev.yona.domain.project.OrganizationUserRepository
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.project.RecentProjectRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 프로젝트 생성 폼(`GET /projectform`)과 생성(`POST /projects`)을 검증한다.
 *
 * 원본: `app/controllers/ProjectApp.java:152-292`, `app/views/project/create.scala.html`, `app/models/Project.java:142-148`,
 * `app/models/RecentProject.java`.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-project-create"])
class ProjectCreateTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var organizations: OrganizationRepository
    @Autowired private lateinit var organizationUsers: OrganizationUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var watches: WatchRepository
    @Autowired private lateinit var recentProjects: RecentProjectRepository

    private var sequence = 0

    @AfterEach
    fun cleanRepositories() {
        File("build/tmp/it-project-create").deleteRecursively()
    }

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(
            User(
                loginId = "pc$n", name = "pc$n", email = "pc$n@example.com", state = UserState.ACTIVE,
                passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)
            )
        )
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun newOrganization(admin: User? = null, member: User? = null): Organization {
        val org = organizations.save(Organization(name = "org${++sequence}"))
        admin?.let { organizationUsers.save(OrganizationUser(user = it, organization = org, role = roles.findById(RoleType.ORG_ADMIN.id).get())) }
        member?.let { organizationUsers.save(OrganizationUser(user = it, organization = org, role = roles.findById(RoleType.ORG_MEMBER.id).get())) }
        return org
    }

    private fun doc(result: MvcResult): Document = Jsoup.parse(result.response.contentAsString)

    private fun submit(
        session: MockHttpSession?, owner: String, name: String, vararg extra: Pair<String, String>
    ): MvcResult {
        val request: MockHttpServletRequestBuilder = post("/projects").param("owner", owner).param("name", name)
        session?.let { request.session(it) }
        extra.forEach { (k, v) -> request.param(k, v) }
        return mockMvc.perform(request).andReturn()
    }

    // --- 폼 ---

    @Test
    fun `폼은 로그인해야 열 수 있고 안내를 남긴다`() {
        mockMvc.perform(get("/projectform"))
            .andExpect(status().isSeeOther)
            .andExpect(header().string("Location", "/users/loginform?redirectUrl=/projectform"))
            .andExpect(flash().attribute("warning", "user.login.alert"))
    }

    @Test
    fun `폼에는 제목과 소유자 이름 설명 입력란이 있다`() {
        val user = newUser()
        val page = doc(mockMvc.perform(get("/projectform").session(login(user))).andExpect(status().isOk).andReturn())
        assertEquals("Create new project", page.title())
        val form = page.selectFirst("form#newProjectForm")!!
        assertEquals("/projects", form.attr("action"))
        assertEquals("post", form.attr("method").lowercase())
        assertEquals("Create new project", form.selectFirst("legend")?.ownText()?.trim())
        assertEquals("/_import?owner=", form.selectFirst("legend a.ybtn")?.attr("href"))
        assertNotNull(form.selectFirst("input#project-name[name=name][maxlength=250]"))
        assertNotNull(form.selectFirst("textarea#description[name=overview]"))
        assertEquals("Enter project name in alphabetnumerical or symbol characters(_-.)", form.selectFirst("#project-name")?.attr("placeholder"))
    }

    @Test
    fun `소유자 선택지는 본인과 관리하는 조직이다`() {
        val user = newUser()
        val adminOrg = newOrganization(admin = user)
        newOrganization(member = user) // 일반 구성원인 조직은 나오지 않는다
        val options = doc(mockMvc.perform(get("/projectform").session(login(user))).andReturn()).select("select#project-owner option")
        assertEquals(listOf(user.loginId, adminOrg.name), options.map { it.attr("value") })
        assertEquals(listOf("user", "group"), options.map { it.attr("data-type") })
    }

    @Test
    fun `owner 파라미터로 소유자를 미리 고른다`() {
        val user = newUser()
        val org = newOrganization(admin = user)
        val page = doc(mockMvc.perform(get("/projectform?owner=${org.name}").session(login(user))).andReturn())
        assertTrue(page.selectFirst("select#project-owner option[value=${org.name}]")!!.hasAttr("selected"))
        assertEquals("/_import?owner=${org.name}", page.selectFirst("legend a.ybtn")?.attr("href"))
        assertNull(page.selectFirst("#opt-protected")?.attr("style")?.takeIf { it.contains("display:none") }, "조직이면 보호 옵션이 보인다")
    }

    @Test
    fun `공개 범위 라디오는 기본이 공개이고 보호는 조직일 때만 보인다`() {
        val user = newUser()
        val page = doc(mockMvc.perform(get("/projectform").session(login(user))).andReturn())
        assertTrue(page.selectFirst("input#public")!!.hasAttr("checked"))
        assertFalse(page.selectFirst("input#private")!!.hasAttr("checked"))
        assertTrue(page.selectFirst("#opt-protected")!!.attr("style").contains("display:none"))
    }

    @Test
    fun `저장소 종류와 메뉴 설정의 기본값이 채워진다`() {
        val user = newUser()
        val page = doc(mockMvc.perform(get("/projectform").session(login(user))).andReturn())
        val vcs = page.select("select#vcs option")
        assertEquals(setOf("GIT", "Subversion"), vcs.map { it.attr("value") }.toSet())
        assertEquals("Git", vcs.first { it.attr("value") == "GIT" }.text().trim())
        assertTrue(vcs.first { it.attr("value") == "GIT" }.hasAttr("selected"))
        assertEquals("display: none;", page.selectFirst("#svn")?.attr("style"))
        val menus = page.select(".advanced-options input[type=checkbox]")
        assertEquals(listOf("code", "issue", "pullRequest", "review", "milestone", "board", "wiki"), menus.map { it.attr("name") })
        assertTrue(menus.all { it.hasAttr("checked") && it.attr("value") == "true" })
    }

    @Test
    fun `폼 스크립트는 프로젝트 생성 모듈에 오류를 넘긴다`() {
        val user = newUser()
        val html = mockMvc.perform(get("/projectform").session(login(user))).andReturn().response.contentAsString
        assertTrue(html.contains("project.New") && html.contains("#newProjectForm"))
        assertTrue(Regex("""htError"\s*:\s*\{\s*\}""").containsMatchIn(html), "오류가 없으면 빈 객체")
    }

    // --- 생성 ---

    @Test
    fun `프로젝트를 만들면 홈으로 이동하고 모든 것이 갖춰진다`() {
        val user = newUser()
        val result = submit(
            login(user), user.loginId!!, "my.project", "overview" to "설명", "projectScope" to "PRIVATE", "vcs" to "GIT",
            "code" to "true", "issue" to "true", "board" to "true"
        )
        assertEquals(303, result.response.status)
        assertEquals("/${user.loginId}/my.project", result.response.getHeader("Location"))

        val project = projects.findByOwnerIgnoreCaseAndNameIgnoreCase(user.loginId!!, "my.project")!!
        assertEquals("설명", project.overview)
        assertEquals(ProjectScope.PRIVATE, project.projectScope)
        assertEquals("GIT", project.vcs)
        assertEquals("http://localhost:9000/my.project", project.siteurl)
        assertNotNull(project.createdDate)
        assertNull(project.organization)

        // 만든 사람은 매니저, 사이트 관리자는 SITEMANAGER 역할로 모든 프로젝트에 들어 있다.
        assertTrue(projectUsers.existsByUserIdAndProjectIdAndRoleId(user.id!!, project.id!!, RoleType.MANAGER.id))
        assertTrue(projectUsers.existsByUserIdAndProjectIdAndRoleId(1L, project.id!!, RoleType.SITEMANAGER.id))

        // 메뉴 설정: 체크한 항목만 켜진다.
        val menu = menuSettings.findFirstByProjectId(project.id!!)!!
        assertEquals(listOf(true, true, false, false, false, true),
            listOf(menu.code, menu.issue, menu.pullRequest, menu.review, menu.milestone, menu.board))

        // 저장소와 구독
        assertTrue(File("build/tmp/it-project-create/repo/git/${user.loginId}/my.project.git/HEAD").isFile)
        assertTrue(watches.existsByUserIdAndResourceTypeAndResourceId(user.id!!, ResourceType.PROJECT, project.id.toString()))
    }

    @Test
    fun `Subversion 프로젝트를 만들면 Subversion 저장소를 만든다`() {
        val user = newUser()
        val result = submit(login(user), user.loginId!!, "svnproj", "projectScope" to "PUBLIC", "vcs" to "Subversion", "code" to "true")
        assertEquals(303, result.response.status)
        val project = projects.findByOwnerIgnoreCaseAndNameIgnoreCase(user.loginId!!, "svnproj")!!
        assertEquals("Subversion", project.vcs)
        assertTrue(File("build/tmp/it-project-create/repo/svn/${user.loginId}/svnproj/format").isFile)
        assertTrue(!File("build/tmp/it-project-create/repo/git/${user.loginId}/svnproj.git").exists())
    }

    @Test
    fun `조직 관리자는 조직 소유 프로젝트를 만들 수 있다`() {
        val user = newUser()
        val org = newOrganization(admin = user)
        val result = submit(login(user), org.name!!, "org-proj", "projectScope" to "PROTECTED", "vcs" to "GIT")
        assertEquals(303, result.response.status)
        val project = projects.findByOwnerIgnoreCaseAndNameIgnoreCase(org.name!!, "org-proj")!!
        assertEquals(org.id, project.organization?.id)
        assertEquals(ProjectScope.PROTECTED, project.projectScope)
    }

    @Test
    fun `조직의 관리자가 아니면 403이다`() {
        val user = newUser()
        val org = newOrganization(member = user)
        val result = submit(login(user), org.name!!, "denied-proj")
        assertEquals(403, result.response.status)
        assertEquals("'${user.name}' has no permission", doc(result).selectFirst(".error-wrap p")?.text())
        assertNull(projects.findByOwnerIgnoreCaseAndNameIgnoreCase(org.name!!, "denied-proj"))
    }

    @Test
    fun `로그인하지 않으면 403이다`() {
        val result = submit(null, "someone", "anon-proj")
        assertEquals(403, result.response.status)
        // v1.6 정적 anonymous의 이름은 서버 JVM 기본 언어로 굳는다
        assertEquals("'${java.util.ResourceBundle.getBundle("messages", java.util.Locale.getDefault()).getString("user.notExists.name")}' has no permission", doc(result).selectFirst(".error-wrap p")?.text())
    }

    @Test
    fun `최근 방문 프로젝트에 기록된다`() {
        val user = newUser()
        submit(login(user), user.loginId!!, "visited-proj", "vcs" to "GIT")
        val project = projects.findByOwnerIgnoreCaseAndNameIgnoreCase(user.loginId!!, "visited-proj")!!
        // 방문 기록은 비동기로 남는다.
        val deadline = System.currentTimeMillis() + 5000
        while (recentProjects.findByUserIdOrderByIdDesc(user.id!!).none { it.project?.id == project.id } && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }
        assertEquals(project.id, recentProjects.findByUserIdOrderByIdDesc(user.id!!).first().project?.id)
    }

    // --- 검증 ---

    private fun errors(result: MvcResult): String =
        Regex("""htError"\s*:\s*(\{.*?\})\s*[,}]""", RegexOption.DOT_MATCHES_ALL).find(result.response.contentAsString)?.groupValues?.get(1).orEmpty()

    @Test
    fun `없는 소유자는 400과 오류를 돌려준다`() {
        val user = newUser()
        val result = submit(login(user), "no-such-owner", "valid-name")
        assertEquals(400, result.response.status)
        assertTrue(errors(result).contains("\"owner\":[\"Owner information is not valid.\"]"), errors(result))
    }

    @Test
    fun `다른 사용자를 소유자로 지정하면 거부한다`() {
        val user = newUser()
        val other = newUser()
        val result = submit(login(user), other.loginId!!, "valid-name")
        assertEquals(400, result.response.status)
        assertTrue(errors(result).contains("Owner information is not valid."))
    }

    @Test
    fun `같은 소유자의 같은 이름은 대소문자를 가리지 않고 거부한다`() {
        val user = newUser()
        val session = login(user)
        submit(session, user.loginId!!, "Dup-Name")
        val result = submit(session, user.loginId!!, "dup-name")
        assertEquals(400, result.response.status)
        assertTrue(errors(result).contains("\"name\":[\"This project name already exists.\"]"), errors(result))
    }

    @Test
    fun `허용되지 않은 문자가 있으면 이름 규칙 안내를 보여 준다`() {
        val user = newUser()
        val result = submit(login(user), user.loginId!!, "bad name!")
        assertEquals(400, result.response.status)
        assertTrue(errors(result).contains("Enter name in alphabetnumerical or symbol characters(_-.)"), errors(result))
    }

    @Test
    fun `예약어는 대소문자를 가리지 않고 거부한다`() {
        // v1.6 결함 재현: ignoreCase=false(기본)가 오히려 대소문자 무시 비교를 한다([refactoring-backlog.md] R-13).
        val user = newUser()
        val session = login(user)
        listOf(".", "..", ".git", ".GIT").forEach { reserved ->
            val result = submit(session, user.loginId!!, reserved)
            assertEquals(400, result.response.status, reserved)
            assertTrue(errors(result).contains("can't use reserved names"), "$reserved: ${errors(result)}")
        }
    }

    @Test
    fun `이름이 비어 있으면 필수 입력 오류이다`() {
        val user = newUser()
        val result = submit(login(user), user.loginId!!, "")
        assertEquals(400, result.response.status)
        assertTrue(errors(result).contains("\"name\":[\"This field is required\"]"), errors(result))
    }

    @Test
    fun `검증에 실패하면 입력값을 유지한 채 폼을 다시 보여 준다`() {
        val user = newUser()
        val result = submit(login(user), user.loginId!!, "bad name!", "overview" to "남긴 설명", "projectScope" to "PRIVATE")
        val page = doc(result)
        assertEquals("bad name!", page.selectFirst("#project-name")?.attr("value"))
        assertEquals("남긴 설명", page.selectFirst("#description")?.text())
        assertTrue(page.selectFirst("input#private")!!.hasAttr("checked"))
    }

    @Test
    fun `검증에 실패하면 프로젝트도 저장소도 만들지 않는다`() {
        val user = newUser()
        submit(login(user), user.loginId!!, "bad name!")
        assertNull(projects.findByOwnerIgnoreCaseAndNameIgnoreCase(user.loginId!!, "bad name!"))
        assertFalse(File("build/tmp/it-project-create/repo/git/${user.loginId}").exists())
    }
}
