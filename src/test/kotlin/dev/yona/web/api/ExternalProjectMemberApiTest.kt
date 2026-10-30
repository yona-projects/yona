package dev.yona.web.api

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 프로젝트 구성원 REST(로드맵 X-37b, `decisions.md` D-52)를 검증한다: 더하기, 역할 바꾸기, 빼기(본인이면 나가기). 조회(`GET .../members`)는 X-19에 있다.
 * 웹 화면(`ProjectMemberController`)과 같은 서비스와 판정이다: 프로젝트를 고칠 수 있는 사용자가 바꾸고, 본인은 스스로 나갈 수 있으며, 소유자는 나가거나 구성원이 될 수 없다.
 */
@AutoConfigureMockMvc
@Transactional
class ExternalProjectMemberApiTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var apiTokenRepository: dev.yona.domain.apitoken.ApiTokenRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var entityManager: EntityManager

    private var sequence = 0
    private val tokens = mutableMapOf<Long, String>()

    private fun newUser(scopes: Collection<String>? = null): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        val saved = users.save(User(loginId = "mb$n", name = "mb$n", email = "mb$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        tokens[saved.id!!] = "mt$n"
        if (scopes == null) dev.yona.support.ApiTokenSupport.grant(apiTokenRepository, saved, tokens.getValue(saved.id!!))
        else dev.yona.support.ApiTokenSupport.grant(apiTokenRepository, saved, tokens.getValue(saved.id!!), scopes)
        return saved
    }

    private fun tokenOf(user: User): String = tokens.getValue(user.id!!)

    private fun newProject(owner: User? = null, scope: ProjectScope = ProjectScope.PUBLIC): Project {
        val n = ++sequence
        val project = projects.save(Project(owner = owner?.loginId ?: "mbo$n", name = "mbp$n", vcs = "GIT", projectScope = scope, lastIssueNumber = 0, lastPostingNumber = 0))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        return project
    }

    private fun roleOn(project: Project, user: User, role: RoleType) { projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(role.id).get())) }

    private fun roleOf(project: Project, user: User): Long? { entityManager.flush(); entityManager.clear(); return projectUsers.findAll().firstOrNull { it.project?.id == project.id && it.user?.id == user.id && it.role?.id != RoleType.SITEMANAGER.id }?.role?.id }

    private fun api(project: Project, path: String = "members") = "/-_-api/v1/owners/${project.owner}/projects/${project.name}/$path"

    private fun json(builder: org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder, token: String?, body: String?) =
        mockMvc.perform(builder.apply { token?.let { header("Authorization", "token $it") } }.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).apply { body?.let { content(it) } })

    // --- 더하기 ---

    @Test
    fun `구성원을 더하면 기본은 멤버이고 관리자로도 더하며 목록에 나타난다`() {
        val project = newProject(); val manager = newUser(); roleOn(project, manager, RoleType.MANAGER); val a = newUser(); val b = newUser()
        json(post(api(project)), tokenOf(manager), """{"loginId":"${a.loginId}"}""").andExpect(status().isCreated).andExpect(jsonPath("$.result.loginId").value(a.loginId)).andExpect(jsonPath("$.result.role").value("member"))
        json(post(api(project)), tokenOf(manager), """{"loginId":"${b.loginId}","role":"manager"}""").andExpect(status().isCreated).andExpect(jsonPath("$.result.role").value("manager"))
        assertEquals(RoleType.MEMBER.id, roleOf(project, a)); assertEquals(RoleType.MANAGER.id, roleOf(project, b))
        json(get(api(project)), tokenOf(manager), null).andExpect(jsonPath("$.members.length()").value(3))
    }

    @Test
    fun `더하기의 오류와 권한`() {
        val project = newProject(); val manager = newUser(); val member = newUser(); roleOn(project, manager, RoleType.MANAGER); roleOn(project, member, RoleType.MEMBER); val target = newUser()
        json(post(api(project)), tokenOf(manager), """{"loginId":"${member.loginId}"}""").andExpect(status().isConflict)
        json(post(api(project)), tokenOf(manager), """{"loginId":"nobody-${sequence}"}""").andExpect(status().isNotFound)
        json(post(api(project)), tokenOf(manager), """{"loginId":"${target.loginId}","role":"owner"}""").andExpect(status().isBadRequest)
        json(post(api(project)), tokenOf(manager), """{}""").andExpect(status().isBadRequest)
        json(post(api(project)), tokenOf(manager), null).andExpect(status().isBadRequest)
        json(post(api(project)), tokenOf(member), """{"loginId":"${target.loginId}"}""").andExpect(status().isForbidden)
        json(post(api(project)), tokenOf(newUser()), """{"loginId":"${target.loginId}"}""").andExpect(status().isForbidden)
        json(post(api(project)), null, """{"loginId":"${target.loginId}"}""").andExpect(status().isUnauthorized)
        json(post("/-_-api/v1/owners/${project.owner}/projects/none-${sequence}/members"), tokenOf(manager), """{"loginId":"${target.loginId}"}""").andExpect(status().isNotFound)
        assertNull(roleOf(project, target))
    }

    // --- 역할 바꾸기 ---

    @Test
    fun `역할을 바꾸고 소유자는 관리자로 남으며 구성원이 아니면 찾지 못한다`() {
        val owner = newUser(); val project = newProject(owner); roleOn(project, owner, RoleType.MANAGER)
        val manager = newUser(); val member = newUser(); roleOn(project, manager, RoleType.MANAGER); roleOn(project, member, RoleType.MEMBER)
        json(patch(api(project, "members/${member.loginId}")), tokenOf(manager), """{"role":"manager"}""").andExpect(status().isOk).andExpect(jsonPath("$.result.role").value("manager"))
        assertEquals(RoleType.MANAGER.id, roleOf(project, member))
        json(patch(api(project, "members/${member.loginId}")), tokenOf(manager), """{"role":"member"}""").andExpect(status().isOk).andExpect(jsonPath("$.result.role").value("member"))
        json(patch(api(project, "members/${owner.loginId}")), tokenOf(manager), """{"role":"member"}""").andExpect(status().isBadRequest)
        assertEquals(RoleType.MANAGER.id, roleOf(project, owner))
        json(patch(api(project, "members/${newUser().loginId}")), tokenOf(manager), """{"role":"member"}""").andExpect(status().isNotFound)
        json(patch(api(project, "members/${member.loginId}")), tokenOf(manager), """{"role":"owner"}""").andExpect(status().isBadRequest)
        json(patch(api(project, "members/${member.loginId}")), tokenOf(manager), """{}""").andExpect(status().isBadRequest)
        json(patch(api(project, "members/${member.loginId}")), tokenOf(member), """{"role":"manager"}""").andExpect(status().isForbidden)
        json(patch(api(project, "members/${member.loginId}")), null, """{"role":"manager"}""").andExpect(status().isUnauthorized)
    }

    // --- 빼기 ---

    @Test
    fun `관리자가 구성원을 빼고 본인은 스스로 나가며 소유자는 나가지 못한다`() {
        val owner = newUser(); val project = newProject(owner); roleOn(project, owner, RoleType.MANAGER)
        val manager = newUser(); val a = newUser(); val b = newUser(); val c = newUser()
        roleOn(project, manager, RoleType.MANAGER); roleOn(project, a, RoleType.MEMBER); roleOn(project, b, RoleType.MEMBER); roleOn(project, c, RoleType.MEMBER)
        json(delete(api(project, "members/${a.loginId}")), tokenOf(manager), null).andExpect(status().isNoContent)
        assertNull(roleOf(project, a))
        json(delete(api(project, "members/${b.loginId}")), tokenOf(b), null).andExpect(status().isNoContent)
        assertNull(roleOf(project, b))
        json(delete(api(project, "members/${manager.loginId}")), tokenOf(c), null).andExpect(status().isForbidden)
        json(delete(api(project, "members/${owner.loginId}")), tokenOf(owner), null).andExpect(status().isForbidden)
        json(delete(api(project, "members/${owner.loginId}")), tokenOf(manager), null).andExpect(status().isForbidden)
        json(delete(api(project, "members/${a.loginId}")), tokenOf(manager), null).andExpect(status().isNotFound)
        json(delete(api(project, "members/${c.loginId}")), null, null).andExpect(status().isUnauthorized)
        assertEquals(RoleType.MEMBER.id, roleOf(project, c))
    }

    // --- 범위 ---

    @Test
    fun `공개 프로젝트의 구성원은 public_repo로도 바꾼다`() {
        val project = newProject(); val manager = newUser(listOf("public_repo")); roleOn(project, manager, RoleType.MANAGER); val target = newUser()
        val noScope = newUser(listOf("read:org")); roleOn(project, noScope, RoleType.MANAGER)
        json(post(api(project)), tokenOf(manager), """{"loginId":"${target.loginId}"}""").andExpect(status().isCreated)
        json(post(api(project)), tokenOf(noScope), """{"loginId":"${newUser().loginId}"}""").andExpect(status().isForbidden)
    }
}
