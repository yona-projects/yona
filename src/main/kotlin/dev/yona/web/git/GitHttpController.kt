package dev.yona.web.git

import dev.yona.access.AccessControl
import dev.yona.access.ProjectResource
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectService
import dev.yona.domain.project.RecentVisitService
import dev.yona.domain.user.User
import dev.yona.repository.GitHttpService
import dev.yona.security.CurrentUserProvider
import dev.yona.security.LocalAuthenticator
import dev.yona.security.LoginResult
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
    private val currentUserProvider: CurrentUserProvider,
    private val authenticator: LocalAuthenticator,
    private val gitHttp: GitHttpService,
    private val recentVisits: RecentVisitService,
    private val messages: MessageSource
) {

    /** 서비스(`service=git-upload-pack` 또는 `git-receive-pack`)가 광고하는 ref 목록이다. */
    @GetMapping("/{ownerName}/{project}/info/refs")
    fun advertise(
        @PathVariable ownerName: String, @PathVariable("project") projectName: String, @RequestParam(required = false) service: String?,
        request: HttpServletRequest, response: HttpServletResponse
    ): Any? {
        val credentials = parseCredentials(request)
        if (credentials === MALFORMED) return ResponseEntity.badRequest().build<Void>()
        // 서비스를 정하지 않으면 getanyfile 서비스이다. 지원하지 않는다.
        if (service == null) return text(HttpStatus.FORBIDDEN, "Unsupported service: getanyfile")
        return handle(ownerName, projectName, service, advertise = true, credentials, request, response)
    }

    /** 복제나 가져오기, 푸시의 요청 본문을 처리한다. */
    @PostMapping("/{ownerName}/{project}/{service:git-upload-pack|git-receive-pack}")
    fun rpc(
        @PathVariable ownerName: String, @PathVariable("project") projectName: String, @PathVariable service: String,
        request: HttpServletRequest, response: HttpServletResponse
    ): Any? {
        val credentials = parseCredentials(request)
        if (credentials === MALFORMED) return ResponseEntity.badRequest().build<Void>()
        return handle(ownerName, projectName, service, advertise = false, credentials, request, response)
    }

    private fun handle(
        ownerName: String, projectName: String, service: String, advertise: Boolean, credentials: Credentials?, request: HttpServletRequest, response: HttpServletResponse
    ): Any? {
        if (!GitHttpService.isSupported(service)) return text(HttpStatus.FORBIDDEN, "Unsupported service: '$service'")
        val project = projectService.findByOwnerAndProjectName(ownerName, projectName) ?: return ResponseEntity.notFound().build<Void>()
        if (project.vcs != "GIT") return ResponseEntity.notFound().build<Void>()

        val user = authenticate(credentials)
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
            user?.let { recentVisits.record(it, project) }
            response.contentType = "application/x-$service-result"
            val input = if ("gzip".equals(request.getHeader("Content-Encoding"), ignoreCase = true)) GZIPInputStream(request.inputStream) else request.inputStream
            gitHttp.rpc(project, service, input, response.outputStream)
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

    /** Basic 자격으로 사용자를 찾는다. 틀렸으면 `null`(익명)이다. Basic 헤더가 없으면 세션, 토큰, 쿠키로 식별한 현재 사용자이다. */
    private fun authenticate(credentials: Credentials?): User? {
        if (credentials != null) return (authenticator.authenticate(credentials.loginId, credentials.password) as? LoginResult.Success)?.user
        return currentUserProvider.get().user
    }

    private fun forbidden(user: User, owner: String, project: String, userAgent: String?): ResponseEntity<String> {
        val locale = Locale.getDefault()
        return if (isCharsetAllowed(userAgent)) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).header("Content-Type", "text/plain;charset=UTF-8")
                .body(messages.getMessage("git.error.permission", arrayOf<Any>(user.loginId.orEmpty(), owner, project), locale))
        } else {
            ResponseEntity.status(HttpStatus.FORBIDDEN).header("Content-Type", "text/plain")
                .body(messages.getMessage("git.error.notAllowedCharset", arrayOf<Any>(user.loginId.orEmpty(), owner, project), locale))
        }
    }

    /** Git 2.1.0부터 Content-Type에 문자 집합을 허용한다. `User-Agent`의 버전이 올바른 세 자리 버전이 아니면 허용하지 않는다 (v1.6 `isCharsetAllowed`). */
    private fun isCharsetAllowed(userAgent: String?): Boolean {
        if (userAgent == null) return false
        val match = SEMVER.matchEntire(semverize(userAgent.substringAfter('/'))) ?: return false
        val major = match.groupValues[1].toInt()
        val minor = match.groupValues[2].toInt()
        return major > 2 || (major == 2 && minor >= 1)
    }

    /** v1.6 `Config.semverize`: `v` 접두어 제거, `.pre` 같은 문자 단계는 `-pre`로, 두 자리는 세 자리로 맞춘다. */
    private fun semverize(version: String): String {
        var v = version.replaceFirst(Regex("^v"), "")
        v = v.replace(Regex("\\.([^\\d]+)"), "-$1")
        return v.replaceFirst(Regex("^(\\d+\\.\\d+)($|-)"), "$1.0$2")
    }

    private fun text(status: HttpStatus, body: String): ResponseEntity<String> =
        ResponseEntity.status(status).header("Content-Type", "text/plain;charset=UTF-8").body(body)

    // --- Basic 인증 ---

    private class Credentials(val loginId: String, val password: String)

    /**
     * `Authorization: Basic …`의 자격이다. Basic 헤더가 아니면 `null`(다른 방식으로 식별), 사용자와 비밀번호를 가르는 `:`가 없으면
     * [MALFORMED]이다. ISO-8859-1로 읽는다(v1.6 `BasicAuthAction`).
     */
    private fun parseCredentials(request: HttpServletRequest): Credentials? {
        val header = request.getHeader("Authorization") ?: return null
        if (!header.startsWith("Basic ")) return null
        val decoded = try { String(Base64.getDecoder().decode(header.substring(6).trim()), Charsets.ISO_8859_1) } catch (e: IllegalArgumentException) { return MALFORMED }
        val colon = decoded.indexOf(':')
        if (colon < 0) return MALFORMED
        return Credentials(decoded.substring(0, colon), decoded.substring(colon + 1))
    }

    private companion object {
        val SEMVER = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)(?:-[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$")
        val MALFORMED = Credentials("", "")
    }
}
