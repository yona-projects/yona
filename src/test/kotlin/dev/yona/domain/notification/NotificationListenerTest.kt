package dev.yona.domain.notification

import dev.yona.domain.board.Posting
import dev.yona.domain.board.PostingComment
import dev.yona.domain.board.PostingCommentActivity
import dev.yona.domain.board.PostingCreatedActivity
import dev.yona.domain.board.PostingDeletedActivity
import dev.yona.domain.board.PostingRepository
import dev.yona.domain.board.PostingUpdatedActivity
import dev.yona.domain.comment.IssueComment
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Assignee
import dev.yona.domain.issue.AssigneeRepository
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueChangedActivity
import dev.yona.domain.issue.IssueCommentActivity
import dev.yona.domain.issue.IssueCreatedActivity
import dev.yona.domain.issue.IssueDeletedActivity
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.IssueSharer
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
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 애플리케이션 활동이 알림 이벤트로 바뀌는 규칙(제목, 값, 수신자)을 검증한다.
 *
 * 원본: `app/models/NotificationEvent.java`의 `afterNewIssue`, `afterStateChanged`, `afterAssigneeChanged`, `afterIssueBodyChanged`, `afterMilestoneChanged`,
 * `afterIssueMoved`, `afterIssueSharerChanged`, `afterIssueLabelChanged`, `afterResourceDeleted`, `afterNewComment`, `afterCommentUpdated`,
 * `afterNewPost`, `afterUpdatePosting`.
 */
@Transactional
class NotificationListenerTest : AbstractIntegrationTest() {

    @Autowired private lateinit var publisher: ApplicationEventPublisher
    @Autowired private lateinit var eventRepository: NotificationEventRepository
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var postings: PostingRepository
    @Autowired private lateinit var assignees: AssigneeRepository
    @Autowired private lateinit var watches: WatchRepository
    @Autowired private lateinit var unwatches: UnwatchRepository
    @Autowired private lateinit var entityManager: EntityManager

    private var sequence = 0

    private fun user(name: String? = null): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = name ?: "nl$n", name = name ?: "nl$n", email = "nl$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun project(): Project = projects.save(Project(owner = "nlo${++sequence}", name = "nlp$sequence", vcs = "GIT", projectScope = ProjectScope.PUBLIC, lastIssueNumber = 1, lastPostingNumber = 1))

    private fun issue(project: Project, author: User, assignee: User? = null, body: String = "본문"): Issue = issues.save(
        Issue(
            project = project, number = 1, title = "제목", body = body, state = State.OPEN, authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
            assignee = assignee?.let { assignees.save(Assignee(user = it, project = project)) }, numOfComments = 0, weight = 0, createdDate = Date(), updatedDate = Date()
        )
    )

    private fun posting(project: Project, author: User): Posting = postings.save(
        Posting(project = project, number = 1, title = "글", body = "글 본문", authorId = author.id, authorLoginId = author.loginId, authorName = author.name, notice = false, readme = false, numOfComments = 0, createdDate = Date(), updatedDate = Date())
    )

    private fun watch(user: User, type: ResourceType, id: Long) { watches.save(Watch(user = user, resourceType = type, resourceId = id.toString())) }

    private fun only(): NotificationEvent { entityManager.flush(); return eventRepository.findAll().single() }

    private fun logins(e: NotificationEvent) = e.receivers.map { it.loginId }.toSet()

    @Test
    fun `새 이슈는 프로젝트 구독자와 멘션된 사용자가 받고 작성자는 받지 않는다`() {
        val p = project(); val author = user(); val projectWatcher = user(); val mentioned = user("mentioned-user"); val i = issue(p, author, body = "안녕 @mentioned-user")
        watch(projectWatcher, ResourceType.PROJECT, p.id!!); watch(author, ResourceType.PROJECT, p.id!!)
        publisher.publishEvent(IssueCreatedActivity(i, author))
        val e = only()
        assertEquals(EventType.NEW_ISSUE, e.eventType); assertEquals("[${p.name}] 제목 (#1)", e.title); assertEquals("안녕 @mentioned-user", e.newValue); assertEquals(null, e.oldValue)
        assertEquals(ResourceType.ISSUE_POST, e.resourceType); assertEquals(i.id.toString(), e.resourceId); assertEquals(author.id, e.senderId)
        assertEquals(setOf(projectWatcher.loginId, mentioned.loginId), logins(e))
    }

