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
import dev.yona.domain.sshkey.DeployKeyAddition
import dev.yona.domain.sshkey.DeployKeyService
import dev.yona.domain.sshkey.TestSshKey
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals

/**
 * 배포 키 REST(X-3 4단계, `decisions.md` D-66)를 검증한다: 프로젝트의 배포 키 목록, 한 건 조회, 등록, 삭제. 범위는 `read:deploy_key`, `write:deploy_key`, `admin:deploy_key`이고
 * (앞의 범위는 뒤의 범위가 포함한다) 토큰 주인이 프로젝트 관리자여야 한다. 토큰이 할 수 있는 일은 범위와 실제 권한의 교집합이다.
 */
@AutoConfigureMockMvc
@Transactional
@org.springframework.test.context.TestPropertySource(properties = ["yona.data-dir=build/tmp/it-deploy-key-api"])
class ExternalDeployKeyApiTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var apiTokenRepository: dev.yona.domain.apitoken.ApiTokenRepository
    @Autowired private lateinit var keyService: DeployKeyService

    private val mapper = JsonMapper.builder().build()
    private var sequence = 0
    private val stamp = System.nanoTime()

    private fun newUser(scopes: Collection<String>): Pair<User, String> {
        val n = ++sequence
        val user = users.save(User(loginId = "dk$n-$stamp", name = "키$n", email = "dk$n-$stamp@example.com", state = UserState.ACTIVE))
        val token = "dt$n$stamp"
        dev.yona.support.ApiTokenSupport.grant(apiTokenRepository, user, token, scopes)
        return user to token
    }

    private fun newProject(vcs: String = "GIT"): Project {
        val n = ++sequence
        val project = projects.save(Project(owner = "dko$n", name = "dkp$n", vcs = vcs, projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        return project
    }

    private fun joined(project: Project, role: RoleType, scopes: Collection<String>): String {
        val (user, token) = newUser(scopes)
        projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(role.id).get()))
        return token
    }

    private fun path(project: Project, tail: String = "") = "/-_-api/v1/owners/${project.owner}/projects/${project.name}/deploy_keys$tail"

    private fun json(builder: org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder, token: String?, body: String? = null) =
        mockMvc.perform(builder.apply { token?.let { header("Authorization", "token $it") } }.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).apply { body?.let { content(it) } })

    private fun body(name: String?, key: String, readOnly: Boolean? = null) = mapper.writeValueAsString(linkedMapOf<String, Any?>("name" to name, "publicKey" to key).also { m -> readOnly?.let { m["readOnly"] = it } })

    private fun add(project: Project, key: TestSshKey, name: String = "k", writable: Boolean = false) = (keyService.add(project, name, key.publicLine, writable) as DeployKeyAddition.Added).key

    @Test
    fun `토큰이 없으면 401이고 범위가 모자라면 403이다`() {
        val project = newProject()
        json(get(path(project)), null).andExpect(status().isUnauthorized)
        val token = joined(project, RoleType.MANAGER, listOf("user"))
        json(get(path(project)), token).andExpect(status().isForbidden)
        json(post(path(project)), token, "{}").andExpect(status().isForbidden)
    }

    @Test
    fun `프로젝트 관리자가 아니면 범위가 있어도 403이다`() {
        val project = newProject()
        val token = joined(project, RoleType.MEMBER, listOf("admin:deploy_key"))
        json(get(path(project)), token).andExpect(status().isForbidden)
        json(post(path(project)), token, body("a", TestSshKey.generate().publicLine)).andExpect(status().isForbidden)
        val (_, outsider) = newUser(listOf("admin:deploy_key"))
        json(get(path(project)), outsider).andExpect(status().isForbidden)
    }

    @Test
    fun `없는 프로젝트는 404이고 Git이 아닌 프로젝트는 400이다`() {
        val (_, token) = newUser(listOf("admin:deploy_key"))
        json(get("/-_-api/v1/owners/nobody/projects/none/deploy_keys"), token).andExpect(status().isNotFound)
        val svn = newProject("Subversion")
        json(get(path(svn)), joined(svn, RoleType.MANAGER, listOf("read:deploy_key"))).andExpect(status().isBadRequest)
    }

    @Test
    fun `목록은 최근 등록한 순서이고 읽기 전용 여부를 담는다`() {
        val project = newProject(); val token = joined(project, RoleType.MANAGER, listOf("read:deploy_key"))
        val first = add(project, TestSshKey.generate("a@x"), "첫째")
        val second = add(project, TestSshKey.generate("b@x"), "둘째", writable = true)
        add(newProject(), TestSshKey.generate(), "다른 저장소")
        val keys = mapper.readTree(json(get(path(project)), token).andExpect(status().isOk).andReturn().response.contentAsString).get("keys")
        assertEquals(listOf(second.id!!, first.id!!), keys.toList().map { it.get("id").asLong(0L) })
        assertEquals(listOf(false, true), keys.toList().map { it.get("readOnly").asBoolean() })
        assertEquals(first.fingerprint, keys[1].get("fingerprint").asString())
        assertEquals("ssh-ed25519", keys[1].get("keyType").asString())
    }

    @Test
    fun `한 건 조회는 이 프로젝트의 키만 보이고 나머지는 404이다`() {
        val project = newProject(); val token = joined(project, RoleType.MANAGER, listOf("read:deploy_key"))
        val mine = add(project, TestSshKey.generate()); val theirs = add(newProject(), TestSshKey.generate())
        json(get(path(project, "/${mine.id}")), token).andExpect(status().isOk).andExpect(jsonPath("$.result.fingerprint").value(mine.fingerprint!!))
        json(get(path(project, "/${theirs.id}")), token).andExpect(status().isNotFound)
    }

    @Test
    fun `등록하면 201이고 기본은 읽기 전용이다`() {
        val project = newProject(); val token = joined(project, RoleType.MANAGER, listOf("write:deploy_key"))
        val a = TestSshKey.generate(); val b = TestSshKey.generate()
        json(post(path(project)), token, body("읽기", a.publicLine)).andExpect(status().isCreated).andExpect(jsonPath("$.result.readOnly").value(true)).andExpect(jsonPath("$.result.fingerprint").value(a.fingerprint))
        json(post(path(project)), token, body("쓰기", b.publicLine, readOnly = false)).andExpect(status().isCreated).andExpect(jsonPath("$.result.readOnly").value(false))
        json(get(path(project)), token).andExpect(status().isOk)
    }

    @Test
    fun `등록한 사용자가 createdBy로 보인다`() {
        val project = newProject(); val (user, token) = newUser(listOf("write:deploy_key", "read:deploy_key"))
        projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(RoleType.MANAGER.id).get()))
        json(post(path(project)), token, body("a", TestSshKey.generate().publicLine)).andExpect(status().isCreated).andExpect(jsonPath("$.result.createdBy").value(user.loginId!!))
        json(get(path(project)), token).andExpect(jsonPath("$.keys[0].createdBy").value(user.loginId!!))
    }

    @Test
    fun `받을 수 없는 키와 틀린 본문은 400, 같은 키는 409이다`() {
        val project = newProject(); val token = joined(project, RoleType.MANAGER, listOf("write:deploy_key"))
        json(post(path(project)), token, body("x", "not a key")).andExpect(status().isBadRequest)
        json(post(path(project)), token, "{}").andExpect(status().isBadRequest)
        json(post(path(project)), token, null).andExpect(status().isBadRequest)
        json(post(path(project)), token, """{"publicKey": "${TestSshKey.generate().publicLine}", "readOnly": "yes"}""").andExpect(status().isBadRequest)
        val source = TestSshKey.generate()
        json(post(path(project)), token, body("a", source.publicLine)).andExpect(status().isCreated)
        json(post(path(project)), token, body("b", source.publicLine)).andExpect(status().isConflict)
    }

    @Test
    fun `삭제는 admin 범위가 필요하고 성공하면 204이다`() {
        val project = newProject()
        val writeToken = joined(project, RoleType.MANAGER, listOf("write:deploy_key")); val adminToken = joined(project, RoleType.MANAGER, listOf("admin:deploy_key"))
        val key = add(project, TestSshKey.generate())
        json(delete(path(project, "/${key.id}")), writeToken).andExpect(status().isForbidden)
        assertEquals(1, keyService.list(project).size)
        json(delete(path(project, "/${key.id}")), adminToken).andExpect(status().isNoContent)
        assertEquals(0, keyService.list(project).size)
        json(delete(path(project, "/${key.id}")), adminToken).andExpect(status().isNotFound)
    }

    @Test
    fun `다른 저장소의 키는 지울 수 없다`() {
        val project = newProject(); val other = newProject()
        val theirs = add(other, TestSshKey.generate())
        json(delete(path(project, "/${theirs.id}")), joined(project, RoleType.MANAGER, listOf("admin:deploy_key"))).andExpect(status().isNotFound)
        assertEquals(1, keyService.list(other).size)
    }
}
