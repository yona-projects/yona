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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 조직 REST(로드맵 X-30a, `decisions.md` D-49)를 검증한다. 경로는 GitHub처럼 `/-_-api/v1/orgs/{org}`이고 토큰 전용이다.
 * 범위는 읽기 `read:org`, 구성원 변경 `write:org`, 조직 만들기와 설정과 삭제 `admin:org`이다. 판정은 웹 화면과 같다(조직 관리자만 고치고 구성원 목록을 본다).
 */
@AutoConfigureMockMvc
@Transactional
class ExternalOrganizationApiTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var apiTokenRepository: dev.yona.domain.apitoken.ApiTokenRepository
    @Autowired private lateinit var organizations: OrganizationRepository
    @Autowired private lateinit var organizationUsers: OrganizationUserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var entityManager: EntityManager

    private var sequence = 0
    private val tokens = mutableMapOf<Long, String>()

    private fun newUser(token: String? = null, scopes: Collection<String>? = null, guest: Boolean = false): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        val saved = users.save(User(loginId = "og$n", name = "og$n", email = "og$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt), isGuest = guest))
        token?.let {
            tokens[saved.id!!] = "ot$n$it"
            if (scopes == null) dev.yona.support.ApiTokenSupport.grant(apiTokenRepository, saved, tokens.getValue(saved.id!!))
            else dev.yona.support.ApiTokenSupport.grant(apiTokenRepository, saved, tokens.getValue(saved.id!!), scopes)
        }
        return saved
    }

    private fun tokenOf(user: User): String = tokens.getValue(user.id!!)

    /** 저장소 디렉터리가 실행 사이에 남으므로 이름에 시각을 넣어 이전 실행과 겹치지 않게 한다. */
    private fun newOrg(): Organization { val n = ++sequence; return organizations.save(Organization(name = "orgx$n-${System.nanoTime()}", descr = "설명", created = Date())) }

    private fun joinOrg(org: Organization, admin: Boolean, scopes: Collection<String>? = null): Pair<User, String> {
        val user = newUser("a", scopes)
        organizationUsers.save(OrganizationUser(user = user, organization = org, role = roles.findById((if (admin) RoleType.ORG_ADMIN else RoleType.ORG_MEMBER).id).get()))
        return user to tokenOf(user)
    }

    private fun json(builder: org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder, token: String?, body: String?) =
        mockMvc.perform(builder.apply { token?.let { header("Authorization", "token $it") } }.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).apply { body?.let { content(it) } })

    private fun roleOf(org: Organization, user: User): Long? { entityManager.flush(); entityManager.clear(); return organizationUsers.findFirstByOrganizationIdAndUserId(org.id!!, user.id!!)?.role?.id }

    // --- 조회 ---

    @Test
    fun `내 조직과 조직 한 건을 읽고 역할과 프로젝트 수를 담는다`() {
        val org = newOrg(); val (_, adminToken) = joinOrg(org, true); val (_, memberToken) = joinOrg(org, false); val other = newOrg(); val outsider = tokenOf(newUser("x"))
        projects.save(Project(owner = org.name!!, name = "pub", vcs = "GIT", projectScope = ProjectScope.PUBLIC, organization = org))
        projects.save(Project(owner = org.name!!, name = "priv", vcs = "GIT", projectScope = ProjectScope.PRIVATE, organization = org))
        json(get("/-_-api/v1/user/organizations"), adminToken, null).andExpect(status().isOk).andExpect(jsonPath("$.result.length()").value(1)).andExpect(jsonPath("$.result[0].name").value(org.name)).andExpect(jsonPath("$.result[0].role").value("admin"))
        json(get("/-_-api/v1/user/organizations"), outsider, null).andExpect(jsonPath("$.result.length()").value(0))
        json(get("/-_-api/v1/orgs/${org.name}"), adminToken, null).andExpect(status().isOk).andExpect(jsonPath("$.result.description").value("설명")).andExpect(jsonPath("$.result.projectCount").value(2)).andExpect(jsonPath("$.result.role").value("admin"))
        json(get("/-_-api/v1/orgs/${org.name}"), memberToken, null).andExpect(jsonPath("$.result.role").value("member"))
        json(get("/-_-api/v1/orgs/${org.name}"), outsider, null).andExpect(status().isOk).andExpect(jsonPath("$.result.projectCount").value(1)).andExpect(jsonPath("$.result.role").doesNotExist())
        json(get("/-_-api/v1/orgs/${org.name.orEmpty().uppercase()}"), adminToken, null).andExpect(status().isOk)
        json(get("/-_-api/v1/orgs/nothing-${other.id}"), adminToken, null).andExpect(status().isNotFound)
        json(get("/-_-api/v1/orgs/${org.name}"), null, null).andExpect(status().isUnauthorized)
    }

    @Test
    fun `구성원 목록은 조직 관리자만 본다 (웹과 같다)`() {
        val org = newOrg(); val (admin, adminToken) = joinOrg(org, true); val (member, memberToken) = joinOrg(org, false)
        json(get("/-_-api/v1/orgs/${org.name}/members"), adminToken, null).andExpect(status().isOk).andExpect(jsonPath("$.result.length()").value(2))
            .andExpect(jsonPath("$.result[0].loginId").value(admin.loginId)).andExpect(jsonPath("$.result[0].role").value("admin")).andExpect(jsonPath("$.result[1].loginId").value(member.loginId)).andExpect(jsonPath("$.result[1].role").value("member"))
        json(get("/-_-api/v1/orgs/${org.name}/members"), memberToken, null).andExpect(status().isForbidden)
        json(get("/-_-api/v1/orgs/${org.name}/members"), tokenOf(newUser("x")), null).andExpect(status().isForbidden)
        json(get("/-_-api/v1/orgs/zzz-none/members"), adminToken, null).andExpect(status().isNotFound)
    }

    // --- 구성원 변경 ---

    @Test
    fun `구성원을 더하고 역할을 바꾸며 틀린 입력과 권한과 마지막 관리자를 거절한다`() {
        val org = newOrg(); val (admin, adminToken) = joinOrg(org, true); val (_, memberToken) = joinOrg(org, false)
        val a = newUser(); val b = newUser(); val guest = newUser(guest = true)
        json(post("/-_-api/v1/orgs/${org.name}/members"), adminToken, """{"loginId":"${a.loginId}"}""").andExpect(status().isCreated).andExpect(jsonPath("$.result.role").value("member"))
        assertEquals(RoleType.ORG_MEMBER.id, roleOf(org, a))
        json(post("/-_-api/v1/orgs/${org.name}/members"), adminToken, """{"loginId":"${b.loginId}","role":"admin"}""").andExpect(status().isCreated).andExpect(jsonPath("$.result.role").value("admin"))
        assertEquals(RoleType.ORG_ADMIN.id, roleOf(org, b))
        json(post("/-_-api/v1/orgs/${org.name}/members"), adminToken, """{"loginId":"${a.loginId}"}""").andExpect(status().isConflict)
        json(post("/-_-api/v1/orgs/${org.name}/members"), adminToken, """{"loginId":"nobody-${sequence}"}""").andExpect(status().isNotFound)
        json(post("/-_-api/v1/orgs/${org.name}/members"), adminToken, """{"loginId":"${guest.loginId}"}""").andExpect(status().isForbidden)
        json(post("/-_-api/v1/orgs/${org.name}/members"), adminToken, """{"loginId":"${newUser().loginId}","role":"owner"}""").andExpect(status().isBadRequest)
        json(post("/-_-api/v1/orgs/${org.name}/members"), adminToken, """{}""").andExpect(status().isBadRequest)
        json(post("/-_-api/v1/orgs/${org.name}/members"), adminToken, null).andExpect(status().isBadRequest)
        json(post("/-_-api/v1/orgs/${org.name}/members"), memberToken, """{"loginId":"${newUser().loginId}"}""").andExpect(status().isForbidden)
        json(patch("/-_-api/v1/orgs/${org.name}/members/${a.loginId}"), adminToken, """{"role":"admin"}""").andExpect(status().isOk).andExpect(jsonPath("$.result.role").value("admin"))
        assertEquals(RoleType.ORG_ADMIN.id, roleOf(org, a))
        json(patch("/-_-api/v1/orgs/${org.name}/members/${a.loginId}"), memberToken, """{"role":"member"}""").andExpect(status().isForbidden)
        json(patch("/-_-api/v1/orgs/${org.name}/members/${a.loginId}"), adminToken, """{"role":"owner"}""").andExpect(status().isBadRequest)
        json(patch("/-_-api/v1/orgs/${org.name}/members/${newUser().loginId}"), adminToken, """{"role":"member"}""").andExpect(status().isNotFound)
        // 관리자가 둘이면 하나를 구성원으로 내릴 수 있고 마지막 한 명은 내릴 수 없다
        json(patch("/-_-api/v1/orgs/${org.name}/members/${a.loginId}"), adminToken, """{"role":"member"}""").andExpect(status().isOk)
        json(patch("/-_-api/v1/orgs/${org.name}/members/${b.loginId}"), adminToken, """{"role":"member"}""").andExpect(status().isOk)
        json(patch("/-_-api/v1/orgs/${org.name}/members/${admin.loginId}"), adminToken, """{"role":"member"}""").andExpect(status().isConflict)
    }

    @Test
    fun `구성원을 빼고 본인은 스스로 나가며 마지막 관리자는 뺄 수 없다`() {
        val org = newOrg(); val (admin, adminToken) = joinOrg(org, true); val (member, memberToken) = joinOrg(org, false); val (other, otherToken) = joinOrg(org, false)
        json(delete("/-_-api/v1/orgs/${org.name}/members/${other.loginId}"), memberToken, null).andExpect(status().isForbidden)
        json(delete("/-_-api/v1/orgs/${org.name}/members/${member.loginId}"), memberToken, null).andExpect(status().isNoContent)
        assertNull(roleOf(org, member))
        json(delete("/-_-api/v1/orgs/${org.name}/members/${member.loginId}"), adminToken, null).andExpect(status().isNotFound)
        json(delete("/-_-api/v1/orgs/${org.name}/members/${other.loginId}"), adminToken, null).andExpect(status().isNoContent)
        json(delete("/-_-api/v1/orgs/${org.name}/members/${admin.loginId}"), adminToken, null).andExpect(status().isConflict)
        assertNotNull(roleOf(org, admin))
        json(delete("/-_-api/v1/orgs/${org.name}/members/${newUser().loginId}"), adminToken, null).andExpect(status().isNotFound)
        json(delete("/-_-api/v1/orgs/zzz-none/members/${admin.loginId}"), adminToken, null).andExpect(status().isNotFound)
    }

    // --- 만들기, 설정, 삭제 ---

    @Test
    fun `조직을 만들면 만든 사람이 관리자가 되고 이름의 형식과 중복을 거절한다`() {
        val creator = newUser("a"); val token = tokenOf(creator); val taken = newOrg()
        json(post("/-_-api/v1/orgs"), token, """{"name":"neworg${sequence}","description":"새 조직"}""").andExpect(status().isCreated)
            .andExpect(jsonPath("$.result.name").value("neworg$sequence")).andExpect(jsonPath("$.result.description").value("새 조직")).andExpect(jsonPath("$.result.role").value("admin"))
        entityManager.flush(); entityManager.clear()
        val created = organizations.findByNameIgnoreCase("neworg$sequence")!!
        assertEquals(RoleType.ORG_ADMIN.id, organizationUsers.findFirstByOrganizationIdAndUserId(created.id!!, creator.id!!)?.role?.id)
        json(post("/-_-api/v1/orgs"), token, """{"name":"${taken.name}"}""").andExpect(status().isConflict)
        json(post("/-_-api/v1/orgs"), token, """{"name":"${creator.loginId}"}""").andExpect(status().isConflict)
        json(post("/-_-api/v1/orgs"), token, """{"name":"bad name!"}""").andExpect(status().isBadRequest)
        json(post("/-_-api/v1/orgs"), token, """{"name":""}""").andExpect(status().isBadRequest)
        json(post("/-_-api/v1/orgs"), token, """{}""").andExpect(status().isBadRequest)
        json(post("/-_-api/v1/orgs"), token, null).andExpect(status().isBadRequest)
        json(post("/-_-api/v1/orgs"), tokenOf(newUser("g", guest = true)), """{"name":"guestorg"}""").andExpect(status().isForbidden)
        json(post("/-_-api/v1/orgs"), null, """{"name":"anon"}""").andExpect(status().isUnauthorized)
    }

    @Test
    fun `설정은 보낸 항목만 바꾸고 이름을 바꾸면 프로젝트의 소유자도 바뀌며 형식과 중복과 권한을 거절한다`() {
        val org = newOrg(); val other = newOrg(); val (_, adminToken) = joinOrg(org, true); val (_, memberToken) = joinOrg(org, false)
        val project = projects.save(Project(owner = org.name!!, name = "p1", vcs = "GIT", projectScope = ProjectScope.PUBLIC, organization = org))
        menuSettings.save(ProjectMenuSetting(project = project, code = true))
        repositories.createRepository(project)
        json(patch("/-_-api/v1/orgs/${org.name}"), adminToken, """{"description":"바뀐 설명"}""").andExpect(status().isOk).andExpect(jsonPath("$.result.description").value("바뀐 설명")).andExpect(jsonPath("$.result.name").value(org.name))
        val renamed = "renamed${sequence}-${System.nanoTime()}"
        json(patch("/-_-api/v1/orgs/${org.name}"), adminToken, """{"name":"$renamed"}""").andExpect(status().isOk).andExpect(jsonPath("$.result.name").value(renamed)).andExpect(jsonPath("$.result.description").value("바뀐 설명"))
        entityManager.flush(); entityManager.clear()
        assertEquals(renamed, organizations.findById(org.id!!).get().name); assertEquals(renamed, projects.findById(project.id!!).get().owner)
        json(patch("/-_-api/v1/orgs/$renamed"), adminToken, """{}""").andExpect(status().isBadRequest)
        json(patch("/-_-api/v1/orgs/$renamed"), adminToken, """{"name":"${other.name}"}""").andExpect(status().isConflict)
        json(patch("/-_-api/v1/orgs/$renamed"), adminToken, """{"name":"bad name!"}""").andExpect(status().isBadRequest)
        json(patch("/-_-api/v1/orgs/$renamed"), adminToken, """{"name":""}""").andExpect(status().isBadRequest)
        json(patch("/-_-api/v1/orgs/$renamed"), adminToken, null).andExpect(status().isBadRequest)
        json(patch("/-_-api/v1/orgs/$renamed"), memberToken, """{"description":"x"}""").andExpect(status().isForbidden)
        json(patch("/-_-api/v1/orgs/nothing-here"), adminToken, """{"description":"x"}""").andExpect(status().isNotFound)
    }

    @Test
    fun `조직 삭제는 관리자만 하고 프로젝트가 남아 있으면 거절한다`() {
        val org = newOrg(); val (_, adminToken) = joinOrg(org, true); val (_, memberToken) = joinOrg(org, false)
        val project = projects.save(Project(owner = org.name!!, name = "p1", vcs = "GIT", projectScope = ProjectScope.PUBLIC, organization = org))
        json(delete("/-_-api/v1/orgs/${org.name}"), memberToken, null).andExpect(status().isForbidden)
        json(delete("/-_-api/v1/orgs/${org.name}"), adminToken, null).andExpect(status().isConflict)
        projects.delete(project); entityManager.flush()
        json(delete("/-_-api/v1/orgs/${org.name}"), adminToken, null).andExpect(status().isNoContent)
        entityManager.flush(); entityManager.clear()
        assertNull(organizations.findById(org.id!!).orElse(null))
        json(delete("/-_-api/v1/orgs/${org.name}"), adminToken, null).andExpect(status().isNotFound)
    }

    // --- 범위 ---

    @Test
    fun `토큰의 범위가 부족하면 읽기와 쓰기를 거절한다`() {
        val org = newOrg(); val target = newUser()
        val (_, readOnly) = joinOrg(org, true, listOf("read:org"))
        val (_, writeOrg) = joinOrg(org, true, listOf("write:org"))
        val (_, repoOnly) = joinOrg(org, true, listOf("repo"))
        json(get("/-_-api/v1/orgs/${org.name}"), readOnly, null).andExpect(status().isOk)
        json(get("/-_-api/v1/orgs/${org.name}/members"), readOnly, null).andExpect(status().isOk)
        json(post("/-_-api/v1/orgs/${org.name}/members"), readOnly, """{"loginId":"${target.loginId}"}""").andExpect(status().isForbidden)
        json(post("/-_-api/v1/orgs/${org.name}/members"), writeOrg, """{"loginId":"${target.loginId}"}""").andExpect(status().isCreated)
        json(get("/-_-api/v1/orgs/${org.name}"), writeOrg, null).andExpect(status().isOk)
        json(patch("/-_-api/v1/orgs/${org.name}"), writeOrg, """{"description":"x"}""").andExpect(status().isForbidden)
        json(delete("/-_-api/v1/orgs/${org.name}"), writeOrg, null).andExpect(status().isForbidden)
        json(get("/-_-api/v1/orgs/${org.name}"), repoOnly, null).andExpect(status().isForbidden)
        json(post("/-_-api/v1/orgs"), readOnly, """{"name":"scopeorg"}""").andExpect(status().isForbidden)
    }
}
