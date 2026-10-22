package dev.yona.web.issue

import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.domain.comment.IssueComment
import dev.yona.domain.comment.IssueCommentRepository
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.State
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueEvent
import dev.yona.domain.issue.IssueEventRepository
import dev.yona.domain.issue.IssueLabelService
import dev.yona.domain.issue.IssueListService
import dev.yona.domain.issue.IssueService
import dev.yona.domain.misc.UnwatchRepository
import dev.yona.domain.misc.WatchRepository
import dev.yona.domain.project.Project
import dev.yona.domain.user.User
import dev.yona.domain.user.UserDisplay
import dev.yona.domain.user.UserRepository
import dev.yona.markdown.MarkdownRenderer
import dev.yona.markdown.ProjectMarkdownRenderer
import dev.yona.security.AvatarService
import dev.yona.web.AttachmentListing
import dev.yona.web.TemplateHelper
import jakarta.persistence.EntityManager
import org.jsoup.Jsoup
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 이슈 보기 화면의 모델을 만든다. v1.6 `issue/view.scala.html`과 그 부분 템플릿이 템플릿 안에서 하던 조회와 판단을 한곳에 모았다.
 */
@Component
class IssueViewAssembler(
    private val issueService: IssueService,
    private val comments: IssueCommentRepository,
    private val events: IssueEventRepository,
    private val listService: IssueListService,
    private val labelService: IssueLabelService,
    private val users: UserRepository,
    private val watches: WatchRepository,
    private val unwatches: UnwatchRepository,
    private val accessControl: AccessControl,
    private val avatars: AvatarService,
    private val markdown: ProjectMarkdownRenderer,
    private val rawMarkdown: MarkdownRenderer,
    private val attachmentListing: AttachmentListing,
    private val listAssembler: IssueListAssembler,
    private val eventHtml: IssueEventHtml,
    private val helper: TemplateHelper,
    private val messages: MessageSource,
    private val entityManager: EntityManager,
    private val clock: Clock,
    private val milestoneService: dev.yona.domain.issue.MilestoneService,
    private val formOptions: IssueFormOptions
) {

    fun assemble(project: Project, issue: Issue, viewer: User?): IssueDetail {
        val issueId = requireNotNull(issue.id)
        val resource = issue.asResource()
        val lang = viewer?.lang
        val allComments = comments.findByIssueIdOrderByCreatedDateAscIdAsc(issueId)
        val allEvents = events.findByIssueIdOrderByCreatedAscIdAsc(issueId)
        val voters = issue.voters.sortedBy { it.id }
        val votedByMe = viewer != null && voters.any { it.id == viewer.id }
        val sharers = entityManager.createQuery("select s from IssueSharer s where s.issue.id = :id order by s.id", dev.yona.domain.issue.IssueSharer::class.java)
            .setParameter("id", issueId).resultList
        val sharedWithMe = viewer != null && sharers.any { it.user?.id == viewer.id }
        val canUpdate = accessControl.isAllowed(viewer, resource, Operation.UPDATE)
        val timeline = timeline(project, issue, allComments, allEvents, viewer)
        val body = issue.body.orEmpty()
        val author = issue.authorLoginId?.let { users.findByLoginIdIgnoreCase(it) }
        val milestone = issue.milestone
        val due = issue.dueDate
        val overdue = issue.state == State.OPEN && due != null && clock.instant().isAfter(due.toInstant())
        val labels = listAssembler.labelTags(issue)
        val history = issue.history
        val allMilestones = if (project.id != null) milestoneService.list(project, State.ALL, "dueDate", false) else emptyList()

        return IssueDetail(
            id = issueId, number = issue.number ?: 0, title = issue.title.orEmpty(), body = body,
            bodyHtml = if (body.isEmpty()) null else markdown.render(body, project, lang), history = history,
            historyHtml = if (history.isNullOrEmpty()) null else rawMarkdown.sanitize(history),
            state = issue.state?.state.orEmpty(), stateKey = "issue.state.${issue.state?.state}", draft = issue.isDraft == true,
            hasParent = issue.parent != null, weight = issue.weight ?: 0,
            createdTitle = helper.dateString(issue.createdDate), createdAgo = helper.agoOrDateString(issue.createdDate),
            updatedTime = issue.updatedDate?.time ?: 0, updatedByName = issue.updatedByAuthorId?.let { users.findById(it).orElse(null) }?.let { UserDisplay.pureName(it, lang) },
            updatedAgo = helper.agoOrDateString(issue.updatedDate),
            me = viewer?.let { person(it, lang) }, author = author?.let { person(it, lang) }, favorite = viewer != null && isFavorite(viewer, issueId),
            watching = viewer != null && isWatching(viewer, issueId), watchable = accessControl.isAllowed(viewer, resource, Operation.WATCH),
            canUpdate = canUpdate, canDelete = accessControl.isAllowed(viewer, resource, Operation.DELETE),
            deletable = issueService.canBeDeleted(issue), canVoteOrComment = accessControl.isResourceCreatable(viewer, resource, ResourceType.ISSUE_COMMENT),
            voters = voters.map { person(it, lang) }, votedByMe = votedByMe,
            // v1.6 `VoteApp.getVotersExceptCurrentUser`는 이슈의 추천자 집합에서 현재 사용자를 실제로 뺀다. 그 뒤에 그려지는 추천자 대화상자도 같은 집합을 쓴다.
            votersExceptMe = voters.filter { it.id != viewer?.id }.map { person(it, lang) },
            sharers = sharers.mapNotNull { it.user }.map { person(it, lang) }, sharerLoginIds = sharers.joinToString(",") { it.loginId.orEmpty() }, sharedWithMe = sharedWithMe,
            parentIssueId = issue.parent?.id ?: issueId, assignee = issue.assignee?.user?.let { person(it, lang) },
            milestoneId = milestone?.id, milestoneTitle = milestone?.title,
            hasMilestones = allMilestones.isNotEmpty(),
            openMilestones = allMilestones.filter { it.state == State.OPEN }.map { MilestoneOption(requireNotNull(it.id), it.title.orEmpty(), "OPEN") },
            closedMilestones = allMilestones.filter { it.state == State.CLOSED }.map { MilestoneOption(requireNotNull(it.id), it.title.orEmpty(), "CLOSED") },
            projectLabels = formOptions.labelGroups(labelService.labels(project)),
            dueDateString = due?.let { java.text.SimpleDateFormat("yyyy-MM-dd").apply { timeZone = java.util.TimeZone.getTimeZone(clock.zone) }.format(it) },
            dueDateText = if (due != null && issue.state == State.OPEN) (if (overdue) message("issue.dueDate.overdue") else until(due)) else null,
            dueOver = overdue, labels = labels, selectedLabelIds = LinkedHashSet(labels.map { it.id }),
            attachmentsJson = attachmentListing.fileListJson(ResourceType.ISSUE_POST, issueId), bodyChecksum = sha1Hex(body),
            numOfComments = allComments.size, nextStateName = if (issue.state == State.CLOSED) "open" else "closed",
            subtasks = subtasks(issue, viewer, sharedWithMe), timeline = timeline, commentCount = allComments.size, index = indexComments(project, allComments, viewer)
        )
    }

    private fun person(user: User, lang: String?) = PersonView(
        user.loginId, user.name, UserDisplay.of(user, lang), UserDisplay.pureName(user, lang), avatars.avatarUrl(user), user.email
    )

    private fun isFavorite(viewer: User, issueId: Long): Boolean =
        entityManager.createQuery("select count(f) from FavoriteIssue f where f.user.id = :u and f.issue.id = :i", java.lang.Long::class.java)
            .setParameter("u", viewer.id).setParameter("i", issueId).singleResult.toLong() > 0

    private fun isWatching(viewer: User, issueId: Long): Boolean {
        val userId = requireNotNull(viewer.id)
        return watches.existsByUserIdAndResourceTypeAndResourceId(userId, ResourceType.ISSUE_POST, issueId.toString()) &&
            !unwatches.existsByUserIdAndResourceTypeAndResourceId(userId, ResourceType.ISSUE_POST, issueId.toString())
    }

    private fun until(due: java.util.Date): String {
        val dueDay = LocalDate.ofInstant(due.toInstant(), clock.zone)
        val today = LocalDate.now(clock)
        if (dueDay == today) return message("common.time.today")
        val days = if (clock.instant().isAfter(due.toInstant())) ChronoUnit.DAYS.between(dueDay, today) else ChronoUnit.DAYS.between(today, dueDay)
        return messages.getMessage("common.time.default.day", arrayOf(days), LocaleContextHolder.getLocale())
    }

    private fun message(key: String): String = messages.getMessage(key, null, key, LocaleContextHolder.getLocale()) ?: key

    /** 댓글과 이벤트를 시간 순서로 섞는다. 한 줄 댓글은 부모 댓글 아래에 보이므로 목록에서는 뺀다. */
    private fun timeline(project: Project, issue: Issue, allComments: List<IssueComment>, allEvents: List<IssueEvent>, viewer: User?): List<TimelineEntry> {
        val items = (allComments.map { it.createdDate to it } + allEvents.map { it.created to it }).sortedBy { it.first?.time ?: 0L }
        val result = mutableListOf<TimelineEntry>()
        items.forEachIndexed { index, (_, item) ->
            when (item) {
                is IssueComment -> if (item.parentComment == null) result += TimelineEntry(commentView(project, issue, item, viewer), null)
                is IssueEvent -> {
                    val previous = items.getOrNull(index - 1)?.second as? IssueEvent
                    eventHtml.render(item, project, previous, viewer)?.let { result += TimelineEntry(null, EventView(requireNotNull(item.id), it)) }
                }
            }
        }
        return result
    }

    private fun commentView(project: Project, issue: Issue, comment: IssueComment, viewer: User?): CommentView {
        val lang = viewer?.lang
        val resource = comment.asResource()
        val id = requireNotNull(comment.id)
        val author = comment.authorLoginId?.let { users.findByLoginIdIgnoreCase(it) }
        val children = comments.findByParentCommentIdOrderByIdAsc(id)
        val voters = comment.voters.sortedBy { it.id }
        val originalEmail = entityManager.createQuery(
            "select count(o) from OriginalEmail o where o.resourceType = :t and o.resourceId = :i", java.lang.Long::class.java
        ).setParameter("t", ResourceType.ISSUE_COMMENT).setParameter("i", id.toString()).singleResult.toLong() > 0
        return CommentView(
            id = id, author = author?.let { person(it, lang) } ?: PersonView(comment.authorLoginId, comment.authorName, comment.authorName.orEmpty(), comment.authorName.orEmpty(), "", null),
            createdTitle = helper.dateString(comment.createdDate), createdAgo = helper.agoOrDateString(comment.createdDate),
            contents = comment.contents.orEmpty(), contentsHtml = markdown.render(comment.contents.orEmpty(), project, lang),
            own = viewer != null && comment.authorLoginId == viewer.loginId,
            canRead = accessControl.isAllowed(viewer, resource, Operation.READ), canUpdate = accessControl.isAllowed(viewer, resource, Operation.UPDATE),
            canDelete = accessControl.isAllowed(viewer, resource, Operation.DELETE),
            voters = voters.map { person(it, lang) }, votedByMe = viewer != null && voters.any { it.id == viewer.id }, viewerAnonymous = viewer == null,
            attachmentsJson = attachmentListing.fileListJson(ResourceType.ISSUE_COMMENT, id),
            attachments = attachmentListing.fileList(ResourceType.ISSUE_COMMENT, id)?.get("attachments").orEmpty(), viaEmail = originalEmail,
            childAnchorIds = children.mapNotNull { it.id },
            children = children.map { child ->
                val childAuthor = child.authorId?.let { users.findById(it).orElse(null) }
                ChildCommentView(
                    requireNotNull(child.id), markdown.render(child.contents.orEmpty(), project, lang), childAuthor?.loginId, childAuthor?.let { UserDisplay.pureName(it, lang) }.orEmpty(),
                    helper.dateString(child.createdDate), helper.agoOrDateString(child.createdDate), accessControl.isAllowed(viewer, child.asResource(), Operation.DELETE)
                )
            },
            canReply = accessControl.isResourceCreatable(viewer, issue.asResource(), ResourceType.ISSUE_COMMENT)
        )
    }

    /** 오른쪽 창 아래의 댓글 색인이다. 한 줄 댓글을 뺀 댓글마다 본문 앞 60자와 작성자, 시각을 보인다. */
    private fun indexComments(project: Project, allComments: List<IssueComment>, viewer: User?): List<IndexCommentView> {
        val mention = viewer?.loginId?.let { "@$it " }
        return allComments.filter { it.parentComment == null }.map { comment ->
            val children = comments.findByParentCommentIdOrderByIdAsc(requireNotNull(comment.id))
            val text = Jsoup.parse(markdown.render(comment.contents.orEmpty(), project, viewer?.lang)).text().trim()
            val author = comment.authorLoginId?.let { users.findByLoginIdIgnoreCase(it) }
            IndexCommentView(
                comment.id!!, if (text.length > ELLIPSIS_LENGTH) text.substring(0, ELLIPSIS_LENGTH) + "..." else text,
                mention != null && comment.contents.orEmpty().contains(mention), mention != null && children.any { it.contents.orEmpty().contains(mention) },
                children.size, comment.authorLoginId, author?.let { UserDisplay.pureName(it, viewer?.lang) }.orEmpty(),
                helper.dateString(comment.createdDate), helper.agoOrDateString(comment.createdDate)
            )
        }
    }

    /** 부모 이슈와 하위 이슈 목록이다. 하위 이슈가 없으면 `null`이다 (v1.6 `partial_view_childIssueList`). 공유받은 하위 이슈에서는 보이지 않는다. */
    private fun subtasks(issue: Issue, viewer: User?, sharedWithMe: Boolean): SubtaskView? {
        if (sharedWithMe && issue.parent != null) return null
        val parentId = issue.parent?.id ?: requireNotNull(issue.id)
        val open = listService.children(parentId, State.OPEN)
        val closed = listService.children(parentId, State.CLOSED)
        val draft = listService.children(parentId, State.DRAFT)
        val showDrafts = draft.isNotEmpty() && issue.isDraft == true && viewer != null && issue.authorLoginId == viewer.loginId
        if (open.isEmpty() && closed.isEmpty() && !showDrafts) return null
        val parent = issue.parent?.let { entityManager.find(Issue::class.java, it.id) } ?: issue
        val rows = (draft.map { "draft" to it } + open.map { "open" to it } + closed.map { "closed" to it })
            .filter { (_, child) -> child.isDraft != true || child.authorLoginId == viewer?.loginId }
        val ids = rows.map { requireNotNull(it.second.id) }
        val commentCounts = listService.commentCounts(ids)
        val voterCounts = listService.voterCounts(ids)
        val items = rows.map { (state, child) ->
            ChildIssueItem(
                state = state, owner = child.project?.owner.orEmpty(), project = child.project?.name.orEmpty(), number = child.number ?: 0,
                draft = child.isDraft == true, title = child.title.orEmpty(), assigneeName = child.assignee?.user?.let { UserDisplay.pureName(it, viewer?.lang) },
                commentCount = commentCounts[child.id] ?: 0, voterCount = voterCounts[child.id] ?: 0, labels = listAssembler.labelTags(child),
                createdTitle = helper.dateString(child.createdDate), createdAgo = helper.agoOrDateString(child.createdDate), selected = child.id == issue.id
            )
        }
        return SubtaskView(
            parentOwner = parent.project?.owner.orEmpty(), parentProject = parent.project?.name.orEmpty(), parentNumber = parent.number ?: 0,
            parentTitle = parent.title.orEmpty(), parentAssigneeName = parent.assignee?.user?.let { UserDisplay.pureName(it, viewer?.lang) },
            parentState = parent.state?.state.orEmpty(), parentIsCurrent = parent.id == issue.id,
            percent = helper.percent(closed.size, open.size + closed.size), done = closed.size, total = open.size + closed.size, children = items
        )
    }

    private fun sha1Hex(text: String): String =
        java.security.MessageDigest.getInstance("SHA-1").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private companion object {
        const val ELLIPSIS_LENGTH = 60
    }
}
