package dev.yona.web.code

import dev.yona.web.AjaxRequests
import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectService
import dev.yona.repository.BranchInfo
import dev.yona.repository.BranchService
import dev.yona.repository.RepositoryService
import dev.yona.security.CurrentUserProvider
import dev.yona.util.SiteUrls
import dev.yona.util.UrlPaths
import dev.yona.web.TemplateHelper
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.CodeAccessCheck
import dev.yona.web.access.IsAllowed
import dev.yona.web.access.IsOnlyGitAvailable
import dev.yona.web.error.ErrorViews
import dev.yona.web.project.ProjectViewFactory
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.view.RedirectView
import java.util.Date

/** 브랜치 목록 한 줄의 화면 값이다. */
data class BranchRow(
    val name: String, val shortName: String, val head: Boolean, val codeUrl: String, val historyUrl: String, val commitId: String, val commitShortId: String,
    val date: String, val dateTitle: String, val pullRequestUrl: String?, val pullRequestLabel: String?, val pullRequestState: String?,
    val setDefaultUrl: String?, val deleteUrl: String?
)

/**
 * 브랜치 목록, 기본 브랜치 지정, 브랜치 삭제이다. v1.6 `BranchApp`(`app/controllers/BranchApp.java`)에 해당한다. Git 프로젝트에서만 동작한다.
 *
 * 목록은 기본 브랜치를 맨 위에 두고 나머지는 머리 커밋이 최근인 순서이다. 기본 브랜치 지정은 수정 권한, 삭제는 삭제 권한이 필요하다.
 * 브랜치 이름은 주소에서 두 번 디코딩한다([UrlPaths.decodeSegmentOrSelf]).
 */
@Controller
// v1.6 BranchApp: @IsOnlyGitAvailable이 @AnonymousCheck보다 먼저 선언되어 프로젝트 검사(Git 여부)가 익명 검사보다 바깥이다
@IsOnlyGitAvailable
@AnonymousCheck
class BranchController(
    private val projectService: ProjectService,
    private val branchService: BranchService,
    private val repositories: RepositoryService,
    private val views: ProjectViewFactory,
    private val accessControl: AccessControl,
    private val currentUserProvider: CurrentUserProvider,
    private val helper: TemplateHelper,
    private val siteUrls: SiteUrls
) {

    @GetMapping("/{user}/{project}/branches")
    @CodeAccessCheck
    @Transactional(readOnly = true)
    fun branches(@PathVariable("user") owner: String, @PathVariable("project") name: String): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val head = branchService.headBranch(project) ?: return ErrorViews.notFound("error.notfound", views.of(project), "code")
        val others = branchService.branches(project).filter { it.name != head.name }
        val viewer = currentUserProvider.get().user
        // v1.6 branches.scala.html은 project.asResource()(전역 리소스)로 판정한다. 요청 처리(@IsAllowed)와 같은 기준이다.
        val resource = project.asResource()
        val canUpdate = accessControl.isAllowed(viewer, resource, Operation.UPDATE)
        val canDelete = accessControl.isAllowed(viewer, resource, Operation.DELETE)
        val shortDefault = head.name.let { if (it.lastIndexOf('/') > 0) it.substring(it.lastIndexOf('/') + 1) else it }
        val base = siteUrls.path("/$owner/$name")
        return ModelAndView("code/branches")
            .addObject("title", "title.branches")
            .addObject("projectView", views.of(project))
            .addObject("filesTabUrl", "$base/code/${UrlPaths.encode(shortDefault)}")
            .addObject("commitsTabUrl", "$base/commits/${UrlPaths.encode(shortDefault)}/")
            .addObject("showActions", canDelete || canUpdate)
            .addObject("rows", (listOf(head to true) + others.map { it to false }).map { (b, isHead) -> row(project, b, isHead, head.name, canUpdate, canDelete) })
    }

    private fun row(project: Project, b: BranchInfo, isHead: Boolean, defaultName: String, canUpdate: Boolean, canDelete: Boolean): BranchRow {
        val base = siteUrls.path("/${project.owner}/${project.name}")
        val segment = UrlPaths.routeSegment(b.name)
        val pr = b.pullRequest
        return BranchRow(
            name = b.name, shortName = b.shortName, head = isHead, codeUrl = "$base/code/$segment", historyUrl = "$base/commits/$segment/", commitId = b.headCommitId,
            commitShortId = b.headCommitShortId, date = helper.agoOrDateString(b.committerDate), dateTitle = helper.dateString(b.committerDate),
            pullRequestUrl = pr?.let { siteUrls.path("/${it.toProject?.owner}/${it.toProject?.name}/pullRequest/${it.number}") },
            pullRequestLabel = pr?.let { "pullRequest-${it.number}" }, pullRequestState = pr?.state?.name?.lowercase(),
            setDefaultUrl = if (canUpdate && b.name != defaultName) "$base/code/$segment/setAsDefault" else null,
            deleteUrl = if (canDelete && !isHead) "$base/code/$segment/" else null
        )
    }

    /** 브랜치를 지우고 목록으로 돌아간다. */
    @DeleteMapping("/{user}/{project}/code/{branch}/")
    @IsAllowed(Operation.DELETE)
    @Transactional
    fun deleteBranch(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable branch: String): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        branchService.delete(project, UrlPaths.decodeSegmentOrSelf(branch))
        return RedirectView(siteUrls.path("/$owner/$name/branches"), false, true, false).also { it.setStatusCode(org.springframework.http.HttpStatus.SEE_OTHER) }
    }

    /** 기본 브랜치를 바꾼다. 비동기 요청이면 `200`, 아니면 브랜치 목록으로 돌아간다. */
    @PostMapping("/{user}/{project}/code/{branch}/setAsDefault")
    @IsAllowed(Operation.UPDATE)
    @Transactional
    fun setAsDefault(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable branch: String, request: HttpServletRequest): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        repositories.setDefaultBranch(project, UrlPaths.decodeSegmentOrSelf(branch))
        if (AjaxRequests.isXhr(request)) return ResponseEntity.ok().build<Void>()
        return RedirectView(siteUrls.path("/$owner/$name/branches"), false, true, false).also { it.setStatusCode(org.springframework.http.HttpStatus.SEE_OTHER) }
    }
}
