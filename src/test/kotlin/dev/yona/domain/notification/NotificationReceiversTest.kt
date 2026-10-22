package dev.yona.domain.notification

import dev.yona.domain.comment.IssueComment
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Assignee
import dev.yona.domain.issue.AssigneeRepository
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.misc.Unwatch
import dev.yona.domain.misc.UnwatchRepository
import dev.yona.domain.misc.Watch
import dev.yona.domain.misc.WatchRepository
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
 * 댓글 알림의 필수 수신자 계산을 검증한다.
 *
 * 원본: `app/models/NotificationEvent.java:1075-1103`(`getMandatoryReceivers(Comment, EventType)`), `app/models/UserProjectNotification.java:148-167`.
 */
@Transactional
class NotificationReceiversTest : AbstractIntegrationTest() {

    @Autowired private lateinit var receivers: NotificationReceivers
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var assignees: AssigneeRepository
    @Autowired private lateinit var watches: WatchRepository
    @Autowired private lateinit var unwatches: UnwatchRepository
    @Autowired private lateinit var projectNotifications: UserProjectNotificationRepository
    @Autowired private lateinit var entityManager: EntityManager

    private var sequence = 0

    private fun user(state: UserState = UserState.ACTIVE): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "nr$n", name = "nr$n", email = "nr$n@example.com", state = state, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun project(): Project = projects.save(Project(owner = "nro${++sequence}", name = "nrp$sequence", vcs = "GIT", projectScope = ProjectScope.PUBLIC, lastIssueNumber = 1))

    private fun issue(project: Project, author: User, assignee: User? = null): Issue = issues.save(
        Issue(
            project = project, number = 1, title = "t", body = "b", state = State.OPEN, authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
            assignee = assignee?.let { assignees.save(Assignee(user = it, project = project)) }, numOfComments = 0, weight = 0, createdDate = Date(), updatedDate = Date()
        )
    )

    private fun comment(issue: Issue, author: User, contents: String = "c", parent: IssueComment? = null): IssueComment {
        val c = IssueComment(contents = contents, createdDate = Date(), authorId = author.id, authorLoginId = author.loginId, authorName = author.name, issue = issue, projectId = issue.project!!.id!!, parentComment = parent)
        entityManager.persist(c); entityManager.flush()
        return c
    }

    private fun watch(u: User, i: Issue) { watches.save(Watch(user = u, resourceType = ResourceType.ISSUE_POST, resourceId = i.id.toString())) }

    private fun logins(set: Set<User>) = set.map { it.loginId }.toSet()

    @Test
    fun `구독자, 작성자, 담당자가 받고 댓글을 쓰는 사람은 뺀다`() {
        val p = project(); val author = user(); val assignee = user(); val watcher = user(); val commenter = user()
        val i = issue(p, author, assignee); watch(watcher, i); watch(commenter, i)
        assertEquals(logins(setOf(author, assignee, watcher)), logins(receivers.forIssueComment(i, commenter.id, "안녕", null, EventType.NEW_COMMENT, commenter)))
    }

    @Test
    fun `구독을 해제한 사용자와 활성이 아닌 사용자는 받지 않는다`() {
        val p = project(); val author = user(); val muted = user(); val locked = user(UserState.LOCKED); val commenter = user()
        val i = issue(p, author)
        watch(muted, i); watch(locked, i)
        unwatches.save(Unwatch(user = muted, resourceType = ResourceType.ISSUE_POST, resourceId = i.id.toString()))
        assertEquals(logins(setOf(author)), logins(receivers.forIssueComment(i, commenter.id, "c", null, EventType.NEW_COMMENT, commenter)))
    }

    @Test
    fun `프로젝트의 이벤트별 알림 설정을 따른다`() {
        val p = project(); val author = user(); val wants = user(); val refuses = user(); val commenter = user()
        val i = issue(p, author)
        projectNotifications.save(UserProjectNotification(user = wants, project = p, notificationType = EventType.NEW_COMMENT, allowed = true))
        projectNotifications.save(UserProjectNotification(user = refuses, project = p, notificationType = EventType.NEW_COMMENT, allowed = false))
        projectNotifications.save(UserProjectNotification(user = refuses, project = p, notificationType = EventType.ISSUE_STATE_CHANGED, allowed = true))
        watch(refuses, i)
        assertEquals(logins(setOf(author, wants)), logins(receivers.forIssueComment(i, commenter.id, "c", null, EventType.NEW_COMMENT, commenter)))
        assertEquals(logins(setOf(author, refuses)), logins(receivers.forIssueComment(i, commenter.id, "c", null, EventType.ISSUE_STATE_CHANGED, commenter)))
    }

    @Test
    fun `한 줄 댓글은 부모 댓글 작성자가 받고 이슈 작성자는 받지 않는다`() {
        val p = project(); val author = user(); val parentAuthor = user(); val commenter = user(); val mentionedInParent = user()
        val i = issue(p, author)
        val parent = comment(i, parentAuthor, "부모 @${mentionedInParent.loginId}")
        assertEquals(logins(setOf(parentAuthor, mentionedInParent)), logins(receivers.forIssueComment(i, commenter.id, "답", parent, EventType.NEW_COMMENT, commenter)))
    }

    @Test
    fun `부모 댓글 작성자가 답하면 앞의 한 줄 댓글 작성자들도 받는다`() {
        val p = project(); val author = user(); val parentAuthor = user(); val other = user()
        val i = issue(p, author)
        val parent = comment(i, parentAuthor, "부모")
        comment(i, other, "한 줄", parent)
        assertEquals(logins(setOf(other)), logins(receivers.forIssueComment(i, parentAuthor.id, "답", parent, EventType.NEW_COMMENT, parentAuthor)))
    }

    @Test
    fun `댓글의 멘션은 마지막에 더해지고 자기 자신을 멘션하면 다시 받는다`() {
        val p = project(); val author = user(); val commenter = user(); val locked = user(UserState.LOCKED)
        val i = issue(p, author)
        val result = receivers.forIssueComment(i, commenter.id, "@${commenter.loginId} @${locked.loginId}", null, EventType.NEW_COMMENT, commenter)
        assertEquals(logins(setOf(author, commenter, locked)), logins(result))
    }

    @Test
    fun `로그인하지 않았으면 댓글 작성자를 뺀다`() {
        val p = project(); val author = user(); val watcher = user()
        val i = issue(p, author); watch(watcher, i)
        assertEquals(logins(setOf(watcher)), logins(receivers.forIssueComment(i, author.id, "c", null, EventType.NEW_COMMENT, null)))
    }
}
