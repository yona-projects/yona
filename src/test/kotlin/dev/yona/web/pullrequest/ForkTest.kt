package dev.yona.web.pullrequest

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
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
import kotlin.test.assertTrue

/**
 * 포크(`newFork`, `fork`, `clone`)를 검증한다.
 *
 * 원본: `app/controllers/PullRequestApp.java`(`newFork`, `fork`, `doClone`), `app/views/git/fork.scala.html`, `clone.scala.html`,
 * `app/models/Project.java`(`copy`, `addFork`, `newProjectName`), `GitRepository.cloneLocalRepository`.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-code-browser"])
class ForkTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var organizations: OrganizationRepository
    @Autowired private lateinit var organizationUsers: OrganizationUserRepository
    @Autowired private lateinit var repositories: RepositoryService

    private var sequence = 0

    private fun user(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "fk$n", name = "fk$n", email = "fk$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234")) }

    private fun project(scope: ProjectScope = ProjectScope.PUBLIC): Project {
        val n = ++sequence
        val p = projects.save(Project(owner = "fko$n", name = "fkp$n", overview = "원본 설명", vcs = "GIT", projectScope = scope))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = false, pullRequest = true, review = false, milestone = true, board = true))
        repositories.createRepository(p)
        GitTestSupport.pushCommits(repositories, p, TestCommit("첫 커밋", mapOf("a.txt" to "a".toByteArray())))
        GitTestSupport.pushCommits(repositories, p, TestCommit("기능", mapOf("b.txt" to "b".toByteArray())), branch = "feature", startTime = 1_800_000_000_000L)
        return p
    }

    private fun organization(admin: User): Organization {
        val org = organizations.save(Organization(name = "fkorg${++sequence}", created = Date()))
        organizationUsers.save(OrganizationUser(user = admin, organization = org, role = roles.findById(RoleType.ORG_ADMIN.id).get()))
        return org
    }

    @Test
    fun `포크 폼은 로그인이 필요하다`() {
        val p = project()
        mockMvc.perform(get("/${p.owner}/${p.name}/newFork")).andExpect(status().is3xxRedirection)
    }

    @Test
    fun `포크 폼은 내 이름의 소유자와 원본 이름을 기본으로 보여 준다`() {
        val p = project(); val me = user()
        val doc = Jsoup.parse(mockMvc.perform(get("/${p.owner}/${p.name}/newFork").session(login(me))).andExpect(status().isOk).andReturn().response.contentAsString)
        assertEquals(p.name, doc.selectFirst("input#inputName")!!.attr("value"))
        assertEquals(listOf(me.loginId), doc.select("select#project-owner option").map { it.attr("value") })
        assertEquals("/${p.owner}/${p.name}/fork", doc.selectFirst("form.form-horizontal")!!.attr("action"))
        assertNotNull(doc.selectFirst("p.lead"))
        assertTrue(doc.select("input[name=projectScope]").map { it.attr("value") } == listOf("PUBLIC", "PRIVATE"))
    }

    @Test
    fun `내가 관리하는 조직이 소유자 후보에 나오고 forkOwner로 고를 수 있으며 조직에는 그룹내 공개가 나온다`() {
        val p = project(); val me = user(); val org = organization(me)
        val doc = Jsoup.parse(mockMvc.perform(get("/${p.owner}/${p.name}/newFork").param("forkOwner", org.name!!).session(login(me))).andExpect(status().isOk).andReturn().response.contentAsString)
        assertEquals(listOf(me.loginId, org.name), doc.select("select#project-owner option").map { it.attr("value") })
        assertEquals(org.name, doc.selectFirst("input[name=owner]")!!.attr("value"))
        assertEquals(listOf("PUBLIC", "PROTECTED", "PRIVATE"), doc.select("input[name=projectScope]").map { it.attr("value") })
    }

    @Test
    fun `관리자가 아닌 조직을 forkOwner로 주면 내 이름으로 본다`() {
        val p = project(); val me = user(); val other = user(); val org = organization(other)
        val doc = Jsoup.parse(mockMvc.perform(get("/${p.owner}/${p.name}/newFork").param("forkOwner", org.name!!).session(login(me))).andExpect(status().isOk).andReturn().response.contentAsString)
        assertEquals(me.loginId, doc.selectFirst("input[name=owner]")!!.attr("value"))
    }

    @Test
    fun `이미 같은 원본을 포크한 프로젝트가 있으면 알려 주고 이름에 번호를 붙인다`() {
        val p = project(); val me = user()
        val session = login(me)
        mockMvc.perform(post("/${p.owner}/${p.name}/clone").session(session).param("owner", me.loginId!!).param("name", p.name!!).param("projectScope", "PUBLIC")).andExpect(status().isOk)
        val doc = Jsoup.parse(mockMvc.perform(get("/${p.owner}/${p.name}/newFork").session(session)).andExpect(status().isOk).andReturn().response.contentAsString)
        assertEquals("${p.name}-1", doc.selectFirst("input#inputName")!!.attr("value"))
        assertTrue(doc.selectFirst("#helpMessage")!!.text().contains("already exists"))
        assertEquals("/${me.loginId}/${p.name}", doc.selectFirst("#helpMessage a")!!.attr("href"))
    }

    @Test
    fun `포크를 시작하면 복제 화면이 나오고 같은 이름이 있으면 폼으로 돌아간다`() {
        val p = project(); val me = user()
        val session = login(me)
        val body = mockMvc.perform(post("/${p.owner}/${p.name}/fork").session(session).param("owner", me.loginId!!).param("name", "내포크").param("projectScope", "PRIVATE"))
            .andExpect(status().isOk).andReturn().response.contentAsString
        assertTrue(body.contains("""\/${p.owner}\/${p.name}\/clone"""), body)
        assertTrue(body.contains("\"PRIVATE\"") && body.contains("내포크"), body)
        projects.save(Project(owner = me.loginId, name = "dup-name", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        mockMvc.perform(post("/${p.owner}/${p.name}/fork").session(session).param("owner", me.loginId!!).param("name", "DUP-name").param("projectScope", "PUBLIC"))
            .andExpect(status().isSeeOther).andExpect(redirectedUrl("/${p.owner}/${p.name}/newFork?forkOwner=${me.loginId}"))
    }

    @Test
    fun `클론은 프로젝트를 만들고 저장소와 설정을 복사하며 만든 사람을 매니저로 둔다`() {
        val p = project(); val me = user()
        val json = mockMvc.perform(post("/${p.owner}/${p.name}/clone").session(login(me)).param("owner", me.loginId!!).param("name", "myfork").param("projectScope", "PRIVATE"))
            .andExpect(status().isOk).andReturn().response.contentAsString
        assertEquals("""{"status":"success","url":"/${me.loginId}/myfork"}""", json)
        val fork = projects.findByOwnerIgnoreCaseAndNameIgnoreCase(me.loginId!!, "myfork")!!
        assertEquals(p.id, fork.originalProject?.id)
        assertEquals("원본 설명", fork.overview); assertEquals("GIT", fork.vcs); assertEquals(ProjectScope.PRIVATE, fork.projectScope)
        assertNotNull(fork.createdDate); assertEquals("http://localhost:9000/myfork", fork.siteurl)
        val menu = menuSettings.findFirstByProjectId(fork.id!!)!!
        assertEquals(listOf(true, false, true, false, true, true), listOf(menu.code, menu.issue, menu.pullRequest, menu.review, menu.milestone, menu.board))
        assertTrue(projectUsers.existsByUserIdAndProjectIdAndRoleId(me.id!!, fork.id!!, RoleType.MANAGER.id))
        assertTrue(projectUsers.existsByUserIdAndProjectIdAndRoleId(1L, fork.id!!, RoleType.SITEMANAGER.id))
        assertEquals(repositories.refNames(p), repositories.refNames(fork))
        assertEquals("refs/heads/master", repositories.defaultBranch(fork))
    }

    @Test
    fun `포크는 원본을 가리키는 bare 복제이고 PR용 ref는 복사하지 않는다 (v1_6 실제 결과)`() {
        val p = project(); val me = user()
        // PR 가져오기용 비표준 ref를 원본에 하나 둔다. v1.6 폴백 복제는 refs/heads/*와 태그만 가져온다.
        val originDirectory = repositories.openRepository(p).use { origin ->
            val head = origin.resolve("HEAD")
            origin.updateRef("refs/yobi/pull/1/head").apply { setNewObjectId(head); update() }
            origin.directory.canonicalPath
        }
        mockMvc.perform(post("/${p.owner}/${p.name}/clone").session(login(me)).param("owner", me.loginId!!).param("name", "barefork").param("projectScope", "PUBLIC")).andExpect(status().isOk)
        val fork = projects.findByOwnerIgnoreCaseAndNameIgnoreCase(me.loginId!!, "barefork")!!
        repositories.openRepository(fork).use { forked ->
            assertTrue(forked.isBare)
            assertEquals(setOf("refs/heads/master", "refs/heads/feature"), forked.refDatabase.getRefsByPrefix("refs/heads/").map { it.name }.toSet())
            assertTrue(forked.refDatabase.getRefsByPrefix("refs/yobi/").isEmpty())
            val config = forked.config
            // remote.origin은 file: URI가 아니라 원본 디렉터리의 canonical 절대 경로이다
            assertEquals(originDirectory, config.getString("remote", "origin", "url"))
            assertEquals("+refs/heads/*:refs/heads/*", config.getString("remote", "origin", "fetch"))
            assertEquals("origin", config.getString("branch", "master", "remote"))
            assertEquals("refs/heads/master", config.getString("branch", "master", "merge"))
        }
    }

    @Test
    fun `조직 소유로 포크하면 조직이 프로젝트에 연결된다`() {
        val p = project(); val me = user(); val org = organization(me)
        mockMvc.perform(post("/${p.owner}/${p.name}/clone").session(login(me)).param("owner", org.name!!).param("name", "orgfork").param("projectScope", "PROTECTED")).andExpect(status().isOk)
        val fork = projects.findByOwnerIgnoreCaseAndNameIgnoreCase(org.name!!, "orgfork")!!
        assertEquals(org.id, fork.organization?.id); assertEquals(ProjectScope.PROTECTED, fork.projectScope)
    }

    @Test
    fun `원본이 없으면 로그인한 사용자에게도 403 오류 화면이다`() {
        val me = user()
        mockMvc.perform(post("/nobody/noproject/clone").session(login(me)).param("owner", me.loginId!!).param("name", "x").param("projectScope", "PUBLIC")).andExpect(status().isForbidden)
    }

    @Test
    fun `비공개 프로젝트는 구성원이 아니면 포크할 수 없다`() {
        val p = project(ProjectScope.PRIVATE); val me = user()
        mockMvc.perform(get("/${p.owner}/${p.name}/newFork").session(login(me))).andExpect(status().isForbidden)
    }

    @Test
    fun `이름 규칙을 어기면 v1_6처럼 500이다`() {
        val p = project(); val me = user()
        mockMvc.perform(post("/${p.owner}/${p.name}/fork").session(login(me)).param("owner", me.loginId!!).param("name", "../evil").param("projectScope", "PUBLIC")).andExpect(status().isInternalServerError)
        mockMvc.perform(post("/${p.owner}/${p.name}/clone").session(login(me)).param("owner", me.loginId!!).param("name", "a b").param("projectScope", "PUBLIC")).andExpect(status().isInternalServerError)
    }

    @Test
    fun `클론 때 같은 이름이 이미 있으면 저장소를 건드리지 않고 실패로 답한다`() {
        val p = project(); val me = user()
        val session = login(me)
        mockMvc.perform(post("/${p.owner}/${p.name}/clone").session(session).param("owner", me.loginId!!).param("name", "twice").param("projectScope", "PUBLIC")).andExpect(status().isOk)
        val json = mockMvc.perform(post("/${p.owner}/${p.name}/clone").session(session).param("owner", me.loginId!!).param("name", "twice").param("projectScope", "PUBLIC")).andReturn().response.contentAsString
        assertEquals("""{"status":"failed","url":"/${p.owner}/${p.name}/pullRequests"}""", json)
        assertEquals(listOf("refs/heads/feature", "refs/heads/master"), repositories.refNames(projects.findByOwnerIgnoreCaseAndNameIgnoreCase(me.loginId!!, "twice")!!))
    }
}
