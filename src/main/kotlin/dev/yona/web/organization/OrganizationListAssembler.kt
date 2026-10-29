package dev.yona.web.organization

import dev.yona.domain.comment.CommentThreadRepository
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.ThreadState
import dev.yona.domain.issue.Issue
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.AvatarService
import dev.yona.web.TemplateHelper
import dev.yona.web.issue.IssueListAssembler
import dev.yona.web.issue.IssueListItem
import dev.yona.web.project.ProjectViewFactory
import org.springframework.stereotype.Component
import java.text.SimpleDateFormat

/**
 * 조직 이슈 목록의 한 줄이다. v1.6 `group_issue_list_partial.scala.html`이 보이는 값을 미리 계산해 담는다.
 *
 * @property authorAvatarUrl 작성자 아바타. 기본 아바타이면 gravatar(32px)이다. 작성자를 찾지 못하면 `null`이다
 * @property milestoneVisible 이슈에 마일스톤이 있고 프로젝트의 마일스톤 메뉴가 켜져 있으면 `true`이다
 * @property dueDateString 마감일을 `yyyy-MM-dd`로 쓴 문자열이다. 마감일이 없으면 `null`이다
 */
data class OrganizationIssueRow(
    val item: IssueListItem, val owner: String, val project: String, val authorAvatarUrl: String?, val milestoneVisible: Boolean, val dueDateString: String?
) {
    /** 마감이 지난 열린 이슈이면 `true`이다. */
    val overdue: Boolean get() = item.due?.cssClass == "overdue"
}

/** 조직 풀 리퀘스트 목록 한 줄의 사람(보낸 사람, 담당자)이다. */
data class OrganizationPerson(val loginId: String, val name: String?, val avatarUrl: String)

/**
 * 조직 풀 리퀘스트 목록의 한 줄이다. v1.6 `group_pullrequest_list_partial.scala.html`.
 *
 * @property progress 리뷰 스레드가 있을 때 닫힌 수, 전체 수, 진행 비율이다
 */
data class OrganizationPullRequestRow(
    val number: Long, val href: String, val headerWordsHtml: String, val title: String, val conflict: Boolean,
    val contributor: OrganizationPerson?, val ago: String, val agoTitle: String, val owner: String, val project: String,
    val progress: Progress?, val receiver: OrganizationPerson?, val stateClass: String, val stateKey: String,
    /** 담당자(추가 항목 X-14, D-34). 받은 사람과 다른 역할이라 따로 보인다 */
    val assignee: OrganizationPerson? = null,
    /** 붙은 라벨(분류 이름, 라벨 이름 순) */
    val labels: List<dev.yona.web.issue.IssueLabelTag> = emptyList()
) {
    data class Progress(val closed: Long, val total: Long, val percent: Int, val href: String)
}

/** 조직 목록 화면이 쓰는 행을 만든다. */
@Component
class OrganizationListAssembler(
    private val issueItems: IssueListAssembler,
    private val users: UserRepository,
    private val avatars: AvatarService,
    private val projectViews: ProjectViewFactory,
    private val threads: CommentThreadRepository,
    private val helper: TemplateHelper
) {

    /** 이슈 행을 만든다. 초안은 검색에서 이미 빠져 있다. */
    fun issueRows(list: List<Issue>, viewer: User?): List<OrganizationIssueRow> {
        val items = issueItems.assemble(null, list, viewer, includeOthersDrafts = true).associateBy { it.id }
        return list.mapNotNull { issue ->
            val item = items[issue.id] ?: return@mapNotNull null
            val project = requireNotNull(issue.project)
            val author = issue.authorLoginId?.let { users.findByLoginIdIgnoreCase(it) }
            OrganizationIssueRow(
                item = item, owner = project.owner.orEmpty(), project = project.name.orEmpty(), authorAvatarUrl = author?.let { avatarOf(it) },
                milestoneVisible = issue.milestone != null && projectViews.of(project).menuSetting.milestone == true,
                dueDateString = issue.dueDate?.let { SimpleDateFormat("yyyy-MM-dd").format(it) }
            )
        }
    }

    /** 풀 리퀘스트 행을 만든다. */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    fun pullRequestRows(list: List<PullRequest>, viewer: User?): List<OrganizationPullRequestRow> = list.map { pr ->
        val to = requireNotNull(pr.toProject)
        val base = "${dev.yona.util.UrlPaths.projectPath(to.owner, to.name)}/pullRequest/${pr.number}"
        val id = requireNotNull(pr.id)
        val total = threads.countByPullRequestId(id)
        val closed = if (total > 0) threads.countByPullRequestIdAndState(id, ThreadState.CLOSED) else 0
        val conflict = pr.isConflict == true
        val state = (pr.state ?: State.OPEN).name.lowercase()
        val contributor = pr.contributor?.loginId?.let { users.findByLoginIdIgnoreCase(it) }
        OrganizationPullRequestRow(
            number = requireNotNull(pr.number), href = base, headerWordsHtml = helper.headerWordsHtml(pr.title), title = helper.removeHeaderWords(pr.title),
            conflict = conflict, contributor = contributor?.let { OrganizationPerson(it.loginId.orEmpty(), it.name, avatarOf(it)) },
            ago = helper.agoOrDateString(pr.created), agoTitle = helper.dateString(pr.created), owner = to.owner.orEmpty(), project = to.name.orEmpty(),
            progress = if (total > 0) OrganizationPullRequestRow.Progress(closed, total, helper.percent(closed.toDouble(), total.toDouble()), "$base/changes") else null,
            receiver = pr.receiver?.let { OrganizationPerson(it.loginId.orEmpty(), it.name, avatars.avatarUrl(it)) },
            stateClass = if (conflict) "conflict" else state, stateKey = if (conflict) "pullRequest.state.conflict" else "pullRequest.state.$state",
            assignee = pr.assignees.firstOrNull()?.let { OrganizationPerson(it.loginId.orEmpty(), it.name, avatars.avatarUrl(it)) }, labels = dev.yona.web.pullrequest.labelTagsOf(pr)
        )
    }

    /** 기본 아바타이면 gravatar를 쓴다 (v1.6 `urlToPicture(email, 32)`). */
    private fun avatarOf(user: User): String {
        val avatar = avatars.avatarUrl(user)
        return if (avatar == AvatarService.DEFAULT_AVATAR_URL) avatars.gravatarUrl(user.email, AVATAR_SIZE) else avatar
    }

    private companion object {
        const val AVATAR_SIZE = 32
    }
}
