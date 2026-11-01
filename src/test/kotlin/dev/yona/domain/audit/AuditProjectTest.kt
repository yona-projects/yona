package dev.yona.domain.audit

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectCreateService
import dev.yona.domain.project.ProjectDeletionService
import dev.yona.domain.project.ProjectForm
import dev.yona.domain.project.ProjectMemberService
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectSettingForm
import dev.yona.domain.project.ProjectSettingService
import dev.yona.domain.project.ProjectTransferService
import dev.yona.domain.project.RulesetInput
import dev.yona.domain.project.RulesetService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.domain.webhook.WebhookService
import dev.yona.repository.RepositoryService
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 감사 로그의 프로젝트 이벤트(로드맵 X-9 A5, `decisions.md` D-62)를 검증한다: 만들기, 지우기, 설정 변경(바뀐 항목의 전후), 이전, 구성원과 역할, 규칙 묶음, 웹훅.
 * 변경과 같은 트랜잭션에서 남기며(fail-closed) 거부되거나 바뀐 것이 없으면 남기지 않는다. 비밀 값(웹훅 비밀값)은 남기지 않는다.
 */
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-audit-project"])
class AuditProjectTest : AbstractIntegrationTest() {

    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var createService: ProjectCreateService
    @Autowired private lateinit var deletion: ProjectDeletionService
    @Autowired private lateinit var settings: ProjectSettingService
    @Autowired private lateinit var transfers: ProjectTransferService
    @Autowired private lateinit var members: ProjectMemberService
    @Autowired private lateinit var rulesets: RulesetService
    @Autowired private lateinit var webhooks: WebhookService
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var logs: AuditLogRepository

    private var sequence = 0
    private val stamp = System.nanoTime()

    private fun user(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "ap$n-$stamp", name = "프로젝트$n", email = "ap$n-$stamp@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt), createdDate = Date()))
    }

