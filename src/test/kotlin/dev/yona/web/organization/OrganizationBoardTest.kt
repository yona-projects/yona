package dev.yona.web.organization

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
import dev.yona.domain.board.Posting
import dev.yona.domain.board.PostingRepository
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 조직 게시판 목록(`GET /organizations/:name/boards`)을 검증한다.
 *
 * 원본: `app/controllers/BoardApp.java:47-113`, `app/views/organization/group_board_list.scala.html`, `group_board_list_partial.scala.html`.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-org-board"])
class OrganizationBoardTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var organizations: OrganizationRepository
    @Autowired private lateinit var organizationUsers: OrganizationUserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var postings: PostingRepository

    private var sequence = 0
    private var clock = 1_700_000_000_000L

    @AfterEach
    fun clean() {
        File("build/tmp/it-org-board").deleteRecursively()
    }

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "ob$n", name = "ob$n", email = "ob$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun newOrg(): Organization = organizations.save(Organization(name = "borg${++sequence}", descr = "설명", created = Date()))

    private fun orgProject(org: Organization, scope: ProjectScope = ProjectScope.PUBLIC, name: String? = null): Project {
        val project = projects.save(Project(owner = org.name, name = name ?: "bp${++sequence}", vcs = "GIT", projectScope = scope, organization = org, overview = "개요", createdDate = Date()))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        return project
    }

    private fun post(project: Project, title: String, author: User? = null, comments: Int = 0, notice: Boolean = false, body: String = "본문"): Posting {
        clock += 60_000
        val number = (project.lastPostingNumber ?: 0) + 1
        project.lastPostingNumber = number; projects.save(project)
        return postings.save(
            Posting(
                project = project, number = number, title = title, body = body, authorId = author?.id, authorLoginId = author?.loginId, authorName = author?.name, notice = notice, readme = false,
                numOfComments = comments, createdDate = Date(clock), updatedDate = Date(clock)
            )
        )
    }

    private fun page(path: String, session: MockHttpSession? = null): Document =
        Jsoup.parse(mockMvc.perform(get(path).apply { session?.let { session(it) } }).andExpect(status().isOk).andReturn().response.contentAsString)

    private fun titles(doc: Document) = doc.select("ul.post-list-wrap li.post-item a.title").map { it.text() }

    @Test
    fun `조직의 모든 프로젝트의 글을 최근 작성 순서로 보인다`() {
        val org = newOrg(); val a = orgProject(org); val b = orgProject(org)
        post(a, "A1"); post(b, "B1"); post(a, "A2")
        val doc = page("/organizations/${org.name}/boards")
        assertEquals(listOf("A2", "B1", "A1"), titles(doc))
    }

    @Test
    fun `공지도 일반 글과 같이 섞여 나온다`() {
        val org = newOrg(); val a = orgProject(org)
        post(a, "일반"); post(a, "공지", notice = true)
        val doc = page("/organizations/${org.name}/boards")
        assertEquals(listOf("공지", "일반"), titles(doc))
        assertTrue(doc.select("ul.notice-wrap").isEmpty())
    }

    @Test
    fun `행에 프로젝트 이름, 번호, 작성자, 댓글 수가 있다`() {
        val org = newOrg(); val a = orgProject(org, name = "alpha"); val author = newUser()
        post(a, "제목", author = author, comments = 3)
        val row = page("/organizations/${org.name}/boards").selectFirst("li.post-item")!!
        assertEquals("/${org.name}/alpha/post/1", row.attr("href"))
        assertEquals("/${org.name}/alpha", row.selectFirst("a.group-project-name")!!.attr("href"))
        assertEquals("alpha", row.selectFirst("a.group-project-name")!!.text())
        assertEquals("#1", row.selectFirst(".post-id")!!.text())
        assertEquals(author.name, row.selectFirst("a.infos-link-item:not(.group-project-name)")!!.text())
        assertTrue(row.select(".item-count-groups").isNotEmpty())
        assertEquals("/${author.loginId}", row.selectFirst("a.avatar-wrap")!!.attr("href"))
    }

    @Test
    fun `프로젝트 이름으로 거른다`() {
        val org = newOrg(); val a = orgProject(org, name = "alpha"); val b = orgProject(org, name = "beta"); orgProject(org, name = "gamma")
        post(a, "A"); post(b, "B"); post(orgProject(org, name = "delta"), "D")
        assertEquals(listOf("B", "A"), titles(page("/organizations/${org.name}/boards?projectNames[]=alpha&projectNames[]=BETA")))
        assertEquals(listOf("B"), titles(page("/organizations/${org.name}/boards?projectNames=beta")))
        assertEquals(emptyList(), titles(page("/organizations/${org.name}/boards?projectNames[]=nothing")))
    }

    @Test
    fun `제목의 말머리를 떼지 않고 그대로 보인다`() {
        val org = newOrg(); val a = orgProject(org, name = "alpha")
        post(a, "[공지] 회의록")
        // v1.6 group_board_list_partial은 @post.title 전체를 출력한다(프로젝트 게시판과 달리 말머리를 분리하지 않는다)
        assertEquals(listOf("[공지] 회의록"), titles(page("/organizations/${org.name}/boards")))
    }

    @Test
    fun `제목이나 본문으로 검색한다`() {
        val org = newOrg(); val a = orgProject(org)
        post(a, "Alpha"); post(a, "Beta", body = "has ALPHA inside"); post(a, "Gamma")
        assertEquals(listOf("Beta", "Alpha"), titles(page("/organizations/${org.name}/boards?filter=alpha")))
    }

    @Test
    fun `보이지 않는 프로젝트의 글은 나오지 않는다`() {
        val org = newOrg(); val open = orgProject(org); val secret = orgProject(org, ProjectScope.PRIVATE)
        post(open, "공개"); post(secret, "비공개")
        assertEquals(listOf("공개"), titles(page("/organizations/${org.name}/boards")))
        val member = newUser()
        organizationUsers.save(OrganizationUser(user = member, organization = org, role = roles.findById(RoleType.ORG_ADMIN.id).get()))
        assertEquals(listOf("비공개", "공개"), titles(page("/organizations/${org.name}/boards", login(member))))
        // 보이지 않는 프로젝트 이름으로 거르면 아무것도 나오지 않는다.
        assertEquals(emptyList(), titles(page("/organizations/${org.name}/boards?projectNames[]=${secret.name}")))
    }

    @Test
    fun `프로젝트 선택기에는 보이는 프로젝트가 있고 고른 것이 선택되어 있다`() {
        val org = newOrg(); val a = orgProject(org, name = "alpha"); orgProject(org, name = "beta")
        val doc = page("/organizations/${org.name}/boards?projectNames[]=alpha")
        val options = doc.select("select#projects option")
        assertEquals(listOf("alpha", "beta"), options.map { it.text() })
        assertTrue(options[0].hasAttr("selected")); assertFalse(options[1].hasAttr("selected"))
        assertEquals("projectNames[]", doc.selectFirst("select#projects")!!.attr("name"))
        assertNotNull(a.id)
    }

    @Test
    fun `정렬 링크와 쪽 나누기`() {
        val org = newOrg(); val a = orgProject(org)
        (1..16).forEach { post(a, "글 $it") }
        val doc = page("/organizations/${org.name}/boards")
        assertEquals(15, titles(doc).size)
        assertTrue(doc.select("script").any { Regex("\"nTotalPages\"\\s*:\\s*2").containsMatchIn(it.data()) })
        val links = doc.select(".filters a.filter")
        assertEquals("/organizations/${org.name}/boards?orderBy=createdDate&orderDir=asc", links[1].attr("href"))
        assertEquals("/organizations/${org.name}/boards?orderBy=updatedDate&orderDir=desc", links[0].attr("href"))
        assertEquals(listOf("글 1"), titles(page("/organizations/${org.name}/boards?pageNum=2")))
    }

    @Test
    fun `글이 없으면 안내가 보인다`() {
        val org = newOrg(); orgProject(org)
        assertEquals("No post has been added.", page("/organizations/${org.name}/boards").selectFirst(".error-wrap p")!!.text())
    }

    @Test
    fun `없는 조직은 404이다`() {
        mockMvc.perform(get("/organizations/nothing/boards")).andExpect(status().isNotFound)
    }
}