    @Test
    fun `상태 변경은 작성자, 담당자, 공유자, 이슈 구독자가 받고 바꾼 사람은 뺀다`() {
        val p = project(); val author = user(); val assignee = user(); val sharer = user(); val watcher = user(); val actor = user(); val muted = user()
        val i = issue(p, author, assignee)
        entityManager.persist(IssueSharer(created = LocalDate.now(), loginId = sharer.loginId, user = sharer, issue = i))
        watch(watcher, ResourceType.ISSUE_POST, i.id!!); watch(actor, ResourceType.ISSUE_POST, i.id!!); watch(muted, ResourceType.ISSUE_POST, i.id!!)
        unwatches.save(Unwatch(user = muted, resourceType = ResourceType.ISSUE_POST, resourceId = i.id.toString()))
        publisher.publishEvent(IssueChangedActivity(i, EventType.ISSUE_STATE_CHANGED, "open", "closed", actor))
        val e = only()
        assertEquals("Re: [${p.name}] 제목 (#1)", e.title); assertEquals("open", e.oldValue); assertEquals("closed", e.newValue)
        assertEquals(setOf(author.loginId, assignee.loginId, sharer.loginId, watcher.loginId), logins(e))
    }

    @Test
    fun `담당자 변경은 이전 담당자도 받는다`() {
        val p = project(); val author = user(); val old = user(); val new = user(); val actor = user()
        val i = issue(p, author, new)
        publisher.publishEvent(IssueChangedActivity(i, EventType.ISSUE_ASSIGNEE_CHANGED, old.loginId, new.loginId, actor))
        val e = only()
        assertEquals(old.loginId, e.oldValue); assertEquals(new.loginId, e.newValue)
        assertEquals(setOf(author.loginId, new.loginId, old.loginId), logins(e))
    }

    @Test
    fun `본문 변경은 새로 멘션된 사용자도 받는다`() {
        val p = project(); val author = user(); val oldMentioned = user("old-mention"); val newMentioned = user("new-mention"); val actor = user()
        val i = issue(p, author, body = "@old-mention @new-mention")
        publisher.publishEvent(IssueChangedActivity(i, EventType.ISSUE_BODY_CHANGED, "@old-mention", "@old-mention @new-mention", actor))
        val e = only()
        assertEquals(setOf(author.loginId, newMentioned.loginId), logins(e)); assertNotNull(oldMentioned.id)
    }

    @Test
    fun `마일스톤 변경은 이슈의 필수 수신자가 받는다`() {
        val p = project(); val author = user(); val actor = user(); val i = issue(p, author)
        publisher.publishEvent(IssueChangedActivity(i, EventType.ISSUE_MILESTONE_CHANGED, "-1", "5", actor))
        val e = only()
        assertEquals(setOf(author.loginId), logins(e)); assertEquals("5", e.newValue)
    }

    @Test
    fun `공유자 추가는 그 공유자만 받고 라벨 변경은 받는 사람이 없어 저장하지 않는다`() {
        val p = project(); val author = user(); val sharer = user(); val actor = user(); val i = issue(p, author)
        publisher.publishEvent(IssueChangedActivity(i, EventType.ISSUE_SHARER_CHANGED, "", sharer.loginId, actor))
        publisher.publishEvent(IssueChangedActivity(i, EventType.ISSUE_LABEL_CHANGED, "", "Type - bug #fff", actor))
        val e = only()
        assertEquals(EventType.ISSUE_SHARER_CHANGED, e.eventType); assertEquals(setOf(sharer.loginId), logins(e)); assertEquals("", e.oldValue)
    }

    @Test
    fun `이슈 이동은 활동이 가져온 이전 구독자가 받는다`() {
        val p = project(); val author = user(); val previousWatcher = user(); val actor = user(); val i = issue(p, author)
        publisher.publishEvent(IssueChangedActivity(i, EventType.ISSUE_MOVED, "a/b", "${p.owner}/${p.name}", actor, setOf(previousWatcher)))
        val e = only()
        assertEquals("a/b", e.oldValue); assertEquals(setOf(previousWatcher.loginId), logins(e))
    }

