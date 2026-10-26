package dev.yona.web.access

import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectService
import dev.yona.security.CurrentUserProvider
import dev.yona.web.RedirectSupport
import dev.yona.web.error.ErrorViews
import dev.yona.web.error.ViewResultException
import dev.yona.web.layout.FlashMessageTranslator
import dev.yona.web.project.ProjectViewFactory
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.ModelAndView

/**
 * 프로젝트 화면의 검사 애너테이션을 처리한다. v1.6의 `actions` 패키지(`AbstractProjectCheckAction`과
 * 하위 액션, `NullProjectCheckAction`, `GuestProhibitAction`)에 해당한다.
 *
 * ## 처리 순서
 * 1. [GuestProhibit]: 게스트이면 홈으로 보낸다.
 * 2. [NullProjectCheck]만 있으면 프로젝트 존재만 확인한다.
 * 3. [IsAllowed], [IsCreatable], [IsOnlyGitAvailable], [DefaultProjectCheck], [CodeAccessCheck] 중 하나라도 있으면
 *    프로젝트를 찾고 읽기 권한을 확인한 뒤, 붙은 검사를 [IsAllowed], [IsCreatable], [IsOnlyGitAvailable],
 *    [CodeAccessCheck] 순으로 실행한다. v1.6은 여러 액션이 붙었을 때의 순서를 프레임워크가 정했다.
 *
 * ## 프로젝트를 찾는 규칙
 * 경로의 첫 두 구간이 소유자와 프로젝트 이름이다. API 경로(`/-_-api/v1/`)는 `owners`와 `projects` 다음 구간이다.
 * [ProjectService.findByOwnerAndProjectName]이 이전 이름까지 찾으므로 v1.6의 "이전 위치로 리다이렉트" 분기는
 * 실제로 실행되지 않았고, v2도 리다이렉트하지 않는다.
 *
 * 검사에 실패하면 [ViewResultException]으로 오류 화면을 전달한다.
 */
