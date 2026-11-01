package dev.yona.web.wiki

import dev.yona.access.AccessControl
import dev.yona.access.ProjectResource
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectService
import dev.yona.domain.wiki.WikiConflictException
import dev.yona.domain.wiki.WikiException
import dev.yona.domain.wiki.WikiLinks
import dev.yona.domain.wiki.WikiPageExistsException
import dev.yona.domain.wiki.WikiPageNotFoundException
import dev.yona.domain.wiki.WikiService
import dev.yona.domain.wiki.WikiTitleInvalidException
import dev.yona.domain.wiki.WikiUnavailableException
import dev.yona.markdown.ProjectMarkdownRenderer
import dev.yona.repository.CommitInfo
import dev.yona.security.CurrentUserProvider
import dev.yona.util.SiteUrls
import dev.yona.util.UrlPaths
import dev.yona.web.RedirectSupport
import dev.yona.web.TemplateHelper
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.IsAllowed
import dev.yona.web.error.ErrorViews
import dev.yona.web.project.ProjectViewFactory
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.ModelAndView

/** 사이드바 `Pages` 목록의 한 줄이다. */
data class WikiPageLink(val name: String, val title: String, val url: String, val active: Boolean)

/** 이력 화면의 리비전 한 줄이다. [compareUrl]은 그 커밋 하나의 변경을 보는 주소이다. */
data class WikiRevisionRow(val id: String, val shortId: String, val message: String, val author: String, val date: String, val dateTitle: String, val compareUrl: String)

/** 비교 화면의 diff 한 줄이다. [kind]는 `add`, `del`, `hunk`, `ctx` 중 하나이다. */
data class WikiDiffLine(val kind: String, val text: String)

/**
 * 프로젝트 위키 화면이다(로드맵 X-17 W4, `decisions.md` D-60). 화면은 GitHub 위키 모양이다: 오른쪽 `Pages` 사이드바와 거르기 상자, `Edit`/`New Page`,
 * 편집 메시지와 `Save Page`, `Revisions`에서 두 리비전을 골라 비교한다. v1.6에 없는 추가 화면이다.
 *
 * 읽기는 프로젝트를 읽을 수 있으면 되고 쓰기는 구성원이다(`ResourceType.WIKI_PAGE`의 `UPDATE`). 페이지 이름은 제목의 공백이 `-`인 값이며 주소의 한 구간이다.
 * 위키 첫 화면은 `Home` 페이지이고 없으면 안내이다.
 */
