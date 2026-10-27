package dev.yona.web.issue

import dev.yona.domain.enumeration.State
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueListService
import dev.yona.domain.project.Project
import dev.yona.domain.user.User
import dev.yona.domain.user.UserDisplay
import dev.yona.domain.user.UserRepository
import dev.yona.security.AvatarService
import dev.yona.web.TemplateHelper
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Date

/** 목록에 보일 라벨 한 개이다. */
data class IssueLabelTag(val id: Long, val categoryId: Long?, val categoryName: String, val categoryExclusive: Boolean, val name: String, val color: String)

/** 하위 이슈 진행 현황이다. */
data class SubtaskProgress(val closed: Int, val total: Int, val percent: Int)

/** 부모 이슈로 가는 링크 정보이다. */
data class ParentLink(val owner: String, val project: String, val number: Long, val label: String)

/** 마감일 표시이다. [cssClass]는 `darkgray-txt`, `overdue` 같은 추가 클래스이고 [tooltip]이 있으면 말풍선으로 날짜를 보인다. */
data class DueInfo(val cssClass: String, val tooltip: String?, val text: String)

/** 목록 한 줄 아래에 접어 두는 하위 이슈 한 개이다 (v1.6 `partial_view_child`). */
data class ChildIssueItem(
    val state: String, val owner: String, val project: String, val number: Long, val draft: Boolean, val title: String,
    val assigneeName: String?, val commentCount: Int, val voterCount: Int, val labels: List<IssueLabelTag>, val createdTitle: String,
    val createdAgo: String, val selected: Boolean
)

/** 이슈 목록의 한 줄이다. 템플릿이 필요한 값을 미리 계산해 담는다. */
data class IssueListItem(
    val id: Long, val number: Long, val draft: Boolean, val weight: Int, val title: String,
    val headerWordsHtml: String, val titleWithoutHeaders: String,
    val authorLoginId: String?, val authorName: String?, val authorDisplayName: String?,
    val createdTitle: String, val createdAgo: String,
    val subtask: SubtaskProgress?, val parent: ParentLink?,
    val milestoneId: Long?, val milestoneTitle: String?,
    val commentCount: Int, val voterCount: Int, val sharerCount: Int,
    val labels: List<IssueLabelTag>, val labelsData: String,
    val children: List<ChildIssueItem>,
    val assigneeLoginId: String?, val assigneeName: String?, val assigneeAvatarUrl: String?,
    val due: DueInfo?
)

/**
 * [Issue] 목록을 템플릿이 쓰는 [IssueListItem]으로 만든다. v1.6 `issue/partial_list.scala.html`이 템플릿 안에서 하던 조회를
 * 한 번에 묶어서 한다(댓글, 추천, 공유자, 하위 이슈 수를 이슈 번호들로 묶어 조회).
 */
