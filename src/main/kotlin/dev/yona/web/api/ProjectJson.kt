package dev.yona.web.api

import dev.yona.domain.board.Posting
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueLabel
import dev.yona.domain.issue.Milestone
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.user.User
import dev.yona.util.SiteUrls
import dev.yona.util.UrlPaths
import org.springframework.stereotype.Component

/**
 * 외부용 일반 REST API(1차)가 프로젝트, 라벨, 마일스톤, 이슈 목록 항목을 JSON으로 보여 주는 형식이다(로드맵 X-19, `decisions.md` D-40).
 * [PullRequestJson]과 같은 규칙이다: 이슈 API와 같은 카멜 규칙, 사용자는 `{loginId, name}`, 시각은 ISO이고 없으면 `null`이다.
 */
@Component
class ProjectJson(private val menuSettings: ProjectMenuSettingRepository, private val pullRequestJson: PullRequestJson, private val siteUrls: SiteUrls) {

    /** 프로젝트 한 건이다. `menus`는 켜 둔 메뉴이고, 포크이면 `originalProject`, 조직 소유이면 `organization`이 있다. */
    fun project(project: Project): Map<String, Any?> {
        val menu = project.id?.let { menuSettings.findFirstByProjectId(it) }
        return linkedMapOf(
            "id" to project.id, "owner" to project.owner, "name" to project.name, "overview" to project.overview, "vcs" to project.vcs, "scope" to project.projectScope?.name,
            "createdAt" to pullRequestJson.iso(project.createdDate), "url" to siteUrls.path(UrlPaths.projectPath(project.owner, project.name)),
            "originalProject" to project.originalProject?.let { linkedMapOf("owner" to it.owner, "name" to it.name) },
            "organization" to project.organization?.name,
            "menus" to linkedMapOf("code" to (menu?.code == true), "issue" to (menu?.issue == true), "pullRequest" to (menu?.pullRequest == true), "review" to (menu?.review == true), "milestone" to (menu?.milestone == true), "board" to (menu?.board == true), "wiki" to (menu?.wiki == true))
        )
    }

    fun label(label: IssueLabel): Map<String, Any?> = linkedMapOf(
        "id" to label.id, "labelName" to label.name, "labelColor" to label.color, "category" to label.category?.name, "categoryId" to label.category?.id, "isExclusive" to (label.category?.isExclusive == true)
    )

    fun milestone(milestone: Milestone): Map<String, Any?> = linkedMapOf(
        "id" to milestone.id, "title" to milestone.title, "state" to (milestone.state?.state ?: "open"), "description" to milestone.contents, "dueDate" to pullRequestJson.iso(milestone.dueDate)
    )

    /** 이슈 목록의 한 항목이다. 본문과 댓글은 담지 않는다(`GET .../issues/{number}`가 담는다). */
    fun issueSummary(issue: Issue, author: User?): Map<String, Any?> {
        val project = requireNotNull(issue.project)
        return linkedMapOf(
            "number" to issue.number, "id" to issue.id, "title" to issue.title, "state" to (issue.state?.state ?: "open"),
            "author" to (author?.let { pullRequestJson.user(it) } ?: linkedMapOf("loginId" to issue.authorLoginId, "name" to issue.authorName)),
            "assignees" to listOfNotNull(issue.assignee?.user).map { pullRequestJson.user(it) },
            "labels" to issue.labels.sortedBy { it.id }.map { linkedMapOf("id" to it.id, "labelName" to it.name, "labelColor" to it.color, "category" to it.category?.name) },
            "milestone" to issue.milestone?.let { linkedMapOf("id" to it.id, "title" to it.title) },
            "createdAt" to pullRequestJson.iso(issue.createdDate), "updatedAt" to pullRequestJson.iso(issue.updatedDate ?: issue.createdDate), "dueDate" to pullRequestJson.iso(issue.dueDate),
            "numOfComments" to (issue.numOfComments ?: 0), "url" to siteUrls.path("${UrlPaths.projectPath(project.owner, project.name)}/issue/${issue.number}")
        )
    }

    /** 게시글 목록의 한 항목이다. 본문과 댓글은 담지 않는다(`GET .../posts/{number}`가 담는다). 공지는 `notice`로 가린다(X-29b). */
    fun postingSummary(posting: Posting, author: User?): Map<String, Any?> {
        val project = requireNotNull(posting.project)
        return linkedMapOf(
            "number" to posting.number, "id" to posting.id, "title" to posting.title, "notice" to (posting.notice == true),
            "author" to (author?.let { pullRequestJson.user(it) } ?: linkedMapOf("loginId" to posting.authorLoginId, "name" to posting.authorName)),
            "labels" to posting.labels.sortedBy { it.id }.map { label(it) },
            "createdAt" to pullRequestJson.iso(posting.createdDate), "updatedAt" to pullRequestJson.iso(posting.updatedDate ?: posting.createdDate),
            "numOfComments" to (posting.numOfComments ?: 0), "url" to siteUrls.path("${UrlPaths.projectPath(project.owner, project.name)}/post/${posting.number}")
        )
    }
}