@Controller
@AnonymousCheck
class WikiViewController(
    private val projectService: ProjectService,
    private val wikiService: WikiService,
    private val views: ProjectViewFactory,
    private val accessControl: AccessControl,
    private val currentUserProvider: CurrentUserProvider,
    private val markdown: ProjectMarkdownRenderer,
    private val helper: TemplateHelper,
    private val siteUrls: SiteUrls,
    private val repositoryUrls: dev.yona.web.project.RepositoryUrls
) {

    // --- 읽기 ---

    @GetMapping("/{user}/{project}/wiki")
    @IsAllowed(Operation.READ)
    @Transactional(readOnly = true)
    fun home(@PathVariable("user") owner: String, @PathVariable("project") name: String): Any {
        val project = wikiProject(owner, name) ?: return ErrorViews.notFound()
        val page = wikiService.page(project, HOME)
        return if (page != null) pageView(project, page) else shell(project, "wiki/empty", "Wiki", null).addObject("available", wikiService.available(project))
    }

    @GetMapping("/{user}/{project}/wiki/{page}")
    @IsAllowed(Operation.READ)
    @Transactional(readOnly = true)
    fun view(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable page: String): Any {
        val project = wikiProject(owner, name) ?: return ErrorViews.notFound()
        val found = wikiService.page(project, page) ?: return ErrorViews.notFound("wiki.page.notFound", views.of(project), null)
        return pageView(project, found)
    }

    @GetMapping("/{user}/{project}/wiki/{page}/_history")
    @IsAllowed(Operation.READ)
    @Transactional(readOnly = true)
    fun history(
        @PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable page: String,
        @RequestParam(defaultValue = "0") number: Int
    ): Any {
        val project = wikiProject(owner, name) ?: return ErrorViews.notFound()
        val pageName = wikiService.pageNameOrNull(page) ?: return ErrorViews.notFound("wiki.page.notFound", views.of(project), null)
        val current = number.coerceAtLeast(0)
        val commits = wikiService.history(project, pageName, current, HISTORY_SIZE + 1)
        val base = wikiBase(project)
        return shell(project, "wiki/history", "${WikiService.titleOf(pageName)} - Revisions", pageName)
            .addObject("pageTitle", WikiService.titleOf(pageName))
            .addObject("pageUrl", pageUrl(base, pageName))
            .addObject("compareAction", "${pageUrl(base, pageName)}/_compare")
            .addObject("revisions", commits.take(HISTORY_SIZE).map { row(it, base, pageName) })
            .addObject("olderUrl", if (commits.size > HISTORY_SIZE) "${pageUrl(base, pageName)}/_history?number=${current + 1}" else null)
            .addObject("newerUrl", if (current > 0) "${pageUrl(base, pageName)}/_history?number=${current - 1}" else null)
    }

    @GetMapping("/{user}/{project}/wiki/{page}/_compare")
    @IsAllowed(Operation.READ)
    @Transactional(readOnly = true)
    fun compare(
        @PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable page: String,
        @RequestParam(required = false) commit: String?, @RequestParam(required = false) from: String?, @RequestParam(required = false) to: String?
    ): Any {
        val project = wikiProject(owner, name) ?: return ErrorViews.notFound()
        val pageName = wikiService.pageNameOrNull(page) ?: return ErrorViews.notFound("wiki.page.notFound", views.of(project), null)
        val text = try {
            when {
                !commit.isNullOrBlank() -> wikiService.diff(project, pageName, commit)
                !from.isNullOrBlank() && !to.isNullOrBlank() -> wikiService.compare(project, pageName, from, to)
                else -> return ErrorViews.badRequest("wiki.compare.required", views.of(project), "WIKI")
            }
        } catch (e: WikiPageNotFoundException) {
            return ErrorViews.notFound("wiki.revision.notFound", views.of(project), null)
        }
        val base = wikiBase(project)
        return shell(project, "wiki/compare", "${WikiService.titleOf(pageName)} - Compare", pageName)
            .addObject("pageTitle", WikiService.titleOf(pageName))
            .addObject("pageUrl", pageUrl(base, pageName))
            .addObject("historyUrl", "${pageUrl(base, pageName)}/_history")
            .addObject("lines", diffLines(text))
    }

    // --- 쓰기 ---

    @GetMapping("/{user}/{project}/wiki/_new")
    @IsAllowed(Operation.READ)
    @Transactional(readOnly = true)
    fun newForm(@PathVariable("user") owner: String, @PathVariable("project") name: String, @RequestParam(required = false) title: String?): Any {
        val project = wikiProject(owner, name) ?: return ErrorViews.notFound()
        deny(project)?.let { return it }
        return form(project, null, title.orEmpty(), "", "", null, HttpStatus.OK)
    }

    @PostMapping("/{user}/{project}/wiki")
    @IsAllowed(Operation.READ)
    @Transactional
    fun create(
        @PathVariable("user") owner: String, @PathVariable("project") name: String,
        @RequestParam(defaultValue = "") title: String, @RequestParam(defaultValue = "") content: String, @RequestParam(defaultValue = "") message: String,
        request: HttpServletRequest, response: HttpServletResponse
    ): Any {
        val project = wikiProject(owner, name) ?: return ErrorViews.notFound()
        deny(project)?.let { return it }
        return try {
            val created = wikiService.create(project, requireNotNull(currentUserProvider.get().user), title, content, message)
            RedirectSupport.see(request, response, pageUrl(wikiBase(project), created.name))
        } catch (e: WikiException) {
            failure(project, null, title, content, message, e)
        }
    }

    @GetMapping("/{user}/{project}/wiki/{page}/_edit")
    @IsAllowed(Operation.READ)
    @Transactional(readOnly = true)
    fun editForm(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable page: String): Any {
        val project = wikiProject(owner, name) ?: return ErrorViews.notFound()
        deny(project)?.let { return it }
        val found = wikiService.page(project, page) ?: return ErrorViews.notFound("wiki.page.notFound", views.of(project), null)
        return form(project, found.name, found.title, found.content, "", null, HttpStatus.OK)
    }

    @PostMapping("/{user}/{project}/wiki/{page}/_edit")
    @IsAllowed(Operation.READ)
    @Transactional
    fun edit(
        @PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable page: String,
        @RequestParam(defaultValue = "") title: String, @RequestParam(defaultValue = "") content: String, @RequestParam(defaultValue = "") message: String,
        request: HttpServletRequest, response: HttpServletResponse
    ): Any {
        val project = wikiProject(owner, name) ?: return ErrorViews.notFound()
        deny(project)?.let { return it }
        return try {
            val updated = wikiService.update(project, requireNotNull(currentUserProvider.get().user), page, title, content, message)
            RedirectSupport.see(request, response, pageUrl(wikiBase(project), updated.name))
        } catch (e: WikiPageNotFoundException) {
            ErrorViews.notFound("wiki.page.notFound", views.of(project), null)
        } catch (e: WikiException) {
            failure(project, wikiService.pageNameOrNull(page), title, content, message, e)
        }
    }

    @PostMapping("/{user}/{project}/wiki/{page}/_delete")
    @IsAllowed(Operation.READ)
    @Transactional
    fun delete(
        @PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable page: String,
        @RequestParam(defaultValue = "") message: String, request: HttpServletRequest, response: HttpServletResponse
    ): Any {
        val project = wikiProject(owner, name) ?: return ErrorViews.notFound()
        deny(project)?.let { return it }
        return try {
            wikiService.delete(project, requireNotNull(currentUserProvider.get().user), page, message)
            RedirectSupport.see(request, response, wikiBase(project))
        } catch (e: WikiPageNotFoundException) {
            ErrorViews.notFound("wiki.page.notFound", views.of(project), null)
        } catch (e: WikiException) {
            ErrorViews.badRequest("wiki.error.conflict", views.of(project), "WIKI")
        }
    }

    // --- 조립 ---

    /** 프로젝트를 찾고 위키가 켜져 있을 때만 돌려준다. 없거나 꺼져 있으면 `null`이다(404). */
    private fun wikiProject(owner: String, name: String): Project? =
        projectService.findByOwnerAndProjectName(owner, name)?.takeIf { wikiService.enabled(it) }

    private fun wikiBase(project: Project) = siteUrls.path(UrlPaths.projectPath(project.owner, project.name) + "/wiki")

    private fun pageUrl(base: String, name: String) = "$base/${UrlPaths.encode(name)}"

    private fun canWrite(project: Project): Boolean {
        val user = currentUserProvider.get().user ?: return false
        return accessControl.isAllowed(user, ProjectResource(requireNotNull(project.id).toString(), ResourceType.WIKI_PAGE, project), Operation.UPDATE)
    }

    /** 쓸 수 없으면 돌려줄 403 화면이다. 쓸 수 있으면 `null`이다. */
    private fun deny(project: Project): ModelAndView? = if (canWrite(project)) null else ErrorViews.forbidden("error.forbidden", views.of(project))

    /** 모든 위키 화면의 공통 모델이다: 프로젝트 화면 정보, 사이드바 목록, 사이드바와 푸터 페이지. [pageList]는 이미 읽어 둔 페이지 목록이다. */
    private fun shell(project: Project, template: String, title: String, activeName: String?, pageList: List<dev.yona.domain.wiki.WikiPageSummary> = wikiService.pages(project)): ModelAndView {
        val base = wikiBase(project)
        val names = pageList.map { it.name }.toSet()
        return ModelAndView(template)
            .addObject("title", title)
            .addObject("projectView", views.of(project))
            .addObject("base", base)
            .addObject("canWrite", canWrite(project))
            .addObject("activeName", activeName)
            .addObject("cloneUrl", repositoryUrls.wikiUrlFor(project, currentRequest(), currentUserProvider.get().user?.loginId))
            .addObject("pages", pageList.map { WikiPageLink(it.name, it.title, pageUrl(base, it.name), it.name == activeName) })
            .addObject("sidebarHtml", wikiService.sidebar(project)?.let { render(project, it, names) })
            .addObject("footerHtml", wikiService.footer(project)?.let { render(project, it, names) })
    }

    /** 위키 마크다운을 HTML로 만든다: 위키 링크(`[[Page]]`)를 풀고 렌더링한 뒤 상대 링크를 위키 주소로 바꾼다(D-61). */
    private fun render(project: Project, text: String, names: Set<String>): String {
        val base = wikiBase(project)
        return WikiLinks.rewriteHtml(markdown.render(WikiLinks.expand(text, base) { it in names }, project), base)
    }

    private fun pageView(project: Project, page: dev.yona.domain.wiki.WikiPage): ModelAndView {
        val base = wikiBase(project)
        val last = page.lastCommit
        val list = wikiService.pages(project)
        return shell(project, "wiki/view", page.title, page.name, list)
            .addObject("pageTitle", page.title)
            .addObject("pageUrl", pageUrl(base, page.name))
            .addObject("bodyHtml", render(project, page.content, list.map { it.name }.toSet()))
            .addObject("lastAuthor", last?.authorName)
            .addObject("lastDate", last?.let { helper.agoOrDateString(it.authorDate) })
    }

    /** 만들기와 고치기 폼이다. [pageName]이 `null`이면 만들기이다. [error]가 있으면 그 문구 키를 보여 주고 입력은 그대로 둔다. */
    private fun form(project: Project, pageName: String?, title: String, content: String, message: String, error: String?, status: HttpStatus): ModelAndView {
        val base = wikiBase(project)
        val action = if (pageName == null) base else "${pageUrl(base, pageName)}/_edit"
        return shell(project, "wiki/edit", if (pageName == null) "New Page" else "Edit ${WikiService.titleOf(pageName)}", pageName)
            .addObject("editing", pageName != null)
            .addObject("formAction", action)
            .addObject("deleteAction", pageName?.let { "${pageUrl(base, it)}/_delete" })
            .addObject("cancelUrl", pageName?.let { pageUrl(base, it) } ?: base)
            .addObject("titleValue", title)
            .addObject("contentValue", content)
            .addObject("messageValue", message)
            .addObject("errorKey", error)
            .apply { setStatus(status) }
    }

    private fun failure(project: Project, pageName: String?, title: String, content: String, message: String, e: WikiException): ModelAndView {
        val (key, status) = when (e) {
            is WikiTitleInvalidException -> "wiki.error.title" to HttpStatus.BAD_REQUEST
            is WikiPageExistsException -> "wiki.error.exists" to HttpStatus.CONFLICT
            is WikiUnavailableException -> "wiki.error.unavailable" to HttpStatus.CONFLICT
            is WikiConflictException -> "wiki.error.conflict" to HttpStatus.CONFLICT
            is WikiPageNotFoundException -> "wiki.page.notFound" to HttpStatus.NOT_FOUND
        }
        return form(project, pageName, title, content, message, key, status)
    }

    private fun row(commit: CommitInfo, base: String, pageName: String) = WikiRevisionRow(
        id = commit.id, shortId = commit.shortId, message = commit.shortMessage, author = commit.authorName,
        date = helper.agoOrDateString(commit.authorDate), dateTitle = helper.dateString(commit.authorDate),
        compareUrl = "${pageUrl(base, pageName)}/_compare?commit=${commit.id}"
    )

    /** 통합 diff 글에서 파일 머리말(`diff --git`, `index`, `---`, `+++`)을 빼고 줄마다 종류를 붙인다. */
    private fun diffLines(text: String): List<WikiDiffLine> = text.lineSequence()
        .dropWhile { !it.startsWith("@@") }
        .map { line ->
            when {
                line.startsWith("@@") -> WikiDiffLine("hunk", line)
                line.startsWith("+") -> WikiDiffLine("add", line)
                line.startsWith("-") -> WikiDiffLine("del", line)
                else -> WikiDiffLine("ctx", line)
            }
        }.toList().dropLastWhile { it.text.isEmpty() }

    private fun currentRequest(): HttpServletRequest =
        (org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes() as org.springframework.web.context.request.ServletRequestAttributes).request

    private companion object {
        const val HOME = "Home"
        const val HISTORY_SIZE = 30
    }
}
