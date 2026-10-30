package dev.yona.web.organization

import dev.yona.support.selectFirst
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.misc.AttachmentService
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationRepository
import dev.yona.domain.project.OrganizationUser
import dev.yona.domain.project.OrganizationUserRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.SiteAdmin
import dev.yona.domain.user.SiteAdminRepository
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 조직 홈(`GET /organizations/:name`)과 조직 목록(`GET /orgs`)을 검증한다.
 *
 * 원본: `app/controllers/OrganizationApp.java:117-124, 466-482`, `app/views/organization/view.scala.html`,
 * `header.scala.html`, `menu.scala.html`, `list.scala.html`, `app/models/Organization.java:90-140`.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-org-view"])
class OrganizationViewTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var organizations: OrganizationRepository
    @Autowired private lateinit var organizationUsers: OrganizationUserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var siteAdmins: SiteAdminRepository
    @Autowired private lateinit var attachments: AttachmentService

    private var sequence = 0

    @AfterEach
    fun clean() {
        File("build/tmp/it-org-view").deleteRecursively()
    }

    private fun newUser(name: String? = null, guest: Boolean = false): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(
            User(
                loginId = "ov$n", name = name ?: "ov$n", email = "ov$n@example.com", state = UserState.ACTIVE, isGuest = guest,
                passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)
            )
        )
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun newOrg(descr: String? = "조직 설명", created: Date = Date()): Organization =
        organizations.save(Organization(name = "vorg${++sequence}", descr = descr, created = created))

    private fun join(org: Organization, user: User, role: RoleType): User {
        organizationUsers.save(OrganizationUser(user = user, organization = org, role = roles.findById(role.id).get()))
        return user
    }

    private fun orgProject(org: Organization, scope: ProjectScope = ProjectScope.PUBLIC, name: String? = null): Project {
        val project = projects.save(Project(owner = org.name, name = name ?: "op${++sequence}", vcs = "GIT", projectScope = scope, organization = org, overview = "개요", createdDate = Date()))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        return project
    }

    private fun page(path: String, session: MockHttpSession? = null): Document =
        Jsoup.parse(mockMvc.perform(get(path).apply { session?.let { session(it) } }).andExpect(status().isOk).andReturn().response.contentAsString)

    // --- 홈 ---

    @Test
    fun `없는 조직은 404이다`() {
        mockMvc.perform(get("/organizations/nothing-here")).andExpect(status().isNotFound)
    }

    @Test
    fun `조직 이름은 대소문자를 구별하지 않고 찾는다`() {
        val org = newOrg()
        mockMvc.perform(get("/organizations/${org.name!!.uppercase()}")).andExpect(status().isOk)
    }

    @Test
    fun `홈의 제목과 머리글`() {
        val org = newOrg()
        val page = page("/organizations/${org.name}")
        assertEquals(org.name, page.title())
        assertEquals(org.name, page.selectFirst(".project-breadcrumb .project-author a")?.text())
        assertEquals("/organizations/${org.name}", page.selectFirst(".project-breadcrumb .project-author a")?.attr("href"))
        assertEquals("group", page.selectFirst(".group-title-head")?.text())
        assertTrue(page.selectFirst(".project-header-outer")!!.attr("style").contains("/assets/images/group_default.png"))
        assertEquals("조직 설명", page.selectFirst("#project-description")?.text())
    }

    @Test
    fun `조직 로고가 있으면 머리글에 쓴다`() {
        val org = newOrg()
        val file = File.createTempFile("logo", ".png").apply { writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1)) }
        val logo = attachments.store(file, "logo.png", ResourceType.ORGANIZATION, org.id!!, "x").attachment
        assertTrue(page("/organizations/${org.name}").selectFirst(".project-header-avatar img")!!.attr("src").endsWith("/files/${logo.id}"))
    }

    @Test
    fun `메뉴는 홈 이슈 게시판 풀 리퀘스트이고 홈이 활성이다`() {
        val org = newOrg()
        val items = page("/organizations/${org.name}").select(".project-menu-gruop li")
        assertEquals(listOf("Group Home", "Issue", "Board", "Pull request"), items.map { it.text().trim() })
        assertEquals(listOf("/organizations/${org.name}", "/organizations/${org.name}/issues", "/organizations/${org.name}/boards", "/organizations/${org.name}/pullrequests"), items.map { it.selectFirst("a")!!.attr("href") })
        assertTrue(items[0].hasClass("active"))
    }

    @Test
    fun `설정 메뉴는 조직 관리자와 사이트 관리자에게만 보인다`() {
        val org = newOrg()
        val admin = join(org, newUser(), RoleType.ORG_ADMIN)
        val member = join(org, newUser(), RoleType.ORG_MEMBER)
        val siteAdmin = newUser().also { siteAdmins.save(SiteAdmin(admin = it)) }
        assertEquals("/organizations/${org.name}/settingform", page("/organizations/${org.name}", login(admin)).selectFirst(".project-setting a")?.attr("href"))
        assertNotNull(page("/organizations/${org.name}", login(siteAdmin)).selectFirst(".project-setting a"))
        assertNull(page("/organizations/${org.name}", login(member)).selectFirst(".project-setting a"))
        assertNull(page("/organizations/${org.name}").selectFirst(".project-setting a"))
    }

    @Test
    fun `새 프로젝트 버튼은 조직 관리자와 사이트 관리자에게만 보인다`() {
        val org = newOrg()
        val admin = join(org, newUser(), RoleType.ORG_ADMIN)
        val member = join(org, newUser(), RoleType.ORG_MEMBER)
        assertEquals("/projectform?owner=${org.name}", page("/organizations/${org.name}", login(admin)).selectFirst(".project-search-wrap a.ybtn-primary")?.attr("href"))
        assertNull(page("/organizations/${org.name}", login(member)).selectFirst(".project-search-wrap a.ybtn-primary"))
    }

    @Test
    fun `가입 신청 버튼은 조직에 속하지 않은 로그인 사용자에게만 보인다`() {
        val org = newOrg()
        val outsider = newUser()
        val member = join(org, newUser(), RoleType.ORG_MEMBER)
        val button = page("/organizations/${org.name}", login(outsider)).selectFirst("#enrollBtn")
        assertEquals("/organizations/${org.name}/enroll", button?.attr("href"))
        assertEquals("Send sign-up request", button?.text()?.trim())
        assertNull(page("/organizations/${org.name}", login(member)).selectFirst("#enrollBtn"))
        assertNull(page("/organizations/${org.name}").selectFirst("#enrollBtn"))
    }

    @Test
    fun `이미 신청했으면 신청 취소 버튼을 보여 준다`() {
        val org = newOrg()
        val applicant = newUser()
        applicant.enrolledOrganizations.add(org)
        users.save(applicant)
        val button = page("/organizations/${org.name}", login(applicant)).selectFirst("#enrollBtn")
        assertEquals("/organizations/${org.name}/cancel/enroll", button?.attr("href"))
        assertEquals("Cancel sign-up request", button?.text()?.trim())
    }

    @Test
    fun `홈에는 볼 수 있는 프로젝트를 이름순으로 보여 준다`() {
        val org = newOrg()
        orgProject(org, name = "zeta")
        orgProject(org, name = "alpha")
        orgProject(org, ProjectScope.PRIVATE, name = "secret")
        val names = page("/organizations/${org.name}").select("ul.all-projects li.project .header > a.black").map { it.text() }
        assertEquals(listOf("alpha", "zeta"), names)
    }

    @Test
    fun `조직 구성원은 비공개 프로젝트를 구성원일 때만 보고 관리자는 모두 본다`() {
        val org = newOrg()
        val admin = join(org, newUser(), RoleType.ORG_ADMIN)
        val member = join(org, newUser(), RoleType.ORG_MEMBER)
        val mine = orgProject(org, ProjectScope.PRIVATE, name = "mine")
        orgProject(org, ProjectScope.PRIVATE, name = "other")
        projectUsers.save(ProjectUser(user = member, project = mine, role = roles.findById(RoleType.MEMBER.id).get()))
        fun names(user: User) = page("/organizations/${org.name}", login(user)).select("ul.all-projects li.project .header > a.black").map { it.text() }
        assertEquals(listOf("mine"), names(member))
        assertEquals(listOf("mine", "other"), names(admin))
    }

    @Test
    fun `프로젝트 항목의 구성`() {
        val org = newOrg()
        val project = orgProject(org, ProjectScope.PROTECTED, name = "proj")
        val item = page("/organizations/${org.name}", login(join(org, newUser(), RoleType.ORG_ADMIN))).selectFirst("li.project")!!
        assertEquals("/${org.name}/proj", item.selectFirst(".header a.black")?.attr("href"))
        assertEquals("G", item.selectFirst(".project-protected")?.text())
        assertEquals("개요", item.selectFirst(".desc")?.text())
        assertEquals("proj 개요", item.attr("data-value"))
        assertEquals("project-item", item.attr("data-item"))
        assertNotNull(item.selectFirst(".name-tag"))
        assertNotNull(project.id)
    }

    @Test
    fun `개요가 없는 프로젝트 항목의 검색 값은 이름 뒤에 빈 문자열이 붙는다 (Twirl은 null을 빈 문자열로 쓴다)`() {
        val org = newOrg()
        val project = orgProject(org, name = "noover")
        project.overview = null
        projects.save(project)
        val item = page("/organizations/${org.name}", login(join(org, newUser(), RoleType.ORG_ADMIN))).selectFirst("li.project")!!
        assertEquals("noover ", item.attr("data-value"))
    }

    @Test
    fun `구성원 영역은 조직에 속한 사람과 사이트 관리자에게만 보인다`() {
        val org = newOrg()
        val admin = join(org, newUser("관리자"), RoleType.ORG_ADMIN)
        val member = join(org, newUser("멤버"), RoleType.ORG_MEMBER)
        val outsider = newUser()
        val full = page("/organizations/${org.name}", login(admin))
        val boxes = full.select(".col-lg-3 .bubble-wrap")
        assertEquals(2, boxes.size)
        assertEquals("Group Manager", boxes[0].selectFirst("h3")?.text())
        assertEquals(listOf("관리자"), boxes[0].select(".project-members li.member a:not(.avatar-wrap)").map { it.text() })
        assertEquals("Group Member", boxes[1].selectFirst("h3")?.text())
        assertEquals(listOf("멤버"), boxes[1].select(".project-members li.member a:not(.avatar-wrap)").map { it.text() })
        assertNull(page("/organizations/${org.name}", login(outsider)).selectFirst(".col-lg-3 .bubble-wrap"))
        assertNull(page("/organizations/${org.name}").selectFirst(".col-lg-3 .bubble-wrap"))
        assertNotNull(member.id)
    }

    @Test
    fun `탈퇴 버튼은 v1 6 규칙을 따른다`() {
        // 조직 관리자가 아니면서 관리자가 한 명뿐이면 탈퇴 버튼이 없다. 관리자는 한 명뿐이어도 탈퇴 버튼이 있다 (v1.6 결함).
        val org = newOrg()
        val admin = join(org, newUser(), RoleType.ORG_ADMIN)
        val member = join(org, newUser(), RoleType.ORG_MEMBER)
        assertEquals("/organizations/${org.name}/member/leave", page("/organizations/${org.name}", login(admin)).selectFirst("#groupLeaveBtn")?.attr("data-href"))
        assertNull(page("/organizations/${org.name}", login(member)).selectFirst("#groupLeaveBtn"))
        join(org, newUser(), RoleType.ORG_ADMIN)
        assertNotNull(page("/organizations/${org.name}", login(member)).selectFirst("#groupLeaveBtn"))
    }

    @Test
    fun `탈퇴 확인 대화상자와 스크립트가 있다`() {
        val org = newOrg()
        val page = page("/organizations/${org.name}")
        assertEquals("Do you want to leave this group?", page.selectFirst("#alertLeave .modal-body p")?.text())
        assertTrue(page.select("script").any { "organization.View" in it.data() })
        assertTrue(page.select("script").any { "organization.Global" in it.data() })
    }

    // --- 목록 ---

    @Test
    fun `목록은 게스트에게 막혀 있다`() {
        mockMvc.perform(get("/orgs").session(login(newUser(guest = true)))).andExpect(status().isSeeOther)
    }

    @Test
    fun `목록의 구성과 최신순 정렬`() {
        val first = newOrg("첫째")
        val second = newOrg("둘째")
        val page = page("/orgs", login(newUser()))
        assertEquals("Project list", page.title())
        assertEquals(listOf("PUBLIC Project list", "Group List"), page.select(".site-breadcrumb-inner li").map { it.text().trim() })
        assertTrue(page.selectFirst(".site-breadcrumb-inner li.active a")!!.attr("href") == "/orgs")
        assertEquals(listOf(second.name, first.name), page.select("ul.all-projects li.project .header a.black").map { it.text() })
        assertEquals("둘째", page.selectFirst("ul.all-projects li.project .desc")?.text())
    }

    @Test
    fun `검색어는 이름이나 설명에 들어 있는 조직을 찾는다`() {
        val a = newOrg("backend team")
        newOrg("frontend")
        val byName = page("/orgs?filter=${a.name}", login(newUser()))
        assertEquals(listOf(a.name), byName.select("li.project .header a.black").map { it.text() })
        val byDesc = page("/orgs?filter=BACKEND", login(newUser()))
        assertEquals(listOf(a.name), byDesc.select("li.project .header a.black").map { it.text() })
    }

    @Test
    fun `검색어의 밑줄과 퍼센트는 like의 와일드카드로 동작한다 (v1_6은 이스케이프하지 않는다)`() {
        val a = newOrg("wxa-team"); val b = newOrg("wyb-team")
        val names = page("/orgs?filter=w_a-", login(newUser())).select("li.project .header a.black").map { it.text() }
        assertEquals(listOf(a.name), names)
        val all = Jsoup.parse(mockMvc.perform(get("/orgs").param("filter", "w%-team").session(login(newUser()))).andExpect(status().isOk).andReturn().response.contentAsString)
            .select("li.project .header a.black").map { it.text() }
        assertEquals(setOf(a.name, b.name), all.toSet())
    }

    @Test
    fun `조직이 없으면 안내를 보여 준다`() {
        val page = page("/orgs?filter=zzzz-no-match", login(newUser()))
        assertEquals("You do not belong to any group", page.selectFirst(".error-wrap p")?.text())
        assertFalse(page.select("ul.all-projects").isNotEmpty())
    }

    @Test
    fun `쪽 번호가 1보다 작으면 404이다`() {
        mockMvc.perform(get("/orgs?pageNum=0").session(login(newUser()))).andExpect(status().isNotFound)
    }
}
