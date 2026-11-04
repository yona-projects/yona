package dev.yona.web.project

import dev.yona.support.selectFirst
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationRepository
import dev.yona.domain.project.OrganizationUser
import dev.yona.domain.project.OrganizationUserRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
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
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 외부 Git 저장소 가져오기(`/_import`)를 검증한다. 가져올 저장소는 같은 서버의 다른 프로젝트 저장소를 `file:` 주소로 가리킨다.
 *
 * 원본: `app/controllers/ImportApp.java`, `app/views/project/importing.scala.html`.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-code-browser", "yona.import-allow-local-sources=true"])
class ImportTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var organizations: OrganizationRepository
    @Autowired private lateinit var organizationUsers: OrganizationUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var repositories: RepositoryService

    private var sequence = 0

    private fun user(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "im$n", name = "이름im$n", email = "im$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234")) }

    /** 가져올 원본 저장소이다. 커밋 두 개와 브랜치 두 개가 있다. */
    private fun source(): String {
        val n = ++sequence
        val p = projects.save(Project(owner = "imo$n", name = "imp$n", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        repositories.createRepository(p)
        GitTestSupport.pushCommits(repositories, p, TestCommit("하나", mapOf("a.txt" to "a".toByteArray())), TestCommit("둘", mapOf("b.txt" to "b".toByteArray())))
        GitTestSupport.appendCommits(repositories, p, TestCommit("기능", mapOf("c.txt" to "c".toByteArray())), branch = "feature")
        return repositories.openRepository(p).use { it.directory.toURI().toString() }
    }

    private fun send(session: MockHttpSession, owner: String, name: String, url: String, extra: Map<String, String> = emptyMap()) =
        post("/_import").session(session).param("owner", owner).param("name", name).param("url", url).param("projectScope", "PUBLIC").param("vcs", "GIT")
            .param("code", "true").param("issue", "true").apply { extra.forEach { (k, v) -> param(k, v) } }

    @Test
    fun `가져오기 폼은 로그인이 필요하고 소유자를 미리 고를 수 있다`() {
        mockMvc.perform(get("/_import")).andExpect(status().is3xxRedirection)
        val me = user(); val org = organizations.save(Organization(name = "imorg${++sequence}", created = Date()))
        organizationUsers.save(OrganizationUser(user = me, organization = org, role = roles.findById(RoleType.ORG_ADMIN.id).get()))
        val doc = Jsoup.parse(mockMvc.perform(get("/_import").param("owner", org.name!!).session(login(me))).andExpect(status().isOk).andReturn().response.contentAsString)
        assertEquals("/_import", doc.selectFirst("form#importGit")!!.attr("action"))
        assertEquals(listOf(me.loginId, org.name), doc.select("select#project-owner option").map { it.attr("value") })
        assertEquals(org.name, doc.selectFirst("select#project-owner option[selected]")!!.attr("value"))
        assertEquals("GIT", doc.selectFirst("input[name=vcs]")!!.attr("value"))
        assertEquals(7, doc.select("input[type=checkbox][name][value=true]").size)
    }

    @Test
    fun `저장소를 가져와 프로젝트를 만들고 만든 사람을 매니저로 둔다`() {
        val me = user(); val url = source()
        mockMvc.perform(send(login(me), me.loginId!!, "imported", url, mapOf("overview" to "설명"))).andExpect(status().isSeeOther).andExpect(redirectedUrl("/${me.loginId}/imported"))
        val project = projects.findByOwnerIgnoreCaseAndNameIgnoreCase(me.loginId!!, "imported")!!
        assertEquals("GIT", project.vcs); assertEquals("설명", project.overview); assertEquals(ProjectScope.PUBLIC, project.projectScope); assertNotNull(project.createdDate)
        assertEquals(setOf("refs/heads/feature", "refs/heads/master"), repositories.refNames(project).toSet())
        assertTrue(projectUsers.existsByUserIdAndProjectIdAndRoleId(me.id!!, project.id!!, RoleType.MANAGER.id))
        assertTrue(projectUsers.existsByUserIdAndProjectIdAndRoleId(1L, project.id!!, RoleType.SITEMANAGER.id))
        val menu = menuSettings.findFirstByProjectId(project.id!!)!!
        assertEquals(listOf(true, true, false, false, false, false, false), listOf(menu.code, menu.issue, menu.pullRequest, menu.review, menu.milestone, menu.board, menu.wiki))
    }

    @Test
    fun `조직 소유로 가져오면 조직에 연결되고 만든 사람은 매니저가 되지 않는다`() {
        val me = user(); val org = organizations.save(Organization(name = "imorg${++sequence}", created = Date()))
        organizationUsers.save(OrganizationUser(user = me, organization = org, role = roles.findById(RoleType.ORG_ADMIN.id).get()))
        mockMvc.perform(send(login(me), org.name!!, "orgimport", source())).andExpect(status().isSeeOther)
        val project = projects.findByOwnerIgnoreCaseAndNameIgnoreCase(org.name!!, "orgimport")!!
        assertEquals(org.id, project.organization?.id)
        assertTrue(!projectUsers.existsByUserIdAndProjectIdAndRoleId(me.id!!, project.id!!, RoleType.MANAGER.id))
    }

    @Test
    fun `관리자가 아닌 조직이면 403이고 다른 사용자 소유이면 폼에 오류가 보인다`() {
        val me = user(); val other = user(); val org = organizations.save(Organization(name = "imorg${++sequence}", created = Date()))
        mockMvc.perform(send(login(me), org.name!!, "x", source())).andExpect(status().isForbidden)
        val doc = Jsoup.parse(mockMvc.perform(send(login(me), other.loginId!!, "x", source())).andExpect(status().isBadRequest).andReturn().response.contentAsString)
        assertTrue(doc.html().contains("project.owner.invalidate") || doc.html().contains("owner"))
    }

    @Test
    fun `실패해서 다시 그릴 때는 고른 메뉴만 켜져 있다 (R-46-4)`() {
        val me = user(); val session = login(me)
        // 코드와 이슈만 고르고 주소를 비워 실패시킨다
        val html = mockMvc.perform(send(session, me.loginId!!, "menusonly", "  ")).andExpect(status().isBadRequest).andReturn().response.contentAsString
        val doc = org.jsoup.Jsoup.parse(html)
        fun checked(id: String) = doc.selectFirst("input#$id")!!.hasAttr("checked")
        assertTrue(checked("menuSettingCode") && checked("menuSettingIssue"))
        assertEquals(listOf(false, false, false, false), listOf("menuSettingPullRequest", "menuSettingReview", "menuSettingMilestone", "menuSettingBoard").map { checked(it) })
    }

    @Test
    fun `이름에는 문제가 없고 주소만 비었으면 이름 안내를 더하지 않는다 (R-46-5)`() {
        val me = user(); val session = login(me)
        fun errorsOf(html: String) = html.substringAfter("\"htError\": ").substringBefore("\n")
        val blank = errorsOf(mockMvc.perform(send(session, me.loginId!!, "goodname", "  ")).andExpect(status().isBadRequest).andReturn().response.contentAsString)
        // v1.6은 오류가 하나라도 있으면 이름 규칙 안내(project.name.alert)를 이름에 더 붙였다
        assertTrue(blank.contains("\"url\"") && !blank.contains("\"name\""), blank)
        val badName = errorsOf(mockMvc.perform(send(session, me.loginId!!, "bad name!", source())).andExpect(status().isBadRequest).andReturn().response.contentAsString)
        assertTrue(badName.contains("\"name\""), badName)
    }

    @Test
    fun `같은 이름이 있거나 주소가 비었거나 이름 규칙을 어기면 400이고 이름 안내가 더해진다`() {
        val me = user(); val session = login(me)
        mockMvc.perform(send(session, me.loginId!!, "taken", source())).andExpect(status().isSeeOther)
        val duplicate = mockMvc.perform(send(session, me.loginId!!, "TAKEN", source())).andExpect(status().isBadRequest).andReturn().response.contentAsString
        assertTrue(duplicate.contains("htError") || duplicate.contains("\"name\""), duplicate)
        val blank = mockMvc.perform(send(session, me.loginId!!, "newone", "  ")).andExpect(status().isBadRequest).andReturn().response.contentAsString
        assertTrue(blank.contains("\"url\""), blank)
        mockMvc.perform(send(session, me.loginId!!, "bad name!", source())).andExpect(status().isBadRequest)
        assertNull(projects.findByOwnerIgnoreCaseAndNameIgnoreCase(me.loginId!!, "newone"))
    }

    @Test
    fun `저장소가 아닌 주소는 주소 오류이고 복제하다 만 디렉터리를 남기지 않는다`() {
        val me = user()
        val response = mockMvc.perform(send(login(me), me.loginId!!, "broken", "file:///no/such/repository")).andExpect(status().isBadRequest).andReturn().response.contentAsString
        assertTrue(response.contains("\"url\""), response)
        assertNull(projects.findByOwnerIgnoreCaseAndNameIgnoreCase(me.loginId!!, "broken"))
        assertTrue(!java.io.File("build/tmp/it-code-browser/repo/git/${me.loginId}/broken.git").exists())
    }

    @Test
    fun `로그인하지 않으면 만들 수 없다`() {
        mockMvc.perform(post("/_import").param("owner", "x").param("name", "y").param("url", "z")).andExpect(status().isForbidden)
    }
}
