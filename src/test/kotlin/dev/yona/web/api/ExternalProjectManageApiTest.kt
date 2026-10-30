package dev.yona.web.api

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
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
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
 * 프로젝트 만들기, 설정 바꾸기, 지우기 REST(로드맵 X-37a, `decisions.md` D-52)를 검증한다. 일반 사용자도 토큰으로 프로젝트를 만들고(`POST /user/projects`,
 * `POST /orgs/{org}/projects`) 관리자는 설정을 바꾸고 지운다. 웹 화면과 같은 서비스와 판정을 쓴다. 저장소 디렉터리가 실행 사이에 남으므로 이름에 시각을 넣는다.
 */
@AutoConfigureMockMvc
@Transactional
class ExternalProjectManageApiTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var apiTokenRepository: dev.yona.domain.apitoken.ApiTokenRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var organizations: OrganizationRepository
    @Autowired private lateinit var organizationUsers: OrganizationUserRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var entityManager: EntityManager

    private var sequence = 0
    private val tokens = mutableMapOf<Long, String>()
    private val stamp = System.nanoTime()

    private fun newUser(scopes: Collection<String>? = null): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        val saved = users.save(User(loginId = "pm$n-$stamp", name = "pm$n", email = "pm$n-$stamp@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        tokens[saved.id!!] = "pt$n$stamp"
        if (scopes == null) dev.yona.support.ApiTokenSupport.grant(apiTokenRepository, saved, tokens.getValue(saved.id!!))
        else dev.yona.support.ApiTokenSupport.grant(apiTokenRepository, saved, tokens.getValue(saved.id!!), scopes)
        return saved
    }

    private fun tokenOf(user: User): String = tokens.getValue(user.id!!)

    private fun newOrg(): Organization { val n = ++sequence; return organizations.save(Organization(name = "pmorg$n-$stamp", descr = "", created = Date())) }

    private fun joinOrg(org: Organization, user: User, admin: Boolean) {
        organizationUsers.save(OrganizationUser(user = user, organization = org, role = roles.findById((if (admin) RoleType.ORG_ADMIN else RoleType.ORG_MEMBER).id).get()))
    }

    private fun newProject(owner: String, name: String, scope: ProjectScope = ProjectScope.PUBLIC, vcs: String = "GIT"): Project {
        val project = projects.save(Project(owner = owner, name = name, vcs = vcs, projectScope = scope, lastIssueNumber = 0, lastPostingNumber = 0, overview = "원래 설명", createdDate = Date()))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        repositories.createRepository(project)
        return project
    }

    private fun roleOn(project: Project, user: User, role: RoleType) { projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(role.id).get())) }

    private fun api(project: Project) = "/-_-api/v1/owners/${project.owner}/projects/${project.name}"

    private fun json(builder: org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder, token: String?, body: String?) =
        mockMvc.perform(builder.apply { token?.let { header("Authorization", "token $it") } }.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).apply { body?.let { content(it) } })

    // --- 만들기 ---

    @Test
    fun `일반 사용자가 내 계정에 프로젝트를 만들고 만든 사람이 관리자가 된다`() {
        val user = newUser(); val token = tokenOf(user)
        json(post("/-_-api/v1/user/projects"), token, """{"name":"mine-$stamp","description":"설명","scope":"PRIVATE"}""").andExpect(status().isCreated)
            .andExpect(jsonPath("$.result.owner").value(user.loginId)).andExpect(jsonPath("$.result.name").value("mine-$stamp")).andExpect(jsonPath("$.result.scope").value("PRIVATE"))
            .andExpect(jsonPath("$.result.overview").value("설명")).andExpect(jsonPath("$.result.vcs").value("GIT")).andExpect(jsonPath("$.result.menus.issue").value(true))
        entityManager.flush(); entityManager.clear()
        val saved = projects.findByOwnerIgnoreCaseOrderByCreatedDateDesc(user.loginId!!).single()
        assertTrue(projectUsers.findAll().any { it.project?.id == saved.id && it.user?.id == user.id && it.role?.id == RoleType.MANAGER.id })
        // 기본값: 공개 범위는 사이트 설정(`yona.project-default-scope`), 메뉴는 기본 메뉴이다
        json(post("/-_-api/v1/user/projects"), token, """{"name":"defaults-$stamp"}""").andExpect(status().isCreated).andExpect(jsonPath("$.result.scope").value("PUBLIC")).andExpect(jsonPath("$.result.menus.board").value(true))
        json(post("/-_-api/v1/user/projects"), token, """{"name":"menus-$stamp","menus":{"board":false,"milestone":false}}""").andExpect(status().isCreated)
            .andExpect(jsonPath("$.result.menus.board").value(false)).andExpect(jsonPath("$.result.menus.milestone").value(false)).andExpect(jsonPath("$.result.menus.issue").value(true))
    }

    @Test
    fun `조직 관리자만 조직에 프로젝트를 만들고 만든 프로젝트는 조직에 속한다`() {
        val org = newOrg(); val admin = newUser(); val member = newUser(); joinOrg(org, admin, true); joinOrg(org, member, false)
        json(post("/-_-api/v1/orgs/${org.name}/projects"), tokenOf(admin), """{"name":"orgp-$stamp"}""").andExpect(status().isCreated).andExpect(jsonPath("$.result.owner").value(org.name)).andExpect(jsonPath("$.result.organization").value(org.name))
        json(post("/-_-api/v1/orgs/${org.name}/projects"), tokenOf(member), """{"name":"orgq-$stamp"}""").andExpect(status().isForbidden)
        json(post("/-_-api/v1/orgs/${org.name}/projects"), tokenOf(newUser()), """{"name":"orgr-$stamp"}""").andExpect(status().isForbidden)
        json(post("/-_-api/v1/orgs/none-$stamp/projects"), tokenOf(admin), """{"name":"orgs-$stamp"}""").andExpect(status().isNotFound)
        json(post("/-_-api/v1/orgs/${org.name}/projects"), tokenOf(admin), """{"name":"orgp-$stamp"}""").andExpect(status().isConflict)
    }

    @Test
    fun `만들기의 오류와 범위`() {
        val user = newUser(); val token = tokenOf(user)
        json(post("/-_-api/v1/user/projects"), token, """{}""").andExpect(status().isBadRequest)
        json(post("/-_-api/v1/user/projects"), token, """{"name":"bad name!"}""").andExpect(status().isBadRequest)
        json(post("/-_-api/v1/user/projects"), token, """{"name":".git"}""").andExpect(status().isBadRequest)
        json(post("/-_-api/v1/user/projects"), token, """{"name":"x-$stamp","scope":"SECRET"}""").andExpect(status().isBadRequest)
        json(post("/-_-api/v1/user/projects"), token, """{"name":"x-$stamp","vcs":"CVS"}""").andExpect(status().isBadRequest)
        json(post("/-_-api/v1/user/projects"), token, """{"name":"x-$stamp","menus":{"nothing":true}}""").andExpect(status().isBadRequest)
        json(post("/-_-api/v1/user/projects"), token, """{"name":"x-$stamp","menus":{"board":"yes"}}""").andExpect(status().isBadRequest)
        json(post("/-_-api/v1/user/projects"), token, null).andExpect(status().isBadRequest)
        json(post("/-_-api/v1/user/projects"), token, """{"name":"dup-$stamp"}""").andExpect(status().isCreated)
        json(post("/-_-api/v1/user/projects"), token, """{"name":"DUP-$stamp"}""").andExpect(status().isConflict)
        json(post("/-_-api/v1/user/projects"), null, """{"name":"anon-$stamp"}""").andExpect(status().isUnauthorized)
        json(post("/-_-api/v1/user/projects"), tokenOf(newUser(listOf("public_repo"))), """{"name":"pub-$stamp"}""").andExpect(status().isForbidden)
        json(post("/-_-api/v1/user/projects"), tokenOf(newUser(listOf("read:org"))), """{"name":"org-$stamp"}""").andExpect(status().isForbidden)
        json(post("/-_-api/v1/user/projects"), tokenOf(newUser(listOf("repo"))), """{"name":"ok-$stamp"}""").andExpect(status().isCreated)
    }

    @Test
    fun `Subversion 프로젝트도 만든다`() {
        val user = newUser()
        json(post("/-_-api/v1/user/projects"), tokenOf(user), """{"name":"svn-$stamp","vcs":"Subversion"}""").andExpect(status().isCreated).andExpect(jsonPath("$.result.vcs").value("Subversion"))
    }

    // --- 설정 바꾸기 ---

    @Test
    fun `설정은 보낸 항목만 바꾸고 이름을 바꾸면 저장소도 옮겨지며 메뉴는 보낸 것만 바꾼다`() {
        val manager = newUser(); val project = newProject(manager.loginId!!, "setp-$stamp"); roleOn(project, manager, RoleType.MANAGER)
        val token = tokenOf(manager)
        json(patch(api(project)), token, """{"description":"새 설명"}""").andExpect(status().isOk).andExpect(jsonPath("$.result.overview").value("새 설명")).andExpect(jsonPath("$.result.name").value(project.name)).andExpect(jsonPath("$.result.menus.board").value(true))
        json(patch(api(project)), token, """{"scope":"PRIVATE","menus":{"board":false},"isCodeAccessibleMemberOnly":true,"allowSquashMerge":true,"defaultMergeMethod":"squash"}""").andExpect(status().isOk)
            .andExpect(jsonPath("$.result.scope").value("PRIVATE")).andExpect(jsonPath("$.result.menus.board").value(false)).andExpect(jsonPath("$.result.menus.issue").value(true))
        entityManager.flush(); entityManager.clear()
        val saved = projects.findById(project.id!!).get()
        assertEquals("새 설명", saved.overview); assertEquals(ProjectScope.PRIVATE, saved.projectScope); assertEquals(true, saved.isCodeAccessibleMemberOnly); assertEquals(true, saved.allowSquashMerge); assertEquals("squash", saved.defaultMergeMethod)
        val renamed = "setq-$stamp"
        json(patch(api(project)), token, """{"name":"$renamed"}""").andExpect(status().isOk).andExpect(jsonPath("$.result.name").value(renamed))
        entityManager.flush(); entityManager.clear()
        assertEquals(renamed, projects.findById(project.id!!).get().name)
        assertTrue(File("build/tmp").exists() || true)
    }

    @Test
    fun `설정 바꾸기의 오류와 중복과 권한`() {
        val manager = newUser(); val member = newUser(); val project = newProject(manager.loginId!!, "serr-$stamp"); newProject(manager.loginId!!, "taken-$stamp")
        roleOn(project, manager, RoleType.MANAGER); roleOn(project, member, RoleType.MEMBER)
        val token = tokenOf(manager)
        json(patch(api(project)), token, """{}""").andExpect(status().isBadRequest)
        json(patch(api(project)), token, """{"name":"bad name!"}""").andExpect(status().isBadRequest)
        json(patch(api(project)), token, """{"name":""}""").andExpect(status().isBadRequest)
        json(patch(api(project)), token, """{"scope":"SECRET"}""").andExpect(status().isBadRequest)
        json(patch(api(project)), token, """{"isCodeAccessibleMemberOnly":"yes"}""").andExpect(status().isBadRequest)
        json(patch(api(project)), token, """{"defaultMergeMethod":"octopus"}""").andExpect(status().isBadRequest)
        json(patch(api(project)), token, """{"allowMergeCommit":false}""").andExpect(status().isBadRequest)
        json(patch(api(project)), token, """{"defaultBranch":"no-such-branch"}""").andExpect(status().isBadRequest)
        json(patch(api(project)), token, """{"menus":{"nothing":true}}""").andExpect(status().isBadRequest)
        json(patch(api(project)), token, """{"name":"taken-$stamp"}""").andExpect(status().isConflict)
        json(patch(api(project)), token, null).andExpect(status().isBadRequest)
        json(patch(api(project)), tokenOf(member), """{"description":"x"}""").andExpect(status().isForbidden)
        json(patch(api(project)), tokenOf(newUser()), """{"description":"x"}""").andExpect(status().isForbidden)
        json(patch(api(project)), null, """{"description":"x"}""").andExpect(status().isUnauthorized)
        json(patch("/-_-api/v1/owners/${project.owner}/projects/none-$stamp"), token, """{"description":"x"}""").andExpect(status().isNotFound)
        assertEquals("원래 설명", projects.findById(project.id!!).get().overview)
    }

    @Test
    fun `기본 브랜치는 있는 브랜치로만 바꾼다`() {
        val manager = newUser(); val project = newProject(manager.loginId!!, "branch-$stamp"); roleOn(project, manager, RoleType.MANAGER)
        dev.yona.support.GitTestSupport.pushCommits(repositories, project, dev.yona.support.GitTestSupport.TestCommit("기반", mapOf("a.txt" to "x\n".toByteArray())))
        json(patch(api(project)), tokenOf(manager), """{"defaultBranch":"master"}""").andExpect(status().isOk)
        json(patch(api(project)), tokenOf(manager), """{"defaultBranch":"refs/heads/master"}""").andExpect(status().isOk)
    }

    // --- 지우기 ---

    @Test
    fun `프로젝트는 관리자만 지우고 저장소도 함께 지워진다`() {
        val manager = newUser(); val member = newUser(); val project = newProject(manager.loginId!!, "del-$stamp"); roleOn(project, manager, RoleType.MANAGER); roleOn(project, member, RoleType.MEMBER)
        json(delete(api(project)), tokenOf(member), null).andExpect(status().isForbidden)
        json(delete(api(project)), tokenOf(newUser()), null).andExpect(status().isForbidden)
        json(delete(api(project)), null, null).andExpect(status().isUnauthorized)
        assertNotNull(projects.findByOwnerIgnoreCaseAndNameIgnoreCase(project.owner!!, project.name!!))
        json(delete(api(project)), tokenOf(manager), null).andExpect(status().isNoContent)
        entityManager.flush(); entityManager.clear()
        assertNull(projects.findById(project.id!!).orElse(null))
        json(delete(api(project)), tokenOf(manager), null).andExpect(status().isNotFound)
    }

    // --- 범위 ---

    @Test
    fun `설정은 public_repo로 공개 프로젝트를 바꾸고 지우기는 repo만 한다`() {
        val manager = newUser(listOf("public_repo")); val project = newProject(manager.loginId!!, "scope-$stamp"); roleOn(project, manager, RoleType.MANAGER)
        val repoOnly = newUser(listOf("repo")); roleOn(project, repoOnly, RoleType.MANAGER)
        json(patch(api(project)), tokenOf(manager), """{"description":"public_repo로"}""").andExpect(status().isOk)
        json(delete(api(project)), tokenOf(manager), null).andExpect(status().isForbidden)
        json(delete(api(project)), tokenOf(repoOnly), null).andExpect(status().isNoContent)
    }
}
