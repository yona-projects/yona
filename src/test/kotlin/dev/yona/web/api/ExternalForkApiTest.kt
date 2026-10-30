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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 포크 REST(로드맵 X-30d, `decisions.md` D-49)를 검증한다. 웹의 포크(`ForkController`)와 같은 서비스(`ForkService`)와 규칙을 쓴다: Git 프로젝트만 포크하고,
 * 같은 소유자에게 같은 이름이 있으면 거절하며, 조직에는 그 조직의 관리자만 만들 수 있고 조직에서만 "그룹 내 공개"(`PROTECTED`)를 쓴다.
 * 저장소 디렉터리가 실행 사이에 남으므로 이름에 시각을 넣어 이전 실행과 겹치지 않게 한다.
 */
@AutoConfigureMockMvc
@Transactional
class ExternalForkApiTest : AbstractIntegrationTest() {

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
        val saved = users.save(User(loginId = "fk$n-$stamp", name = "fk$n", email = "fk$n-$stamp@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        tokens[saved.id!!] = "ft$n$stamp"
        if (scopes == null) dev.yona.support.ApiTokenSupport.grant(apiTokenRepository, saved, tokens.getValue(saved.id!!))
        else dev.yona.support.ApiTokenSupport.grant(apiTokenRepository, saved, tokens.getValue(saved.id!!), scopes)
        return saved
    }

    private fun tokenOf(user: User): String = tokens.getValue(user.id!!)

    private fun newProject(scope: ProjectScope = ProjectScope.PUBLIC, vcs: String = "GIT"): Project {
        val n = ++sequence
        val project = projects.save(Project(owner = "fko$n-$stamp", name = "fkp$n", vcs = vcs, projectScope = scope, lastIssueNumber = 0, lastPostingNumber = 0, overview = "원본 설명", createdDate = Date()))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = false, review = true, milestone = true, board = true))
        if (vcs == "GIT") repositories.createRepository(project)
        return project
    }

    private fun newOrg(): Organization { val n = ++sequence; return organizations.save(Organization(name = "fkorg$n-$stamp", descr = "", created = Date())) }

    private fun joinOrg(org: Organization, user: User, admin: Boolean) {
        organizationUsers.save(OrganizationUser(user = user, organization = org, role = roles.findById((if (admin) RoleType.ORG_ADMIN else RoleType.ORG_MEMBER).id).get()))
    }

    private fun fork(project: Project, token: String?, body: String?) =
        mockMvc.perform(post("/-_-api/v1/owners/${project.owner}/projects/${project.name}/forks").apply { token?.let { header("Authorization", "token $it") } }
            .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).apply { body?.let { content(it) } })

    // --- 만들기 ---

    @Test
    fun `기본값으로 포크하면 내 소유로 같은 이름과 원본의 공개 범위를 물려받고 나는 관리자가 된다`() {
        val original = newProject(); val user = newUser()
        fork(original, tokenOf(user), """{}""").andExpect(status().isCreated)
            .andExpect(jsonPath("$.result.owner").value(user.loginId)).andExpect(jsonPath("$.result.name").value(original.name)).andExpect(jsonPath("$.result.scope").value("PUBLIC"))
            .andExpect(jsonPath("$.result.originalProject.owner").value(original.owner)).andExpect(jsonPath("$.result.originalProject.name").value(original.name)).andExpect(jsonPath("$.result.vcs").value("GIT"))
        entityManager.flush(); entityManager.clear()
        val saved = projects.findByOwnerIgnoreCaseOrderByCreatedDateDesc(user.loginId!!).single()
        assertEquals(original.id, saved.originalProject?.id); assertEquals("원본 설명", saved.overview); assertNull(saved.organization)
        assertTrue(projectUsers.findAll().any { it.project?.id == saved.id && it.user?.id == user.id && it.role?.id == RoleType.MANAGER.id })
        assertEquals(false, menuSettings.findFirstByProjectId(saved.id!!)?.pullRequest)
    }

    @Test
    fun `이름과 공개 범위를 정해 포크하고 같은 프로젝트를 이름만 바꿔 다시 포크하면 이름이 겹치지 않는 한 만든다`() {
        val original = newProject(); val user = newUser()
        fork(original, tokenOf(user), """{"name":"my-fork","projectScope":"PRIVATE"}""").andExpect(status().isCreated).andExpect(jsonPath("$.result.name").value("my-fork")).andExpect(jsonPath("$.result.scope").value("PRIVATE"))
        fork(original, tokenOf(user), """{"name":"my-fork"}""").andExpect(status().isConflict)
        fork(original, tokenOf(user), """{"name":"MY-FORK"}""").andExpect(status().isConflict)
        fork(original, tokenOf(user), """{"name":"my-fork2"}""").andExpect(status().isCreated)
        fork(original, tokenOf(user), """{}""").andExpect(status().isCreated).andExpect(jsonPath("$.result.name").value(original.name))
    }

    @Test
    fun `조직에는 그 조직의 관리자만 포크하고 그룹 내 공개는 조직에서만 쓴다`() {
        val original = newProject(); val org = newOrg(); val admin = newUser(); val member = newUser(); joinOrg(org, admin, true); joinOrg(org, member, false)
        fork(original, tokenOf(admin), """{"owner":"${org.name}","projectScope":"PROTECTED"}""").andExpect(status().isCreated).andExpect(jsonPath("$.result.owner").value(org.name)).andExpect(jsonPath("$.result.scope").value("PROTECTED")).andExpect(jsonPath("$.result.organization").value(org.name))
        entityManager.flush(); entityManager.clear()
        assertEquals(org.id, projects.findByOwnerIgnoreCaseOrderByCreatedDateDesc(org.name!!).single().organization?.id)
        fork(original, tokenOf(member), """{"owner":"${org.name}"}""").andExpect(status().isForbidden)
        fork(original, tokenOf(newUser()), """{"owner":"${org.name}"}""").andExpect(status().isForbidden)
        fork(original, tokenOf(admin), """{"owner":"someone-else"}""").andExpect(status().isForbidden)
        fork(original, tokenOf(admin), """{"owner":"${admin.loginId}","projectScope":"PROTECTED"}""").andExpect(status().isBadRequest)
        fork(original, tokenOf(admin), """{"owner":"${admin.loginId}","name":"mine"}""").andExpect(status().isCreated).andExpect(jsonPath("$.result.owner").value(admin.loginId)).andExpect(jsonPath("$.result.organization").doesNotExist())
    }

    @Test
    fun `포크의 오류와 권한`() {
        val original = newProject(); val secret = newProject(ProjectScope.PRIVATE); val svn = newProject(vcs = "SVN"); val user = newUser(); val token = tokenOf(user)
        fork(original, token, """{"name":"bad name!"}""").andExpect(status().isBadRequest)
        fork(original, token, """{"name":".git"}""").andExpect(status().isBadRequest)
        fork(original, token, """{"name":""}""").andExpect(status().isBadRequest)
        fork(original, token, """{"projectScope":"SECRET"}""").andExpect(status().isBadRequest)
        fork(original, token, """{"owner":5}""").andExpect(status().isBadRequest)
        fork(svn, token, """{}""").andExpect(status().isBadRequest)
        fork(secret, token, """{}""").andExpect(status().isForbidden)
        fork(original, null, """{}""").andExpect(status().isUnauthorized)
        mockMvc.perform(post("/-_-api/v1/owners/${original.owner}/projects/none-$stamp/forks").header("Authorization", "token $token").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNotFound)
        assertTrue(projects.findByOwnerIgnoreCaseOrderByCreatedDateDesc(user.loginId!!).isEmpty())
        // 본문이 모두 선택이라 본문이 없어도 기본값으로 포크한다
        fork(original, token, null).andExpect(status().isCreated)
        // 비공개 프로젝트의 구성원은 포크할 수 있다
        val member = newUser(); projectUsers.save(ProjectUser(user = member, project = secret, role = roles.findById(RoleType.MEMBER.id).get()))
        fork(secret, tokenOf(member), """{"projectScope":"PRIVATE"}""").andExpect(status().isCreated).andExpect(jsonPath("$.result.scope").value("PRIVATE"))
        assertNotNull(projects.findByOwnerIgnoreCaseOrderByCreatedDateDesc(member.loginId!!).singleOrNull())
    }

    // --- 범위 ---

    @Test
    fun `포크는 repo 범위나 공개 프로젝트의 public_repo가 필요하다`() {
        val original = newProject(); val secret = newProject(ProjectScope.PRIVATE)
        val publicRepo = newUser(listOf("public_repo")); val readOrg = newUser(listOf("read:org")); val repo = newUser(listOf("repo"))
        projectUsers.save(ProjectUser(user = publicRepo, project = secret, role = roles.findById(RoleType.MEMBER.id).get()))
        fork(original, tokenOf(publicRepo), """{}""").andExpect(status().isCreated)
        fork(secret, tokenOf(publicRepo), """{"name":"s1"}""").andExpect(status().isForbidden)
        fork(original, tokenOf(readOrg), """{}""").andExpect(status().isForbidden)
        fork(original, tokenOf(repo), """{}""").andExpect(status().isCreated)
    }
}
