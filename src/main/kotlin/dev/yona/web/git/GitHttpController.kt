package dev.yona.web.git

import dev.yona.util.LenientVersion
import dev.yona.access.AccessControl
import dev.yona.access.ProjectResource
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectService
import dev.yona.domain.project.RecentVisitService
import dev.yona.domain.user.User
import dev.yona.repository.GitHttpService
import dev.yona.web.access.BasicCredentials
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.MessageSource
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import java.util.Base64
import java.util.Locale
import java.util.zip.GZIPInputStream

/**
 * Git 클라이언트(`git clone`, `fetch`, `push`)가 쓰는 smart-HTTP 주소이다. v1.6 `GitApp`(`app/controllers/GitApp.java`)에 해당한다.
 *
 * 인증은 HTTP Basic(`loginId:password`)이다. 자격이 없거나 틀리면 익명으로 본다. 익명이 권한이 없으면 `401`로 로그인을 요구하고
 * (`WWW-Authenticate: Basic realm="Yobi"`), 로그인한 사용자가 권한이 없으면 `403`과 안내 문구이다.
 * 복제와 가져오기는 읽기 권한, 푸시는 수정 권한이 필요하다. 코드를 구성원에게만 여는 프로젝트는 읽기도 수정 권한을 요구한다.
 * 프로젝트 이름이 바뀌었으면 이전 이름으로도 찾는다.
 */
