package dev.yona.web.code

import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectService
import dev.yona.markdown.AutoLinkRenderer
import dev.yona.domain.user.UserRepository
import dev.yona.repository.CommitHistoryService
import dev.yona.repository.CommitInfo
import dev.yona.repository.RepositoryService
import dev.yona.security.AvatarService
import dev.yona.util.SiteUrls
import dev.yona.util.UrlPaths
import dev.yona.web.TemplateHelper
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.CodeAccessCheck
import dev.yona.web.error.ErrorViews
import dev.yona.web.project.ProjectViewFactory
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.util.HtmlUtils
import java.net.URLEncoder

/** 커밋 이력 한 줄의 화면 값이다. [messageHtml]은 첫 줄을 뺀 나머지 메시지이다(없으면 빈 값). */
data class HistoryRow(
    val id: String, val shortId: String, val showUrl: String, val shortMessageHtml: String, val moreHtml: String, val hasMore: Boolean, val browseUrl: String?,
    val commentCount: Int, val date: String, val authorHref: String?, val authorLoginId: String?, val authorName: String?, val avatarUrl: String?, val authorEmail: String?
)

/**
 * 커밋 이력 화면이다. v1.6 `CodeHistoryApp.history`, `historyUntilHead`(`app/controllers/CodeHistoryApp.java:60-105`)와
 * `app/views/code/history.scala.html`에 해당한다.
 *
 * 한 쪽에 25개씩 보이고 최근 것이 위에 온다. 경로를 정하면 그 경로를 바꾼 커밋만 모으고, 브랜치 선택과 탭 대신 이동 경로와 "코드 보기" 단추가 나온다.
 * 저장소에 커밋이 없으면 `404`로 처음 올리는 방법을 안내한다. 커밋 메시지는 이스케이프한 뒤 자동 링크를 만든다([decisions.md] A-71).
 */
