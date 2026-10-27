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
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.support.RequestContextUtils

/**
 * 프로젝트 화면의 검사 애너테이션 한 개를 실행한다. v1.6의 `actions` 패키지(`AbstractProjectCheckAction`과
 * 하위 액션, `NullProjectCheckAction`, `GuestProhibitAction`)에 해당한다.
 *
 * ## 어노테이션 하나가 하는 일
 * - [GuestProhibit]: 게스트이면 홈으로 보낸다.
 * - [NullProjectCheck]: 프로젝트가 있는지만 확인한다.
 * - [IsAllowed], [IsCreatable], [IsOnlyGitAvailable], [DefaultProjectCheck], [CodeAccessCheck]: 프로젝트를 찾고 읽기 권한을 확인한 뒤
 *   자기 검사를 한다. v1.6의 `AbstractProjectCheckAction.call`이 하위 액션마다 공통으로 하는 일이다. 같은 요청에서 여러 개가 붙으면
 *   읽기 확인은 한 번만 계산한다(결과가 같다).
 *
 * 어노테이션을 어떤 순서로 실행하는지는 [AccessChainInterceptor]가 정한다(v1.6의 Play 2.3 규칙).
 *
 * ## 프로젝트를 찾는 규칙
 * 경로의 첫 두 구간이 소유자와 프로젝트 이름이다. API 경로(`/-_-api/v1/`)는 `owners`와 `projects` 다음 구간이다.
 * [ProjectService.findByOwnerAndProjectName]이 이전 이름까지 찾으므로 v1.6의 "이전 위치로 리다이렉트" 분기는
 * 실제로 실행되지 않았고, v2도 리다이렉트하지 않는다.
 *
 * 검사에 실패하면 [ViewResultException]으로 오류 화면을 전달한다.
 */
@Component
class ProjectCheckStage(
    private val currentUserProvider: CurrentUserProvider,
    private val projectService: ProjectService,
    private val accessControl: AccessControl,
    private val locator: ProjectResourceLocator,
    private val projectViews: ProjectViewFactory,
    private val flashTranslator: FlashMessageTranslator,
    private val messages: MessageSource
) {

    /** 통과하면 `true`, 응답을 이미 썼으면(리다이렉트) `false`이다. 실패하면 [ViewResultException]을 던진다. 프로젝트 검사가 아닌 어노테이션은 무시한다. */
    fun run(annotation: Annotation, request: HttpServletRequest, response: HttpServletResponse): Boolean {
        when (annotation) {
            is GuestProhibit -> return guestPassed(annotation, request, response)
            is NullProjectCheck -> {
                findProject(pathSegments(request), request) ?: throw ViewResultException(projectNotFound(request))
            }
            is IsAllowed, is IsCreatable, is IsOnlyGitAvailable, is DefaultProjectCheck, is CodeAccessCheck -> {
                val segments = pathSegments(request)
                val project = readableProject(request, segments)
                when (annotation) {
                    is IsAllowed -> checkAllowed(annotation, project, segments)
                    is IsCreatable -> checkCreatable(annotation, project)
                    is IsOnlyGitAvailable -> if (project.vcs != "GIT") {
                        throw ViewResultException(ErrorViews.badRequest("error.badrequest.only.available.for.git"))
                    }
                    is CodeAccessCheck -> if (project.isCodeAccessibleMemberOnly == true &&
                        !projectService.hasMember(project, currentUserProvider.get().user)
                    ) {
                        throw ViewResultException(forbiddenOrLogin(request))
                    }
                    else -> Unit
                }
            }
            else -> Unit
        }
        return true
    }

    /** 프로젝트를 찾고 읽을 수 있는지 한 번만 확인한다. 같은 요청의 다음 검사는 결과를 다시 쓴다. */
    private fun readableProject(request: HttpServletRequest, segments: List<String>): Project {
        (request.getAttribute(READABLE_PROJECT) as? Project)?.let { return it }
        return checkProjectReadable(request, segments).also { request.setAttribute(READABLE_PROJECT, it) }
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
            ErrorViews.forbidden("error.forbidden.or.notfound").also { if (withFailedFlash) addFailedFlash(it, request) }
        }

    /**
     * `failed` 안내를 같은 응답에 보인다. v1.6는 앞 요청이 남긴 flash 위에 `failed`를 더해 응답에 다시 심으므로,
     * 앞의 안내가 함께 보이고 다음 응답에도 남는다.
     */
    private fun addFailedFlash(view: ModelAndView, request: HttpServletRequest) {
        val message = messages.getMessage("error.auth.unauthorized.waringMessage", null, LocaleContextHolder.getLocale())
        dev.yona.web.RedirectSupport.flashNow(mapOf("failed" to message))
    }

    /** 로그인 화면을 403으로 보여 준다. 안내(`failed`)는 같은 응답에서 바로 보인다. */
    private fun loginRequired(request: HttpServletRequest, withFailedFlash: Boolean = true): ModelAndView {
        val view = ErrorViews.loginRequired(request.requestURI)
        if (withFailedFlash) addFailedFlash(view, request)
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
        const val READABLE_PROJECT = "dev.yona.web.access.ReadableProject"
    }
}
