package dev.yona.web.project

import dev.yona.domain.project.DashboardByAssignee
import dev.yona.domain.project.DashboardByMilestone
import dev.yona.domain.project.DashboardLabelGroup
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectDashboardService
import dev.yona.domain.project.ProjectHistoryService
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.security.AvatarService
import dev.yona.util.SiteUrls
import dev.yona.web.TemplateHelper
import org.springframework.stereotype.Component
import org.springframework.web.util.HtmlUtils

/**
 * 이력 탭 한 줄이다. v1.6 `partial_history.scala.html`이 템플릿 안에서 만들던 HTML 조각을 미리 만들어 담는다.
 *
 * @property headerHtml `project.history.item` 메시지에 사용자 링크, 종류 문구, 제목 링크를 넣은 HTML이다
 */
data class HistoryRow(val userPageUrl: String, val avatarUrl: String, val headerHtml: String, val ago: String, val agoTitle: String)

/** 대시보드 풀 리퀘스트 영역 한 줄이다. */
data class DashboardPullRequestRow(
    val href: String, val title: String, val contributorId: Long?, val contributorLoginId: String, val contributorName: String, val avatarUrl: String,
    val filterHref: String, val ago: String,
    /** 붙은 라벨과 담당자(추가 항목 X-14, D-34) */
    val labels: List<dev.yona.web.issue.IssueLabelTag> = emptyList(), val assigneeLoginId: String? = null, val assigneeName: String? = null, val assigneeAvatarUrl: String? = null
)

/**
 * 프로젝트 홈의 이력, 대시보드 탭이 쓰는 값을 만든다.
 *
 * @property issueBase 이슈 목록 링크의 앞부분이다. 뒤에 `&키=값`을 붙인다 (v1.6 `makeIssuesLink`)
 */
data class DashboardView(
    val issueMenu: Boolean, val pullRequestArea: Boolean, val owner: String, val name: String, val issueBase: String,
    val assignee: DashboardByAssignee?, val assigneeAvatars: Map<Long, String>, val milestone: DashboardByMilestone?,
    val pullRequests: List<DashboardPullRequestRow>, val pullRequestOpenCount: Long, val labels: List<DashboardLabelGroup>
)

/** 이력 탭과 대시보드 탭의 화면 값을 만든다. */
@Component
class ProjectHomeTabsAssembler(
    private val history: ProjectHistoryService,
    private val dashboard: ProjectDashboardService,
    private val avatars: AvatarService,
    private val siteUrls: SiteUrls,
    private val helper: TemplateHelper,
    private val messages: org.springframework.context.MessageSource
) {

    /** [owner]는 요청 주소에 쓴 소유자이다. 이력 항목의 주소가 이 값을 쓴다. */
    fun historyRows(owner: String, project: Project): List<HistoryRow> {
        val locale = org.springframework.context.i18n.LocaleContextHolder.getLocale()
        fun text(key: String) = HtmlUtils.htmlEscape(messages.getMessage(key, null, key, locale) ?: key)
        return history.history(owner, project).map { item ->
            val page = item.userPageUrl ?: "#"
            val who = """<a href="${HtmlUtils.htmlEscape(page)}" class="actor">${HtmlUtils.htmlEscape(item.who.orEmpty())}</a>"""
            val how = """<span class="whereis"><a href="${HtmlUtils.htmlEscape(item.url)}" class="where">${HtmlUtils.htmlEscape(item.shortTitle)}</a> """ +
                """<a href="${HtmlUtils.htmlEscape(item.url)}" class="title">${HtmlUtils.htmlEscape(item.how.orEmpty())}</a></span> """
            HistoryRow(
                userPageUrl = page, avatarUrl = item.userAvatarUrl ?: siteUrls.path("/assets/images/default-avatar-64.png"),
                headerHtml = java.text.MessageFormat(messages.getMessage("project.history.item", null, "{0} {1} {2}", locale) ?: "{0} {1} {2}", locale)
                    .format(arrayOf(who, text("project.history.type.${item.what}"), how)),
                ago = helper.agoOrDateString(item.whenDate), agoTitle = helper.dateString(item.whenDate)
            )
        }
    }

    /** 메뉴 설정에 따라 보일 영역만 채운다. */
    fun dashboard(project: Project, issueMenu: Boolean, pullRequestMenu: Boolean, git: Boolean): DashboardView {
        val owner = project.owner.orEmpty(); val name = project.name.orEmpty()
        val assignee = if (issueMenu) dashboard.byAssignee(project) else null
        val pullRequestArea = pullRequestMenu && git
        val pulls = if (pullRequestArea) dashboard.pullRequests(project) else null
        return DashboardView(
            issueMenu = issueMenu, pullRequestArea = pullRequestArea, owner = owner, name = name, issueBase = siteUrls.path("${dev.yona.util.UrlPaths.projectPath(owner, name)}/issues?state=open"),
            assignee = assignee, assigneeAvatars = assignee?.rows?.associate { requireNotNull(it.user.id) to avatars.avatarUrl(it.user, SMALL_AVATAR) }.orEmpty(),
            milestone = if (issueMenu) dashboard.byMilestone(project) else null,
            pullRequests = pulls?.recent.orEmpty().map { row(it, owner, name) }, pullRequestOpenCount = pulls?.openCount ?: 0,
            labels = if (issueMenu) dashboard.byLabel(project) else emptyList()
        )
    }

    private fun row(pr: PullRequest, owner: String, name: String): DashboardPullRequestRow {
        val to = requireNotNull(pr.toProject)
        val who = pr.contributor
        return DashboardPullRequestRow(
            href = siteUrls.path("${dev.yona.util.UrlPaths.projectPath(to.owner, to.name)}/pullRequest/${pr.number}"), title = pr.title.orEmpty(), contributorId = who?.id, contributorLoginId = who?.loginId.orEmpty(),
            contributorName = who?.name.orEmpty(), avatarUrl = who?.let { avatars.avatarUrl(it, SMALL_AVATAR) }.orEmpty(),
            filterHref = siteUrls.path("${dev.yona.util.UrlPaths.projectPath(owner, name)}/pullRequests?contributorId=${who?.id}"), ago = helper.agoOrDateString(pr.created),
            labels = dev.yona.web.pullrequest.labelTagsOf(pr), assigneeLoginId = pr.assignees.firstOrNull()?.loginId, assigneeName = pr.assignees.firstOrNull()?.name,
            assigneeAvatarUrl = pr.assignees.firstOrNull()?.let { avatars.avatarUrl(it, SMALL_AVATAR) }
        )
    }

    private companion object {
        const val SMALL_AVATAR = 64
    }
}
