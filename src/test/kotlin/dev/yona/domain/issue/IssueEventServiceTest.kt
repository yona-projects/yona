package dev.yona.domain.issue

import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.State
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.support.AbstractIntegrationTest
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals

/**
 * 이슈 타임라인 이벤트(`issue_event`)의 합치기 규칙을 검증한다. 30초 안에 같은 사용자가 같은 종류를 연달아 바꾸면 중간 값을 건너뛴다.
 *
 * 원본: `app/models/IssueEvent.java`(`add`, `addWithoutSkipEvent`).
 */
@Transactional
class IssueEventServiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: IssueEventService
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var entityManager: EntityManager

    private fun issue(): Issue {
        val project: Project = projects.save(Project(owner = "ev", name = "ep${System.nanoTime()}", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        return issues.save(Issue(project = project, number = 1, title = "t", state = State.OPEN, createdDate = Date(), updatedDate = Date()))
    }

    private fun events(issue: Issue) = entityManager.createQuery("select e from IssueEvent e where e.issue.id = :id order by e.id", IssueEvent::class.java).setParameter("id", issue.id).resultList

    @Test
    fun `이벤트를 기록한다`() {
        val issue = issue()
        service.record(issue, EventType.ISSUE_STATE_CHANGED, "open", "closed", "alice")
        val saved = events(issue).single()
        assertEquals(EventType.ISSUE_STATE_CHANGED, saved.eventType); assertEquals("open", saved.oldValue); assertEquals("closed", saved.newValue); assertEquals("alice", saved.senderLoginId)
    }

    @Test
    fun `같은 사용자가 같은 종류를 연달아 바꾸면 처음 값에서 마지막 값으로 합친다`() {
        val issue = issue()
        service.record(issue, EventType.ISSUE_ASSIGNEE_CHANGED, "a", "b", "alice")
        service.record(issue, EventType.ISSUE_ASSIGNEE_CHANGED, "b", "c", "alice")
        val saved = events(issue).single()
        assertEquals("a", saved.oldValue); assertEquals("c", saved.newValue)
    }

    @Test
    fun `원래대로 되돌리면 두 이벤트를 모두 지운다`() {
        val issue = issue()
        service.record(issue, EventType.ISSUE_STATE_CHANGED, "open", "closed", "alice")
        service.record(issue, EventType.ISSUE_STATE_CHANGED, "closed", "open", "alice")
        assertEquals(0, events(issue).size)
    }

    @Test
    fun `사용자나 종류가 다르면 합치지 않는다`() {
        val issue = issue()
        service.record(issue, EventType.ISSUE_STATE_CHANGED, "open", "closed", "alice")
        service.record(issue, EventType.ISSUE_STATE_CHANGED, "closed", "open", "bob")
        service.record(issue, EventType.ISSUE_ASSIGNEE_CHANGED, "a", "b", "bob")
        assertEquals(3, events(issue).size)
    }

    @Test
    fun `건너뛰지 않는 기록은 중간 값을 남기고 되돌릴 때만 지운다`() {
        val issue = issue()
        service.record(issue, EventType.ISSUE_LABEL_CHANGED, "", "x", "alice", skipWaypoints = false)
        service.record(issue, EventType.ISSUE_LABEL_CHANGED, "", "y", "alice", skipWaypoints = false)
        assertEquals(2, events(issue).size)
        service.record(issue, EventType.ISSUE_LABEL_CHANGED, "y", "", "alice", skipWaypoints = false)
        assertEquals(1, events(issue).size)
    }

    @Test
    fun `30초가 지난 이벤트와는 합치지 않는다`() {
        val issue = issue()
        entityManager.persist(IssueEvent(created = Date(System.currentTimeMillis() - 60_000), senderLoginId = "alice", issue = issue, eventType = EventType.ISSUE_STATE_CHANGED, oldValue = "open", newValue = "closed"))
        entityManager.flush()
        service.record(issue, EventType.ISSUE_STATE_CHANGED, "closed", "open", "alice")
        assertEquals(2, events(issue).size)
    }
}
