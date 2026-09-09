package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.support.MarkdownService
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.Commit
import com.github.yonaprojects.yona.domain.wiki.WikiPageSummary
import com.github.yonaprojects.yona.domain.wiki.WikiService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// `yona wiki` CLI/REST 클라이언트 및 MCP 위키 도구용 JSON API
// (`/api/v1/projects/{owner}/{project}/wiki`). TagRestApiController와 동일하게 위임할 만한
// 기존 세션/폼 컨트롤러가 없어(완전 신규 기능) AccessControl 판정을 이 컨트롤러가 직접 수행한다.
//
// 권한: 읽기는 프로젝트 읽기 권한과 동일(공개 프로젝트는 게스트 제외 누구나), 쓰기(생성/수정/
// 삭제)는 "프로젝트 쓰기 권한(멤버)과 동일" — BoardViewController의 코드브라우저 온라인편집이
// 쓰는 것과 동일한 "existsByProjectIdAndUserId" 멤버십 검사를 재사용한다(TagRestApiController
// 처럼 매니저 전용 Operation.UPDATE를 쓰지 않는다 — 위키는 코드 push와 동일한 문턱이어야
// 한다는 요구사항이므로 의도적으로 다른 임계값).
//
// ApiTokenAuthenticationFilter의 resourceSegmentToResourceType에 이미 "wiki" ->
// ResourceType.WIKI_PAGE(WIKI 스코프 그룹) 매핑이 있어(scopedApiPattern이 3번째 세그먼트 뒤
// 나머지 경로는 통째로 허용하므로 /wiki/pages, /wiki/history/** 등 전부 이 매핑을 그대로 탄다),
// 별도 스코프 배선 없이 바로 PAT 인증이 적용된다.
@RestController
@RequestMapping("/api/v1/projects/{owner}/{project}/wiki")
class WikiRestApiController(
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val accessControl: AccessControl,
    private val wikiService: WikiService,
    private val markdownService: MarkdownService
) {

    private fun getLoginUser(authentication: Authentication?): User? {
        if (authentication == null) return null
        return userRepository.findByLoginId(authentication.name).orElse(null)
    }

    private fun findProject(owner: String, project: String): Project? {
        return projectRepository.findByOwnerAndNameOrPreviousPlace(owner, project).orElse(null)
    }

    private fun canRead(user: User?, project: Project): Boolean = accessControl.isAllowedToReadProject(user, project)

    // 위키 쓰기 권한 = 프로젝트 멤버(코드 push 권한과 동일 문턱). BoardViewController의 코드브라우저
    // 온라인편집 커밋 경로가 쓰는 것과 동일한 판정 방식이다.
    private fun canWrite(user: User?, project: Project): Boolean {
        if (user == null || user.isGuest) return false
        if (user.isSiteManager) return true
        return projectUserRepository.existsByProjectIdAndUserId(project.id!!, user.id!!)
    }

    @GetMapping("/pages")
    fun listPages(
        @PathVariable owner: String,
        @PathVariable project: String,
        @RequestParam(required = false) q: String?,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val found = findProject(owner, project) ?: return ResponseEntity.notFound().build()
        val user = getLoginUser(authentication)
        if (!canRead(user, found)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build()

        val pages = if (q.isNullOrBlank()) wikiService.listPages(found) else wikiService.search(found, q)
        return ResponseEntity.ok(pages.map { toPageSummaryNode(it) })
    }

    @GetMapping("/pages/{*title}")
    fun getPage(
        @PathVariable owner: String,
        @PathVariable project: String,
        @PathVariable title: String,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val found = findProject(owner, project) ?: return ResponseEntity.notFound().build()
        val user = getLoginUser(authentication)
        if (!canRead(user, found)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build()

        val decodedTitle = decodeWildcardPath(title)
        val page = wikiService.getPage(found, decodedTitle) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(
            mapOf(
                "title" to page.title,
                "path" to page.path,
                "content" to page.content,
                "renderedHtml" to markdownService.render(page.content, true, found),
                "revision" to page.revision
            )
        )
    }

    @PostMapping("/pages")
    fun createPage(
        @PathVariable owner: String,
        @PathVariable project: String,
        @RequestBody request: CreateWikiPageRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val found = findProject(owner, project) ?: return ResponseEntity.notFound().build()
        val user = getLoginUser(authentication)
        if (!canWrite(user, found)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build()

        if (request.title.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "title은 비어 있을 수 없습니다."))
        }

        return try {
            if (wikiService.getPage(found, request.title) != null) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to "이미 존재하는 페이지입니다: ${request.title}"))
            }
            val commitId = wikiService.savePage(found, user!!, null, request.title, request.content ?: "", request.message)
            ResponseEntity.status(HttpStatus.CREATED).body(mapOf("title" to request.title, "revision" to commitId))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @PutMapping("/pages/{*title}")
    fun updatePage(
        @PathVariable owner: String,
        @PathVariable project: String,
        @PathVariable title: String,
        @RequestBody request: UpdateWikiPageRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val found = findProject(owner, project) ?: return ResponseEntity.notFound().build()
        val user = getLoginUser(authentication)
        if (!canWrite(user, found)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build()

        val decodedTitle = decodeWildcardPath(title)
        if (wikiService.getPage(found, decodedTitle) == null) {
            return ResponseEntity.notFound().build()
        }
        val newTitle = request.newTitle?.takeIf { it.isNotBlank() } ?: decodedTitle
        return try {
            val commitId = wikiService.savePage(found, user!!, decodedTitle, newTitle, request.content ?: "", request.message)
            ResponseEntity.ok(mapOf("title" to newTitle, "revision" to commitId))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @DeleteMapping("/pages/{*title}")
    fun deletePage(
        @PathVariable owner: String,
        @PathVariable project: String,
        @PathVariable title: String,
        @RequestParam(required = false) message: String?,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val found = findProject(owner, project) ?: return ResponseEntity.notFound().build()
        val user = getLoginUser(authentication)
        if (!canWrite(user, found)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build()

        val decodedTitle = decodeWildcardPath(title)
        if (wikiService.getPage(found, decodedTitle) == null) {
            return ResponseEntity.notFound().build()
        }
        wikiService.deletePage(found, user!!, decodedTitle, message)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/history/{*title}")
    fun history(
        @PathVariable owner: String,
        @PathVariable project: String,
        @PathVariable title: String,
        @RequestParam(defaultValue = "0") pageNum: Int,
        @RequestParam(defaultValue = "20") pageSize: Int,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val found = findProject(owner, project) ?: return ResponseEntity.notFound().build()
        val user = getLoginUser(authentication)
        if (!canRead(user, found)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build()

        val decodedTitle = decodeWildcardPath(title)
        val commits = wikiService.history(found, decodedTitle, pageNum, pageSize)
        return ResponseEntity.ok(commits.map { toCommitNode(it) })
    }

    @GetMapping("/diff/{commitId}/{*title}")
    fun diff(
        @PathVariable owner: String,
        @PathVariable project: String,
        @PathVariable commitId: String,
        @PathVariable title: String,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val found = findProject(owner, project) ?: return ResponseEntity.notFound().build()
        val user = getLoginUser(authentication)
        if (!canRead(user, found)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build()

        val decodedTitle = decodeWildcardPath(title)
        val patch = wikiService.diff(found, decodedTitle, commitId)
        return ResponseEntity.ok(mapOf("title" to decodedTitle, "commitId" to commitId, "patch" to patch))
    }

    @GetMapping("/compare/{revA}/{revB}/{*title}")
    fun compare(
        @PathVariable owner: String,
        @PathVariable project: String,
        @PathVariable revA: String,
        @PathVariable revB: String,
        @PathVariable title: String,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val found = findProject(owner, project) ?: return ResponseEntity.notFound().build()
        val user = getLoginUser(authentication)
        if (!canRead(user, found)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build()

        val decodedTitle = decodeWildcardPath(title)
        val patch = wikiService.compare(found, decodedTitle, revA, revB)
        return ResponseEntity.ok(mapOf("title" to decodedTitle, "revA" to revA, "revB" to revB, "patch" to patch))
    }

    // Spring의 `{*title}`은 선행 "/"를 포함해 캡처한다("/pages/Guides/Setup" -> "/Guides/Setup") —
    // WikiService가 기대하는 "Guides/Setup" 형태로 앞의 "/"만 제거한다(그 외 문자는 그대로 둔다,
    // 슬래시 자체가 중첩 페이지 구분자이므로 URL 인코딩되지 않은 실제 슬래시를 그대로 살려야 한다).
    private fun decodeWildcardPath(raw: String): String = raw.trimStart('/')

    private fun toPageSummaryNode(page: WikiPageSummary): Map<String, Any?> = mapOf(
        "title" to page.title,
        "path" to page.path,
        "updatedAt" to page.updatedAt?.toString(),
        "lastCommitMessage" to page.lastCommitMessage,
        "lastAuthorName" to page.lastAuthorName
    )

    private fun toCommitNode(commit: Commit): Map<String, Any?> = mapOf(
        "id" to commit.getId(),
        "shortId" to commit.getShortId(),
        "shortMessage" to commit.getShortMessage(),
        "message" to commit.getMessage(),
        "authorName" to commit.getAuthorName(),
        "authorEmail" to commit.getAuthorEmail(),
        "authorDate" to commit.getAuthorDate()?.toInstant()?.toString()
    )

    data class CreateWikiPageRequest(
        val title: String,
        val content: String? = null,
        val message: String? = null
    )

    data class UpdateWikiPageRequest(
        val newTitle: String? = null,
        val content: String? = null,
        val message: String? = null
    )
}