@Component
class ProjectCheckInterceptor(
    private val currentUserProvider: CurrentUserProvider,
    private val projectService: ProjectService,
    private val accessControl: AccessControl,
    private val locator: ProjectResourceLocator,
    private val projectViews: ProjectViewFactory,
    private val flashTranslator: FlashMessageTranslator,
    private val messages: MessageSource
) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val method = (handler as? HandlerMethod) ?: return true

        fun <A : Annotation> find(type: Class<A>): A? =
            AnnotatedElementUtils.findMergedAnnotation(method.method, type) ?: AnnotatedElementUtils.findMergedAnnotation(method.beanType, type)

        find(GuestProhibit::class.java)?.let { if (!guestPassed(it, request, response)) return false }

        val isAllowed = find(IsAllowed::class.java)
        val isCreatable = find(IsCreatable::class.java)
        val onlyGit = find(IsOnlyGitAvailable::class.java)
        val defaultCheck = find(DefaultProjectCheck::class.java)
        val codeAccess = find(CodeAccessCheck::class.java)
        val nullCheck = find(NullProjectCheck::class.java)

        val needsReadCheck = isAllowed != null || isCreatable != null || onlyGit != null || defaultCheck != null || codeAccess != null
        if (!needsReadCheck && nullCheck == null) return true

        val segments = pathSegments(request)
        val project = if (needsReadCheck) {
            checkProjectReadable(request, segments)
        } else {
            findProject(segments, request).also { if (it == null) throw ViewResultException(projectNotFound(request)) }
        } ?: return true

        isAllowed?.let { checkAllowed(it, project, segments) }
        isCreatable?.let { checkCreatable(it, project) }
        if (onlyGit != null && project.vcs != "GIT") {
            throw ViewResultException(ErrorViews.badRequest("error.badrequest.only.available.for.git"))
        }
        if (codeAccess != null && project.isCodeAccessibleMemberOnly == true &&
            !projectService.hasMember(project, currentUserProvider.get().user)
        ) {
            throw ViewResultException(forbiddenOrLogin(request))
        }
        return true
    }

    // --- 게스트 ---

    private fun guestPassed(annotation: GuestProhibit, request: HttpServletRequest, response: HttpServletResponse): Boolean {
        if (currentUserProvider.get().user?.isGuest != true) return true
        val flash = if (annotation.displaysFlashMessage) mapOf(RedirectSupport.WARNING to "error.forbidden.or.not.allowed") else emptyMap()
        val redirect = RedirectSupport.see(request, response, "/", flash)
        response.status = redirect.statusCode.value()
        response.setHeader(HttpHeaders.LOCATION, redirect.headers.getFirst(HttpHeaders.LOCATION))
        return false
    }

    // --- 프로젝트 존재와 읽기 권한 ---

    /** 프로젝트를 찾고 읽을 수 있는지 확인한다. 실패하면 화면을 전달하는 예외를 던진다. */
    private fun checkProjectReadable(request: HttpServletRequest, segments: List<String>): Project {
        val project = findProject(segments, request) ?: throw ViewResultException(projectNotFound(request))
        if (!accessControl.isAllowed(currentUserProvider.get().user, project.asResource(), Operation.READ)) {
            throw ViewResultException(forbiddenOrLogin(request, withFailedFlash = true))
        }
        return project
    }

    private fun findProject(segments: List<String>, request: HttpServletRequest): Project? {
        val (owner, name) = ownerAndName(segments, request.requestURI)
        return projectService.findByOwnerAndProjectName(owner, name)
    }

    /** 프로젝트가 없거나 읽을 수 없을 때의 응답: 익명에게는 로그인 화면, 로그인한 사용자에게는 오류 화면이다. */
    private fun projectNotFound(request: HttpServletRequest): ModelAndView =
        if (currentUserProvider.get().isAnonymous) {
            loginRequired(request)
        } else {
            ErrorViews.notFound("error.forbidden.or.notfound").also { it.status = org.springframework.http.HttpStatus.FORBIDDEN }
        }

    /**
     * 읽기 권한이 없을 때의 응답: 익명에게는 로그인 화면, 로그인한 사용자에게는 오류 화면이다.
     * [withFailedFlash]이면 어느 쪽에도 `failed` 안내를 붙인다 (v1.6 `AbstractProjectCheckAction`은 로그인 여부와 관계없이 flash를 심는다).
     */
    private fun forbiddenOrLogin(request: HttpServletRequest, withFailedFlash: Boolean = false): ModelAndView =
        if (currentUserProvider.get().isAnonymous) {
            loginRequired(request, withFailedFlash)
        } else {
            ErrorViews.forbidden("error.forbidden.or.notfound").also { if (withFailedFlash) addFailedFlash(it) }
        }

    private fun addFailedFlash(view: ModelAndView) {
        val message = messages.getMessage("error.auth.unauthorized.waringMessage", null, LocaleContextHolder.getLocale())
        view.addObject("flashMessages", flashTranslator.translate(mapOf("failed" to message)))
    }

    /** 로그인 화면을 403으로 보여 준다. 안내(`failed`)는 같은 응답에서 바로 보인다. */
    private fun loginRequired(request: HttpServletRequest, withFailedFlash: Boolean = true): ModelAndView {
        val view = ErrorViews.loginRequired(request.requestURI)
        if (withFailedFlash) addFailedFlash(view)
        return view
    }

    // --- 개별 검사 ---

    private fun checkAllowed(annotation: IsAllowed, project: Project, segments: List<String>) {
        val resource = locator.locate(segments, project, annotation.resourceType)
        val view = projectViews.of(project)
        if (resource == null) {
            throw ViewResultException(ErrorViews.notFound("error.notfound", view, annotation.resourceType.resource))
        }
        if (!accessControl.isAllowed(currentUserProvider.get().user, resource, annotation.value)) {
            throw ViewResultException(ErrorViews.forbidden("error.forbidden", view))
        }
    }

    private fun checkCreatable(annotation: IsCreatable, project: Project) {
        if (!accessControl.isProjectResourceCreatable(currentUserProvider.get().user, project, annotation.value)) {
            throw ViewResultException(ErrorViews.forbidden("error.forbidden", projectViews.of(project)))
        }
    }

    // --- 경로 ---

    private fun pathSegments(request: HttpServletRequest): List<String> =
        request.requestURI.removePrefix(request.contextPath.orEmpty()).split('/').filter { it.isNotEmpty() }

    /**
     * 소유자와 프로젝트 이름을 경로에서 읽는다. API 경로는 `/-_-api/v1/owners/<소유자>/projects/<이름>/...` 형태로,
     * `이름/값` 쌍을 짝으로 읽는다 (v1.6 `PathVariable`).
     */
    private fun ownerAndName(segments: List<String>, requestUri: String): Pair<String, String> {
        if (requestUri.contains(API_PREFIX)) {
            val pairs = segments.drop(2).chunked(2).associate { it[0] to (it.getOrNull(1) ?: "") }
            return (pairs["owners"] ?: "") to (pairs["projects"] ?: "")
        }
        return segments[0] to segments[1]
    }

    private companion object {
        const val API_PREFIX = "/-_-api/v1/"
    }
}
