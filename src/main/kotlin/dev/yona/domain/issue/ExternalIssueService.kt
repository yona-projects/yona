package dev.yona.domain.issue

import dev.yona.domain.board.Posting
import dev.yona.domain.board.PostingCommentRepository
import dev.yona.domain.board.PostingRepository
import dev.yona.domain.comment.IssueComment
import dev.yona.domain.comment.IssueCommentRepository
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.State
import dev.yona.domain.misc.Attachment
import dev.yona.domain.misc.AttachmentRepository
import dev.yona.domain.notification.MentionService
import dev.yona.domain.project.Project
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import jakarta.persistence.EntityManager
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Date

/**
 * 외부 API가 이슈를 만들 때 받는 값이다. 이슈 가져오기(이전)에 쓰므로 시각과 번호까지 지정할 수 있다.
 *
 * @property number 지정하면 그 번호로 만든다(프로젝트의 마지막 번호는 올리지 않는다). 없거나 0 이하이면 다음 번호이다
 * @property labels `(라벨 이름, 분류 이름)` 쌍. 프로젝트에 없는 라벨은 건너뛴다
 */
data class ExternalIssue(
    val title: String, val body: String, val state: State, val createdDate: Date?, val updatedDate: Date?, val assignee: User?, val milestoneTitle: String?,
    val dueDate: Date?, val labels: List<Pair<String, String>>, val number: Long?
)

/**
 * 이슈 부분 수정이 바꿀 값이다(로드맵 X-29a, `decisions.md` D-47). `null`이거나 `...Set`이 거짓인 항목은 바꾸지 않는다. 마일스톤, 담당자, 라벨은 "값 없음으로 바꾼다"와
 * "바꾸지 않는다"를 구분해야 하므로 `...Set` 표시를 따로 둔다.
 *
 * @property milestoneId [milestoneSet]이 참일 때 새 마일스톤 번호이다. `null`이면 마일스톤을 지운다
 * @property assigneeLoginId [assigneeSet]이 참일 때 새 담당자의 로그인 ID이다. `null`이면 담당자를 지운다
 * @property labelIds [labelsSet]이 참일 때 이슈에 붙일 라벨 번호 전체이다. 빈 집합이면 라벨을 모두 뗀다
 */
data class IssuePatch(
    val title: String? = null, val body: String? = null, val state: State? = null,
    val milestoneSet: Boolean = false, val milestoneId: Long? = null,
    val assigneeSet: Boolean = false, val assigneeLoginId: String? = null,
    val labelsSet: Boolean = false, val labelIds: Set<Long> = emptySet()
)

/**
 * 외부 REST API가 쓰는 이슈 도메인 동작이다. v1.6 `IssueApi`의 `createIssuesNode`, `updateIssueNode`, `updateIssueState`, `createComment`, `imports`
 * (`app/controllers/api/IssueApi.java`)에 해당한다. 권한 확인은 호출하는 쪽이 한다.
 */