@Component
class IssueListAssembler(
    private val listService: IssueListService,
    private val users: UserRepository,
    private val avatars: AvatarService,
    private val helper: TemplateHelper,
    private val messages: MessageSource,
    private val clock: Clock
) {

    /**
     * 초안은 쓴 사람에게만 보인다. 다른 사람이 쓴 초안은 목록에서 뺀다.
     *
     * @param viewer 보는 사람. 없으면 익명이다
     * @param includeOthersDrafts 다른 사람이 쓴 초안도 담는다. 사용자 페이지가 쓴다(v1.6 `UserApp.userInfo`는 초안을 거르지 않는다)
     */
    fun assemble(project: Project?, issues: List<Issue>, viewer: User?, includeOthersDrafts: Boolean = false): List<IssueListItem> {
        val visible = issues.filter { includeOthersDrafts || !(it.isDraft == true && it.authorLoginId != viewer?.loginId) }
        val ids = visible.map { requireNotNull(it.id) }
        val comments = listService.commentCounts(ids)
        val voters = listService.voterCounts(ids)
        val sharers = listService.sharerCounts(ids)
        val childCounts = listService.childCounts(ids)
        val withChildren = listService.parentsWithChildren(ids)
        val authors = HashMap<String, User?>()
        val language = viewer?.lang

        return visible.map { issue ->
            val id = requireNotNull(issue.id)
            val author = issue.authorLoginId?.let { login -> authors.getOrPut(login) { users.findByLoginIdIgnoreCase(login) } }
            val open = childCounts[id to State.OPEN] ?: 0
            val closed = childCounts[id to State.CLOSED] ?: 0
            val title = issue.title.orEmpty()
            IssueListItem(
                id = id, number = issue.number ?: 0, draft = issue.isDraft == true, weight = issue.weight ?: 0, title = title,
                headerWordsHtml = helper.headerWordsHtml(title), titleWithoutHeaders = helper.removeHeaderWords(title),
                authorLoginId = author?.loginId, authorName = author?.name, authorDisplayName = author?.let { UserDisplay.of(it, language) },
                createdTitle = helper.dateString(issue.createdDate), createdAgo = helper.agoOrDateString(issue.createdDate),
                subtask = if (id in withChildren) SubtaskProgress(closed, open + closed, helper.percent(closed, open + closed)) else null,
                parent = issue.parent?.let { parent ->
                    val text = parent.title.orEmpty()
                    ParentLink(
                        parent.project?.owner.orEmpty(), parent.project?.name.orEmpty(), parent.number ?: 0,
                        "#${parent.number} " + text.take(10).trim() + if (text.length > 10) "..." else ""
                    )
                },
                milestoneId = issue.milestone?.id, milestoneTitle = issue.milestone?.title,
                commentCount = comments[id] ?: 0, voterCount = voters[id] ?: 0, sharerCount = sharers[id] ?: 0,
                labels = labelTags(issue), labelsData = labelsData(issue),
                children = if (id in withChildren) childItems(issue, comments, voters, language) else emptyList(),
                assigneeLoginId = issue.assignee?.user?.loginId, assigneeName = issue.assignee?.user?.name,
                assigneeAvatarUrl = issue.assignee?.user?.let { avatars.avatarUrl(it) },
                due = dueInfo(issue)
            )
        }
    }

    /** 라벨을 분류 이름, 라벨 이름 순으로 정렬한다 (v1.6 `partial_list`의 `sortBy(r => (r.category.name, r.name))`). */
    fun labelTags(issue: Issue): List<IssueLabelTag> = tags(issue.labels.sortedWith(compareBy({ it.category?.name }, { it.name })))

    /** 라벨을 번호순으로 돌려준다. v1.6 이슈 보기는 `issue.labels.toList`를 정렬 없이 그대로 쓴다(DB 순서). */
    fun labelTagsById(issue: Issue): List<IssueLabelTag> = tags(issue.labels.sortedBy { it.id })

    private fun tags(labels: List<dev.yona.domain.issue.IssueLabel>): List<IssueLabelTag> = labels.map {
        IssueLabelTag(requireNotNull(it.id), it.category?.id, it.category?.name.orEmpty(), it.category?.isExclusive == true, it.name.orEmpty(), it.color.orEmpty())
    }

    /** 일괄 처리 화면이 읽는 라벨 정보 문자열이다. 라벨마다 `분류이름,라벨번호,라벨이름,분류번호,단일여부|`를 이어 붙인다. */
    private fun labelsData(issue: Issue): String =
        labelTags(issue).joinToString("") { "${it.categoryName},${it.id},${it.name},${it.categoryId},${it.categoryExclusive}|" }

    /** 부모 이슈(없으면 이슈 자신)의 열린 하위 이슈, 닫힌 하위 이슈를 차례로 담는다 (v1.6 `partial_view_childIssueListOnly`). */
    private fun childItems(issue: Issue, comments: Map<Long, Int>, voters: Map<Long, Int>, viewerLang: String?): List<ChildIssueItem> {
        val parentId = issue.parent?.id ?: requireNotNull(issue.id)
        val open = listService.children(parentId, State.OPEN)
        val closed = listService.children(parentId, State.CLOSED)
        val childIds = (open + closed).map { requireNotNull(it.id) }.filter { it !in comments }
        val childComments = comments + listService.commentCounts(childIds)
        val childVoters = voters + listService.voterCounts(childIds)
        return (open.map { "open" to it } + closed.map { "closed" to it }).map { (state, child) ->
            val childId = requireNotNull(child.id)
            ChildIssueItem(
                state = state, owner = child.project?.owner.orEmpty(), project = child.project?.name.orEmpty(), number = child.number ?: 0,
                draft = child.isDraft == true, title = child.title.orEmpty(),
                assigneeName = child.assignee?.user?.let { UserDisplay.pureName(it, viewerLang) },
                commentCount = childComments[childId] ?: 0, voterCount = childVoters[childId] ?: 0, labels = labelTags(child),
                createdTitle = helper.dateString(child.createdDate), createdAgo = helper.agoOrDateString(child.createdDate),
                selected = childId == issue.id
            )
        }
    }

    private fun dueInfo(issue: Issue): DueInfo? {
        val due = issue.dueDate ?: return null
        val open = issue.state == State.OPEN
        val overdue = open && clock.instant().isAfter(due.toInstant())
        val dateText = java.text.SimpleDateFormat("yyyy-MM-dd").apply { timeZone = java.util.TimeZone.getTimeZone(clock.zone) }.format(due)
        val css = when {
            issue.state == State.CLOSED -> "darkgray-txt"
            overdue -> "overdue"
            else -> ""
        }
        val text = when {
            !open -> dateText
            overdue -> message("issue.dueDate.overdue")
            else -> until(due)
        }
        return DueInfo(css, if (open) dateText else null, text)
    }

    /** 오늘이면 `common.time.today`, 아니면 남았거나 지난 날수이다 (v1.6 `Issue.until`). */
    private fun until(due: Date): String {
        val dueDay = LocalDate.ofInstant(due.toInstant(), clock.zone)
        val today = LocalDate.now(clock)
        if (dueDay == today) return message("common.time.today")
        val days = if (clock.instant().isAfter(due.toInstant())) ChronoUnit.DAYS.between(dueDay, today) else ChronoUnit.DAYS.between(today, dueDay)
        return messages.getMessage("common.time.default.day", arrayOf(days), LocaleContextHolder.getLocale())
    }

    private fun message(key: String): String = messages.getMessage(key, null, key, LocaleContextHolder.getLocale()) ?: key
}
