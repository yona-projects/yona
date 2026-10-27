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
    private val svn: SvnRepositories
) {

    /** 요청 처리를 마치고 응답을 썼다. */
    fun service(request: HttpServletRequest, response: HttpServletResponse) {
        val credentials = BasicCredentials.parse(request)
        if (credentials === BasicCredentials.MALFORMED) return text(response, 400, null)
        // v1.6은 BasicAuthAction이 가장 바깥이라 이후의 403, 404 응답에도 세션 쿠키가 실린다
        val user = basicAuth.resolve(request, response, credentials)

        // 컨텍스트 경로와 앞의 `/`를 떼고 `svn`, 소유자, 나머지 경로로 가른다.
        val path = java.net.URI(request.requestURI).path.removePrefix(request.contextPath).removePrefix("/")
        val segments = path.split("/", limit = 3)
        if (segments.size < 3) return text(response, 403, null)
        val owner = segments[1]
        val pathInfo = segments[2]
        val projectName = pathInfo.split("/", limit = 2)[0]

        val project = projectService.findByOwnerAndProjectName(owner, projectName)
        if (project == null || project.vcs != "Subversion") return text(response, 404, null)

        val operation = if (request.method in READ_METHODS) Operation.READ else Operation.UPDATE
        if (!accessControl.isAllowed(user, ProjectResource(requireNotNull(project.id).toString(), ResourceType.CODE, project), operation)) {
            if (user == null) {
                response.setHeader("WWW-Authenticate", "Basic realm=\"Yobi\"")
                return text(response, 401, "Invalid username or password")
            }
            return text(response, 403, "You have no permission to access this repository.")
        }

        val servlet = DAVServlet().apply { init(JavaxServletBridge.config(File(svn.rootDirectory(), "$owner/").absolutePath, properties.server)) }
        // PROPFIND는 응답의 href를 인코딩하는 래퍼를 거친다(A-137). 나머지는 서블릿이 바로 쓴다.
        val target = if (request.method == "PROPFIND") HrefEncodingResponse(response) else response
        servlet.service(JavaxServletBridge.request(request, pathInfo, user?.loginId.orEmpty(), properties.server), JavaxServletBridge.response(target))
        (target as? HrefEncodingResponse)?.finish()
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
        private val READ_METHODS = setOf(DAVHandlerFactory.METHOD_OPTIONS, DAVHandlerFactory.METHOD_PROPFIND, DAVHandlerFactory.METHOD_GET, DAVHandlerFactory.METHOD_REPORT)

        /** Play가 라우팅하지 못해 v1.6이 따로 가로채던 WebDAV 메서드이다 (`SvnApp.WEBDAV_METHODS`). */
        val WEBDAV_METHODS: List<String> = listOf(
            DAVHandlerFactory.METHOD_PROPFIND, DAVHandlerFactory.METHOD_REPORT, DAVHandlerFactory.METHOD_TRACE, DAVHandlerFactory.METHOD_PROPPATCH,
            DAVHandlerFactory.METHOD_COPY, DAVHandlerFactory.METHOD_MOVE, DAVHandlerFactory.METHOD_LOCK, DAVHandlerFactory.METHOD_UNLOCK,
            DAVHandlerFactory.METHOD_MKCOL, DAVHandlerFactory.METHOD_VERSION_CONTROL, DAVHandlerFactory.METHOD_MKWORKSPACE,
            DAVHandlerFactory.METHOD_MKACTIVITY, DAVHandlerFactory.METHOD_CHECKIN, DAVHandlerFactory.METHOD_CHECKOUT, DAVHandlerFactory.METHOD_MERGE
        )
    }
}

