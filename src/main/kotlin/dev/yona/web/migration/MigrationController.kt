package dev.yona.web.migration

import dev.yona.config.YonaProperties
import dev.yona.domain.migration.GithubTokenClient
import dev.yona.domain.migration.MigrationService
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectService
import dev.yona.security.CurrentUserProvider
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.error.ErrorViews
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.ModelAndView

/**
 * 이전(Migration) 화면이다. v1.6 `MigrationApp`(`app/controllers/MigrationApp.java`)에 해당한다. Yona 프로젝트의 마일스톤, 이슈, 게시글을 GitHub로
 * 옮기는 보조 기능이며, GitHub에 쓰는 일은 브라우저의 스크립트(`yona.Migration.js`)가 GitHub API를 직접 불러서 한다. 서버는 화면과 GitHub 로그인의
 * 토큰 교환, 옮길 데이터의 JSON만 맡는다.
 *
 * 모든 주소는 로그인이 필요하다. 화면(`GET /migration`)은 설정 `yona.github.allow-migration`이 켜져 있어야 열린다. 프로젝트별 JSON 주소는 그 프로젝트의
 * 매니저, 소속 조직의 관리자, 사이트 매니저만 쓸 수 있다(D-23. v1.6은 로그인만 요구했다). 없는 프로젝트는 404이다(v1.6은 500).
 */
@Controller
@AnonymousCheck
class MigrationController(
    private val migration: MigrationService,
    private val projectService: ProjectService,
    private val tokens: GithubTokenClient,
    private val currentUserProvider: CurrentUserProvider,
    private val properties: YonaProperties
) {

    @GetMapping("/migration")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    fun home(@RequestParam(required = false) code: String?, @RequestParam(required = false) state: String?, request: HttpServletRequest): Any {
        if (!properties.github.allowMigration) return ErrorViews.forbidden("error.forbidden.or.not.allowed")
        val session = request.getSession(true)
        // 돌아온 요청(code)은 인증을 시작할 때 이 세션에 둔 state와 같아야 한다. v1.6은 state가 없어 공격자가 만든 code가 든 주소를 열게 하면
        // 공격자의 GitHub 토큰이 이 사용자의 이전 화면에 묶였다(R-58-2). state는 한 번만 쓴다
        val expected = session.getAttribute(STATE_ATTRIBUTE) as? String
        if (!code.isNullOrBlank()) {
            if (expected == null || state != expected) return ErrorViews.forbidden("error.forbidden.or.not.allowed")
            session.removeAttribute(STATE_ATTRIBUTE)
        }
        val token = if (code.isNullOrBlank()) null else tokens.exchange(code)
        val user = currentUserProvider.get().user
        val newState = java.util.UUID.randomUUID().toString().replace("-", "") + java.util.UUID.randomUUID().toString().replace("-", "")
        session.setAttribute(STATE_ATTRIBUTE, newState)
        return ModelAndView("migration/home")
            .addObject("oauthState", newState)
            .addObject("title", "Migration")
            .addObject("token", token.orEmpty())
            .addObject("loginId", user?.loginId.orEmpty())
            .addObject("email", user?.email.orEmpty())
    }

    @GetMapping("/migration/projects")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @Transactional(readOnly = true)
    fun projects(): ResponseEntity<Any> = json(migration.sourceProjects(requireNotNull(currentUserProvider.get().user)))

    @GetMapping("/migration/{owner}/projects/{projectName}")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @Transactional(readOnly = true)
    fun project(@PathVariable owner: String, @PathVariable projectName: String): Any = export(owner, projectName) { migration.summary(it) }

    @GetMapping("/migration/{owner}/projects/{projectName}/labels")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @Transactional(readOnly = true)
    fun labels(@PathVariable owner: String, @PathVariable projectName: String): Any = export(owner, projectName) { migration.labels(it) }

    @GetMapping("/migration/{owner}/projects/{projectName}/issuelabel")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @Transactional(readOnly = true)
    fun issueLabelPairs(@PathVariable owner: String, @PathVariable projectName: String): Any = export(owner, projectName) { migration.issueLabelPairs(it) }

    @GetMapping("/migration/{owner}/projects/{projectName}/milestones")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @Transactional(readOnly = true)
    fun milestones(@PathVariable owner: String, @PathVariable projectName: String): Any = export(owner, projectName) { migration.milestones(it) }

    @GetMapping("/migration/{owner}/projects/{projectName}/issues")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @Transactional(readOnly = true)
    fun issues(@PathVariable owner: String, @PathVariable projectName: String, request: HttpServletRequest): Any =
        export(owner, projectName) { migration.issues(it, wikiCommit(request)) }

    @GetMapping("/migration/{owner}/projects/{projectName}/posts")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @Transactional(readOnly = true)
    fun posts(@PathVariable owner: String, @PathVariable projectName: String, request: HttpServletRequest): Any =
        export(owner, projectName) { migration.posts(it, wikiCommit(request)) }

    /** `withWikiCommit`이 비어 있지 않고 `true`로 끝나면 위키 커밋 방식이다. */
    private fun wikiCommit(request: HttpServletRequest): Boolean = request.getParameter("withWikiCommit")?.let { it.isNotBlank() && it.endsWith("true") } ?: false

    private fun export(owner: String, projectName: String, build: (Project) -> Any): Any {
        val project = projectService.findByOwnerAndProjectName(owner, projectName) ?: return ErrorViews.notFound()
        if (!migration.canExport(requireNotNull(currentUserProvider.get().user), project)) return ErrorViews.forbidden()
        return json(build(project))
    }

    private fun json(body: Any): ResponseEntity<Any> = ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body)

    private companion object {
        /** 인증을 시작할 때 세션에 두는 state의 이름이다(R-58-2). */
        const val STATE_ATTRIBUTE = "migration.oauth.state"
    }
}
