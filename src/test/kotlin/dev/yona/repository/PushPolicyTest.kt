package dev.yona.repository

import dev.yona.domain.audit.AuditLogRepository
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.Ruleset
import dev.yona.domain.project.RulesetRepository
import dev.yona.domain.vcs.RefUpdate
import dev.yona.domain.vcs.RefUpdateType
import dev.yona.support.AbstractIntegrationTest
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 푸시 전 검사([PushPolicy])가 Git 없이 [RefUpdate]만으로 동작하는지 확인한다(Mercurial 도입을 위한 공통화 3단계). 종류 이름(`hg`)이 지표와 감사 기록에
 * 그대로 쓰이므로 Mercurial 서빙이 같은 검사를 쓸 수 있다.
 */
@Transactional
class PushPolicyTest : AbstractIntegrationTest() {

    @Autowired private lateinit var policy: PushPolicy
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var rulesets: RulesetRepository
    @Autowired private lateinit var audits: AuditLogRepository
    @Autowired private lateinit var entityManager: EntityManager

    private val a = "a".repeat(40)
    private val b = "b".repeat(40)

    private fun project(configure: Ruleset.() -> Unit = {}): Project {
        val n = counter.incrementAndGet()
        val project = projects.save(Project(owner = "pp", name = "push$n", vcs = "Mercurial", projectScope = ProjectScope.PUBLIC))
        rulesets.save(Ruleset(project = project, name = "master", pattern = "master", createdDate = Date()).apply(configure))
        entityManager.flush(); entityManager.refresh(project)
        return project
    }

    private fun update(ref: String, type: RefUpdateType, old: String = a, new: String = b) = RefUpdate(ref, if (type == RefUpdateType.CREATE) RefUpdate.NULL_ID else old, if (type == RefUpdateType.DELETE) RefUpdate.NULL_ID else new, type)

    private fun noCommits(): (RefUpdate) -> List<Pair<String, String>> = { emptyList() }

    @Test
    fun `내부용 ref에는 밀어 넣지 못하고 감사 기록에 종류 이름이 붙는다`() {
        val project = project()
        val rejections = policy.evaluate(project, null, "hg", listOf(update("refs/yona/pull/1/head", RefUpdateType.UPDATE)), noCommits())
        assertEquals(listOf("reserved"), rejections.map { it.kind })
        assertTrue(rejections.single().message.contains("reserved for internal use"))
        assertTrue(audits.findAll().any { it.action == "hg.push_rejected" && it.projectId == project.id })
    }

    @Test
    fun `보호 규칙이 강제 푸시와 삭제를 막고 보호하지 않은 브랜치는 막지 않는다`() {
        val project = project { blockForcePushes = true; restrictDeletions = true }
        val forced = policy.evaluate(project, null, "hg", listOf(update("refs/heads/master", RefUpdateType.FORCE_UPDATE)), noCommits())
        assertEquals(listOf("ruleset_force"), forced.map { it.kind }); assertTrue(forced.single().message.contains("force pushes to 'master' are not allowed"))
        val deleted = policy.evaluate(project, null, "hg", listOf(update("refs/heads/master", RefUpdateType.DELETE)), noCommits())
        assertEquals(listOf("ruleset_deletion"), deleted.map { it.kind })
        // 빨리 감기 갱신과 보호하지 않는 브랜치, 태그는 막지 않는다
        val allowed = policy.evaluate(
            project, null, "hg",
            listOf(update("refs/heads/master", RefUpdateType.UPDATE), update("refs/heads/feature", RefUpdateType.FORCE_UPDATE), update("refs/tags/v1", RefUpdateType.CREATE)), noCommits()
        )
        assertTrue(allowed.isEmpty())
    }

    @Test
    fun `서명 커밋을 요구하는 브랜치는 확인 결과가 나쁘면 거부하고 삭제는 검사하지 않는다`() {
        // 규칙의 기본값은 삭제와 강제 푸시를 막는다. 서명 검사만 보려고 둘을 끈다
        val project = project { requireSignedCommits = true; restrictDeletions = false; blockForcePushes = false }
        val bad = policy.evaluate(project, null, "hg", listOf(update("refs/heads/master", RefUpdateType.UPDATE))) { listOf("abc1234" to "unsigned") }
        assertEquals(listOf("ruleset_signed_commits"), bad.map { it.kind }); assertTrue(bad.single().message.contains("abc1234 (unsigned)"))
        // 확인 중 오류가 나면 안전하게 거부한다
        val failed = policy.evaluate(project, null, "hg", listOf(update("refs/heads/master", RefUpdateType.UPDATE))) { error("서명 읽기 실패") }
        assertEquals(listOf("ruleset_signed_commits"), failed.map { it.kind }); assertTrue(failed.single().message.contains("? (error)"))
        // 모두 Verified이면 통과하고, 삭제는 커밋이 없어 확인하지 않는다
        assertTrue(policy.evaluate(project, null, "hg", listOf(update("refs/heads/master", RefUpdateType.UPDATE)), noCommits()).isEmpty())
        assertTrue(policy.evaluate(project, null, "hg", listOf(update("refs/heads/master", RefUpdateType.DELETE))) { listOf("x" to "unsigned") }.isEmpty())
    }

    private companion object {
        val counter = AtomicInteger()
    }
}