@Controller
@ResponseBody
class GitHttpController(
    private val projectService: ProjectService,
    private val accessControl: AccessControl,
    private val basicAuth: dev.yona.web.access.BasicAuthResolver,
    private val gitHttp: GitHttpService,
    private val recentVisits: RecentVisitService,
    private val messages: MessageSource,
    private val properties: dev.yona.config.YonaProperties
) {

    /** 서비스(`service=git-upload-pack` 또는 `git-receive-pack`)가 광고하는 ref 목록이다. */
    @GetMapping("/{ownerName}/{project}/info/refs")
    fun advertise(
        @PathVariable ownerName: String, @PathVariable("project") projectName: String, @RequestParam(required = false) service: String?,
        request: HttpServletRequest, response: HttpServletResponse
    ): Any? {
        val credentials = BasicCredentials.parse(request)
        if (credentials === BasicCredentials.MALFORMED) return malformed(response)
        // v1.6은 BasicAuthAction이 가장 바깥이라 이후의 403, 404 응답에도 세션 쿠키가 실린다
        val user = basicAuth.resolve(request, response, credentials)
        // 서비스를 정하지 않으면 getanyfile 서비스이다. 지원하지 않는다.
        if (service == null) return text(HttpStatus.FORBIDDEN, "Unsupported service: getanyfile")
        return handle(ownerName, projectName, service, advertise = true, user, request, response)
    }

    /**
     * 자격 형식이 틀리면 본문 없는 400이다. v1.6 `BasicAuthAction`은 기본 액션 밖에서 직접 `badRequest()`를 내므로 `Cache-Control`이 붙지 않는다.
     */
    private fun malformed(response: HttpServletResponse): ResponseEntity<Void> {
        response.setHeader("Cache-Control", null)
        return ResponseEntity.badRequest().build()
    }

    /** 복제나 가져오기, 푸시의 요청 본문을 처리한다. */
    @PostMapping("/{ownerName}/{project}/{service:git-upload-pack|git-receive-pack}")
    fun rpc(
        @PathVariable ownerName: String, @PathVariable("project") projectName: String, @PathVariable service: String,
        request: HttpServletRequest, response: HttpServletResponse
    ): Any? {
        val credentials = BasicCredentials.parse(request)
        if (credentials === BasicCredentials.MALFORMED) return malformed(response)
        val user = basicAuth.resolve(request, response, credentials)
        return handle(ownerName, projectName, service, advertise = false, user, request, response)
    }

    private fun handle(
        ownerName: String, projectName: String, service: String, advertise: Boolean, user: User?, request: HttpServletRequest, response: HttpServletResponse
    ): Any? {
        if (!GitHttpService.isSupported(service)) return text(HttpStatus.FORBIDDEN, "Unsupported service: '$service'")
        val project = projectService.findByOwnerAndProjectName(ownerName, projectName) ?: return ResponseEntity.notFound().build<Void>()
        if (project.vcs != "GIT") return ResponseEntity.notFound().build<Void>()

        if (!isAllowed(project, user, service)) {
            if (user == null) {
                response.setHeader("WWW-Authenticate", "Basic realm=\"Yobi\"")
                return text(HttpStatus.UNAUTHORIZED, "Invalid username or password")
            }
            return forbidden(user, ownerName, projectName, request.getHeader("User-Agent"))
        }


        if (advertise) {
            response.contentType = "application/x-$service-advertisement"
            gitHttp.advertise(project, service, response.outputStream)
        } else {
            // v1.6 `GitApp.service`는 `isMaxSizeExceeded()`가 참이면 413이지만, Play가 이 요청(`application/x-git-*-request`)을 원시 본문으로 받아
            // 한도가 사실상 없다. 공식 v1.16.0에 130MB를 푸시해 성공을 확인했다. 폼 본문의 한도(`parsers.text.maxLength`)와 다르다.
            val raw = request.inputStream
            recentVisits.visits(user, project)
            response.contentType = "application/x-$service-result"
            val input = if ("gzip".equals(request.getHeader("Content-Encoding"), ignoreCase = true)) GZIPInputStream(raw) else raw
            gitHttp.rpc(project, service, input, response.outputStream, user)
        }
        // 응답 본문을 직접 썼으므로 더 쓸 것이 없다.
        return null
    }

    /** 복제는 읽기, 푸시는 수정 권한이다. 코드를 구성원에게만 여는 프로젝트는 구성원이 아니면 수정 권한을 요구한다. */
    private fun isAllowed(project: Project, user: User?, service: String): Boolean {
        var operation = if (service == GitHttpService.UPLOAD_PACK) Operation.READ else Operation.UPDATE
        if (project.isCodeAccessibleMemberOnly == true && !projectService.hasMember(project, user)) operation = Operation.UPDATE
        return accessControl.isAllowed(user, ProjectResource(requireNotNull(project.id).toString(), ResourceType.CODE, project), operation)
    }

    /**
     * 로그인한 사용자의 권한 없음 응답이다. `git.error.permission`은 요청의 언어(`Accept-Language`)로, `git.error.notAllowedCharset`은
     * 서버 JVM의 기본 로케일로 적는다 (v1.6 `GitApp`: 앞은 `Messages.get(키, …)`, 뒤는 `Messages.get(Lang.defaultLang(), 키, …)`).
     */
    private fun forbidden(user: User, owner: String, project: String, userAgent: String?): ResponseEntity<String> {
        return if (isCharsetAllowed(userAgent)) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).header("Content-Type", "text/plain;charset=UTF-8")
                .body(messages.getMessage("git.error.permission", arrayOf<Any>(user.loginId.orEmpty(), owner, project), org.springframework.context.i18n.LocaleContextHolder.getLocale()))
        } else {
            ResponseEntity.status(HttpStatus.FORBIDDEN).header("Content-Type", "text/plain")
                .body(messages.getMessage("git.error.notAllowedCharset", arrayOf<Any>(user.loginId.orEmpty(), owner, project), Locale.getDefault()))
        }
    }

    /** Git 2.1.0부터 Content-Type에 문자 집합을 허용한다. `User-Agent`의 버전이 올바른 세 자리 버전이 아니면 허용하지 않는다 (v1.6 `isCharsetAllowed`). */
    private fun isCharsetAllowed(userAgent: String?): Boolean {
        if (userAgent == null) return false
        // v1.6은 `java-semver`의 관대한 파서로 읽고, 읽지 못하면 거짓이다
        val version = LenientVersion.parse(semverize(userAgent.substringAfter('/'))) ?: return false
        return version >= LenientVersion.of(2, 1, 0)
    }

    /** v1.6 `Config.semverize`: `v` 접두어 제거, `.pre` 같은 문자 단계는 `-pre`로, 두 자리는 세 자리로 맞춘다. */
    private fun semverize(version: String): String {
        var v = version.replaceFirst(Regex("^v"), "")
        v = v.replace(Regex("\\.([^\\d]+)"), "-$1")
        return v.replaceFirst(Regex("^(\\d+\\.\\d+)($|-)"), "$1.0$2")
    }

    private fun text(status: HttpStatus, body: String): ResponseEntity<String> =
        ResponseEntity.status(status).header("Content-Type", "text/plain;charset=UTF-8").body(body)

    private companion object {
    }
}