@Controller
@AnonymousCheck
class CommitHistoryController(
    private val projectService: ProjectService,
    private val history: CommitHistoryService,
    private val repositories: RepositoryService,
    private val autoLinks: AutoLinkRenderer,
    private val users: UserRepository,
    private val userLookup: dev.yona.domain.user.UserLookup,
    private val avatars: AvatarService,
    private val views: ProjectViewFactory,
    private val helper: TemplateHelper,
    private val siteUrls: SiteUrls,
    private val reviewThreads: dev.yona.domain.comment.ReviewThreadService,
    private val commitComments: dev.yona.domain.comment.CommitCommentRepository
) {

    /** 기본 브랜치(`HEAD`)까지의 이력이다. */
    @GetMapping("/{user}/{project}/commits")
    @CodeAccessCheck
    @Transactional(readOnly = true)
    fun historyUntilHead(@PathVariable("user") owner: String, @PathVariable("project") name: String, @RequestParam(required = false) page: String?, request: HttpServletRequest): Any =
        show(owner, name, null, null, page, request)

    @GetMapping("/{user}/{project}/commits/{branch}", "/{user}/{project}/commits/{branch}/")
    @CodeAccessCheck
    @Transactional(readOnly = true)
    fun historyOfBranch(
        @PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable branch: String, @RequestParam(required = false) page: String?, request: HttpServletRequest
    ): Any = show(owner, name, UrlPaths.decodeSegmentOrSelf(branch), null, page, request)

    @GetMapping("/{user}/{project}/commits/{branch}/{*path}")
    @CodeAccessCheck
    @Transactional(readOnly = true)
    fun historyOfPath(
        @PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable branch: String, @PathVariable path: String,
        @RequestParam(required = false) page: String?, request: HttpServletRequest
    ): Any {
        val decoded = UrlPaths.decodeSegmentOrSelf(path.removePrefix("/"))
        return show(owner, name, UrlPaths.decodeSegmentOrSelf(branch), decoded.ifEmpty { null }, page, request)
    }

    private fun show(owner: String, name: String, branch: String?, path: String?, pageText: String?, request: HttpServletRequest): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val page = if (pageText.isNullOrEmpty()) 0 else pageText.toIntOrNull()?.takeIf { it >= 0 } ?: return ErrorViews.badRequest("error.badrequest", views.of(project), "CODE")
        // 빈 저장소와 풀 수 없는 리비전은 모두 null이고 error.notfound 404이다 (v1.6 `CodeHistoryApp`: nohead 화면에는 도달하지 않는다)
        val commits = history.history(project, page, PAGE_SIZE, branch, path) ?: return ErrorViews.notFound("error.notfound", views.of(project), null)

        val base = siteUrls.path("/$owner/$name")
        val branchSegment = branch?.let { UrlPaths.routeSegment(it) }
        val historyUrl = if (branchSegment != null) "$base/commits/$branchSegment/" else "$base/commits"
        val rows = commits.map { row(project, it, branch, path, base) }
        return ModelAndView("code/history")
            .addObject("title", "title.commitHistory")
            .addObject("projectView", views.of(project))
            .addObject("git", project.vcs != RepositoryService.VCS_SUBVERSION)
            .addObject("rows", rows)
            .addObject("hasPath", path != null)
            .addObject("rootUrl", "$base/commits/${UrlPaths.routeSegment(branch.orEmpty())}/")
            .addObject("crumbs", breadcrumbs(base, branch, path))
            .addObject("branches", repositories.refNames(project).map { ref ->
                BranchOption("$base/commits/${UrlPaths.routeSegment(ref)}/", ref, branch != null && CodePageAssembler.branchItemName(ref) == CodePageAssembler.branchItemName(branch))
            })
            .addObject("filesTabUrl", "$base/code/${if (branchSegment != null) branchSegment else "HEAD"}/")
            .addObject("commitsTabUrl", if (path != null && branchSegment != null) "$base/commits/$branchSegment/${UrlPaths.encodeEach(path)}" else historyUrl)
            .addObject("branchesTabUrl", "$base/branches")
            .addObject("newerUrl", if (page > 0) pageUrl(historyUrl, path, branchSegment, page - 1) else null)
            .addObject("olderUrl", if (commits.isNotEmpty() && commits.last().parentCount > 0) pageUrl(historyUrl, path, branchSegment, page + 1) else null)
    }

    private fun pageUrl(historyUrl: String, path: String?, branchSegment: String?, page: Int): String =
        (if (path != null && branchSegment != null) historyUrl + UrlPaths.encodeEach(path) else historyUrl) + "?page=$page"

    private fun breadcrumbs(base: String, branch: String?, path: String?): List<Crumb> {
        if (path == null) return emptyList()
        val root = "$base/commits/${UrlPaths.routeSegment(branch.orEmpty())}/"
        var partial = ""
        return path.split("/").map { part ->
            partial = if (partial.isEmpty()) part else "$partial/$part"
            Crumb(if (root.endsWith("/")) root + partial else "$root/$partial", part)
        }
    }

    private fun row(project: Project, commit: CommitInfo, branch: String?, path: String?, base: String): HistoryRow {
        val showUrl = if (branch != null) {
            var url = "$base/commit/${UrlPaths.routeSegment(commit.id)}?branch=${URLEncoder.encode(branch, Charsets.UTF_8)}"
            if (path != null) url += "&path=$path#" + path.replace("/", "-").replace(".", "-")
            url
        } else "$base/commit/${UrlPaths.routeSegment(commit.id)}"
        val subversion = project.vcs == RepositoryService.VCS_SUBVERSION
        // Subversion은 작성자의 로그인 ID로 사용자를 찾고 이메일이 없다 (v1.6 `SvnCommit.getAuthor`, `getAuthorEmail`).
        val user = if (subversion) commit.authorName.takeIf { it.isNotEmpty() }?.let { users.findByLoginId(it) } else userLookup.findByEmail(commit.authorEmail)
        // v1.6 commitMsg.scala.html: 자동 링크를 건 전체 메시지를 줄로 나눈다(Java split은 뒤쪽 빈 줄을 버린다). 두 줄 이상이면 첫 줄과 그 줄바꿈을 모두 지운 나머지를 숨은 본문으로 보인다.
        val rendered = autoLinks.render(HtmlUtils.htmlEscape(commit.message), project, null)
        val lines = rendered.split("\n").dropLastWhile { it.isEmpty() }
        val hasMore = lines.size > 1
        return HistoryRow(
            id = commit.id, shortId = commit.shortId, showUrl = showUrl, shortMessageHtml = HtmlUtils.htmlEscape(commit.shortMessage),
            moreHtml = if (hasMore) rendered.replace(lines[0] + "\n", "") else "", hasMore = hasMore,
            browseUrl = path?.let { "$base/code/${UrlPaths.routeSegment(commit.shortId)}/${UrlPaths.encodeEach(it)}" },
            commentCount = if (subversion) commentCount(project, commit.id, path) else reviewThreads.count(commit.id, path), date = helper.agoOrDateString(commit.authorDate),
            authorHref = user?.let { "/${it.loginId}" }, authorLoginId = user?.loginId, authorName = user?.name ?: commit.authorName.takeIf { it.isNotEmpty() },
            avatarUrl = if (user != null) avatars.avatarUrl(user, AVATAR_SIZE) else if (subversion) null else avatars.gravatarUrl(commit.authorEmail, AVATAR_SIZE),
            authorEmail = if (subversion) null else commit.authorEmail
        )
    }

    /** Subversion 커밋의 코멘트 수이다. 경로를 정하면 그 경로의 것만 센다 (v1.6 `CommitComment.count`). */
    private fun commentCount(project: Project, commitId: String, path: String?): Int {
        val projectId = requireNotNull(project.id)
        return if (path != null) commitComments.countByProjectIdAndCommitIdAndPath(projectId, commitId, path) else commitComments.countByProjectIdAndCommitId(projectId, commitId)
    }

    private companion object {
        const val PAGE_SIZE = 25
        const val AVATAR_SIZE = 32
    }
}