    private fun newProject(owner: User = user()): Project {
        val n = ++sequence
        val p = projects.save(Project(owner = owner.loginId, name = "pj$n", vcs = "GIT", projectScope = ProjectScope.PUBLIC, createdDate = Date()))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true, wiki = false))
        repositories.createRepository(p)
        return p
    }

    private fun eventsOf(action: String) = logs.findAll().sortedBy { it.id }.filter { it.action == action }

    private fun allText(): String = logs.findAll().joinToString("\n") { listOfNotNull(it.details, it.targetId, it.targetLabel).joinToString(" ") }

    // --- 만들기와 지우기 ---

    @Test
    fun `프로젝트를 만들면 소유자와 이름과 범위를 남기고 프로젝트에 소속시킨다`() {
        val creator = user()
        val created = createService.create(ProjectForm(owner = creator.loginId, name = "created$stamp", overview = "설명", projectScope = "PRIVATE", vcs = "GIT", menus = setOf("code", "wiki")), creator)
        val e = eventsOf("project.created").single()
        assertEquals("project", e.targetType); assertEquals(created.id.toString(), e.targetId); assertEquals("${creator.loginId}/created$stamp", e.targetLabel)
        assertEquals(created.id, e.projectId)
        assertTrue(e.details!!.contains("\"scope\":\"PRIVATE\"") && e.details!!.contains("\"vcs\":\"GIT\""), e.details)
    }

    @Test
    fun `프로젝트를 지우면 이름을 남기고 소속은 지운 프로젝트 번호이다`() {
        val p = newProject(); val id = p.id!!; val label = "${p.owner}/${p.name}"
        deletion.delete(p)
        val e = eventsOf("project.deleted").single()
        assertEquals(label, e.targetLabel); assertEquals(id, e.projectId)
    }

    // --- 설정 ---

    @Test
    fun `설정에서 바뀐 항목만 전후와 함께 남긴다`() {
        val p = newProject()
        settings.update(p, ProjectSettingForm(name = p.name, projectScope = "PRIVATE", menus = setOf("code", "issue", "pullRequest", "review", "milestone", "board", "wiki")), null, null)
        val e = eventsOf("project.settings_changed").single()
        val d = e.details!!
        assertTrue(d.contains("\"projectScope\":{\"from\":\"PUBLIC\",\"to\":\"PRIVATE\"}"), d)
        assertTrue(d.contains("\"wiki\":{\"from\":false,\"to\":true}"), d)
        assertFalse(d.contains("\"name\""), "이름이 같으면 남기지 않는다: $d")
        assertEquals(p.id, e.projectId)
    }

    @Test
    fun `이름을 바꾸면 전후를 남기고 아무것도 안 바뀌면 남기지 않는다`() {
        val p = newProject(); val oldName = p.name!!
        settings.update(p, ProjectSettingForm(name = oldName, projectScope = "PUBLIC", menus = setOf("code", "issue", "pullRequest", "review", "milestone", "board")), null, null)
        assertTrue(eventsOf("project.settings_changed").isEmpty())
        settings.update(p, ProjectSettingForm(name = "renamed$stamp", projectScope = "PUBLIC", menus = setOf("code", "issue", "pullRequest", "review", "milestone", "board")), null, null)
        assertTrue(eventsOf("project.settings_changed").single().details!!.contains("\"name\":{\"from\":\"$oldName\",\"to\":\"renamed$stamp\"}"))
    }

    // --- 이전 ---

    @Test
    fun `이전을 수락하면 이전 소유자와 새 소유자를 남긴다`() {
        val owner = user(); val receiver = user(); val p = newProject(owner)
        val requested = transfers.request(p, owner, receiver.loginId) as ProjectTransferService.RequestResult.Requested
        val oldOwner = p.owner
        val done = transfers.accept(requireNotNull(requested.transfer.id), requireNotNull(requested.transfer.confirmKey), receiver) { true }
        assertTrue(done is ProjectTransferService.AcceptResult.Done)
        val e = eventsOf("project.transferred").single()
        assertTrue(e.details!!.contains("\"from\":\"$oldOwner\"") && e.details!!.contains("\"to\":\"${receiver.loginId}\""), e.details)
        assertEquals(p.id, e.projectId)
    }

    // --- 구성원 ---

    @Test
    fun `구성원을 추가하고 역할을 바꾸고 빼는 일을 남긴다`() {
        val owner = user(); val member = user(); val p = newProject(owner)
        members.addMember(p, owner, member.loginId)
        val added = eventsOf("project.member_added").single()
        assertEquals(member.id.toString(), added.targetId); assertEquals(member.loginId, added.targetLabel); assertEquals(p.id, added.projectId)
        members.changeRole(p, member.id!!, RoleType.MANAGER.id)
        val changed = eventsOf("project.member_role_changed").single()
        assertTrue(changed.details!!.contains("\"to\":\"MANAGER\""), changed.details)
        members.removeMember(p, member.id!!)
        assertEquals(member.id.toString(), eventsOf("project.member_removed").single().targetId)
    }

    @Test
    fun `거부된 구성원 추가와 구성원이 아닌 사용자 빼기는 남기지 않는다`() {
        val owner = user(); val member = user(); val p = newProject(owner)
        members.addMember(p, owner, "nobody-$stamp")
        members.removeMember(p, member.id!!)
        assertTrue(logs.findAll().none { it.action.startsWith("project.member_") })
        members.addMember(p, owner, member.loginId)
        members.addMember(p, owner, member.loginId)
        assertEquals(1, eventsOf("project.member_added").size)
    }

    // --- 규칙 묶음 ---

    @Test
    fun `규칙 묶음을 만들고 고치고 지우는 일을 남긴다`() {
        val p = newProject()
        val created = rulesets.create(p, RulesetInput(name = "main 보호", pattern = "main", approvals = "2"))
        val c = eventsOf("project.ruleset_created").single()
        assertEquals(created.id.toString(), c.targetId); assertEquals("main 보호", c.targetLabel)
        assertTrue(c.details!!.contains("\"pattern\":\"main\"") && c.details!!.contains("\"approvals\":2"), c.details)
        rulesets.update(p, created.id!!, RulesetInput(name = "main 보호", pattern = "main", approvals = "1"))
        assertTrue(eventsOf("project.ruleset_updated").single().details!!.contains("\"approvals\":{\"from\":2,\"to\":1}"))
        assertTrue(rulesets.delete(p, created.id!!))
        assertEquals(created.id.toString(), eventsOf("project.ruleset_deleted").single().targetId)
        assertFalse(rulesets.delete(p, 999999L))
        assertEquals(1, eventsOf("project.ruleset_deleted").size)
    }

    // --- 웹훅 ---

    @Test
    fun `웹훅을 만들고 지우는 일을 남기고 비밀값과 질의 문자열은 남기지 않는다`() {
        val p = newProject()
        val hook = webhooks.create(p, "https://hooks.example.com/path?token=SECRET-IN-QUERY", "my-webhook-secret", true, null)
        val c = eventsOf("project.webhook_created").single()
        assertEquals(hook.id.toString(), c.targetId); assertEquals("https://hooks.example.com/path", c.targetLabel)
        assertTrue(webhooks.delete(p, hook.id!!))
        assertEquals(hook.id.toString(), eventsOf("project.webhook_deleted").single().targetId)
        assertFalse(allText().contains("my-webhook-secret") || allText().contains("SECRET-IN-QUERY"))
    }

    @Test
    fun `없는 웹훅 지우기는 남기지 않는다`() {
        assertFalse(webhooks.delete(newProject(), 999999L))
        assertNotNull(logs.findAll()) ; assertTrue(eventsOf("project.webhook_deleted").isEmpty())
    }
}
