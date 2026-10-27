package dev.yona.web.issue

import dev.yona.web.AjaxRequests
import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.State
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.IssueService
import dev.yona.domain.issue.RecentIssueService
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectService
import dev.yona.domain.project.RecentVisitService
import dev.yona.security.CurrentUserProvider
import dev.yona.web.HttpPreference
import dev.yona.web.RedirectSupport
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.IsAllowed
import dev.yona.web.access.NullProjectCheck
import dev.yona.web.error.ErrorViews
import dev.yona.web.project.ProjectViewFactory
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.servlet.ModelAndView

/**
 * 이슈 보기, 타임라인 조각, 다음 상태, 삭제이다. v1.6 `IssueApp.issue`, `timeline`, `nextState`, `deleteIssue`
 * (`app/controllers/IssueApp.java:248-309, 680-700, 938-958`)에 해당한다.
 */
@Controller
@AnonymousCheck
class IssueViewController(
    private val projectService: ProjectService,
    private val issues: IssueRepository,
    private val issueService: IssueService,
    private val assembler: IssueViewAssembler,
    private val views: ProjectViewFactory,
    private val accessControl: AccessControl,
    private val currentUserProvider: CurrentUserProvider,
    private val recentVisits: RecentVisitService,
    private val recentIssues: RecentIssueService,
    private val messages: MessageSource,
    private val siteUrls: dev.yona.util.SiteUrls
) {

    @GetMapping("/{user}/{project}/issue/{number:[0-9]+}")
    @NullProjectCheck
    @Transactional
    fun issue(
        @PathVariable("user") owner: String,
        @PathVariable("project") name: String,
        @PathVariable("number") number: Long,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val viewer = currentUserProvider.get().user
        val json = HttpPreference.isJsonPreferred(request.getHeader(HttpHeaders.ACCEPT))
        response.setHeader(HttpHeaders.VARY, HttpHeaders.ACCEPT)

        val issue = issues.findByProjectIdAndNumber(requireNotNull(project.id), number)
        if (issue == null) {
            if (json) {
                return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .body(linkedMapOf("title" to number, "body" to message("error.notfound.issue_post")))
            }
            return ErrorViews.notFound("error.notfound", views.of(project), ResourceType.ISSUE_POST.resource)
        }
        if (issue.isDraft == true && issue.authorLoginId != viewer?.loginId) {
            return ErrorViews.notFound("error.notfound").also { it.status = HttpStatus.FORBIDDEN }
        }
        if (!accessControl.isAllowed(viewer, issue.asResource(), Operation.READ)) {
            return ErrorViews.forbidden("error.forbidden", views.of(project))
        }

        recentVisits.visits(viewer, project)
        recentIssues.visits(viewer, issue)
        recentVisits.markIssueOrPostingVisited(viewer, requireNotNull(issue.id))
        if (json) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(
                linkedMapOf(
                    "id" to issue.number, "title" to issue.title, "state" to issue.state?.name, "body" to abbreviate(issue.body, SUMMARY_LENGTH),
                    "createdDate" to dev.yona.util.JavaDates.toString(issue.createdDate), "link" to "${dev.yona.util.UrlPaths.projectPath(project.owner, project.name)}/issue/${issue.number}"
                )
            )
        }
        val detail = assembler.assemble(project, issue, viewer)
        val listUrl = listUrl(request.getHeader(HttpHeaders.REFERER), "${dev.yona.util.UrlPaths.projectPath(project.owner, project.name)}/issues", issue.state?.state)
        val body = issue.body.orEmpty()
        return ModelAndView("issue/view")
            .addObject("title", (issue.title.orEmpty()) + " |:| " + body.take(OG_BODY_LENGTH))
            .addObject("projectView", views.of(project))
            .addObject("issue", detail)
            .addObject("listUrl", listUrl)
            .addObject("issueResourceId", issue.id)
            .addObject("nextState", detail.nextStateName)
    }

    @GetMapping("/{user}/{project}/issue/{number:[0-9]+}/timeline")
    @IsAllowed(Operation.READ, ResourceType.ISSUE_POST)
    @Transactional(readOnly = true)
    fun timeline(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable("number") number: Long): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val issue = issues.findByProjectIdAndNumber(requireNotNull(project.id), number) ?: return ErrorViews.notFound()
        val viewer = currentUserProvider.get().user
        return ModelAndView("issue/partial-comments :: timelineList")
            .addObject("projectView", views.of(project))
            .addObject("issue", assembler.assemble(project, issue, viewer))
    }

    /** 이슈를 다음 상태로 바꾼다(열림 ↔ 닫힘). 보기 화면으로 돌려보낸다. */
    @GetMapping("/{user}/{project}/issue/{number:[0-9]+}/nextstate")
    @IsAllowed(Operation.UPDATE, ResourceType.ISSUE_POST)
    @Transactional
    fun nextState(
        @PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable("number") number: Long,
        request: HttpServletRequest, response: HttpServletResponse
    ): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val issue = issues.findByProjectIdAndNumber(requireNotNull(project.id), number) ?: return ErrorViews.notFound()
        val actor = currentUserProvider.get().user ?: return ErrorViews.forbidden()
        issueService.toNextState(issue, actor)
        return RedirectSupport.see(request, response, "${dev.yona.util.UrlPaths.projectPath(project.owner, project.name)}/issue/$number")
    }

    /**
     * 이슈를 지운다. 이슈 작성자가 아닌 사람의 댓글이 있으면 `400`이다. Ajax 요청에는 `204`와 목록 주소의 `Location`, 아니면 목록으로 `303`이다.
     */
    @DeleteMapping("/{user}/{project}/issue/{number:[0-9]+}/delete")
    @NullProjectCheck
    @Transactional
    fun deleteIssue(
        @PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable("number") number: Long,
        request: HttpServletRequest, response: HttpServletResponse
    ): Any {
        val project: Project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val issue: Issue = issues.findByProjectIdAndNumber(requireNotNull(project.id), number) ?: return ErrorViews.notFound()
        val viewer = currentUserProvider.get().user
        if (!issueService.canBeDeleted(issue)) return ErrorViews.badRequest()
        if (viewer == null || !accessControl.isAllowed(viewer, issue.asResource(), Operation.DELETE)) return ErrorViews.forbidden("error.forbidden", views.of(project))
        issueService.delete(issue, viewer)
        val list = "${dev.yona.util.UrlPaths.projectPath(project.owner, project.name)}/issues?state=${State.OPEN.state}"
        if (AjaxRequests.isXhr(request)) {
            return ResponseEntity.status(HttpStatus.NO_CONTENT).header(HttpHeaders.LOCATION, list).build<Void>()
        }
        return RedirectSupport.see(request, response, list)
    }

    private fun abbreviate(text: String?, max: Int): String? =
        if (text == null || text.length <= max) text else text.substring(0, max - 3) + "..."

    private fun message(key: String): String = messages.getMessage(key, null, key, LocaleContextHolder.getLocale()) ?: key

    /**
     * `L` 단축키가 가는 목록 주소이다 (v1.6 `view.scala.html`의 `urlToIssues`, `TemplateHelper.urlToList`).
     * Referer가 없으면 이슈의 상태와 관계없이 `state=open`이다. 있으면 호스트, 포트, 경로가 기본 주소(`state=이슈 상태`)와 모두 같을 때만
     * Referer를 쓰고, 그렇지 않으면 기본 주소를 쓴다. 상대 주소는 설정한 사이트 주소로 채워 비교한다.
     */
    private fun listUrl(referer: String?, listPath: String, state: String?): String {
        if (referer == null) return "$listPath?state=open"
        val default = "$listPath?state=$state"
        return siteUrls.urlToList(referer, default)
    }

    private companion object {
        /** JSON 응답의 본문 요약 길이이다 (v1.6 `StringUtils.abbreviate(body, 200)`). */
        const val SUMMARY_LENGTH = 200

        /** 문서 제목 뒤에 붙여 소셜 미리보기 설명으로 쓰는 본문의 길이이다. */
        const val OG_BODY_LENGTH = 200
    }
}