@Service
class ExternalIssueService(
    private val issues: IssueRepository,
    private val issueService: IssueService,
    private val milestones: MilestoneRepository,
    private val labelService: IssueLabelService,
    private val titleHeads: TitleHeadService,
    private val mentions: MentionService,
    private val comments: IssueCommentRepository,
    private val postings: PostingRepository,
    private val postingComments: PostingCommentRepository,
    private val attachments: AttachmentRepository,
    private val events: IssueEventRepository,
    private val users: UserRepository,
    private val publisher: ApplicationEventPublisher,
    private val entityManager: EntityManager
) {

    /**
     * 이슈를 만든다. 번호를 지정하지 않으면 다음 번호를 받고 제목 머리말 개수를 늘리며, 지정하면 그 번호로 저장하고 머리말은 건드리지 않는다
     * (v1.6 `saveWithNumber`). 만든 시각을 주지 않거나 읽을 수 없으면 지금 시각으로, 고친 시각을 주지 않으면 만든 시각으로 채운다(v1.6 `IssueApi`는 생성자의 기본값을 `null`로 덮어써
     * 시각이 비어, 기간 조건과 최근 순 정렬에서 빠졌다. 결함이라 고쳤다, `decisions.md` D-83). [notify]이면 [IssueCreatedActivity]를 발행한다.
     */
    @Transactional
    fun create(project: Project, author: User, spec: ExternalIssue, notify: Boolean, actor: User = author): Issue {
        val numbered = spec.number != null && spec.number > 0
        val createdDate = spec.createdDate ?: Date()
        val issue = issues.save(
            Issue(
                project = project, number = if (numbered) spec.number else issueService.nextIssueNumber(project), title = spec.title, body = spec.body, state = spec.state,
                authorId = author.id, authorLoginId = author.loginId, authorName = author.name, createdDate = createdDate, updatedDate = spec.updatedDate ?: createdDate,
                assignee = spec.assignee?.let { issueService.findOrCreateAssignee(project, it.loginId) },
                milestone = spec.milestoneTitle?.let { milestones.findFirstByProjectIdAndTitle(requireNotNull(project.id), it) }, dueDate = spec.dueDate,
                labels = spec.labels.mapNotNull { (name, category) -> labelService.findCategory(project, category)?.let { labelService.findLabel(project, it, name) } }.toMutableSet(),
                numOfComments = 0, weight = 0
            )
        )
        if (!numbered) titleHeads.save(project, spec.title)
        mentions.update(ResourceType.ISSUE_POST, requireNotNull(issue.id).toString(), spec.body)
        // v1.6 `afterNewIssue`는 JSON의 작성자가 아니라 로그인 사용자를 보낸 사람으로 쓴다
        if (notify) publisher.publishEvent(IssueCreatedActivity(issue, actor))
        return issue
    }

    /**
     * 이슈의 제목, 본문, 마일스톤, 상태, 담당자를 한꺼번에 바꾼다(v1.6 `updateIssueNode`). 마일스톤 제목이 없으면 마일스톤을 지운다.
     * 상태와 담당자가 바뀌면 이벤트를 합치지 않고 그대로 남긴다.
     */
    @Transactional
    fun update(issue: Issue, actor: User, title: String, body: String, milestoneTitle: String?, state: State, assignee: User?): Issue {
        val project = requireNotNull(issue.project)
        issue.title = title
        issue.body = body
        issue.milestone = milestoneTitle?.let { milestones.findFirstByProjectIdAndTitle(requireNotNull(project.id), it) }
        issue.updatedDate = Date()
        changeState(issue, actor, state)
        val old = issue.assignee?.user
        if (old?.loginId.orEmpty() != assignee?.loginId.orEmpty()) event(issue, actor, EventType.ISSUE_ASSIGNEE_CHANGED, old?.loginId, assignee?.loginId)
        issue.assignee = assignee?.let { issueService.findOrCreateAssignee(project, it.loginId) }
        return saveLikeV16(issue)
    }

    /**
     * 이슈를 부분 수정한다(`PATCH`, 로드맵 X-29a). [patch]가 정한 항목만 바꾸고, 상태와 담당자가 바뀌면 [update]처럼 이벤트를 남긴다. 값의 올바름(마일스톤과 라벨이 이
     * 프로젝트의 것인지 등)은 호출하는 쪽이 먼저 확인한다. 저장할 때는 v1.6 `save()`의 부수 효과(댓글 수, 제목 머리말 개수, 멘션)를 [update]와 같게 낸다.
     */
    @Transactional
    fun patch(issue: Issue, actor: User, patch: IssuePatch): Issue {
        val project = requireNotNull(issue.project)
        patch.title?.let { issue.title = it }
        patch.body?.let { issue.body = it }
        if (patch.milestoneSet) issue.milestone = patch.milestoneId?.let { milestones.findById(it).orElse(null) }
        issue.updatedDate = Date()
        patch.state?.let { changeState(issue, actor, it) }
        if (patch.assigneeSet) {
            val old = issue.assignee?.user
            if (old?.loginId.orEmpty() != patch.assigneeLoginId.orEmpty()) event(issue, actor, EventType.ISSUE_ASSIGNEE_CHANGED, old?.loginId, patch.assigneeLoginId)
            issue.assignee = patch.assigneeLoginId?.let { issueService.findOrCreateAssignee(project, it) }
        }
        if (patch.labelsSet) {
            issue.labels.clear()
            issue.labels.addAll(labelService.findByIds(project, patch.labelIds))
        }
        return saveLikeV16(issue)
    }

    /** 상태만 바꾼다. 바뀌면 이벤트를 남긴다(v1.6 `updateIssueState`). 수정 시각은 건드리지 않는다. */
    @Transactional
    fun updateState(issue: Issue, actor: User, state: State): Issue {
        changeState(issue, actor, state)
        return saveLikeV16(issue)
    }

    /**
     * v1.6 `AbstractPosting.save()`와 같은 부수 효과를 낸다. 댓글 수를 다시 세고 저장한 뒤 제목 머리말 개수를 한 번 더 늘리고
     * 멘션 기록을 본문에 맞춘다([refactoring-backlog.md] R-29와 같은 동작).
     */
    private fun saveLikeV16(issue: Issue): Issue {
        issue.numOfComments = comments.countByIssueId(requireNotNull(issue.id)).toInt()
        val saved = issues.save(issue)
        titleHeads.save(requireNotNull(saved.project), saved.title)
        mentions.update(ResourceType.ISSUE_POST, requireNotNull(saved.id).toString(), saved.body.orEmpty())
        return saved
    }

    private fun changeState(issue: Issue, actor: User, state: State) {
        if (issue.state != state) event(issue, actor, EventType.ISSUE_STATE_CHANGED, issue.state?.state, state.state)
        issue.state = state
    }

    private fun event(issue: Issue, actor: User, type: EventType, old: String?, new: String?) {
        events.save(IssueEvent(created = Date(), senderLoginId = actor.loginId, issue = issue, eventType = type, oldValue = old, newValue = new))
    }

    /** 이슈 댓글을 단다. 시각을 주지 않으면 지금이고 활동은 발행하지 않는다(v1.6 `createComment`). 이슈의 댓글 수를 맞춘다. */
    @Transactional
    fun addComment(issue: Issue, author: User, contents: String, createdDate: Date?): IssueComment {
        val comment = comments.save(
            IssueComment(contents = contents, createdDate = createdDate ?: Date(), authorId = author.id, authorLoginId = author.loginId, authorName = author.name, issue = issue, projectId = requireNotNull(issue.project?.id))
        )
        mentions.update(ResourceType.ISSUE_COMMENT, requireNotNull(comment.id).toString(), contents)
        issue.numOfComments = comments.countByIssueId(requireNotNull(issue.id)).toInt()
        issues.save(issue)
        return comment
    }

    /** 이슈의 라벨을 [newLabels]로 바꾼다 (v1.6 `updateIssueLabel`). */
    @Transactional
    fun replaceLabels(issue: Issue, newLabels: Set<IssueLabel>) {
        issue.labels.clear()
        issue.labels.addAll(newLabels)
        saveLikeV16(issue)
    }

    /**
     * 게시글을 이슈로 가져온다(v1.6 `imports`). 제목, 본문, 이력, 시각, 작성자는 그대로 옮기고 새 이슈 번호를 받는다. 댓글(한 줄 댓글의 부모 관계 포함)과
     * 글, 댓글의 첨부를 이슈 쪽으로 옮기고, 글은 지운다. 다른 정리(알림 이벤트, 제목 머리말)는 하지 않는다(v1.6 `deleteOnly`).
     */
    @Transactional
    fun importFromPosting(posting: Posting): Issue {
        val project = requireNotNull(posting.project)
        val issue = issues.save(
            Issue(
                project = project, number = issueService.nextIssueNumber(project), title = posting.title, body = posting.body, history = posting.history, state = State.OPEN,
                authorId = posting.authorId, authorLoginId = posting.authorLoginId, authorName = posting.authorName, createdDate = posting.createdDate, updatedDate = posting.updatedDate,
                numOfComments = 0, weight = 0
            )
        )
        titleHeads.save(project, issue.title)
        mentions.update(ResourceType.ISSUE_POST, requireNotNull(issue.id).toString(), issue.body.orEmpty())

        val all = postingComments.findByPostingIdOrderByCreatedDateAscIdAsc(requireNotNull(posting.id))
        val idMap = linkedMapOf<Long, Long>()
        all.filter { it.parentComment == null }.forEach { top ->
            val copy = copyComment(top, issue, null)
            idMap[requireNotNull(top.id)] = requireNotNull(copy.id)
        }
        all.filter { it.parentComment != null }.forEach { child ->
            val parent = comments.findById(idMap.getValue(requireNotNull(child.parentComment?.id))).orElseThrow()
            copyComment(child, issue, parent)
        }
        issue.numOfComments = comments.countByIssueId(requireNotNull(issue.id)).toInt()
        issues.save(issue)

        moveAttachments(ResourceType.BOARD_POST, requireNotNull(posting.id), ResourceType.ISSUE_POST) { requireNotNull(issue.id) }
        idMap.forEach { (postingCommentId, issueCommentId) -> moveAttachments(ResourceType.NONISSUE_COMMENT, postingCommentId, ResourceType.ISSUE_COMMENT) { issueCommentId } }

        all.forEach { it.parentComment = null }
        entityManager.flush()
        val postingId = requireNotNull(posting.id)
        val commentIds = all.mapNotNull { it.id }
        all.forEach { postingComments.delete(it) }
        posting.labels.clear()
        postings.delete(posting)
        postings.flush()
        // v1.6 `deleteOnly()`는 구독 기록만 지우고 알림 이벤트는 남긴다
        commentIds.forEach { issueService.deleteWatchRows(ResourceType.NONISSUE_COMMENT, it.toString()) }
        issueService.deleteWatchRows(ResourceType.BOARD_POST, postingId.toString())
        return issue
    }

    private fun copyComment(source: dev.yona.domain.board.PostingComment, issue: Issue, parent: IssueComment?): IssueComment {
        val copy = comments.save(
            IssueComment(
                contents = source.contents, createdDate = source.createdDate, authorId = source.authorId, authorLoginId = source.authorLoginId, authorName = source.authorName,
                issue = issue, projectId = source.projectId, parentComment = parent
            )
        )
        mentions.update(ResourceType.ISSUE_COMMENT, requireNotNull(copy.id).toString(), source.contents.orEmpty())
        return copy
    }

    private fun moveAttachments(fromType: ResourceType, fromId: Long, toType: ResourceType, toId: () -> Long) {
        attachments.findByContainerTypeAndContainerIdOrderById(fromType, fromId).forEach { old ->
            attachments.save(
                Attachment(name = old.name, hash = old.hash, containerType = toType, mimeType = old.mimeType, size = old.size, containerId = toId(), createdDate = old.createdDate, ownerLoginId = old.ownerLoginId)
            )
            attachments.delete(old)
            attachments.flush()
        }
    }
}
