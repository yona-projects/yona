package dev.yona.web.project

import dev.yona.support.selectFirst
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Label
import dev.yona.domain.issue.LabelRepository
import dev.yona.domain.misc.Attachment
import dev.yona.domain.misc.AttachmentRepository
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.misc.Watch
import dev.yona.domain.misc.WatchRepository
import dev.yona.domain.project.Project
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
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 프로젝트 목록(`GET /projects`)을 검증한다.
 *
 * 원본: `app/controllers/ProjectApp.java:1052-1136`, `app/views/project/list.scala.html`.
 */
@AutoConfigureMockMvc
@Transactional
class ProjectListTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var siteAdmins: SiteAdminRepository
    @Autowired private lateinit var labels: LabelRepository
    @Autowired private lateinit var watches: WatchRepository
    @Autowired private lateinit var attachments: AttachmentRepository

    private var sequence = 0

    private fun newUser(guest: Boolean = false, name: String? = null): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(
            User(
                loginId = "pl$n", name = name ?: "User $n", email = "pl$n@example.com", state = UserState.ACTIVE, isGuest = guest,
                passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)
            )
        )
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun newProject(
        scope: ProjectScope = ProjectScope.PUBLIC, name: String = "list${++sequence}", owner: String = "lo${sequence}",
        overview: String? = null, createdMinutesAgo: Int = 0, original: Project? = null
    ): Project = projects.save(
        Project(
            owner = owner, name = name, overview = overview, vcs = "GIT", projectScope = scope, originalProject = original,
            createdDate = Date(System.currentTimeMillis() - createdMinutesAgo * 60_000L)
        )
    )

    private fun page(path: String = "/projects", session: MockHttpSession? = null): Document {
        val request = get(path)
        session?.let { request.session(it) }
        return Jsoup.parse(mockMvc.perform(request).andExpect(status().isOk).andReturn().response.contentAsString)
    }

    private fun names(doc: Document) = doc.select("ul.all-projects > li.project .header > a.black").map { it.text() }

    // --- 기본 화면 ---

    @Test
    fun `제목과 탭이 있다`() {
        val doc = page()
        assertEquals("Project list", doc.title())
        val tabs = doc.select(".title_area ul.nav-tabs li a")
        assertEquals(listOf("/projects", "/orgs"), tabs.map { it.attr("href") })
        assertEquals(listOf("PUBLIC Project list", "Group List"), tabs.map { it.text() })
        assertTrue(doc.selectFirst(".title_area ul.nav-tabs li a")!!.hasClass("active"))
        assertTrue(doc.select("header.gnb-nav li.active, .gnb-nav li.active").isNotEmpty(), "상단 메뉴의 목록이 활성이다")
    }

    @Test
    fun `프로젝트가 없으면 안내 문구를 보여 준다`() {
        val doc = page("/projects?filter=zzzz-no-such-project")
        assertEquals("Project is non existent", doc.selectFirst(".error-wrap p")?.text())
        assertNull(doc.selectFirst("ul.all-projects"))
        assertNull(doc.selectFirst("#pagination"))
    }

    @Test
    fun `검색 폼은 filter 이름으로 보내고 입력값을 유지한다`() {
        val form = page("/projects?filter=abc").selectFirst("#search form")!!
        assertEquals("/projects", form.attr("action"))
        assertEquals("get", form.attr("method").lowercase())
        val input = form.selectFirst("input[name=filter]")!!
        assertEquals("abc", input.attr("value"))
        assertEquals("Search by keyword", input.attr("placeholder"))
    }

    // --- 가시성 ---

    @Test
    fun `익명 사용자에게는 공개 프로젝트만 보인다`() {
        val publicProject = newProject(name = "visible-public")
        newProject(ProjectScope.PRIVATE, name = "hidden-private")
        newProject(ProjectScope.PROTECTED, name = "hidden-protected")
        val shown = names(page("/projects?filter=-p"))
        assertTrue(shown.contains(publicProject.name))
        assertFalse(shown.any { it.startsWith("hidden") })
    }

    @Test
    fun `멤버여도 비공개 프로젝트는 목록에 나오지 않는다`() {
        val user = newUser()
        val project = newProject(ProjectScope.PRIVATE, name = "mine-private")
        projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(RoleType.MEMBER.id).get()))
        assertFalse(names(page("/projects?filter=mine-private", login(user))).contains("mine-private"))
    }

    @Test
    fun `사이트 관리자에게는 모든 프로젝트가 보인다`() {
        val admin = newUser().also { siteAdmins.save(SiteAdmin(admin = it)) }
        newProject(ProjectScope.PRIVATE, name = "admin-sees-private")
        assertTrue(names(page("/projects?filter=admin-sees", login(admin))).contains("admin-sees-private"))
    }

    // --- 검색 ---

    @Test
    fun `이름, 소유자, 설명으로 검색한다`() {
        newProject(name = "needle-in-name")
        newProject(name = "other1", owner = "needle-owner")
        newProject(name = "other2", overview = "has NEEDLE inside")
        newProject(name = "unrelated")
        val found = names(page("/projects?filter=needle")).toSet()
        assertEquals(setOf("needle-in-name", "other1", "other2"), found)
    }

    @Test
    fun `라벨 이름으로도 검색한다`() {
        val label = labels.save(Label(category = "Language", name = "KotlinLabelXyz"))
        val project = newProject(name = "has-label")
        project.labels.add(label)
        projects.save(project)
        newProject(name = "no-label")
        assertEquals(listOf("has-label"), names(page("/projects?filter=kotlinlabelxyz")))
    }

    @Test
    fun `labelIds로 라벨이 붙은 프로젝트만 보여 준다`() {
        val label = labels.save(Label(category = "OS", name = "Plan9Xyz"))
        val tagged = newProject(name = "tagged")
        tagged.labels.add(label)
        projects.save(tagged)
        newProject(name = "untagged")
        assertEquals(listOf("tagged"), names(page("/projects?labelIds=${label.id}&filter=tagged")))
        assertEquals(listOf("tagged"), names(page("/projects?labelIds=${label.id}")))
    }

    // --- 정렬과 쪽 나누기 ---

    @Test
    fun `최근에 만든 프로젝트가 먼저 나온다`() {
        newProject(name = "ord-old", createdMinutesAgo = 30)
        newProject(name = "ord-new", createdMinutesAgo = 1)
        newProject(name = "ord-mid", createdMinutesAgo = 10)
        assertEquals(listOf("ord-new", "ord-mid", "ord-old"), names(page("/projects?filter=ord-")))
    }

    @Test
    fun `한 쪽에 10개씩 보여 주고 전체 쪽 수를 스크립트에 넘긴다`() {
        (1..25).forEach { newProject(name = "pg-%02d".format(it), createdMinutesAgo = 100 - it) }
        val first = page("/projects?filter=pg-")
        assertEquals(10, names(first).size)
        assertNotNull(first.selectFirst("#pagination"))
        assertTrue(first.html().contains("yobi.Pagination.update(\$(\"#pagination\"), 3)"), "총 3쪽")
        assertEquals(10, names(page("/projects?filter=pg-&pageNum=2")).size)
        assertEquals(5, names(page("/projects?filter=pg-&pageNum=3")).size)
    }

    @Test
    fun `쪽 번호가 1보다 작으면 404이다`() {
        mockMvc.perform(get("/projects?pageNum=0")).andExpect(status().isNotFound)
    }

    // --- 항목 ---

    @Test
    fun `항목에 이름, 소유자, 설명, 만든 시각이 나온다`() {
        val project = newProject(name = "item-basic", overview = "설명 문구", createdMinutesAgo = 3)
        val item = page("/projects?filter=item-basic").selectFirst("ul.all-projects > li.project")!!
        val link = item.selectFirst(".header a.black")!!
        assertEquals("/${project.owner}/${project.name}", link.attr("href"))
        assertEquals("설명 문구", item.selectFirst(".desc")?.text())
        val tag = item.selectFirst("p.name-tag")!!
        assertEquals("/${project.owner}", tag.selectFirst("a.owner-name-small")?.attr("href"))
        assertEquals(project.owner, tag.selectFirst("a.owner-name-small")?.text())
        assertEquals("3 minutes ago", tag.selectFirst("strong")?.text())
        assertNull(item.selectFirst(".yobicon-lock"))
    }

    @Test
    fun `로고가 있으면 이미지를 보여 주고 없으면 비운다`() {
        val withLogo = newProject(name = "logo-yes")
        val file = attachments.save(Attachment(containerType = ResourceType.PROJECT, containerId = withLogo.id!!, name = "l.png"))
        newProject(name = "logo-no")
        val items = page("/projects?filter=logo-").select("ul.all-projects > li.project")
        val yes = items.first { it.selectFirst(".header a.black")!!.text() == "logo-yes" }
        val no = items.first { it.selectFirst(".header a.black")!!.text() == "logo-no" }
        assertEquals("/files/${file.id}", yes.selectFirst(".owner-avatar-wrap img")?.attr("src"))
        assertNull(no.selectFirst(".owner-avatar-wrap img"))
    }

    @Test
    fun `포크한 프로젝트는 원본을 함께 보여 준다`() {
        val original = newProject(name = "fork-origin", owner = "origowner")
        newProject(name = "fork-child", original = original)
        val child = page("/projects?filter=fork-child").selectFirst("ul.all-projects > li.project")!!
        val origin = child.selectFirst(".origin-title")!!
        assertEquals("/origowner/fork-origin", origin.attr("href"))
        assertEquals("origowner / fork-origin", origin.text())
    }

    @Test
    fun `프로젝트 라벨은 분류를 클래스로 삼아 링크한다`() {
        val label = labels.save(Label(category = "Language", name = "KotlinXyz2"))
        val project = newProject(name = "labeled")
        project.labels.add(label)
        projects.save(project)
        val anchor = page("/projects?filter=labeled").selectFirst(".header a.project-label")!!
        assertTrue(anchor.hasClass("language"))
        assertEquals("/projects?labelIds=${label.id}", anchor.attr("href"))
        assertEquals("KotlinXyz2", anchor.text())
    }

    @Test
    fun `공개 프로젝트는 구성원과 구독자 수를 보여 준다`() {
        val project = newProject(name = "stats-public")
        val member = newUser(name = "Zed")
        val other = newUser(name = "Amy")
        listOf(member, other).forEach { projectUsers.save(ProjectUser(user = it, project = project, role = roles.findById(RoleType.MEMBER.id).get())) }
        watches.save(Watch(user = member, resourceType = ResourceType.PROJECT, resourceId = project.id.toString()))
        val stats = page("/projects?filter=stats-public").selectFirst(".stats-wrap")!!
        assertEquals(listOf("Amy", "Zed"), stats.select(".members li a img").map { it.attr("alt") }, "이름 순서")
        assertEquals(listOf("/${other.loginId}", "/${member.loginId}"), stats.select(".members li a").map { it.attr("href") })
        assertEquals("2", stats.select("p strong")[0].text())
        assertEquals("1", stats.select("p strong")[1].text())
    }

    // --- JSON ---

    @Test
    fun `JSON을 원하면 소유자 이름 목록을 돌려준다`() {
        val a = newProject(name = "json-a", owner = "jo1")
        val b = newProject(name = "json-b", owner = "jo2")
        mockMvc.perform(get("/projects?filter=json-").accept("application/json"))
            .andExpect(status().isOk)
            .andExpect(header().string("Vary", "Accept"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentTypeCompatibleWith("application/json"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.length()").value(2))
        val body = mockMvc.perform(get("/projects?filter=json-").accept("application/json")).andReturn().response.contentAsString
        assertTrue(body.contains("${a.owner}/${a.name}") && body.contains("${b.owner}/${b.name}"), body)
    }

    @Test
    fun `HTML 응답에도 Vary Accept를 붙인다`() {
        mockMvc.perform(get("/projects")).andExpect(header().string("Vary", "Accept"))
    }

    // --- 접근 ---

    @Test
    fun `게스트는 홈으로 보낸다`() {
        val guest = newUser(guest = true)
        mockMvc.perform(get("/projects").session(login(guest)))
            .andExpect(status().isSeeOther).andExpect(header().string("Location", "/"))
    }
}

/** 프로젝트 목록을 숨기는 사이트에서는 403이다. */
@AutoConfigureMockMvc
@TestPropertySource(properties = ["yona.hide-project-listing=true"])
class ProjectListHiddenTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc

    @Test
    fun `목록을 숨기면 403 화면을 보여 준다`() {
        val result = mockMvc.perform(get("/projects")).andExpect(status().isForbidden).andReturn()
        val doc = Jsoup.parse(result.response.contentAsString)
        assertEquals("You are not authorized to access this page or not logged in.", doc.selectFirst(".error-wrap p")?.text())
    }
}

/**
 * 비공개 저장소도 목록에 보여 주는 사이트이다(`displayPrivateRepositories=true`).
 *
 * v1.6 템플릿은 `displayPrivateRepositories || 읽기 권한`일 때 항목을 자세히 보여 주므로, 이 설정이 켜져 있으면
 * 모든 프로젝트가 자세히 나온다. 권한 없음 안내(흐린 항목)는 이 조건이 거짓일 때만 나오는데, 그 경우 목록에는
 * 읽을 수 있는 공개 프로젝트(또는 사이트 관리자가 보는 전체)만 있어 실제로는 나타나지 않는다.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.display-private-repositories=true"])
class ProjectListPrivateDisplayTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var projects: ProjectRepository

    @Test
    fun `비공개 프로젝트도 목록에 자세히 나온다`() {
        projects.save(Project(owner = "pdo", name = "hidden-detail", vcs = "GIT", projectScope = ProjectScope.PRIVATE, createdDate = Date()))
        projects.save(Project(owner = "pdo", name = "shown-detail", vcs = "GIT", projectScope = ProjectScope.PUBLIC, createdDate = Date()))
        val doc = Jsoup.parse(mockMvc.perform(get("/projects?filter=-detail")).andReturn().response.contentAsString)
        val items = doc.select("ul.all-projects > li.project")
        assertEquals(2, items.size)
        assertEquals(setOf("hidden-detail", "shown-detail"), items.map { it.selectFirst(".header a.black")!!.text() }.toSet())
        val hidden = items.first { it.selectFirst(".header a.black")!!.text() == "hidden-detail" }
        assertNotNull(hidden.selectFirst(".header .yobicon-lock"), "비공개 표시")
        assertNull(hidden.selectFirst(".stats-wrap .members"), "구성원은 공개 프로젝트에서만 보인다")
    }
}
