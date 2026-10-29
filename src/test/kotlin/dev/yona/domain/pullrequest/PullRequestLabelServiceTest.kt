package dev.yona.domain.pullrequest

import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.IssueLabel
import dev.yona.domain.issue.IssueLabelCategory
import dev.yona.domain.notification.NotificationEventRepository
import dev.yona.domain.project.Project
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 풀 리퀘스트의 라벨 붙이기와 떼기를 검증한다(로드맵 X-14, `decisions.md` D-34): 받는 프로젝트의 이슈 라벨만, 한 분류만 고르는 분류(exclusive)는 하나만,
 * 변경은 타임라인에만 남기고 알림도 웹훅도 만들지 않는다(v1.6 이슈 라벨 변경과 같다).
 */
@Transactional
class PullRequestLabelServiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: PullRequestLabelService
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var eventRepository: PullRequestEventRepository
    @Autowired private lateinit var notifications: NotificationEventRepository
    @PersistenceContext private lateinit var em: EntityManager

    private var sequence = 0

    private fun user() = users.save(User(loginId = "lbl${++sequence}", name = "lbl$sequence", email = "lbl$sequence@example.com", state = UserState.ACTIVE))

    private fun project() = Project(owner = "lblo${++sequence}", name = "lblp$sequence", vcs = "GIT", projectScope = ProjectScope.PUBLIC).also { em.persist(it) }

    private fun label(p: Project, name: String, exclusive: Boolean = false, category: IssueLabelCategory? = null): IssueLabel {
        val c = category ?: IssueLabelCategory(project = p, name = "cat${++sequence}", isExclusive = exclusive).also { em.persist(it) }
        return IssueLabel(category = c, color = "#aabbcc", name = name, project = p).also { em.persist(it) }
    }

    private fun pullRequest(to: Project) =
        PullRequest(toProject = to, fromProject = to, number = (++sequence).toLong(), title = "요청", contributor = user(), state = State.OPEN, toBranch = "refs/heads/master", fromBranch = "refs/heads/topic").also { em.persist(it) }

    private fun events(pr: PullRequest) = eventRepository.findByPullRequestIdOrderByCreatedAscIdAsc(pr.id!!).filter { it.eventType == EventType.PULL_REQUEST_LABEL_CHANGED }

    @Test
    fun `받는 프로젝트의 라벨을 붙이면 저장하고 타임라인에 추가 이벤트를 남긴다`() {
        val p = project(); val actor = user(); val bug = label(p, "bug"); val pr = pullRequest(p)
        assertEquals(LabelResult.CHANGED, service.add(pr, bug, actor))
        assertEquals(setOf(bug.id), pr.labels.map { it.id }.toSet())
        val event = events(pr).single()
        assertEquals(actor.loginId, event.senderLoginId); assertEquals(null, event.oldValue)
        assertTrue(event.newValue!!.contains("bug"), event.newValue)
    }

    @Test
    fun `이미 붙은 라벨은 다시 붙여도 아무 일도 없다`() {
        val p = project(); val actor = user(); val bug = label(p, "bug"); val pr = pullRequest(p)
        service.add(pr, bug, actor)
        assertEquals(LabelResult.UNCHANGED, service.add(pr, bug, actor))
        assertEquals(1, events(pr).size)
    }

    @Test
    fun `다른 프로젝트의 라벨은 붙일 수 없다`() {
        val p = project(); val other = project(); val foreign = label(other, "bug"); val pr = pullRequest(p)
        assertEquals(LabelResult.WRONG_PROJECT, service.add(pr, foreign, user()))
        assertTrue(pr.labels.isEmpty()); assertTrue(events(pr).isEmpty())
    }

    @Test
    fun `라벨을 떼면 삭제 이벤트를 남기고 붙지 않은 라벨을 떼도 아무 일도 없다`() {
        val p = project(); val actor = user(); val bug = label(p, "bug"); val other = label(p, "other"); val pr = pullRequest(p)
        service.add(pr, bug, actor)
        assertEquals(LabelResult.UNCHANGED, service.remove(pr, other, actor))
        assertEquals(LabelResult.CHANGED, service.remove(pr, bug, actor))
        assertTrue(pr.labels.isEmpty())
        val removed = events(pr).last()
        assertTrue(removed.oldValue!!.contains("bug")); assertEquals(null, removed.newValue)
    }

    @Test
    fun `하나만 고르는 분류는 다른 라벨을 붙이면 같은 분류의 기존 라벨이 빠진다`() {
        val p = project(); val actor = user(); val pr = pullRequest(p)
        val first = label(p, "p1", exclusive = true); val second = label(p, "p2", category = first.category)
        val free1 = label(p, "f1"); val free2 = label(p, "f2", category = free1.category)
        service.add(pr, first, actor); service.add(pr, second, actor)
        assertEquals(setOf(second.id), pr.labels.map { it.id }.toSet())
        service.add(pr, free1, actor); service.add(pr, free2, actor)
        assertEquals(setOf(second.id, free1.id, free2.id), pr.labels.map { it.id }.toSet(), "여러 개 고를 수 있는 분류는 함께 붙는다")
    }

    @Test
    fun `라벨 변경은 알림도 웹훅용 알림 이벤트도 만들지 않는다`() {
        val p = project(); val bug = label(p, "bug"); val pr = pullRequest(p)
        val before = notifications.count()
        service.add(pr, bug, user()); service.remove(pr, bug, user())
        assertEquals(before, notifications.count())
    }
}
