package dev.yona.domain.issue

import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.State
import dev.yona.domain.misc.AttachmentService
import dev.yona.domain.notification.MentionService
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import jakarta.persistence.EntityManager
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Date

/**
 * 이슈를 만들 때 폼에서 받는 값이다.
 *
 * @property title 제목
 * @property body 본문. 없을 수 있다
 * @property dueDate 마감일. 그날의 마지막 초로 맞춘다
 * @property draft 초안으로 저장할지
 * @property parentIssueId 부모 이슈의 번호(PK). 없으면 최상위 이슈
 * @property assigneeLoginId 담당자 로그인 ID. 없거나 없는 사용자이면 담당자 없음
 * @property milestoneId 마일스톤 번호. 없거나 -1이면 마일스톤 없음
 * @property labelIds 붙일 라벨 번호. 이슈가 속한 프로젝트의 라벨만 붙인다
 */
data class NewIssue(
    val title: String,
    val body: String?,
    val dueDate: Date?,
    val draft: Boolean,
    val parentIssueId: Long?,
    val assigneeLoginId: String?,
    val milestoneId: Long?,
    val labelIds: Set<Long>
)

/**
 * 이슈의 생성과 변경을 담당한다. v1.6 `IssueApp`의 이슈 처리와 `AbstractPosting.save`, `Issue`
 * (`app/controllers/IssueApp.java`, `app/models/AbstractPosting.java`)에 해당한다.
 */