    @Test
    fun `이슈를 지우면 프로젝트에 걸린 삭제 알림을 구독자가 받는다`() {
        val p = project(); val author = user(); val watcher = user(); val actor = user(); val i = issue(p, author)
        watch(watcher, ResourceType.ISSUE_POST, i.id!!)
        publisher.publishEvent(IssueDeletedActivity(p.id!!, 1, "제목", "본문", actor))
        val e = only()
        assertEquals(EventType.RESOURCE_DELETED, e.eventType); assertEquals(ResourceType.PROJECT, e.resourceType); assertEquals(p.id.toString(), e.resourceId)
        assertEquals("[${p.name}] 제목 (#1)", e.title); assertEquals("본문", e.oldValue); assertEquals(actor.loginId, e.newValue)
        assertEquals(setOf(author.loginId, watcher.loginId), logins(e))
    }

    @Test
    fun `새 댓글은 이전 내용을 이전 값으로 담고 수정은 댓글 수정 이벤트이다`() {
        val p = project(); val author = user(); val commenter = user(); val i = issue(p, author)
        val c = IssueComment(contents = "댓글", createdDate = Date(), authorId = commenter.id, authorLoginId = commenter.loginId, authorName = commenter.name, issue = i, projectId = p.id!!)
        entityManager.persist(c); entityManager.flush()
        publisher.publishEvent(IssueCommentActivity(c, "이전", true, commenter))
        val created = only()
        assertEquals(EventType.NEW_COMMENT, created.eventType); assertEquals(ResourceType.ISSUE_COMMENT, created.resourceType); assertEquals(c.id.toString(), created.resourceId)
        assertEquals("Re: [${p.name}] 제목 (#1)", created.title); assertEquals("이전", created.oldValue); assertEquals("댓글", created.newValue); assertEquals(setOf(author.loginId), logins(created))
        publisher.publishEvent(IssueCommentActivity(c, null, false, commenter))
        entityManager.flush()
        val updated = eventRepository.findAll().single { it.eventType == EventType.COMMENT_UPDATED }
        assertEquals(null, updated.oldValue); assertEquals("댓글", updated.newValue)
    }

    @Test
    fun `새 글은 구독자와 멘션된 사용자가, 글 수정은 필수 수신자가 받는다`() {
        val p = project(); val author = user(); val watcher = user(); val editor = user(); val post = posting(p, author)
        watch(watcher, ResourceType.PROJECT, p.id!!)
        publisher.publishEvent(PostingCreatedActivity(post, author))
        val created = only()
        assertEquals(EventType.NEW_POSTING, created.eventType); assertEquals("[${p.name}] 글 (1)", created.title); assertEquals("글 본문", created.newValue); assertEquals(setOf(watcher.loginId), logins(created))
        publisher.publishEvent(PostingUpdatedActivity(post, "옛 본문", editor))
        entityManager.flush()
        val updated = eventRepository.findAll().single { it.eventType == EventType.POSTING_BODY_CHANGED }
        assertEquals("[${p.name}] 글 (1)", updated.title); assertEquals("옛 본문", updated.oldValue); assertEquals(setOf(author.loginId), logins(updated))
    }

    @Test
    fun `글 삭제와 글 댓글`() {
        val p = project(); val author = user(); val commenter = user(); val post = posting(p, author)
        publisher.publishEvent(PostingDeletedActivity(p.id!!, 1, "글", "글 본문", commenter))
        val deleted = only()
        assertEquals(EventType.RESOURCE_DELETED, deleted.eventType); assertEquals(setOf(author.loginId), logins(deleted))
        val c = PostingComment(contents = "글 댓글", createdDate = Date(), authorId = commenter.id, authorLoginId = commenter.loginId, authorName = commenter.name, posting = post, projectId = p.id!!)
        entityManager.persist(c); entityManager.flush()
        publisher.publishEvent(PostingCommentActivity(c, "이전", true, commenter))
        entityManager.flush()
        val comment = eventRepository.findAll().single { it.eventType == EventType.NEW_COMMENT }
        assertEquals(ResourceType.NONISSUE_COMMENT, comment.resourceType); assertEquals("Re: [${p.name}] 글 (1)", comment.title); assertEquals(setOf(author.loginId), logins(comment))
        assertTrue(true)
    }
}
