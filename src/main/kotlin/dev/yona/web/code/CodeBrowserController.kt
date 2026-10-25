package dev.yona.web.code

import dev.yona.access.AccessControl
import dev.yona.access.ProjectResource
import dev.yona.config.YonaProperties
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectService
import dev.yona.io.MediaTypes
import dev.yona.repository.CodeBrowserService
import dev.yona.repository.RepositoryService
import dev.yona.security.CurrentUserProvider
import dev.yona.util.UrlPaths
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.CodeAccessCheck
import dev.yona.web.access.IsAllowed
import dev.yona.web.error.ErrorViews
import dev.yona.web.project.ProjectViewFactory
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.apache.tika.Tika
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.servlet.ModelAndView

/**
 * 코드 브라우저(폴더와 파일 보기, 메타데이터 JSON, 원본, 이미지, 내려받기)이다. v1.6 `CodeApp`(`app/controllers/CodeApp.java`)에 해당한다.
 *
 * Git과 Subversion을 지원하며 그 밖의 VCS는 v1.6의 지원하지 않는 VCS 응답(`501`)이다. Subversion은 브랜치가 `HEAD` 하나이고 내려받기는 빈 응답이다.
 * 코드를 구성원에게만 여는 프로젝트는 구성원이 아니면 `403`이다. 브랜치와 경로는 v1.6처럼 두 번 디코딩한다.
 * v1.6은 폴더 메타데이터를 프로젝트의 마지막 수정 시각을 키로 캐시했다. v2는 캐시하지 않는다([decisions.md] A-67).
 */
