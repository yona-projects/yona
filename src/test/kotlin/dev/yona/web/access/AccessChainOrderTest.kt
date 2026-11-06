package dev.yona.web.access

import dev.yona.access.AccessControl
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
import dev.yona.domain.user.SiteAdmin
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.AccessChainGitControllers
import dev.yona.support.AccessChainTestControllers
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.method.HandlerMethod
import java.util.Date
import kotlin.test.assertEquals

/**
 * 접근 검사 어노테이션의 실행 순서를 검증한다 (`AccessChainInterceptor`).
 *
 * v1.6(Play 2.3.10) 규칙: 메서드 어노테이션이 바깥, 클래스 어노테이션이 안쪽이고, 부모 클래스가 자식 클래스보다 바깥이다.
 * 근거는 `play.core.j.JavaActionAnnotations`의 바이트코드와 v1.6 테스트 `EnrollProjectAppTest.enrollProjectNotExist`,
 * `WatchProjectAppTest.testWatch_NoProject`(익명 + 없는 프로젝트가 403)이다.
 */
@AutoConfigureMockMvc
@Transactional
class AccessChainOrderTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var siteAdmins: SiteAdminRepository
    @Autowired private lateinit var controllers: AccessChainTestControllers
    @Autowired private lateinit var gitControllers: AccessChainGitControllers

    private var sequence = 0

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "acc$n", name = "acc$n", email = "acc$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun newProject(vcs: String = "GIT", codeMemberOnly: Boolean = false): Project {
        val n = ++sequence
        val p = projects.save(Project(owner = "accowner$n", name = "accproj$n", vcs = vcs, projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = codeMemberOnly, createdDate = Date()))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        return p
    }

    private fun handler(name: String) = HandlerMethod(controllers, AccessChainTestControllers::class.java.getMethod(name))

    private fun names(name: String) = AccessChainInterceptor.annotationsInPlayOrder(handler(name)).map { it.annotationClass.simpleName }

    // --- 순서 규칙 ---

    @Test
    fun `메서드 어노테이션이 먼저이고 클래스는 부모부터 자식 순서이다`() {
        // 메서드(DefaultProjectCheck) -> 부모 클래스(AnonymousCheck) -> 자식 클래스(RestController, RequiresVcs)
        val noise = setOf("GetMapping", "RestController", "Metadata")
        assertEquals(listOf("DefaultProjectCheck", "AnonymousCheck"), names("projectThenAnonymous").filterNot { it in noise })
        assertEquals(listOf("SiteManagerOnly", "AnonymousCheck"), names("siteManager").filterNot { it in noise })
        val git = AccessChainInterceptor.annotationsInPlayOrder(HandlerMethod(gitControllers, AccessChainGitControllers::class.java.getMethod("codeThenGit"))).map { it.annotationClass.simpleName }
        assertEquals(listOf("CodeAccessCheck", "RequiresVcs"), git.filterNot { it in noise })
    }

    @Test
    fun `같은 요소에 단 어노테이션은 소스에 쓴 순서로 읽힌다 (JVM 가정 확인)`() {
        // JLS는 declaredAnnotations의 순서를 보장하지 않는다. HotSpot은 클래스 파일, 곧 소스 순서를 따르므로 이를 가드로 확인한다.
        fun only(name: String) = names(name).filter { it == "DefaultProjectCheck" || it == "CodeAccessCheck" }
        assertEquals(listOf("DefaultProjectCheck", "CodeAccessCheck"), only("orderAb"))
        assertEquals(listOf("CodeAccessCheck", "DefaultProjectCheck"), only("orderBa"))
    }

    // --- 동작 (v1.6 테스트 근거) ---

    @Test
    fun `익명이 없는 프로젝트에 요청하면 로그인 요구보다 먼저 403이다`() {
        // v1.6 EnrollProjectAppTest.enrollProjectNotExist: 클래스 AnonymousCheck(requiresLogin)보다 메서드의 프로젝트 검사가 바깥이다
        mockMvc.perform(get("/no/project/__chain/project-then-anonymous")).andExpect(status().isForbidden)
    }

    @Test
    fun `익명이 읽을 수 있는 프로젝트에 요청하면 로그인 폼으로 간다`() {
        val p = newProject()
        val path = "/${p.owner}/${p.name}/__chain/project-then-anonymous"
        // v1.6 WatchProjectAppTest.testWatch_AnonymousUser: 303 /users/loginform?redirectUrl=<요청 경로>
        mockMvc.perform(get(path)).andExpect(status().isSeeOther).andExpect(redirectedUrl("/users/loginform?redirectUrl=$path"))
    }

    @Test
    fun `로그인한 사용자는 두 검사를 모두 통과한다`() {
        val p = newProject()
        mockMvc.perform(get("/${p.owner}/${p.name}/__chain/project-then-anonymous").session(login(newUser()))).andExpect(status().isOk)
    }

    @Test
    fun `사이트 관리자 검사가 로그인 요구보다 바깥이라 익명도 403이다`() {
        // v1.6 SiteApp: SiteManagerAuthAction이 AnonymousCheck보다 바깥이다
        mockMvc.perform(get("/__chain/site-manager")).andExpect(status().isForbidden)
        mockMvc.perform(get("/__chain/site-manager").session(login(newUser()))).andExpect(status().isForbidden)
        val admin = newUser().also { siteAdmins.save(SiteAdmin(admin = it)) }
        mockMvc.perform(get("/__chain/site-manager").session(login(admin))).andExpect(status().isOk)
    }

    @Test
    fun `메서드의 로그인 검사가 걸리지 않아도 클래스의 로그인 요구가 이어서 실행된다`() {
        // 메서드 AnonymousCheck는 통과하지만 부모 클래스의 AnonymousCheck(requiresLogin)가 안쪽에서 익명을 막는다. 메서드가 클래스를 덮어쓰지 않는다.
        mockMvc.perform(get("/__chain/open-then-required")).andExpect(status().isSeeOther)
        mockMvc.perform(get("/__chain/open-then-required").session(login(newUser()))).andExpect(status().isOk)
    }

    @Test
    fun `코드 접근 제한이 클래스의 Git 전용 검사보다 먼저 실행된다`() {
        // v1.6 BranchApp.branches: CodeAccess(메서드) -> OnlyGit(클래스). 비 Git이고 구성원 전용 코드이면 403이다(OnlyGit의 400이 아니다).
        val p = newProject(vcs = "Subversion", codeMemberOnly = true)
        mockMvc.perform(get("/${p.owner}/${p.name}/__chain/code-then-git").session(login(newUser()))).andExpect(status().isForbidden)
        val member = newUser().also { projectUsers.save(ProjectUser(user = it, project = p, role = roles.findById(RoleType.MEMBER.id).get())) }
        mockMvc.perform(get("/${p.owner}/${p.name}/__chain/code-then-git").session(login(member))).andExpect(status().isBadRequest)
    }

    @Test
    fun `비 Git 프로젝트의 풀 리퀘스트 화면은 400이다 (v1_6 PullRequestApp 클래스의 IsOnlyGitAvailable)`() {
        val svn = newProject(vcs = "Subversion")
        val user = newUser().also { projectUsers.save(ProjectUser(user = it, project = svn, role = roles.findById(RoleType.MEMBER.id).get())) }
        val session = login(user)
        val base = "/${svn.owner}/${svn.name}"
        for (path in listOf("$base/pullRequests", "$base/closedPullRequests", "$base/sentPullRequests", "$base/newPullRequestForm", "$base/newFork")) {
            mockMvc.perform(get(path).session(session)).andExpect(status().isBadRequest)
        }
    }
}
