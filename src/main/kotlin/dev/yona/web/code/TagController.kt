package dev.yona.web.code

import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectService
import dev.yona.repository.TagInfo
import dev.yona.repository.TagService
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
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.view.RedirectView

/**
 * 태그 목록 한 줄의 화면 값이다.
 *
 * @property codeUrl 태그 시점의 코드 화면 주소
 * @property commitUrl 태그가 가리키는 커밋 화면 주소. 커밋이 아니면 `null`
 * @property deleteUrl 삭제 요청 주소. 삭제 권한이 없으면 `null`
 */
data class TagRow(
    val shortName: String, val annotated: Boolean, val message: String?, val shortMessage: String?, val tagger: String?, val codeUrl: String,
    val commitUrl: String?, val commitShortId: String?, val commitId: String?, val date: String?, val dateTitle: String?, val deleteUrl: String?
)

/**
 * 태그 목록과 삭제이다. v1.6에 없는 추가 화면이다(로드맵 X-18, `decisions.md` D-30). Git 프로젝트에서만 동작한다.
 *
 * 목록은 태그를 단 시각이 최근인 순서이다(주석 태그는 태그 시각, 경량 태그는 커밋 시각). 삭제는 브랜치 삭제와 같은 삭제 권한이 필요하다.
 * 태그 이름은 주소에서 두 번 디코딩하며([UrlPaths.decodeSegmentOrSelf]) 슬래시가 들어 있을 수 있다.
 */
@Controller
@IsOnlyGitAvailable
@AnonymousCheck
class TagController(
    private val projectService: ProjectService,
    private val tagService: TagService,
    private val views: ProjectViewFactory,
    private val accessControl: AccessControl,
    private val currentUserProvider: CurrentUserProvider,
    private val helper: TemplateHelper,
    private val siteUrls: SiteUrls
) {

    @GetMapping("/{user}/{project}/tags")
    @CodeAccessCheck
    @Transactional(readOnly = true)
    fun tags(@PathVariable("user") owner: String, @PathVariable("project") name: String): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val canDelete = accessControl.isAllowed(currentUserProvider.get().user, project.asResource(), Operation.DELETE)
        val base = siteUrls.path(UrlPaths.projectPath(owner, name))
        return ModelAndView("code/tags")
            .addObject("title", "title.tags")
            .addObject("projectView", views.of(project))
            .addObject("filesTabUrl", "$base/code")
            .addObject("commitsTabUrl", "$base/commits")
            .addObject("showActions", canDelete)
            .addObject("rows", tagService.tags(project).map { row(project, it, canDelete) })
    }

    private fun row(project: Project, t: TagInfo, canDelete: Boolean): TagRow {
        val base = siteUrls.path(UrlPaths.projectPath(project.owner, project.name))
        return TagRow(
            shortName = t.shortName, annotated = t.annotated, message = t.message?.trim(), shortMessage = t.shortMessage,
            tagger = t.taggerName?.let { n -> t.taggerDate?.let { "$n, ${helper.agoOrDateString(it)}" } ?: n },
            codeUrl = "$base/code/${UrlPaths.routeSegment(t.name)}", commitUrl = t.commitId?.let { "$base/commit/$it" }, commitShortId = t.commitShortId, commitId = t.commitId,
            date = t.commitDate?.let { helper.agoOrDateString(it) }, dateTitle = t.commitDate?.let { helper.dateString(it) },
            deleteUrl = if (canDelete) "$base/tags/${UrlPaths.routeSegment(t.shortName)}" else null
        )
    }

    /** 태그를 지우고 목록으로 돌아간다. 없는 태그나 태그가 아닌 이름은 404이다. */
    @DeleteMapping("/{user}/{project}/tags/{tag}")
    @IsAllowed(Operation.DELETE)
    @Transactional
    fun deleteTag(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable tag: String): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        if (!tagService.delete(project, UrlPaths.decodeSegmentOrSelf(tag))) return ErrorViews.notFound()
        return RedirectView(siteUrls.path("${UrlPaths.projectPath(owner, name)}/tags"), false, true, false).also { it.setStatusCode(org.springframework.http.HttpStatus.SEE_OTHER) }
    }
}
