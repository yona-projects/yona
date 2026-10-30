package dev.yona.web.project

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.project.Ruleset
import dev.yona.domain.project.RulesetRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.RepositoryService
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.selectFirst
import org.jsoup.Jsoup
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 프로젝트 설정의 규칙 묶음 목록과 편집 화면, 저장을 검증한다(로드맵 X-34, `decisions.md` D-46).
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-ruleset"])
class RulesetWebTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var rulesets: RulesetRepository

    private var sequence = 0

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "rs$n", name = "rs$n", email = "rs$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234")) }

    private fun newProject(vcs: String = "GIT"): Project {
        val n = ++sequence
        val project = projects.save(Project(owner = "rso$n", name = "rsp$n", vcs = vcs, projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        if (vcs == "GIT") repositories.createRepository(project)
        return project
    }

    private fun member(project: Project, role: RoleType): User = newUser().also { projectUsers.save(ProjectUser(user = it, project = project, role = roles.findById(role.id).get())) }

    private fun url(project: Project, tail: String = "") = "/${project.owner}/${project.name}/setting/rulesets$tail"

    private fun send(project: Project, session: MockHttpSession?, tail: String, vararg params: Pair<String, String>) =
        mockMvc.perform(post(url(project, tail)).also { r -> session?.let { r.session(it) }; params.forEach { (k, v) -> r.param(k, v) } })

    private fun page(project: Project, session: MockHttpSession, tail: String = "") =
        Jsoup.parse(mockMvc.perform(get(url(project, tail)).session(session).header("Accept-Language", "ko-KR")).andExpect(status().isOk).andReturn().response.contentAsString)

    private val all = arrayOf(
        "restrictDeletions" to "true", "blockForcePushes" to "true", "requirePullRequest" to "true", "requireLinearHistory" to "true",
        "requireNoChangesRequested" to "true", "requireConversationResolution" to "true", "bypassAdmins" to "true"
    )

    // --- 저장 ---

    @Test
    fun `규칙 묶음을 만들고 고치고 지운다`() {
        val project = newProject(); val session = login(member(project, RoleType.MANAGER))
        send(project, session, "", "name" to "main protect", "enforcement" to "active", "pattern" to "master", "approvals" to "2", *all).andExpect(status().isSeeOther)
        val saved = rulesets.findByProjectIdAndName(project.id!!, "main protect")!!
        assertEquals("master", saved.pattern); assertEquals(2, saved.requiredApprovals); assertTrue(saved.isActive); assertNotNull(saved.createdDate)
        assertEquals(List(7) { true }, listOf(saved.restrictDeletions, saved.blockForcePushes, saved.requirePullRequest, saved.requireLinearHistory, saved.requireNoChangesRequested, saved.requireConversationResolution, saved.bypassAdmins))
        send(project, session, "/${saved.id}", "name" to "main protect", "enforcement" to "disabled", "pattern" to "main", "approvals" to "0").andExpect(status().isSeeOther)
        val updated = rulesets.findById(saved.id!!).get()
        assertEquals("main", updated.pattern); assertFalse(updated.isActive); assertEquals(0, updated.requiredApprovals)
        assertEquals(List(7) { false }, listOf(updated.restrictDeletions, updated.blockForcePushes, updated.requirePullRequest, updated.requireLinearHistory, updated.requireNoChangesRequested, updated.requireConversationResolution, updated.bypassAdmins), "체크하지 않은 조건은 꺼진다")
        assertEquals(1, projects.findById(project.id!!).get().rulesets.size)
        send(project, session, "/${saved.id}/delete").andExpect(status().isSeeOther)
        assertNull(rulesets.findById(saved.id!!).orElse(null)); assertEquals(0, projects.findById(project.id!!).get().rulesets.size)
    }

    @Test
    fun `승인 수를 비우면 0이다`() {
        val project = newProject(); val session = login(member(project, RoleType.MANAGER))
        send(project, session, "", "name" to "a", "enforcement" to "active", "pattern" to "master", "approvals" to "").andExpect(status().isSeeOther)
        assertEquals(0, rulesets.findByProjectIdAndName(project.id!!, "a")!!.requiredApprovals)
    }

    @Test
    fun `잘못된 입력은 400이고 편집 화면을 다시 보이며 저장하지 않는다`() {
        val project = newProject(); val session = login(member(project, RoleType.MANAGER))
        send(project, session, "", "name" to "taken", "enforcement" to "active", "pattern" to "master").andExpect(status().isSeeOther)
        listOf(
            arrayOf("name" to "", "pattern" to "x"), arrayOf("name" to "taken", "pattern" to "x"), arrayOf("name" to "n".repeat(256), "pattern" to "x"),
            arrayOf("name" to "n", "pattern" to ""), arrayOf("name" to "n", "pattern" to "a b"), arrayOf("name" to "n", "pattern" to "refs/heads/x"), arrayOf("name" to "n", "pattern" to "x".repeat(256)),
            arrayOf("name" to "n", "pattern" to "x", "approvals" to "-1"), arrayOf("name" to "n", "pattern" to "x", "approvals" to "101"), arrayOf("name" to "n", "pattern" to "x", "approvals" to "abc"),
            arrayOf("name" to "n", "pattern" to "x", "enforcement" to "evaluate")
        ).forEach { params ->
            val result = send(project, session, "", *params).andExpect(status().isBadRequest).andReturn()
            assertTrue(Jsoup.parse(result.response.contentAsString).select("#rulesetErrors").isNotEmpty(), params.joinToString())
        }
        assertEquals(listOf("taken"), rulesets.findByProjectIdOrderByNameAsc(project.id!!).map { it.name })
    }

    @Test
    fun `같은 이름은 자기 자신으로 고칠 때만 허용한다`() {
        val project = newProject(); val session = login(member(project, RoleType.MANAGER))
        send(project, session, "", "name" to "a", "enforcement" to "active", "pattern" to "master")
        send(project, session, "", "name" to "b", "enforcement" to "active", "pattern" to "dev")
        val a = rulesets.findByProjectIdAndName(project.id!!, "a")!!
        send(project, session, "/${a.id}", "name" to "a", "enforcement" to "active", "pattern" to "main").andExpect(status().isSeeOther)
        send(project, session, "/${a.id}", "name" to "b", "enforcement" to "active", "pattern" to "main").andExpect(status().isBadRequest)
    }

    @Test
    fun `다른 프로젝트의 규칙 묶음은 보거나 고치거나 지울 수 없다`() {
        val project = newProject(); val other = newProject(); val session = login(member(project, RoleType.MANAGER))
        send(other, login(member(other, RoleType.MANAGER)), "", "name" to "theirs", "enforcement" to "active", "pattern" to "master")
        val theirs = rulesets.findByProjectIdAndName(other.id!!, "theirs")!!
        mockMvc.perform(get(url(project, "/${theirs.id}")).session(session)).andExpect(status().isNotFound)
        send(project, session, "/${theirs.id}", "name" to "x", "enforcement" to "active", "pattern" to "x").andExpect(status().isNotFound)
        send(project, session, "/${theirs.id}/delete").andExpect(status().isNotFound)
        assertNotNull(rulesets.findById(theirs.id!!).orElse(null)); assertEquals("theirs", rulesets.findById(theirs.id!!).get().name)
    }

    @Test
    fun `프로젝트 관리자가 아니면 쓸 수 없다`() {
        val project = newProject(); val plain = login(member(project, RoleType.MEMBER))
        mockMvc.perform(get(url(project)).session(plain)).andExpect(status().isForbidden)
        send(project, plain, "", "name" to "a", "enforcement" to "active", "pattern" to "master").andExpect(status().isForbidden)
        send(project, null, "", "name" to "a", "enforcement" to "active", "pattern" to "master").andExpect(status().is4xxClientError)
        assertEquals(0, rulesets.findByProjectIdOrderByNameAsc(project.id!!).size)
    }

    @Test
    fun `Git이 아닌 프로젝트에는 규칙 묶음 화면이 없다`() {
        val project = newProject("SVN"); val session = login(member(project, RoleType.MANAGER))
        mockMvc.perform(get(url(project)).session(session)).andExpect(status().isBadRequest)
    }

    // --- 화면 ---

    @Test
    fun `목록은 이름순으로 규칙 묶음과 대상과 상태와 규칙 요약을 보이고 설정 메뉴에 규칙 묶음 탭이 있다`() {
        val project = newProject(); val session = login(member(project, RoleType.MANAGER))
        send(project, session, "", "name" to "b release", "enforcement" to "disabled", "pattern" to "release/*", "approvals" to "1")
        send(project, session, "", "name" to "a main", "enforcement" to "active", "pattern" to "master", "approvals" to "2", "requirePullRequest" to "true", "requireConversationResolution" to "true", "blockForcePushes" to "true")
        val doc = page(project, session)
        val rows = doc.select("#rulesetList tr.ruleset-row")
        assertEquals(listOf("a main", "b release"), rows.map { it.selectFirst("td.ruleset-name a")!!.text() })
        assertEquals(listOf("master", "release/*"), rows.map { it.selectFirst("td.ruleset-target code")!!.text() })
        assertEquals(listOf("활성", "비활성"), rows.map { it.selectFirst("td.ruleset-enforcement")!!.text() })
        val summary = rows[0].selectFirst("td.ruleset-rules")!!.text()
        assertTrue(summary.contains("승인 2명") && summary.contains("풀 리퀘스트 필요") && summary.contains("대화 해결") && summary.contains("강제 푸시 차단"), summary)
        assertEquals(url(project, "/new"), doc.selectFirst("a#newRuleset")!!.attr("href"))
        assertEquals(url(project), doc.selectFirst("#subMenuRuleset a")!!.attr("href")); assertNotNull(doc.selectFirst("#subMenuRuleset.active"))
        assertTrue(doc.select("#rulesetMigrated").isEmpty())
    }

    @Test
    fun `규칙 묶음이 없으면 비어 있다는 안내를 보인다`() {
        val project = newProject(); val session = login(member(project, RoleType.MANAGER))
        assertEquals("만든 규칙 묶음이 없습니다.", page(project, session).selectFirst("tr.ruleset-empty")!!.text())
    }

    @Test
    fun `이전한 리뷰어 설정 규칙 묶음이 있으면 안내를 보인다`() {
        val project = newProject(); val session = login(member(project, RoleType.MANAGER))
        send(project, session, "", "name" to dev.yona.domain.project.ReviewerSettingMigration.MIGRATED_NAME, "enforcement" to "active", "pattern" to "**", "approvals" to "2")
        assertTrue(page(project, session).selectFirst("#rulesetMigrated")!!.text().contains("이전된 리뷰어 설정"))
    }

    @Test
    fun `새 규칙 묶음 화면은 GitHub 기본처럼 삭제 제한과 강제 푸시 차단만 켠 상태이다`() {
        val project = newProject(); val session = login(member(project, RoleType.MANAGER))
        val doc = page(project, session, "/new")
        assertEquals(url(project), doc.selectFirst("form#rulesetForm")!!.attr("action"))
        val checked = doc.select("form#rulesetForm input[type=checkbox][checked]").map { it.attr("name") }.toSet()
        assertEquals(setOf("restrictDeletions", "blockForcePushes"), checked)
        assertEquals(setOf("bypassAdmins", "restrictDeletions", "requireLinearHistory", "requirePullRequest", "requireNoChangesRequested", "requireConversationResolution", "blockForcePushes"),
            doc.select("form#rulesetForm input[type=checkbox]").map { it.attr("name") }.toSet())
        assertEquals("active", doc.selectFirst("input[name=enforcement][checked]")!!.attr("value"))
        assertTrue(doc.select("form#rulesetDeleteForm").isEmpty(), "새 규칙 묶음에는 삭제가 없다")
    }

    @Test
    fun `편집 화면은 저장한 값을 채우고 삭제 단추가 있다`() {
        val project = newProject(); val session = login(member(project, RoleType.MANAGER))
        send(project, session, "", "name" to "main protect", "enforcement" to "disabled", "pattern" to "release/*", "approvals" to "3", "requireLinearHistory" to "true", "bypassAdmins" to "true")
        val saved = rulesets.findByProjectIdAndName(project.id!!, "main protect")!!
        val doc = page(project, session, "/${saved.id}")
        assertEquals(url(project, "/${saved.id}"), doc.selectFirst("form#rulesetForm")!!.attr("action"))
        assertEquals("main protect", doc.selectFirst("#rulesetName")!!.attr("value")); assertEquals("release/*", doc.selectFirst("#rulesetPattern")!!.attr("value"))
        assertEquals("3", doc.selectFirst("#rulesetApprovals")!!.attr("value")); assertEquals("disabled", doc.selectFirst("input[name=enforcement][checked]")!!.attr("value"))
        assertEquals(setOf("requireLinearHistory", "bypassAdmins"), doc.select("form#rulesetForm input[type=checkbox][checked]").map { it.attr("name") }.toSet())
        assertEquals(url(project, "/${saved.id}/delete"), doc.selectFirst("form#rulesetDeleteForm")!!.attr("action"))
    }

    @Test
    fun `설정 폼에는 리뷰어 수와 규칙 입력이 없고 승인 해제 설정만 남는다`() {
        val project = newProject(); val session = login(member(project, RoleType.MANAGER))
        val doc = Jsoup.parse(mockMvc.perform(get("/${project.owner}/${project.name}/settingform").session(session).header("Accept-Language", "ko-KR")).andExpect(status().isOk).andReturn().response.contentAsString)
        assertTrue(doc.select("input[name=isUsingReviewerCount], [name=defaultReviewerCount], input[name=protectionRulesSent], #protectionRules").isEmpty())
        assertEquals(1, doc.select("#reviewerCountSettingPanel input[name=isDismissingStaleApprovals][checked]").size)
    }

    private fun optionsHidden(doc: org.jsoup.nodes.Document) = doc.selectFirst("#rulesetPullRequestOptions")!!.attr("style").replace(" ", "").contains("display:none")

    @Test
    fun `풀 리퀘스트 필요를 끄고 승인 수와 병합 조건이 기본값이면 하위 항목을 접어 둔다 (GitHub 방식)`() {
        val project = newProject(); val session = login(member(project, RoleType.MANAGER))
        assertTrue(optionsHidden(page(project, session, "/new")), "새 규칙 묶음은 접혀 있다")
        send(project, session, "", "name" to "plain", "enforcement" to "active", "pattern" to "master")
        assertTrue(optionsHidden(page(project, session, "/${rulesets.findByProjectIdAndName(project.id!!, "plain")!!.id}")))
    }

    @Test
    fun `풀 리퀘스트 필요를 켰거나 하위 항목이 켜져 있으면 펼쳐 보인다 (v1_6에서 옮긴 값이 숨지 않게)`() {
        val project = newProject(); val session = login(member(project, RoleType.MANAGER))
        listOf(
            "pr" to arrayOf("requirePullRequest" to "true"), "approvals" to arrayOf("approvals" to "2"),
            "changes" to arrayOf("requireNoChangesRequested" to "true"), "conversation" to arrayOf("requireConversationResolution" to "true")
        ).forEach { (name, params) ->
            send(project, session, "", "name" to name, "enforcement" to "active", "pattern" to "master", *params)
            val id = rulesets.findByProjectIdAndName(project.id!!, name)!!.id
            assertFalse(optionsHidden(page(project, session, "/$id")), name)
        }
    }

    @Test
    fun `편집 화면에는 하위 항목을 펼치고 접는 스크립트가 있다`() {
        val project = newProject(); val session = login(member(project, RoleType.MANAGER))
        val html = mockMvc.perform(get(url(project, "/new")).session(session)).andReturn().response.contentAsString
        assertTrue(html.contains("rulesetPullRequestOptions") && html.contains("addEventListener"), "펼침 스크립트")
    }

    @Test
    fun `편집 화면은 다른 설정 화면과 같은 카드 구조와 부트스트랩 요소를 쓴다`() {
        val project = newProject(); val session = login(member(project, RoleType.MANAGER))
        val doc = page(project, session, "/new")
        val card = doc.selectFirst("form#rulesetForm .bubble-wrap.gray")!!
        assertEquals(listOf("규칙 묶음 이름", "적용 상태", "바이패스", "대상 브랜치", "규칙"), card.select(".box-wrap .cu-label").map { it.text() })
        assertEquals(1, card.select(".box-wrap.bottom button#saveRuleset").size, "저장 단추는 카드 아래 칸에 있다")
        assertEquals(2, card.select("input[type=radio][name=enforcement]").size)
        assertTrue(card.select("label.checkbox").size >= 6, "부트스트랩 checkbox 라벨")
        assertTrue(card.select(".help-block").isNotEmpty(), "부트스트랩 help-block")
        assertTrue(card.select("[style]").none { it.attr("style").contains("background") || it.attr("style").contains("margin") && it.id() != "rulesetPullRequestOptions" }, "꾸밈은 클래스로 하고 인라인 스타일은 쓰지 않는다")
    }

    @Test
    fun `목록은 부트스트랩 표를 쓴다`() {
        val project = newProject(); val session = login(member(project, RoleType.MANAGER))
        send(project, session, "", "name" to "a", "enforcement" to "active", "pattern" to "master")
        val table = page(project, session).selectFirst("table#rulesetList")!!
        assertTrue(table.hasClass("table") && table.hasClass("table-striped") && table.hasClass("table-hover"), table.className())
        assertTrue(table.select("[style]").isEmpty(), "인라인 스타일은 쓰지 않는다")
    }
}
