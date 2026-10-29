package dev.yona.web.project

import dev.yona.support.selectFirst
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.BranchProtectionRuleRepository
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
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 프로젝트 설정의 브랜치 보호 규칙 관리를 검증한다(로드맵 X-32, `decisions.md` D-42).
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-branch-protection"])
class BranchProtectionWebTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var rules: BranchProtectionRuleRepository

    private var sequence = 0

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "bp$n", name = "bp$n", email = "bp$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234")) }

    private fun newProject(): Project {
        val n = ++sequence
        val project = projects.save(Project(owner = "bpo$n", name = "bpp$n", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        repositories.createRepository(project)
        return project
    }

    private fun member(project: Project, role: RoleType): User = newUser().also { projectUsers.save(ProjectUser(user = it, project = project, role = roles.findById(role.id).get())) }

    private fun url(project: Project, tail: String) = "/${project.owner}/${project.name}$tail"

    private fun create(project: Project, session: MockHttpSession, vararg params: Pair<String, String>) =
        mockMvc.perform(post(url(project, "/setting/protections")).session(session).also { r -> params.forEach { (k, v) -> r.param(k, v) } })

    @Test
    fun `규칙을 만들고 같은 패턴을 다시 보내면 고치며 설정 폼으로 돌아간다`() {
        val project = newProject(); val session = login(member(project, RoleType.MANAGER))
        create(project, session, "pattern" to "master", "requiredApprovals" to "2", "enforceAdmins" to "true").andExpect(status().isSeeOther)
        val saved = rules.findByProjectIdAndPattern(project.id!!, "master")!!
        assertEquals(2, saved.requiredApprovals); assertEquals(true, saved.enforceAdmins); assertNotNull(saved.createdDate)
        create(project, session, "pattern" to "master", "requiredApprovals" to "3").andExpect(status().isSeeOther)
        val updated = rules.findByProjectIdOrderByPatternAsc(project.id!!).single()
        assertEquals(3, updated.requiredApprovals); assertEquals(false, updated.enforceAdmins, "체크하지 않으면 끔이다")
    }

    @Test
    fun `잘못된 입력은 400이고 저장하지 않는다`() {
        val project = newProject(); val session = login(member(project, RoleType.MANAGER))
        listOf(
            arrayOf("pattern" to "", "requiredApprovals" to "1"), arrayOf("pattern" to "a b", "requiredApprovals" to "1"), arrayOf("pattern" to "refs/heads/x", "requiredApprovals" to "1"),
            arrayOf("pattern" to "x".repeat(256), "requiredApprovals" to "1"), arrayOf("pattern" to "master", "requiredApprovals" to "0"),
            arrayOf("pattern" to "master", "requiredApprovals" to "abc"), arrayOf("pattern" to "master", "requiredApprovals" to "101")
        ).forEach { params -> create(project, session, *params).andExpect(status().isBadRequest) }
        assertEquals(0, rules.findByProjectIdOrderByPatternAsc(project.id!!).size)
    }

    @Test
    fun `규칙을 지울 수 있고 다른 프로젝트의 규칙은 지울 수 없다`() {
        val project = newProject(); val other = newProject(); val session = login(member(project, RoleType.MANAGER))
        create(project, session, "pattern" to "release/*", "requiredApprovals" to "1")
        val mine = rules.findByProjectIdOrderByPatternAsc(project.id!!).single()
        create(other, login(member(other, RoleType.MANAGER)), "pattern" to "x", "requiredApprovals" to "1")
        val theirs = rules.findByProjectIdOrderByPatternAsc(other.id!!).single()
        mockMvc.perform(post(url(project, "/setting/protections/${theirs.id}/delete")).session(session)).andExpect(status().isNotFound)
        assertNotNull(rules.findById(theirs.id!!).orElse(null))
        mockMvc.perform(post(url(project, "/setting/protections/${mine.id}/delete")).session(session)).andExpect(status().isSeeOther)
        assertNull(rules.findById(mine.id!!).orElse(null))
    }

    @Test
    fun `관리자가 아니면 만들거나 지울 수 없다`() {
        val project = newProject(); val plain = login(member(project, RoleType.MEMBER))
        create(project, plain, "pattern" to "master", "requiredApprovals" to "1").andExpect(status().isForbidden)
        mockMvc.perform(post(url(project, "/setting/protections")).param("pattern", "master").param("requiredApprovals", "1")).andExpect(status().is4xxClientError)
        assertEquals(0, rules.findByProjectIdOrderByPatternAsc(project.id!!).size)
    }

    // --- 설정 폼 한 번의 저장으로 규칙을 맞춘다 (D-44) ---

    private fun saveSetting(project: Project, session: MockHttpSession, rows: List<Triple<String, String, String>>?, vararg extra: Pair<String, String>) =
        mockMvc.perform(multipart("/${project.owner}/${project.name}/setting").session(session).also { r ->
            r.param("id", project.id.toString()); r.param("name", project.name!!)
            listOf("code", "issue", "pullRequest", "review", "milestone", "board").forEach { r.param(it, "true") }
            extra.forEach { (k, v) -> r.param(k, v) }
            if (rows != null) {
                r.param("protectionRulesSent", "true")
                rows.forEach { (pattern, approvals, enforce) -> r.param("protectionPattern", pattern); r.param("protectionApprovals", approvals); r.param("protectionEnforce", enforce) }
            }
        })

    private fun savedRules(project: Project) = rules.findByProjectIdOrderByPatternAsc(project.id!!)

    @Test
    fun `설정 저장 한 번으로 규칙을 만들고 고치고 지운다`() {
        val project = newProject(); val session = login(member(project, RoleType.MANAGER))
        saveSetting(project, session, listOf(Triple("master", "2", "true"), Triple("release/*", "1", "false"))).andExpect(status().isSeeOther)
        assertEquals(listOf("master" to 2, "release/*" to 1), savedRules(project).map { it.pattern to it.requiredApprovals })
        assertEquals(listOf(true, false), savedRules(project).map { it.enforceAdmins })
        val masterId = savedRules(project).first().id
        saveSetting(project, session, listOf(Triple("master", "3", "false"))).andExpect(status().isSeeOther)
        val left = savedRules(project).single()
        assertEquals(masterId, left.id, "같은 패턴은 새로 만들지 않고 고친다"); assertEquals(3, left.requiredApprovals); assertEquals(false, left.enforceAdmins)
        assertEquals(1, projects.findById(project.id!!).get().branchProtectionRules.size, "프로젝트가 들고 있는 목록도 맞는다")
        saveSetting(project, session, emptyList()).andExpect(status().isSeeOther)
        assertEquals(0, savedRules(project).size, "규칙 줄을 모두 지우고 저장하면 전부 지운다")
    }

    @Test
    fun `규칙 항목을 보내지 않으면 규칙을 건드리지 않고 빈 줄은 무시한다`() {
        val project = newProject(); val session = login(member(project, RoleType.MANAGER))
        saveSetting(project, session, listOf(Triple("master", "2", "false")))
        saveSetting(project, session, null).andExpect(status().isSeeOther)
        assertEquals(1, savedRules(project).size)
        saveSetting(project, session, listOf(Triple("master", "2", "false"), Triple("", "1", "false"), Triple("  ", "", "false"))).andExpect(status().isSeeOther)
        assertEquals(listOf("master"), savedRules(project).map { it.pattern })
    }

    @Test
    fun `잘못된 규칙이 하나라도 있으면 400이고 아무것도 바꾸지 않는다`() {
        val project = newProject(); val session = login(member(project, RoleType.MANAGER))
        saveSetting(project, session, listOf(Triple("master", "2", "false")))
        listOf(
            listOf(Triple("a", "1", "false"), Triple("a", "2", "false")), listOf(Triple("a b", "1", "false")), listOf(Triple("refs/heads/x", "1", "false")),
            listOf(Triple("x", "0", "false")), listOf(Triple("x", "101", "false")), listOf(Triple("x", "abc", "false")), listOf(Triple("x", "", "false"))
        ).forEach { saveSetting(project, session, it).andExpect(status().isBadRequest) }
        assertEquals(listOf("master" to 2), savedRules(project).map { it.pattern to it.requiredApprovals })
    }

    @Test
    fun `관리자가 아니면 설정 저장으로 규칙을 바꿀 수 없다`() {
        val project = newProject(); val plain = login(member(project, RoleType.MEMBER))
        saveSetting(project, plain, listOf(Triple("master", "1", "false"))).andExpect(status().isForbidden)
        assertEquals(0, savedRules(project).size)
    }

    @Test
    fun `설정 화면은 리뷰어 영역 안에 설정 폼의 입력칸으로 규칙을 보인다`() {
        val project = newProject(); val session = login(member(project, RoleType.MANAGER))
        saveSetting(project, session, listOf(Triple("release/*", "2", "true"), Triple("master", "1", "false")))
        val doc = Jsoup.parse(mockMvc.perform(get(url(project, "/settingform")).session(session).header("Accept-Language", "ko-KR")).andExpect(status().isOk).andReturn().response.contentAsString)
        assertTrue(doc.select("#branchProtectionForm, #branchProtectionCard").isEmpty(), "별도의 카드와 폼은 없다")
        val panel = doc.selectFirst("form#saveSetting #reviewerCountSettingPanel")!!
        assertEquals(1, panel.select("input[name=protectionRulesSent]").size)
        val rows = panel.select("#protectionRules .protection-row")
        assertEquals(listOf("master", "release/*"), rows.map { it.selectFirst("input[name=protectionPattern]")!!.attr("value") })
        assertEquals(listOf("1", "2"), rows.map { it.selectFirst("input[name=protectionApprovals]")!!.attr("value") })
        assertEquals(listOf("false", "true"), rows.map { it.selectFirst("input[name=protectionEnforce]")!!.attr("value") })
        assertEquals(1, rows[1].select("input.protection-enforce-box[checked]").size); assertEquals(0, rows[0].select("input.protection-enforce-box[checked]").size)
        assertEquals(2, rows.count { it.selectFirst("button.protection-remove") != null })
        assertNotNull(panel.selectFirst("template#protectionRowTemplate .protection-row"), "새 줄의 본보기")
        assertNotNull(panel.selectFirst("button#protectionAdd"))
        assertEquals(2, panel.select("input[name=isUsingReviewerCount]").size); assertEquals(1, panel.select("[data-name=defaultReviewerCount]").size)
        assertEquals(1, panel.select("input[name=isDismissingStaleApprovals][checked]").size, "승인 해제 설정은 같은 영역에 남아 있다")
    }
}