@Controller
@AnonymousCheck
class CodeBrowserController(
    private val projectService: ProjectService,
    private val service: CodeBrowserService,
    private val assembler: CodePageAssembler,
    private val views: ProjectViewFactory,
    private val repositories: RepositoryService,
    private val accessControl: AccessControl,
    private val currentUserProvider: CurrentUserProvider,
    private val properties: YonaProperties,
    private val noheadViews: NoheadViews
) {

    /** 기본 브랜치의 코드 화면으로 보낸다. 저장소가 비었으면 처음 올리는 방법을 안내한다. */
    @GetMapping("/{user}/{project}/code")
    @IsAllowed(Operation.READ)
    @Transactional(readOnly = true)
    fun codeBrowser(@PathVariable("user") owner: String, @PathVariable("project") name: String, request: HttpServletRequest): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        notSupported(project)?.let { return it }
        val viewer = currentUserProvider.get().user
        if (project.isCodeAccessibleMemberOnly == true && !projectService.hasMember(project, viewer)) {
            return ErrorViews.forbidden("error.forbidden", views.of(project))
        }
        if (service.isEmpty(project)) {
            return noheadViews.of(project, request)
        }
        // 기본 브랜치 이름에서 `refs/heads/` 접두어를 뗀다. 알 수 없으면 HEAD이다.
        val defaultBranch = repositories.defaultBranch(project).let { if (it.split("/").size >= 3) it.split("/", limit = 3)[2] else it }
        val segment = UrlPaths.routeSegment(defaultBranch)
        return ModelAndView("redirect:/$owner/$name/code/$segment/")
    }

    /** 브랜치의 폴더나 파일을 화면으로 보여 준다. */
    @GetMapping("/{user}/{project}/code/{branch}")
    @CodeAccessCheck
    @Transactional(readOnly = true)
    fun codeBrowserWithBranchOnly(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable branch: String): Any =
        browse(owner, name, branch, "")

    @GetMapping("/{user}/{project}/code/{branch}/{*path}")
    @CodeAccessCheck
    @Transactional(readOnly = true)
    fun codeBrowserWithBranch(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable branch: String, @PathVariable path: String): Any =
        browse(owner, name, branch, path.removePrefix("/"))

    private fun browse(owner: String, name: String, rawBranch: String, rawPath: String): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        notSupported(project)?.let { return it }
        val branch = UrlPaths.decodeSegmentOrSelf(rawBranch)
        val path = UrlPaths.decodeSegmentOrSelf(rawPath)
        val chain = service.ancestors(project, branch, path) ?: return ErrorViews.notFound(branch, views.of(project), "code")
        val viewer = currentUserProvider.get().user
        val page = assembler.assemble(project, chain, branch, path, viewer != null, accessControl.isSiteManager(viewer), properties.codeBrowserMaxFileSize)
        return ModelAndView("code/view")
            .addObject("title", "menu.code")
            .addObject("projectView", views.of(project))
            .addObject("page", page)
            .addObject("maxFileSize", properties.codeBrowserMaxFileSize)
    }

    /** 기본 브랜치(`HEAD`)의 폴더나 파일 정보를 JSON으로 돌려준다. */
    @GetMapping("/{user}/{project}/code/!", "/{user}/{project}/code/!/{*path}")
    @CodeAccessCheck
    @ResponseBody
    @Transactional(readOnly = true)
    fun ajaxRequest(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable(required = false) path: String?): Any =
        metaJson(owner, name, null, path?.removePrefix("/").orEmpty())

    /** [branch]의 폴더나 파일 정보를 JSON으로 돌려준다. */
    @GetMapping("/{user}/{project}/code/{branch}/!", "/{user}/{project}/code/{branch}/!/{*path}")
    @CodeAccessCheck
    @ResponseBody
    @Transactional(readOnly = true)
    fun ajaxRequestWithBranch(
        @PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable branch: String, @PathVariable(required = false) path: String?
    ): Any = metaJson(owner, name, UrlPaths.decodeSegmentOrSelf(branch), path?.removePrefix("/").orEmpty())

    private fun metaJson(owner: String, name: String, branch: String?, rawPath: String): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ResponseEntity.notFound().build<Void>()
        val info = service.metaData(project, branch, UrlPaths.decodeSegmentOrSelf(rawPath)) ?: return ResponseEntity.notFound().build<Void>()
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(info)
    }

    /** 브랜치를 zip으로 내려받는다. */
    @GetMapping("/{user}/{project}/code/{branch}/download")
    @CodeAccessCheck
    @Transactional(readOnly = true)
    fun download(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable branch: String, response: HttpServletResponse): Any? {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        notSupported(project)?.let { return it }
        val target = UrlPaths.decodeSegmentOrSelf(branch)
        if (service.ancestors(project, target, "") == null) return ErrorViews.notFound()
        response.contentType = "application/octet-stream"
        response.setHeader("Content-Disposition", "attachment; filename=${project.name}-$branch.zip")
        service.archive(project, target, response.outputStream)
        return null
    }

    /** 원본 파일을 `text/plain`으로 보여 준다. 문자 집합은 내용으로 추정한다. 파일이 없으면 `404`이다([decisions.md] A-68). */
    @GetMapping("/{user}/{project}/rawcode/{rev}/{*path}")
    @CodeAccessCheck
    @Transactional(readOnly = true)
    fun showRawFile(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable rev: String, @PathVariable path: String): Any {
        val (bytes, fileName) = rawFile(owner, name, rev, path) ?: return ErrorViews.notFound()
        val charset = MediaTypes.charset(MediaTypes.detect(bytes, fileName))
        val type = "text/plain" + (charset?.let { "; charset=$it" } ?: "")
        return ResponseEntity.ok().header("Content-Type", type).body(bytes)
    }

    /** 이미지 파일을 내용으로 감지한 종류로 보여 준다. */
    @GetMapping("/{user}/{project}/image/{rev}/{*path}")
    @CodeAccessCheck
    @Transactional(readOnly = true)
    fun showImageFile(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable rev: String, @PathVariable path: String): Any {
        val (bytes, _) = rawFile(owner, name, rev, path) ?: return ErrorViews.notFound()
        return ResponseEntity.ok().header("Content-Type", Tika().detect(bytes)).body(bytes)
    }

    /** 파일을 이름과 내용으로 감지한 종류 그대로 브라우저에서 연다. */
    @GetMapping("/{user}/{project}/files/{rev}/{*path}")
    @IsAllowed(Operation.READ)
    @Transactional(readOnly = true)
    fun openFile(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable rev: String, @PathVariable path: String): Any {
        val (bytes, fileName) = rawFile(owner, name, rev, path) ?: return ErrorViews.notFound()
        return ResponseEntity.ok().header("Content-Type", MediaTypes.detect(bytes, fileName)).body(bytes)
    }

    private fun rawFile(owner: String, name: String, rev: String, rawPath: String): Pair<ByteArray, String>? {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return null
        val path = UrlPaths.decodeSegmentOrSelf(rawPath.removePrefix("/"))
        val bytes = service.rawFile(project, UrlPaths.decodeSegmentOrSelf(rev), path) ?: return null
        return bytes to path.substringAfterLast('/')
    }

    /** Git도 Subversion도 아닌 프로젝트이면 v1.6의 지원하지 않는 VCS 응답(`501`)이다. */
    private fun notSupported(project: Project): ResponseEntity<String>? =
        if (project.vcs == "GIT" || project.vcs == "Subversion") null else ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).contentType(MediaType("text", "plain", Charsets.UTF_8)).body("${project.vcs} is not supported!")
}
