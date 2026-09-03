package com.github.yonaprojects.yona.web

import jakarta.servlet.ServletConfig
import jakarta.servlet.ServletContext
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController
import org.tmatesoft.svn.core.internal.server.dav.DAVServlet
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@RestController
class SvnController(
    @Value("\${yona.svn.base-dir:/tmp/yona/svn}")
    private val baseDir: String,
    private val servletContext: ServletContext
) {

    private val logger = LoggerFactory.getLogger(SvnController::class.java)
    private val davServletCache = ConcurrentHashMap<String, DAVServlet>()

    // yona conf/routes:320-325의 "/svn/*path" catch-all(Play 와일드카드 라우트, SvnApp.serviceWithPath())
    // 대응. 이전에는 "/svn/{ownerName}/{projectName}/**"로 매핑돼 있어 ownerName/projectName 세그먼트가
    // 둘 다 없는 짧은 경로(예: "/svn/onlyowner")는 이 핸들러에 아예 도달하지 못하고 Spring MVC 자체가
    // 404를 반환했다 — legacy는 "/svn/*path" catch-all이라 이 핸들러가 항상 호출되고, 세그먼트 부족은
    // 핸들러 내부에서 직접 403으로 판정한다(SvnApp.service():94-96). 매핑을 "/svn/**"로 넓혀 legacy와
    // 동일하게 항상 이 핸들러로 들어오게 하고, 세그먼트 검증은 그대로 내부에서 담당하게 한다.
    @RequestMapping("/svn/**")
    fun service(
        request: HttpServletRequest,
        response: HttpServletResponse
    ) {
        val uri = request.requestURI
        val segments = uri.split("/").filter { it.isNotEmpty() }
        if (segments.size < 3) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN)
            return
        }

        val ownerName = segments[1]

        val davServlet = davServletCache.computeIfAbsent(ownerName) { owner ->
            val servlet = DAVServlet()
            servlet.init(object : ServletConfig {
                override fun getServletName(): String = "DAVServlet-$owner"
                override fun getServletContext(): ServletContext = this@SvnController.servletContext
                
                override fun getInitParameter(name: String): String? {
                    return if (name == "SVNParentPath") {
                        val parentPath = File(baseDir, owner)
                        if (!parentPath.exists()) {
                            parentPath.mkdirs()
                        }
                        parentPath.absolutePath
                    } else {
                        null
                    }
                }

                override fun getInitParameterNames(): Enumeration<String> {
                    return Collections.enumeration(listOf("SVNParentPath"))
                }
            })
            servlet
        }

        val wrappedRequest = SvnServletRequestWrapper(request, ownerName)

        // yona SvnApp.startDavService()의 "catch (Exception e) { response.setStatus(500); ...;
        // play.Logger.error(...) }" 대응 — 저장소가 DB엔 존재해도 실제 디스크 경로가 없거나 손상된
        // 경우 등 DAVServlet 자체가 던지는 예외를 잡아 스택트레이스가 그대로 노출되지 않게 하고 로그를
        // 남긴다(실제 HTTP 상태 코드는 이전에도 500이었으므로 관찰 가능한 응답은 바뀌지 않는다).
        try {
            davServlet.service(wrappedRequest, response)
        } catch (e: Exception) {
            logger.error("Failed to process a SVN request: {}", uri, e)
            if (!response.isCommitted) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
            }
        }
    }

    // PROPFIND/MKCOL/REPORT 등 WebDAV 커스텀 메서드는 Spring의 RequestMethod enum에 아예 없어
    // service()의 "/svn/**" 매핑에 method를 명시할 수 없다(그래서 무제한으로 둔다). 문제는 OPTIONS인데
    // — Spring MVC는 method를 지정하지 않은 매핑에 한해 OPTIONS 요청을 자동으로 가로채 자체적으로
    // 합성한 Allow 헤더만 응답하고(RequestMappingInfoHandlerMapping의 내장 HttpOptionsHandler)
    // DAVServlet까지 절대 도달시키지 않는다. 실제 svn 클라이언트는 체크아웃 시작 시 이 OPTIONS
    // 응답의 Allow/DAV 헤더로 서버가 WebDAV/DeltaV를 지원하는지 판단하므로, 이 자동 응답이 나가면
    // 실제 checkout 자체가 실패한다. legacy(SvnApp.service())는 이런 개념 없이 모든 메서드를 동일하게
    // 처리하므로, OPTIONS만 명시적으로 별도 매핑해 Spring의 자동 가로채기를 우회하고 동일한 핸들러로
    // 보낸다(더 구체적인 method 조건을 가진 매핑이 우선한다는 Spring의 매핑 해석 규칙을 이용).
    @RequestMapping("/svn/**", method = [RequestMethod.OPTIONS])
    fun serviceOptions(request: HttpServletRequest, response: HttpServletResponse) {
        service(request, response)
    }
}
