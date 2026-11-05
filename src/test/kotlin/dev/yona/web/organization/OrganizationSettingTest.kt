package dev.yona.web.organization

import dev.yona.support.selectFirst
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.misc.AttachmentService
import dev.yona.domain.misc.FavoriteOrganization
import dev.yona.domain.misc.FavoriteOrganizationRepository
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationRepository
import dev.yona.domain.project.OrganizationUser
import dev.yona.domain.project.OrganizationUserRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.RepositoryService
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 조직 설정 폼(`GET settingform`)과 저장(`POST setting`)을 검증한다.
 *
 * 원본: `app/controllers/OrganizationApp.java:296-312, 337-430`, `app/views/organization/setting.scala.html`,
 * `partial_settingmenu.scala.html`, `app/models/Organization.java:225-245`.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-org-setting"])
class OrganizationSettingTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var organizations: OrganizationRepository
    @Autowired private lateinit var organizationUsers: OrganizationUserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var attachments: AttachmentService
    @Autowired private lateinit var favoriteOrganizations: FavoriteOrganizationRepository

    private var sequence = 0

    @AfterEach
    fun clean() {
        File("build/tmp/it-org-setting").deleteRecursively()
    }

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(
            User(
                loginId = "os$n", name = "os$n", email = "os$n@example.com", state = UserState.ACTIVE,
                passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)
            )
        )
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun newOrg(): Organization = organizations.save(Organization(name = "sorg${++sequence}", descr = "설명", created = Date()))

    private fun join(org: Organization, user: User, role: RoleType): User {
        organizationUsers.save(OrganizationUser(user = user, organization = org, role = roles.findById(role.id).get()))
        return user
    }

    private fun admin(org: Organization): User = join(org, newUser(), RoleType.ORG_ADMIN)

    private fun page(org: Organization, session: MockHttpSession): Document =
        Jsoup.parse(mockMvc.perform(get("/organizations/${org.name}/settingform").session(session)).andExpect(status().isOk).andReturn().response.contentAsString)

    private fun save(org: Organization, session: MockHttpSession?, vararg params: Pair<String, String>, logo: MockMultipartFile? = null): MvcResult {
        val request = multipart("/organizations/${org.name}/setting")
        session?.let { request.session(it) }
        logo?.let { request.file(it) }
        request.param("id", org.id.toString())
        params.forEach { (k, v) -> request.param(k, v) }
        return mockMvc.perform(request).andReturn()
    }

    private fun reload(org: Organization): Organization {
        organizations.flush()
        return organizations.findById(org.id!!).get()
    }

    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)

    // --- 폼 ---

    @Test
    fun `설정 폼은 조직 관리자만 볼 수 있다`() {
        val org = newOrg()
        val member = join(org, newUser(), RoleType.ORG_MEMBER)
        mockMvc.perform(get("/organizations/${org.name}/settingform").session(login(member))).andExpect(status().isForbidden)
        mockMvc.perform(get("/organizations/${org.name}/settingform")).andExpect(status().isForbidden)
        mockMvc.perform(get("/organizations/nothing/settingform").session(login(admin(org)))).andExpect(status().isNotFound)
        page(org, login(admin(org)))
    }

    @Test
    fun `설정 폼의 구성`() {
        val org = newOrg()
        val page = page(org, login(admin(org)))
        assertEquals(org.name, page.title())
        val form = page.selectFirst("form#saveSetting[name=update-org]")!!
        assertEquals("/organizations/${org.name}/setting", form.attr("action"))
        assertEquals("multipart/form-data", form.attr("enctype"))
        assertEquals(org.id.toString(), form.selectFirst("input[type=hidden][name=id]")?.attr("value"))
        assertEquals(org.name, form.selectFirst("#project-name")?.attr("value"))
        assertEquals("설명", form.selectFirst("#project-desc")?.text())
        assertTrue(form.selectFirst(".logo-wrap")!!.attr("style").contains("/assets/images/group_default.png"))
        assertNotNull(form.selectFirst("input#logoPath[type=file]"))
        assertEquals("Save", form.selectFirst("#save")?.text())
        assertTrue(page.select("script").any { "organization.Setting" in it.data() })
    }

    @Test
    fun `설정 하위 메뉴는 설정 구성원 삭제이고 현재 화면이 활성이다`() {
        val org = newOrg()
        val tabs = page(org, login(admin(org))).select(".project-page-wrap > ul.nav-tabs li")
        assertEquals(listOf("Setting", "Group member", "Audit log", "OAuth apps", "OAuth app policy", "Group Delete"), tabs.map { it.text() })
        assertEquals(listOf("/organizations/${org.name}/settingform", "/organizations/${org.name}/members", "/organizations/${org.name}/audit", "/organizations/${org.name}/oauth-apps", "/organizations/${org.name}/oauth-policy", "/organizations/${org.name}/deleteForm"), tabs.map { it.selectFirst("a")!!.attr("href") })
        assertTrue(tabs[0].selectFirst("a")!!.hasClass("active"))
    }

    // --- 저장 ---

    @Test
    fun `이름과 설명을 저장하고 설정 폼으로 돌아간다`() {
        val org = newOrg()
        val result = save(org, login(admin(org)), "name" to org.name!!, "descr" to "새 설명")
        assertEquals(303, result.response.status)
        assertEquals("/organizations/${org.name}/settingform", result.response.getHeader("Location"))
        assertEquals("새 설명", reload(org).descr)
    }

    @Test
    fun `이름을 바꾸면 조직의 프로젝트 소유자와 저장소 위치도 바뀐다`() {
        val org = newOrg()
        val oldName = org.name!!
        val project = projects.save(Project(owner = oldName, name = "p1", vcs = "GIT", projectScope = ProjectScope.PUBLIC, organization = org))
        menuSettings.save(ProjectMenuSetting(project = project, code = true))
        repositories.createRepository(project)
        val newName = "renamedorg${++sequence}"
        val result = save(org, login(admin(org)), "name" to newName, "descr" to "x")
        assertEquals("/organizations/$newName/settingform", result.response.getHeader("Location"))
        projects.flush()
        assertEquals(newName, projects.findById(project.id!!).get().owner)
        assertTrue(File("build/tmp/it-org-setting/repo/git/$newName/p1.git").isDirectory)
        assertTrue(!File("build/tmp/it-org-setting/repo/git/$oldName/p1.git").exists())
        assertEquals(newName, reload(org).name)
    }

    @Test
    fun `이름을 바꾸면 Subversion 프로젝트의 저장소 위치도 바뀐다`() {
        val org = newOrg()
        val oldName = org.name!!
        val project = projects.save(Project(owner = oldName, name = "svnp1", vcs = "Subversion", projectScope = ProjectScope.PUBLIC, organization = org))
        menuSettings.save(ProjectMenuSetting(project = project, code = true))
        repositories.createRepository(project)
        val newName = "svnrenamed${++sequence}"
        save(org, login(admin(org)), "name" to newName, "descr" to "x")
        projects.flush()
        assertEquals(newName, projects.findById(project.id!!).get().owner)
        assertTrue(File("build/tmp/it-org-setting/repo/svn/$newName/svnp1/format").isFile)
        assertTrue(!File("build/tmp/it-org-setting/repo/svn/$oldName/svnp1").exists())
    }

    @Test
    fun `이름을 바꾸면 즐겨찾기한 조직 이름도 바뀐다`() {
        val org = newOrg()
        val fan = newUser()
        favoriteOrganizations.save(FavoriteOrganization(user = fan, organization = org, organizationName = org.name))
        save(org, login(admin(org)), "name" to "favrenamed${++sequence}", "descr" to "x")
        favoriteOrganizations.flush()
        assertTrue(favoriteOrganizations.findAll().single().organizationName!!.startsWith("favrenamed"))
    }

    @Test
    fun `이름이 중복되어 거부되면 폼은 DB 값이 아니라 제출한 값을 다시 보인다 (C171)`() {
        val org = newOrg()
        val other = newOrg()
        val byOrg = save(org, login(admin(org)), "name" to other.name!!, "descr" to "제출한 설명")
        assertEquals(400, byOrg.response.status)
        val doc = Jsoup.parse(byOrg.response.contentAsString)
        assertEquals(other.name, doc.selectFirst("input[name=name]")!!.attr("value"))
        assertEquals("제출한 설명", doc.selectFirst("textarea[name=descr]")!!.text())
    }

    @Test
    fun `이미 있는 사용자나 조직 이름으로 바꾸면 거부한다`() {
        val org = newOrg()
        val other = newOrg()
        val user = newUser()
        val session = login(admin(org))
        val byOrg = save(org, session, "name" to other.name!!.uppercase(), "descr" to "x")
        assertEquals(400, byOrg.response.status)
        assertEquals("Already existent user's login id or group name.", Jsoup.parse(byOrg.response.contentAsString).selectFirst(".warning")?.text())
        // v1.6는 이름 중복이면 flash를 만들지 않아 알림은 뜨지 않고 이름 아래 문구만 보인다
        assertTrue(byOrg.response.contentAsString.let { !it.contains("Already existent user's login id or group name.\", 1000") && !it.contains("\$yobi.notify(\"Already existent") }, "알림이 떴다")
        assertEquals(400, save(org, session, "name" to user.loginId!!, "descr" to "x").response.status)
        assertEquals(org.name, reload(org).name)
    }

    @Test
    fun `대소문자만 바꾸는 이름 변경은 허용한다 (R-19-2, v1_6은 자기 자신과 겹친다고 거부했다)`() {
        // 이름이 바뀌었는지는 대소문자까지 구별해 보고, 중복 검사에서는 자기 조직을 제외한다.
        val org = newOrg()
        val upper = org.name!!.uppercase()
        assertEquals(303, save(org, login(admin(org)), "name" to upper, "descr" to "x").response.status)
        assertEquals(upper, reload(org).name)
    }

    @Test
    fun `대소문자만 다른 다른 조직의 이름으로는 바꿀 수 없다 (R-19-2)`() {
        val org = newOrg(); val other = newOrg()
        assertEquals(400, save(org, login(admin(org)), "name" to other.name!!.uppercase(), "descr" to "x").response.status)
    }

    @Test
    fun `형식이 틀린 이름은 오류이다 (v1 6은 500)`() {
        val org = newOrg()
        val session = login(admin(org))
        // v1.6은 바인딩 오류가 있는 폼에서 값을 꺼내려다 IllegalStateException이 난다.
        val result = runCatching { save(org, session, "name" to "bad name", "descr" to "x") }
        assertTrue(result.isFailure || result.getOrNull()!!.response.status == 500)
    }

    @Test
    fun `폼이 보내는 id와 상관없이 주소의 조직만 고친다`() {
        val org = newOrg()
        val other = newOrg()
        val request = multipart("/organizations/${org.name}/setting").session(login(admin(org)))
            .param("id", other.id.toString()).param("name", org.name!!).param("descr", "바뀐 설명")
        mockMvc.perform(request)
        assertEquals("바뀐 설명", reload(org).descr)
        assertEquals("설명", reload(other).descr)
    }

    @Test
    fun `설정 저장은 조직 관리자만 할 수 있다`() {
        val org = newOrg()
        val member = join(org, newUser(), RoleType.ORG_MEMBER)
        assertEquals(403, save(org, login(member), "name" to org.name!!, "descr" to "해킹").response.status)
        assertEquals("설명", reload(org).descr)
    }

    @Test
    fun `로고를 올리면 조직 첨부로 저장하고 새 로고가 이전 로고를 대체한다`() {
        val org = newOrg()
        val session = login(admin(org))
        save(org, session, "name" to org.name!!, "descr" to "x", logo = MockMultipartFile("logoPath", "a.png", "image/png", png))
        save(org, session, "name" to org.name!!, "descr" to "x", logo = MockMultipartFile("logoPath", "b.png", "image/png", png + 9))
        assertEquals(listOf("b.png"), attachments.findByContainer(ResourceType.ORGANIZATION, org.id!!).map { it.name })
        assertTrue(page(org, session).selectFirst(".logo-wrap")!!.attr("style").contains("/files/"))
    }

    @Test
    fun `이미지가 아니거나 너무 큰 로고는 거부한다`() {
        val org = newOrg()
        val session = login(admin(org))
        val notImage = save(org, session, "name" to org.name!!, "descr" to "x", logo = MockMultipartFile("logoPath", "d.txt", "text/plain", "x".toByteArray()))
        assertEquals(400, notImage.response.status)
        assertTrue(notImage.response.contentAsString.contains("This is not an image file."))
        val big = save(org, session, "name" to org.name!!, "descr" to "x", logo = MockMultipartFile("logoPath", "big.png", "image/png", ByteArray(1024 * 1000 * 5 + 1)))
        assertEquals(400, big.response.status)
        assertTrue(big.response.contentAsString.contains("Images should be less than 5MB in size."))
        assertTrue(attachments.findByContainer(ResourceType.ORGANIZATION, org.id!!).isEmpty())
    }

    @Test
    fun `설정 화면의 403은 조직 머리말과 메뉴가 있는 화면이다`() {
        // v1.6 `ErrorViews.Forbidden.render("error.forbidden", organization)` -> `forbidden_organization`
        val org = newOrg()
        val member = join(org, newUser(), RoleType.ORG_MEMBER)
        listOf(login(member), null).forEach { session ->
            val result = mockMvc.perform(get("/organizations/${org.name}/settingform").apply { session?.let { session(it) } }).andExpect(status().isForbidden).andReturn()
            val doc = Jsoup.parse(result.response.contentAsString)
            assertEquals(org.name, doc.selectFirst(".project-header-wrap .project-author a")!!.text())
            assertTrue(doc.select(".project-menu-nav").isNotEmpty())
            assertEquals("You are not authorized", doc.selectFirst(".page-wrap-outer .error-wrap p")!!.text())
        }
    }

    @Test
    fun `설정 저장의 403도 조직 머리말과 메뉴가 있는 화면이고 경고가 보인다`() {
        val org = newOrg()
        val member = join(org, newUser(), RoleType.ORG_MEMBER)
        val result = save(org, login(member), "name" to org.name!!, "descr" to "x")
        assertEquals(403, result.response.status)
        val doc = Jsoup.parse(result.response.contentAsString)
        assertEquals(org.name, doc.selectFirst(".project-header-wrap .project-author a")!!.text())
        assertTrue(doc.select(".project-menu-nav").isNotEmpty())
    }
}
