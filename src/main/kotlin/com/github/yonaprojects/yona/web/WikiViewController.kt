package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.support.MarkdownService
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.wiki.WikiService
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam

// GitHub/Forgejo 스타일 프로젝트 위키 웹 UI(P3-42). 페이지는 DB가 아니라
// `<owner>/<project>.wiki.git` bare 저장소의 마크다운 파일이다(WikiService/WikiServiceImpl 참고).
//
// 라우팅: "{*title}"(Spring PathPattern의 나머지-경로 캡처, 슬래시로 중첩 페이지 표현)은
// 패턴 맨 끝에만 올 수 있어, "_new"/"_edit"/"_delete"/"_history"/"_search"를 title보다 앞에 오는
// 예약 리터럴 세그먼트로 두었다(GitHub/Forgejo 위키도 동일하게 "_new"/"_history" 등을
// 예약어로 쓴다) — WikiServiceImpl.titleToPath()가 이 예약어를 페이지 제목 첫 세그먼트로
// 거부해 라우팅과 페이지 제목이 절대 충돌하지 않는다.
@Controller
class WikiViewController(
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val wikiService: WikiService,
    private val markdownService: MarkdownService,
    private val accessControl: AccessControl
) {

    private fun getLoginUser(authentication: Authentication?): User? {
        return authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
    }

    private fun findProject(owner: String, projectName: String): Project? {
        return projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
    }

    private fun canWrite(user: User?, project: Project): Boolean {
        if (user == null || user.isGuest) return false
        if (user.isSiteManager) return true
        return projectUserRepository.existsByProjectIdAndUserId(project.id!!, user.id!!)
    }

    private fun canRead(user: User?, project: Project): Boolean = accessControl.isAllowedToReadProject(user, project)

    private fun addCommonAttributes(model: Model, project: Project, user: User?, activeTitle: String?) {
        model.addAttribute("project", project)
        model.addAttribute("tabId", "wiki")
        model.addAttribute("canWriteWiki", canWrite(user, project))
        model.addAttribute("pages", wikiService.listPages(project))
        model.addAttribute("activeTitle", activeTitle)

        // 특수 페이지(P3-42 2번): _Sidebar.md가 있으면 페이지 목록 대신/함께 커스텀 사이드바 내용을,
        // _Footer.md가 있으면 커스텀 푸터를 렌더링한다(Forgejo와 동일한 동작).
        val sidebarPage = wikiService.getPage(project, "_Sidebar")
        if (sidebarPage != null) {
            model.addAttribute("customSidebarHtml", markdownService.render(sidebarPage.content, true, project))
        }
        val footerPage = wikiService.getPage(project, "_Footer")
        if (footerPage != null) {
            model.addAttribute("customFooterHtml", markdownService.render(footerPage.content, true, project))
        }
    }

    // 위키 홈. 페이지가 하나도 없으면 "Home 페이지 만들기" 유도(P3-42 2번 요구사항의 두 옵션 중
    // "목록이 비어있을 때 Home 만들기를 유도" 쪽을 택함 — 조회만으로 부작용 있는 자동 생성 커밋을
    // 만들지 않는 편이 더 안전하다). Home이 있으면 곧바로 그 내용을 보여준다.
    @GetMapping("/{owner}/{projectName}/wiki")
    fun home(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = findProject(owner, projectName) ?: return "error/404"
        val user = getLoginUser(authentication)
        if (!canRead(user, project)) {
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        addCommonAttributes(model, project, user, "Home")
        val home = wikiService.getPage(project, "Home")
        if (home == null) {
            model.addAttribute("page", null)
            return "wiki/view"
        }
        model.addAttribute("page", home)
        model.addAttribute("renderedHtml", markdownService.render(home.content, true, project))
        return "wiki/view"
    }

    @GetMapping("/{owner}/{projectName}/wiki/_search")
    fun search(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam(required = false, defaultValue = "") q: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = findProject(owner, projectName) ?: return "error/404"
        val user = getLoginUser(authentication)
        if (!canRead(user, project)) {
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        addCommonAttributes(model, project, user, null)
        model.addAttribute("searchQuery", q)
        model.addAttribute("searchResults", wikiService.search(project, q))
        return "wiki/search"
    }

    @GetMapping("/{owner}/{projectName}/wiki/_new")
    fun newForm(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam(required = false, defaultValue = "") title: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = findProject(owner, projectName) ?: return "error/404"
        val user = getLoginUser(authentication)
        if (!canWrite(user, project)) {
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        addCommonAttributes(model, project, user, null)
        model.addAttribute("isNew", true)
        model.addAttribute("oldTitle", null)
        model.addAttribute("editTitle", title)
        model.addAttribute("editContent", "")
        return "wiki/edit"
    }

    @PostMapping("/{owner}/{projectName}/wiki/_new")
    fun create(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam title: String,
        @RequestParam(required = false, defaultValue = "") content: String,
        @RequestParam(required = false) message: String?,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = findProject(owner, projectName) ?: return "error/404"
        val user = getLoginUser(authentication)
        if (!canWrite(user, project)) {
            model.addAttribute("project", project)
            return "error/forbidden"
        }
        if (title.isBlank()) {
            addCommonAttributes(model, project, user, null)
            model.addAttribute("isNew", true)
            model.addAttribute("editTitle", title)
            model.addAttribute("editContent", content)
            model.addAttribute("errorMessage", "페이지 제목은 비어 있을 수 없습니다.")
            return "wiki/edit"
        }
        if (wikiService.getPage(project, title) != null) {
            addCommonAttributes(model, project, user, null)
            model.addAttribute("isNew", true)
            model.addAttribute("editTitle", title)
            model.addAttribute("editContent", content)
            model.addAttribute("errorMessage", "이미 존재하는 페이지입니다: $title")
            return "wiki/edit"
        }

        wikiService.savePage(project, user!!, null, title, content, message)
        return "redirect:/${owner.encodePathSegment()}/${projectName.encodePathSegment()}/wiki/${encodeTitlePath(title)}"
    }

    @GetMapping("/{owner}/{projectName}/wiki/_edit/{*title}")
    fun editForm(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable title: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = findProject(owner, projectName) ?: return "error/404"
        val user = getLoginUser(authentication)
        if (!canWrite(user, project)) {
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        val decodedTitle = decodeWildcardPath(title)
        val page = wikiService.getPage(project, decodedTitle) ?: return "error/404"

        addCommonAttributes(model, project, user, decodedTitle)
        model.addAttribute("isNew", false)
        model.addAttribute("oldTitle", decodedTitle)
        model.addAttribute("editTitle", decodedTitle)
        model.addAttribute("editContent", page.content)
        return "wiki/edit"
    }

    @PostMapping("/{owner}/{projectName}/wiki/_edit/{*title}")
    fun edit(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable title: String,
        @RequestParam(name = "title") newTitle: String,
        @RequestParam(required = false, defaultValue = "") content: String,
        @RequestParam(required = false) message: String?,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = findProject(owner, projectName) ?: return "error/404"
        val user = getLoginUser(authentication)
        if (!canWrite(user, project)) {
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        val oldTitle = decodeWildcardPath(title)
        if (wikiService.getPage(project, oldTitle) == null) return "error/404"

        val effectiveNewTitle = newTitle.trim().takeIf { it.isNotEmpty() } ?: oldTitle
        wikiService.savePage(project, user!!, oldTitle, effectiveNewTitle, content, message)
        return "redirect:/${owner.encodePathSegment()}/${projectName.encodePathSegment()}/wiki/${encodeTitlePath(effectiveNewTitle)}"
    }

    @PostMapping("/{owner}/{projectName}/wiki/_delete/{*title}")
    fun delete(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable title: String,
        @RequestParam(required = false) message: String?,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = findProject(owner, projectName) ?: return "error/404"
        val user = getLoginUser(authentication)
        if (!canWrite(user, project)) {
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        val decodedTitle = decodeWildcardPath(title)
        if (wikiService.getPage(project, decodedTitle) == null) return "error/404"

        wikiService.deletePage(project, user!!, decodedTitle, message)
        return "redirect:/${owner.encodePathSegment()}/${projectName.encodePathSegment()}/wiki"
    }

    // 페이지별 히스토리(P3-42 3번) — 리비전 목록. ?rev=<commitId>를 주면 그 리비전의 diff를
    // 함께 보여주고(부모 커밋과 비교), ?revA=&revB=를 함께 주면 두 임의 리비전 사이의 diff를
    // 보여준다("리비전 간 diff" 요구사항).
    @GetMapping("/{owner}/{projectName}/wiki/_history/{*title}")
    fun history(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable title: String,
        @RequestParam(required = false) rev: String?,
        @RequestParam(required = false) revA: String?,
        @RequestParam(required = false) revB: String?,
        @RequestParam(defaultValue = "0") pageNum: Int,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = findProject(owner, projectName) ?: return "error/404"
        val user = getLoginUser(authentication)
        if (!canRead(user, project)) {
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        val decodedTitle = decodeWildcardPath(title)
        addCommonAttributes(model, project, user, decodedTitle)
        model.addAttribute("historyTitle", decodedTitle)
        model.addAttribute("revisions", wikiService.history(project, decodedTitle, pageNum, 20))

        if (!revA.isNullOrBlank() && !revB.isNullOrBlank()) {
            model.addAttribute("diffLines", wikiService.compare(project, decodedTitle, revA, revB).lines())
            model.addAttribute("diffLabel", "$revA .. $revB")
        } else if (!rev.isNullOrBlank()) {
            model.addAttribute("diffLines", wikiService.diff(project, decodedTitle, rev).lines())
            model.addAttribute("diffLabel", rev)
        }
        return "wiki/history"
    }

    // 위키 페이지 뷰(제목에 슬래시로 중첩 경로 표현, P3-42 7번). 반드시 다른 GetMapping들보다
    // 뒤에 선언 순서와 무관하게 Spring의 PathPattern 특이도 비교로 "_new"/"_search" 등 리터럴
    // 세그먼트를 가진 매핑이 이 와일드카드보다 항상 먼저 매치된다.
    @GetMapping("/{owner}/{projectName}/wiki/{*title}")
    fun view(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable title: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = findProject(owner, projectName) ?: return "error/404"
        val user = getLoginUser(authentication)
        if (!canRead(user, project)) {
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        val decodedTitle = decodeWildcardPath(title)
        val page = wikiService.getPage(project, decodedTitle)

        addCommonAttributes(model, project, user, decodedTitle)
        if (page == null) {
            model.addAttribute("page", null)
            model.addAttribute("notFoundTitle", decodedTitle)
            return "wiki/view"
        }
        model.addAttribute("page", page)
        model.addAttribute("renderedHtml", markdownService.render(page.content, true, project))
        return "wiki/view"
    }

    private fun decodeWildcardPath(raw: String): String = raw.trimStart('/')

    private fun encodeTitlePath(title: String): String =
        title.split("/").joinToString("/") { it.encodePathSegment() }
}
