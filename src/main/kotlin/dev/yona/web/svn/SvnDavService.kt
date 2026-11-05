package dev.yona.web.svn

import dev.yona.access.AccessControl
import dev.yona.access.ProjectResource
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.project.ProjectService
import dev.yona.domain.project.RecentVisitService
import dev.yona.domain.user.User
import dev.yona.repository.svn.SvnRepositories
import dev.yona.web.access.BasicCredentials
import java.io.File
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.tmatesoft.svn.core.internal.server.dav.DAVServlet
import org.tmatesoft.svn.core.internal.server.dav.handlers.DAVHandlerFactory

/**
 * Subversion 저장소의 HTTP(WebDAV) 접근이다. v1.6 `SvnApp.service`(`app/controllers/SvnApp.java:75-165`)에 해당한다.
 *
 * 주소는 `/svn/소유자/프로젝트/…`이다. 프로젝트가 없거나 Subversion이 아니면 `404`, 주소가 짧으면 `403`이다. 읽기(`OPTIONS`, `PROPFIND`, `GET`,
 * `REPORT`)는 읽기 권한이, 그 밖의 메서드는 수정 권한이 있어야 한다(`HEAD`도 수정이다). 권한이 없을 때 익명이면 Basic 인증을 요구하고(`401`),
 * 인증한 사용자이면 `403`이다. 허락하면 SVNKit의 `DAVServlet`이 `<저장소 디렉터리>/<소유자>/`를 부모 경로로 요청을 처리한다.
 */
