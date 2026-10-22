package dev.yona.domain.notification

import dev.yona.domain.comment.IssueComment
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals

/**
 * 같은 사람이 같은 이슈에 상태를 바꾸고 댓글을 단 알림을 한 메일로 묶는 규칙을 검증한다.
 *
 * 원본: `NotificationMail.mergeEvents`(`app/models/NotificationMail.java:196-282`).
 */
@Transactional
class NotificationMergerTest : AbstractIntegrationTest() {

    @Autowired private lateinit var merger: NotificationMerger
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var entityManager: EntityManager

    private var sequence = 0

    private fun user(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "mg$n", name = "mg$n", email = "mg$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun issue(author: User): Issue {
        val p = projects.save(Project(owner = "mgo${++sequence}", name = "mgp$sequence", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        return issues.save(Issue(project = p, number = 1, title = "제목", body = "b", state = State.OPEN, authorId = author.id, authorLoginId = author.loginId, authorName = author.name, numOfComments = 0, weight = 0, createdDate = Date(), updatedDate = Date()))
    }

    private fun comment(issue: Issue, by: User): IssueComment =
        IssueComment(contents = "댓글", createdDate = Date(), authorId = by.id, authorLoginId = by.loginId, authorName = by.name, issue = issue, projectId = issue.project!!.id!!).also { entityManager.persist(it); entityManager.flush() }

    private fun event(sender: User, type: EventType, resourceType: ResourceType, resourceId: Long, receivers: Set<User>, new: String) =
        NotificationEvent(title = "t", senderId = sender.id, created = Date(), resourceType = resourceType, resourceId = resourceId.toString(), eventType = type, newValue = new, receivers = receivers.toMutableSet())
            .also { entityManager.persist(it); entityManager.flush() }

    @Test
    fun `묶을 것이 없으면 이벤트마다 하나씩 그대로 둔다`() {
        val a = user(); val b = user(); val i = issue(a)
        val first = event(a, EventType.ISSUE_ASSIGNEE_CHANGED, ResourceType.ISSUE_POST, i.id!!, setOf(b), "x")
        val second = event(a, EventType.NEW_ISSUE, ResourceType.ISSUE_POST, i.id!!, setOf(b), "y")
        val merged = merger.merge(listOf(first, second))
        assertEquals(listOf(first, second), merged.map { it.main })
        assertEquals(listOf(listOf(first), listOf(second)), merged.map { it.sources })
    }

    @Test
    fun `같은 사용자의 댓글과 상태 변경은 수신자가 같으면 댓글 문구를 앞세워 한 메일로 묶는다`() {
        val a = user(); val b = user(); val i = issue(a); val c = comment(i, a)
        val commentEvent = event(a, EventType.NEW_COMMENT, ResourceType.ISSUE_COMMENT, c.id!!, setOf(b), "댓글")
        val stateEvent = event(a, EventType.ISSUE_STATE_CHANGED, ResourceType.ISSUE_POST, i.id!!, setOf(b), "closed")
        val merged = merger.merge(listOf(commentEvent, stateEvent))
        assertEquals(1, merged.size)
        assertEquals(stateEvent, merged.single().main)
        assertEquals(listOf(commentEvent, stateEvent), merged.single().sources)
    }

    @Test
    fun `보낸 사람이 다르면 묶지 않는다`() {
        val a = user(); val b = user(); val i = issue(a); val c = comment(i, b)
        val commentEvent = event(b, EventType.NEW_COMMENT, ResourceType.ISSUE_COMMENT, c.id!!, setOf(a), "댓글")
        val stateEvent = event(a, EventType.ISSUE_STATE_CHANGED, ResourceType.ISSUE_POST, i.id!!, setOf(b), "closed")
        assertEquals(2, merger.merge(listOf(commentEvent, stateEvent)).size)
    }

    @Test
    fun `수신자가 다르면 둘 다 받는 사람, 댓글만 받는 사람, 상태 변경만 받는 사람의 셋으로 나눈다`() {
        val a = user(); val both = user(); val onlyComment = user(); val onlyState = user(); val i = issue(a); val c = comment(i, a)
        val commentEvent = event(a, EventType.NEW_COMMENT, ResourceType.ISSUE_COMMENT, c.id!!, setOf(both, onlyComment), "댓글")
        val stateEvent = event(a, EventType.ISSUE_STATE_CHANGED, ResourceType.ISSUE_POST, i.id!!, setOf(both, onlyState), "closed")
        val merged = merger.merge(listOf(commentEvent, stateEvent))
        assertEquals(3, merged.size)
        val byReceivers: Map<Set<String?>, List<NotificationEvent>> = merged.associate { m -> m.receivers.map { it.loginId }.toSet() to m.sources.toList() }
        assertEquals(listOf(commentEvent, stateEvent), byReceivers[setOf(both.loginId)])
        assertEquals(listOf(commentEvent), byReceivers[setOf(onlyComment.loginId)])
        assertEquals(listOf(stateEvent), byReceivers[setOf(onlyState.loginId)])
    }
}