@Service
class IssueService(
    private val issues: IssueRepository,
    private val projects: ProjectRepository,
    private val users: UserRepository,
    private val assignees: AssigneeRepository,
    private val milestones: MilestoneRepository,
    private val labels: IssueLabelRepository,
    private val titleHeads: TitleHeadService,
    private val mentions: MentionService,
    private val publisher: ApplicationEventPublisher,
    private val entityManager: EntityManager,
    private val events: IssueEventService,
    private val attachments: AttachmentService
) {

    /**
     * 이슈를 만든다. 번호는 프로젝트의 마지막 이슈 번호에 1을 더해 매기고, 제목 머리말과 본문 멘션을 기록한다.
     * 초안이 아니면 [IssueCreatedActivity]를 발행한다.
     */
    @Transactional
    fun create(project: Project, author: User, input: NewIssue): Issue {
        val now = Date()
        val issue = Issue(
            title = input.title, body = input.body, project = project, state = if (input.draft) State.DRAFT else State.OPEN,
            createdDate = now, updatedDate = now, numOfComments = 0, isDraft = input.draft, weight = 0,
            authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
            dueDate = MilestoneService.lastSecondOfDay(input.dueDate),
            parent = input.parentIssueId?.let { issues.findById(it).orElse(null) },
            assignee = findOrCreateAssignee(project, input.assigneeLoginId),
            milestone = input.milestoneId?.let { milestones.findById(it).orElse(null) },
            labels = projectLabels(project, input.labelIds)
        )
        issue.number = nextIssueNumber(project)
        issues.save(issue)
        titleHeads.save(project, issue.title)
        if (issue.body != null) mentions.update(ResourceType.ISSUE_POST, requireNotNull(issue.id).toString(), issue.body)
        if (!input.draft) publisher.publishEvent(IssueCreatedActivity(issue, author))
        return issue
    }

    /** 프로젝트의 마지막 이슈 번호를 하나 올리고 새 번호를 돌려준다 (v1.6 `Project.increaseLastIssueNumber`). */
    @Transactional
    fun nextIssueNumber(project: Project): Long {
        val id = requireNotNull(project.id)
        entityManager.createQuery("update Project p set p.lastIssueNumber = coalesce(p.lastIssueNumber, 0) + 1 where p.id = :id").setParameter("id", id).executeUpdate()
        entityManager.flush()
        val number = entityManager.createQuery("select p.lastIssueNumber from Project p where p.id = :id", java.lang.Long::class.java).setParameter("id", id).singleResult.toLong()
        projects.findById(id).ifPresent { entityManager.refresh(it) }
        return number
    }

    /** 담당자 로그인 ID로 프로젝트의 담당자 행을 찾아 쓰거나 만든다. 없는 사용자이면 `null`이다 (v1.6 `Assignee.add`). */
    fun findOrCreateAssignee(project: Project, loginId: String?): Assignee? {
        if (loginId.isNullOrEmpty()) return null
        val user = users.findByLoginIdIgnoreCase(loginId) ?: return null
        return assignees.findFirstByUserIdAndProjectId(requireNotNull(user.id), requireNotNull(project.id))
            ?: assignees.save(Assignee(user = user, project = project))
    }

    /** 이 프로젝트의 라벨만 골라 모은다. 다른 프로젝트의 라벨이나 없는 번호는 건너뛴다. */
    private fun projectLabels(project: Project, ids: Set<Long>): MutableSet<IssueLabel> =
        labels.findAllById(ids).filter { it.project?.id == project.id }.toMutableSet()

    /**
     * 이슈를 다음 상태로 바꾸고 새 상태를 돌려준다 (v1.6 `IssueApp.nextState`, `Issue.toNextState`). 열림은 닫힘, 닫힘은 열림,
     * 초안은 닫힘이다. 수정 시각을 갱신하고 타임라인 이벤트를 남기며 [IssueChangedActivity]를 발행한다.
     * 하위 이슈를 다시 열면 닫혀 있는 부모 이슈도 다시 연다.
     */
    @Transactional
    fun toNextState(issue: Issue, actor: User): State {
        val old = issue.state
        val next = if (old == State.CLOSED) State.OPEN else State.CLOSED
        changeState(issue, next, actor, old)
        val parent = issue.parent
        if (next == State.OPEN && parent != null && parent.state == State.CLOSED) {
            changeState(parent, State.OPEN, null, State.CLOSED, record = false)
        }
        return next
    }

    private fun changeState(issue: Issue, next: State, actor: User?, old: State?, record: Boolean = true) {
        issue.state = next
        issue.updatedDate = Date()
        issues.save(issue)
        if (!record || actor == null) return
        // v1.6은 "이전 상태"를 현재 상태의 짝(열림↔닫힘)으로 정한다. 초안에서 닫을 때도 이전 상태는 열림이다.
        val previous = if (next == State.CLOSED) State.OPEN else State.CLOSED
        events.record(issue, EventType.ISSUE_STATE_CHANGED, previous.state, next.state, actor.loginId)
        publisher.publishEvent(IssueChangedActivity(issue, EventType.ISSUE_STATE_CHANGED, previous.state, next.state, actor))
        @Suppress("UNUSED_VARIABLE") val unused = old
    }

    /**
     * 이슈를 지울 수 있으면 `true`이다. 댓글이 없거나 모든 댓글을 이슈 작성자가 썼을 때만 지울 수 있다 (v1.6 `Issue.canBeDeleted`).
     */
    @Transactional(readOnly = true)
    fun canBeDeleted(issue: Issue): Boolean =
        entityManager.createQuery("select c.authorLoginId from IssueComment c where c.issue.id = :id", String::class.java).setParameter("id", issue.id).resultList
            .all { it == issue.authorLoginId }

    /**
     * 이슈를 지운다 (v1.6 `IssueApp.deleteIssue`, `AbstractPosting.delete`). 댓글(첨부와 알림 이벤트 포함), 타임라인 이벤트, 제목 머리말 개수,
     * 이슈의 첨부와 알림 이벤트를 지우고, 하위 이슈의 부모 연결은 끊는다. 초안이 아니면 [IssueDeletedActivity]를 발행한다.
     * v1.6처럼 멘션 기록은 지우지 않는다([refactoring-backlog.md] R-24).
     */
    @Transactional
    fun delete(issue: Issue, actor: User) {
        val id = requireNotNull(issue.id)
        val project = requireNotNull(issue.project)
        if (issue.isDraft != true) {
            publisher.publishEvent(IssueDeletedActivity(requireNotNull(project.id), issue.number ?: 0, issue.title, issue.body, actor))
        }
        // 한 줄 댓글(자식)이 부모 댓글을 가리키므로 연결을 먼저 끊고 모두 지운다.
        val comments = entityManager.createQuery("select c from IssueComment c where c.issue.id = :id", dev.yona.domain.comment.IssueComment::class.java)
            .setParameter("id", id).resultList
        comments.forEach { it.parentComment = null }
        entityManager.flush()
        comments.forEach { comment ->
            val commentId = requireNotNull(comment.id)
            attachments.deleteAll(ResourceType.ISSUE_COMMENT, commentId)
            deleteNotificationEvents(ResourceType.ISSUE_COMMENT, commentId.toString())
            entityManager.remove(comment)
        }
        entityManager.createQuery("select e from IssueEvent e where e.issue.id = :id", IssueEvent::class.java).setParameter("id", id).resultList
            .forEach { entityManager.remove(it) }
        titleHeads.delete(project, issue.title)
        attachments.deleteAll(ResourceType.ISSUE_POST, id)
        deleteNotificationEvents(ResourceType.ISSUE_POST, id.toString())
        entityManager.createQuery("select i from Issue i where i.parent.id = :id", Issue::class.java).setParameter("id", id).resultList
            .forEach { it.parent = null }
        entityManager.flush()
        issue.labels.clear(); issue.voters.clear()
        issues.delete(issue)
        issues.flush()
    }

    private fun deleteNotificationEvents(type: ResourceType, resourceId: String) {
        val ids = entityManager.createQuery(
            "select e.id from NotificationEvent e where e.resourceType = :type and e.resourceId = :rid", java.lang.Long::class.java
        ).setParameter("type", type).setParameter("rid", resourceId).resultList
        if (ids.isEmpty()) return
        entityManager.createNativeQuery("delete from notification_event_n4user where notification_event_id in (:ids)").setParameter("ids", ids).executeUpdate()
        entityManager.createNativeQuery("delete from notification_mail where notification_event_id in (:ids)").setParameter("ids", ids).executeUpdate()
        entityManager.createQuery("delete from NotificationEvent e where e.id in :ids").setParameter("ids", ids).executeUpdate()
    }
}
