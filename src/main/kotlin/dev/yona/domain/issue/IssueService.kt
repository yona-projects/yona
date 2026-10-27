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
 * 이슈를 고칠 때 폼에서 받는 값이다. 폼의 값을 모두 반영하므로 비어 있는 마감일, 담당자, 마일스톤, 라벨은 지운다는 뜻이다.
 *
 * @property state 상태. `null`이면 현재 상태를 유지한다
 * @property draft 초안으로 저장하는지
 * @property publish 초안을 게시하는지
 * @property notifyMail 작성자가 고칠 때도 변경 이벤트를 남기고 알릴지 (폼의 "알림 메일" 체크)
 * @property changeParent `true`이면 [parentIssueId]로 부모를 바꾼다(폼이 하위 이슈 선택을 보냈을 때). 부모 번호가 없으면 부모를 지운다
 */
data class IssueEdit(
    val title: String,
    val body: String,
    val dueDate: Date?,
    val state: State?,
    val draft: Boolean,
    val publish: Boolean,
    val notifyMail: Boolean,
    val parentIssueId: Long?,
    val changeParent: Boolean,
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
    private val attachments: AttachmentService,
    private val labelService: IssueLabelService,
    private val projectUsers: dev.yona.domain.project.ProjectUserRepository,
    private val notificationReceivers: dev.yona.domain.notification.NotificationReceivers
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

    /**
     * 프로젝트의 마지막 이슈 번호를 하나 올리고 새 번호를 돌려준다 (v1.6 `Project.increaseLastIssueNumber`).
     * 카운터가 이슈 수보다 작으면 그 프로젝트의 가장 큰 이슈 번호로 먼저 바로잡는다(v1.6 `isLastIssueNumberCorrect`, `getLastIssueNumber`).
     * 번호를 지정해 가져온 이슈는 카운터를 올리지 않으므로, 이 보정이 없으면 다음 새 이슈가 번호 충돌을 낸다.
     */
    @Transactional
    fun nextIssueNumber(project: Project): Long {
        val id = requireNotNull(project.id)
        entityManager.createQuery(
            "update Project p set p.lastIssueNumber = coalesce((select max(i.number) from Issue i where i.project.id = p.id), 0) " +
                "where p.id = :id and coalesce(p.lastIssueNumber, 0) < (select count(i2) from Issue i2 where i2.project.id = p.id)"
        ).setParameter("id", id).executeUpdate()
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
     * [reopenParent]가 참이면 하위 이슈를 다시 열 때 닫혀 있는 부모 이슈도 다시 연다(`nextState` 액션). 댓글의 상태 전환은 거짓이다.
     */
    @Transactional
    fun toNextState(issue: Issue, actor: User, reopenParent: Boolean = true): State {
        val old = issue.state
        val next = if (old == State.CLOSED) State.OPEN else State.CLOSED
        changeState(issue, next, actor, old)
        val parent = issue.parent
        if (reopenParent && next == State.OPEN && parent != null && parent.state == State.CLOSED) {
            changeState(parent, State.OPEN, null, State.CLOSED, record = false)
        }
        return next
    }

    private fun changeState(issue: Issue, next: State, actor: User?, old: State?, record: Boolean = true) {
        issue.state = next
        issue.updatedDate = Date()
        resync(issue)
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
     * 이슈의 첨부와 알림 이벤트를 지우고, 하위 이슈의 부모 연결은 끊는다. 초안이 아니고 [notify]가 참이면 [IssueDeletedActivity]를 발행한다(일괄 수정의 삭제는 v1.6 `issue.delete()`만 불러 알림과 웹훅이 없다).
     * v1.6처럼 멘션 기록은 지우지 않는다([refactoring-backlog.md] R-24).
     */
    @Transactional
    fun delete(issue: Issue, actor: User, notify: Boolean = true) {
        val id = requireNotNull(issue.id)
        val project = requireNotNull(issue.project)
        if (notify && issue.isDraft != true) {
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
            deleteResourceRelations(ResourceType.ISSUE_COMMENT, commentId.toString())
            entityManager.remove(comment)
        }
        entityManager.createQuery("select e from IssueEvent e where e.issue.id = :id", IssueEvent::class.java).setParameter("id", id).resultList
            .forEach { entityManager.remove(it) }
        titleHeads.delete(project, issue.title)
        attachments.deleteAll(ResourceType.ISSUE_POST, id)
        deleteResourceRelations(ResourceType.ISSUE_POST, id.toString())
        entityManager.createQuery("select i from Issue i where i.parent.id = :id", Issue::class.java).setParameter("id", id).resultList
            .forEach { it.parent = null }
        entityManager.flush()
        issue.labels.clear(); issue.voters.clear()
        issues.delete(issue)
        issues.flush()
    }

    /**
     * 리소스를 지울 때 함께 지우는 관련 기록이다. 구독(Watch)과 구독 해제(Unwatch) 행은 v1.6 `ResourcePersistAdapter.postDelete`가 같은 트랜잭션에서,
     * 알림 이벤트는 `NotificationEvent.deleteBy`가 지운다.
     */
    fun deleteResourceRelations(type: ResourceType, resourceId: String) {
        deleteWatchRows(type, resourceId)
        deleteNotificationEvents(type, resourceId)
    }

    /**
     * 리소스의 구독, 구독 해제 기록만 지운다. v1.6 `ResourcePersistAdapter.postDelete`가 하는 일이며, `deleteOnly()`로 지우는 경로
     * (글을 이슈로 가져온 뒤의 글 삭제)는 알림 이벤트를 남긴다.
     */
    fun deleteWatchRows(type: ResourceType, resourceId: String) {
        entityManager.createQuery("delete from Watch w where w.resourceType = :type and w.resourceId = :rid").setParameter("type", type).setParameter("rid", resourceId).executeUpdate()
        entityManager.createQuery("delete from Unwatch w where w.resourceType = :type and w.resourceId = :rid").setParameter("type", type).setParameter("rid", resourceId).executeUpdate()
    }

    /** 리소스에 걸린 알림 이벤트를 수신자, 메일 대기열과 함께 지운다 (v1.6 `NotificationEvent.deleteBy`). */
    private fun deleteNotificationEvents(type: ResourceType, resourceId: String) {
        val ids = entityManager.createQuery(
            "select e.id from NotificationEvent e where e.resourceType = :type and e.resourceId = :rid", java.lang.Long::class.java
        ).setParameter("type", type).setParameter("rid", resourceId).resultList
        if (ids.isEmpty()) return
        entityManager.createNativeQuery("delete from notification_event_n4user where notification_event_id in (:ids)").setParameter("ids", ids).executeUpdate()
        entityManager.createNativeQuery("delete from notification_mail where notification_event_id in (:ids)").setParameter("ids", ids).executeUpdate()
        entityManager.createQuery("delete from NotificationEvent e where e.id in :ids").setParameter("ids", ids).executeUpdate()
    }

    /**
     * 이슈를 고친다 (v1.6 `IssueApp.editIssue`, `AbstractPostingApp.editPosting`). 제목, 본문, 마감일, 상태, 담당자, 마일스톤, 라벨, 부모를
     * 폼의 값으로 바꾸고 수정한 사람과 시각을 남긴다. 본문이 바뀌면 변경 이력을 앞에 쌓고, 제목 머리말 개수와 멘션을 맞춘다.
     *
     * - [target]이 이슈가 있는 프로젝트와 다르면 그 프로젝트로 옮긴다(새 번호, 하위 이슈도 함께, 마일스톤은 지움, 라벨은 [actor]가 대상
     *   프로젝트의 구성원이면 옮기고 아니면 지움).
     * - 초안 게시는 새 번호와 지금 시각을 받고 이력을 비우며 [IssueCreatedActivity]를 발행한다.
     * - 초안이 아닐 때 [IssueEdit.notifyMail]이 켜져 있거나 [actor]가 작성자가 아니면 담당자, 상태, 본문 변경 이벤트를 남기고
     *   [IssueChangedActivity]를 발행한다. 다른 프로젝트로 옮기면 이동 이벤트와 [IssueCreatedActivity]를 남긴다.
     *
     * @return 수정한 이슈(옮겼으면 대상 프로젝트의 이슈)
     */
    @Transactional
    fun update(issue: Issue, actor: User, edit: IssueEdit, target: Project? = null): Issue {
        val previousProject = requireNotNull(issue.project)
        val moved = target != null && target.id != previousProject.id
        val oldTitle = issue.title
        val oldBody = issue.body.orEmpty()
        val oldState = issue.state
        val oldAssignee = issue.assignee?.user
        val oldHistory = issue.history
        // 이동 알림은 옮기기 전 프로젝트에서의 구독자가 받는다(v1.6 `fromWatchers`).
        val fromWatchers = if (moved) notificationReceivers.issueWatchers(issue) else null

        if (moved) {
            moveToProject(issue, requireNotNull(target), actor)
        } else if (edit.changeParent) {
            issue.parent = edit.parentIssueId?.let { issues.findById(it).orElse(null) }
        }
        if (edit.publish) {
            val number = nextIssueNumber(requireNotNull(issue.project))
            issue.createdDate = Date()
            issue.number = number
        }

        val project = requireNotNull(issue.project)
        issue.title = edit.title
        issue.body = edit.body
        issue.dueDate = MilestoneService.lastSecondOfDay(edit.dueDate)
        issue.assignee = findOrCreateAssignee(project, edit.assigneeLoginId)
        issue.milestone = if (moved) null else edit.milestoneId?.let { milestones.findById(it).orElse(null) }
        if (!moved) issue.labels = projectLabels(project, edit.labelIds)
        issue.isDraft = edit.draft
        issue.state = when {
            edit.draft -> State.DRAFT
            edit.state != null -> edit.state
            oldState == State.DRAFT -> State.OPEN
            else -> oldState
        }
        issue.updatedDate = Date()
        issue.updatedByAuthorId = actor.id
        issue.history = when {
            edit.publish -> ""
            oldBody != edit.body -> IssueHistory.entry(oldBody, edit.body, actor, requireNotNull(issue.updatedDate)) + oldHistory.orEmpty()
            else -> oldHistory
        }
        if (moved && previousProject.isPrivate() && previousProject.owner.equals(actor.loginId, ignoreCase = true)) issue.history = ""
        issues.save(issue)

        // v1.6 `editPosting`: 새 제목을 먼저 더하고 옛 제목을 나중에 뺀다. 빼는 쪽의 프로젝트는 이미 바뀐 `original.project`(옮겼다면 새 프로젝트)이다
        titleHeads.save(project, issue.title)
        titleHeads.delete(project, oldTitle)
        mentions.update(ResourceType.ISSUE_POST, requireNotNull(issue.id).toString(), issue.body)

        recordChanges(issue, actor, edit, moved, previousProject, oldState, oldAssignee, oldBody, fromWatchers)
        return issue
    }

    /** 이슈 하나가 같은 분류에서 라벨을 둘 이상 갖지 않는지 확인한다. 어긋나면 `true`이다 (v1.6 `Issue.checkLabels`). */
    /**
     * 이슈의 가중치를 [delta]만큼 바꾸고 새 값을 돌려준다 (v1.6 `IssueApi.upvoteWeight`, `downvoteWeight`). 수정 시각은 바꾸지 않는다.
     * v1.6의 `update()`처럼 댓글 수를 다시 세고 멘션 기록을 맞춘다.
     */
    @Transactional
    fun adjustWeight(issue: Issue, delta: Int): Int {
        issue.weight = (issue.weight ?: 0) + delta
        resync(issue)
        return requireNotNull(issue.weight)
    }

    /**
     * 라벨을 지운다. v1.6 `IssueLabel.delete()`는 라벨이 달린 이슈마다 `issue.save()`를 불러, 저장 때마다 하는 일(댓글 수 재계산, 제목 머리말 빈도 +1,
     * 본문 멘션 맞춤)이 함께 일어난다. 게시글은 이 순회에 없다.
     */
    @Transactional
    fun deleteLabel(label: IssueLabel) {
        labelService.deleteLabel(label).forEach { issue ->
            resync(issue)
            titleHeads.save(requireNotNull(issue.project), issue.title)
        }
    }

    /**
     * v1.6 `AbstractPosting.update()`가 저장 때마다 하는 일이다: 댓글 수를 실제 수로 다시 세어 저장하고, 본문의 `@멘션`을 `mention` 행에 맞춘다.
     * 수정 시각은 바꾸지 않는다. 이슈를 `update()`로 저장하는 경로(추천, 가중치, 상태 변경, 댓글 등록, 수정, 삭제)가 쓴다.
     */
    @Transactional
    fun resync(issue: Issue) {
        issue.numOfComments = entityManager.createQuery("select count(c) from IssueComment c where c.issue.id = :id", java.lang.Long::class.java)
            .setParameter("id", issue.id).singleResult.toInt()
        issues.save(issue)
        mentions.update(ResourceType.ISSUE_POST, requireNotNull(issue.id).toString(), issue.body)
    }

    /**
     * 본문만 바꾼다 (v1.6 `IssueApi.updateIssueContent`, 체크리스트 갱신). 이력과 이벤트를 남기지 않고 수정 시각도 바꾸지 않는다.
     * 댓글 수를 다시 세고 멘션 기록을 새 본문에 맞춘다 (`AbstractPosting.update`).
     */
    @Transactional
    fun replaceBody(issue: Issue, body: String) {
        issue.body = body
        resync(issue)
    }

    fun violatesExclusiveCategory(labelIds: Set<Long>): Boolean =
        labels.findAllById(labelIds).filter { it.category?.isExclusive == true }.groupBy { it.category?.id }.any { it.value.size > 1 }

    private fun recordChanges(issue: Issue, actor: User, edit: IssueEdit, moved: Boolean, previous: Project, oldState: State?, oldAssignee: User?, oldBody: String, fromWatchers: Set<User>?) {
        if (!edit.publish && edit.draft) return
        if (moved) {
            val ownPrivate = previous.isPrivate() && previous.owner.equals(actor.loginId, ignoreCase = true)
            if (!ownPrivate) {
                val project = requireNotNull(issue.project)
                events.record(issue, EventType.ISSUE_MOVED, "${previous.owner}/${previous.name}", "${project.owner}/${project.name}", actor.loginId)
                publisher.publishEvent(IssueChangedActivity(issue, EventType.ISSUE_MOVED, "${previous.owner}/${previous.name}", "${project.owner}/${project.name}", actor, fromWatchers))
                // v1.6은 이동 알림 뒤에 새 이슈 알림(afterNewIssue)을 부르고, 게시를 함께 하면 게시 분기에서 한 번 더 부른다. 알림 이벤트는 같은 자원, 종류,
                // 보낸 사람의 직전 이벤트를 지우고 새로 저장하므로 한 건으로 남지만 웹훅은 두 번 나간다.
                publisher.publishEvent(IssueCreatedActivity(issue, actor))
            }
        }
        if (edit.publish) {
            publisher.publishEvent(IssueCreatedActivity(issue, actor))
            return
        }
        if (!(edit.notifyMail || issue.authorLoginId != actor.loginId)) return
        val newAssignee = issue.assignee?.user
        if (oldAssignee?.id != newAssignee?.id) {
            events.record(issue, EventType.ISSUE_ASSIGNEE_CHANGED, oldAssignee?.loginId, newAssignee?.loginId, actor.loginId)
            publisher.publishEvent(IssueChangedActivity(issue, EventType.ISSUE_ASSIGNEE_CHANGED, oldAssignee?.loginId, newAssignee?.loginId, actor))
        }
        if (issue.state != oldState) {
            events.record(issue, EventType.ISSUE_STATE_CHANGED, oldState?.state, issue.state?.state, actor.loginId)
            publisher.publishEvent(IssueChangedActivity(issue, EventType.ISSUE_STATE_CHANGED, oldState?.state, issue.state?.state, actor))
        }
        if (oldBody != issue.body.orEmpty()) {
            events.record(issue, EventType.ISSUE_BODY_CHANGED, oldBody, issue.body, actor.loginId)
            publisher.publishEvent(IssueChangedActivity(issue, EventType.ISSUE_BODY_CHANGED, oldBody, issue.body, actor))
        }
    }

    /** 이슈와 그 하위 이슈를 [target] 프로젝트로 옮긴다 (v1.6 `IssueApp.moveIssueToOtherProject`). */
    private fun moveToProject(issue: Issue, target: Project, actor: User) {
        val subtasks = entityManager.createQuery("select i from Issue i where i.parent.id = :id", Issue::class.java).setParameter("id", issue.id).resultList
        (listOf(issue) + subtasks).forEach { moved ->
            // 번호를 먼저 받아야 한다. 번호를 올리는 중에 일어나는 flush가 (대상 프로젝트, 옛 번호)를 쓰면 번호가 겹친다.
            val number = nextIssueNumber(target)
            moved.project = target
            moved.number = number
            moved.createdDate = Date()
            moved.updatedDate = moved.createdDate
            moved.milestone = null
            entityManager.createQuery("update IssueComment c set c.projectId = :project where c.issue.id = :id")
                .setParameter("project", target.id).setParameter("id", moved.id).executeUpdate()
            if (moved.labels.isNotEmpty() && projectUsers.existsByUserIdAndProjectId(requireNotNull(actor.id), requireNotNull(target.id))) {
                moved.labels = transferLabels(moved, target)
            } else {
                moved.labels = mutableSetOf()
            }
            issues.save(moved)
        }
    }

    /** 라벨을 [target] 프로젝트로 옮긴다. 같은 분류, 이름의 라벨이 있으면 그것을 쓰고 없으면 만든다. */
    private fun transferLabels(issue: Issue, target: Project): MutableSet<IssueLabel> =
        issue.labels.map { source ->
            val categoryName = requireNotNull(source.category?.name)
            val category = labelService.findCategory(target, categoryName)
                ?: labelService.createCategory(target, categoryName, source.category?.isExclusive == true)!!
            labelService.findLabel(target, category, requireNotNull(source.name)) ?: labels.save(IssueLabel(project = target, category = category, name = source.name, color = source.color))
        }.toMutableSet()
}