@Component
class SvnDavService(
    private val projectService: ProjectService,
    private val accessControl: AccessControl,
    private val basicAuth: dev.yona.web.access.BasicAuthResolver,
    private val properties: dev.yona.config.YonaProperties,
    private val recentVisits: RecentVisitService,
    private val svn: SvnRepositories,
    private val metrics: dev.yona.observability.VcsMetrics
) {

    /** 요청 처리를 마치고 응답을 썼다. */
    fun service(request: HttpServletRequest, response: HttpServletResponse) {
        val credentials = BasicCredentials.parse(request)
        if (credentials === BasicCredentials.MALFORMED) return text(response, 400, null)
        // v1.6은 BasicAuthAction이 가장 바깥이라 이후의 403, 404 응답에도 세션 쿠키가 실린다
        val identity = metrics.span("vcs.authenticate", "vcs" to "svn") { basicAuth.identify(request, response, credentials) }
        // 같은 주소에서 인증에 연달아 실패해 막았다(추가 개발 항목 X-5, `decisions.md` D-36)
        if (identity.blocked) return text(response, 429, "Too many failed authentication attempts")
        val user = identity.user

        // 컨텍스트 경로와 앞의 `/`를 떼고 `svn`, 소유자, 나머지 경로로 가른다.
        val path = java.net.URI(request.requestURI).path.removePrefix(request.contextPath).removePrefix("/")
        val segments = path.split("/", limit = 3)
        if (segments.size < 3) return text(response, 403, null)
        // v1.6 SvnApp은 Play 기본 액션 안에서 응답하므로 이후의 모든 응답에 Cache-Control이 붙는다(주소가 짧은 403은 라우트 밖이라 붙지 않는다)
        response.setHeader("Cache-Control", "no-cache")
        val owner = segments[1]
        val pathInfo = segments[2]
        val projectName = pathInfo.split("/", limit = 2)[0]

        val project = projectService.findByOwnerAndProjectName(owner, projectName)
        if (project == null || project.vcs != "Subversion") return text(response, 404, null)
        // 토큰으로 인증했으면 범위가 이 프로젝트의 저장소를 허용해야 한다(`repo`, 공개 프로젝트는 `public_repo`)
        var operation = if (request.method in READ_METHODS) Operation.READ else Operation.UPDATE
        // 코드를 구성원에게만 여는 프로젝트는 구성원이 아니면 수정 권한을 요구한다(Git과 같다). v1.6의 Subversion은 이 설정을 보지 않았다(R-62-5)
        if (project.isCodeAccessibleMemberOnly == true && !projectService.hasMember(project, user)) operation = Operation.UPDATE
        if (!basicAuth.allowsRepository(identity, project, write = operation == Operation.UPDATE)) return text(response, 403, "The token scope does not allow repository access")
        if (!metrics.span("vcs.authorize", "vcs" to "svn") { accessControl.isAllowed(user, ProjectResource(requireNotNull(project.id).toString(), ResourceType.CODE, project), operation) }) {
            if (user == null) {
                response.setHeader("WWW-Authenticate", "Basic realm=\"Yobi\"")
                return text(response, 401, if (identity.passwordRefused) dev.yona.web.access.BasicIdentity.PASSWORD_REFUSED_MESSAGE else "Invalid username or password")
            }
            return text(response, 403, "You have no permission to access this repository.")
        }

        val servlet = DAVServlet().apply { init(JavaxServletBridge.config(File(svn.rootDirectory(), "$owner/").absolutePath, properties.server)) }
        // PROPFIND는 응답의 href를 인코딩하는 래퍼를 거친다(A-137). 나머지는 서블릿이 바로 쓴다.
        // REPORT는 위치 구간(`get-location-segments-report`)의 경로에서 앞 슬래시를 뗀다(A-171)
        val target = when (request.method) {
            "PROPFIND" -> HrefEncodingResponse(response)
            "REPORT" -> LocationSegmentResponse(response)
            else -> response
        }
        // SVNKit의 WebDAV 처리 전체(커밋, 체크아웃, 보고서)이다. 요청 하나의 대부분의 시간이다
        metrics.span("vcs.dav", "vcs" to "svn", "service" to dev.yona.observability.VcsMetrics.svnServiceOf(request.method)) {
            servlet.service(JavaxServletBridge.request(request, pathInfo, user?.loginId.orEmpty(), properties.server), JavaxServletBridge.response(target))
        }
        (target as? HrefEncodingResponse)?.finish()
        (target as? LocationSegmentResponse)?.finish()
        response.flushBuffer()
        recentVisits.visits(user, project)
    }

    private fun text(response: HttpServletResponse, status: Int, body: String?) {
        response.status = status
        if (body != null) {
            response.contentType = "text/plain;charset=UTF-8"
            response.writer.write(body)
        }
    }

    companion object {
        private val READ_METHODS = setOf(DAVHandlerFactory.METHOD_OPTIONS, DAVHandlerFactory.METHOD_PROPFIND, DAVHandlerFactory.METHOD_GET, DAVHandlerFactory.METHOD_HEAD, DAVHandlerFactory.METHOD_REPORT)

        /** Play가 라우팅하지 못해 v1.6이 따로 가로채던 WebDAV 메서드이다 (`SvnApp.WEBDAV_METHODS`). */
        val WEBDAV_METHODS: List<String> = listOf(
            DAVHandlerFactory.METHOD_PROPFIND, DAVHandlerFactory.METHOD_REPORT, DAVHandlerFactory.METHOD_TRACE, DAVHandlerFactory.METHOD_PROPPATCH,
            DAVHandlerFactory.METHOD_COPY, DAVHandlerFactory.METHOD_MOVE, DAVHandlerFactory.METHOD_LOCK, DAVHandlerFactory.METHOD_UNLOCK,
            DAVHandlerFactory.METHOD_MKCOL, DAVHandlerFactory.METHOD_VERSION_CONTROL, DAVHandlerFactory.METHOD_MKWORKSPACE,
            DAVHandlerFactory.METHOD_MKACTIVITY, DAVHandlerFactory.METHOD_CHECKIN, DAVHandlerFactory.METHOD_CHECKOUT, DAVHandlerFactory.METHOD_MERGE
        )
    }
}

