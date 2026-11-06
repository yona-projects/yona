package dev.yona.repository

import dev.yona.domain.audit.AuditLogRepository
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.PushedBranchRepository
import dev.yona.domain.vcs.RefUpdate
import dev.yona.domain.vcs.RefUpdateType
import dev.yona.support.AbstractIntegrationTest
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 푸시 뒤 처리([PushEvents])가 [RefUpdate]만으로 마지막 푸시 시각, 최근 푸시한 브랜치, 감사 기록을 처리하는지 확인한다(Mercurial 도입을 위한 공통화 3단계).
 * 감사 기록의 행동 이름 앞부분은 종류 이름(`hg`)을 따른다. 이슈 언급, 풀 리퀘스트 확인, 새 커밋 알림은 [PostReceiveJobsTest]가 본다.
 */
class PushEventsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var events: PushEvents
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var pushedBranches: PushedBranchRepository
    @Autowired private lateinit var audits: AuditLogRepository
    @Autowired private lateinit var transactions: TransactionTemplate
    @Autowired private lateinit var repositories: RepositoryService

    private val a = "a".repeat(40)
    private val b = "b".repeat(40)

    private fun project(): Project {
        val n = counter.incrementAndGet()
        val project = projects.save(Project(owner = "pe", name = "events$n", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        repositories.createRepository(project)
        return project
    }

    @Test
    fun `푸시한 변경마다 종류 이름이 붙은 감사 기록과 마지막 푸시 시각과 최근 브랜치가 남는다`() {
        val project = project()
        events.afterPush(
            project, null, "hg",
            listOf(
                RefUpdate("refs/heads/feature", RefUpdate.NULL_ID, b, RefUpdateType.CREATE),
                RefUpdate("refs/heads/master", a, b, RefUpdateType.FORCE_UPDATE),
                RefUpdate("refs/tags/v1", a, RefUpdate.NULL_ID, RefUpdateType.DELETE)
            )
        )
        val actions = audits.findAll().filter { it.projectId == project.id }.map { it.action }
        // 만들거나 갱신한 ref는 한 건으로, 강제 푸시와 삭제는 따로 남는다
        assertEquals(1, actions.count { it == "hg.push" }); assertEquals(1, actions.count { it == "hg.force_push" }); assertEquals(1, actions.count { it == "hg.ref_deleted" })
        assertTrue(actions.none { it.startsWith("git.") }, "종류 이름이 hg이므로 git.* 기록은 없어야 한다: $actions")
        val deleted = audits.findAll().single { it.action == "hg.ref_deleted" && it.projectId == project.id }
        assertTrue(deleted.details!!.contains("\"kind\":\"tag\""), deleted.details)
        // 강제 푸시는 Git의 푸시 명령 종류 이름으로 기록한다(기존 기록과 같은 모양)
        assertTrue(audits.findAll().single { it.action == "hg.force_push" && it.projectId == project.id }.details!!.contains("UPDATE_NONFASTFORWARD"))
        transactions.executeWithoutResult {
            assertNotNull(projects.findById(requireNotNull(project.id)).get().lastPushedDate)
            val recent = pushedBranches.findByProjectIdOrderByPushedDateDesc(requireNotNull(project.id)).map { it.name }
            assertTrue("refs/heads/feature" in recent && "refs/heads/master" in recent, recent.toString())
        }
    }

    private companion object {
        val counter = AtomicInteger()
    }
}
