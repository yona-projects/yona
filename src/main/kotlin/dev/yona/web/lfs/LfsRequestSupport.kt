package dev.yona.web.lfs

import dev.yona.access.AccessControl
import dev.yona.access.ProjectResource
import dev.yona.access.asResource
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.lfs.LfsService
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectService
import dev.yona.web.access.BasicAuthResolver
import dev.yona.web.access.BasicCredentials
import dev.yona.web.access.BasicIdentity
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component

/**
 * LFS 경로들이 함께 쓰는 요청 처리이다(추가 개발 항목 X-8, `decisions.md` D-79). 신원 확인, 프로젝트 찾기, 권한 판단, LFS 규격의 응답(미디어 타입
 * `application/vnd.git-lfs+json`, 오류는 `{"message": …}`), 지표를 한 곳에 둔다. 권한은 git HTTP와 같다: 개인용 토큰만 받고(비밀번호는 거부),
 * 토큰 범위는 읽기 `repo`나 `read:repo`와 쓰기 `repo`, 프로젝트 권한은 코드 읽기와 푸시 권한이며 공개 프로젝트는 익명도 읽는다.
 */
@Component
class LfsRequestSupport(
    private val projectService: ProjectService,
    private val accessControl: AccessControl,
    private val basicAuth: BasicAuthResolver,
    private val lfs: LfsService,
    private val metrics: dev.yona.observability.VcsMetrics,
    private val tokens: dev.yona.domain.lfs.LfsTokens,
    private val users: dev.yona.domain.user.UserRepository
) {

    /** 요청이 가리키는 프로젝트와 신원이다. 실패하면 [failure]에 응답이 있다. */
    class Context(val project: Project?, val identity: BasicIdentity, val failure: ResponseEntity<*>?)

    /**
     * 신원을 확인하고 프로젝트를 찾고 [write] 권한을 확인한다. 프로젝트가 없거나 LFS를 지원하지 않거나 사이트 설정이 꺼져 있으면 404이다.
     * 주소 끝의 `.git`은 뗀다.
     */
    fun resolve(owner: String, rawProject: String, request: HttpServletRequest, response: HttpServletResponse, write: Boolean): Context {
        // SSH의 git-lfs-authenticate가 준 짧은 수명 토큰(`Bearer yona_lfs_…`)이다. 그 프로젝트와 동작에만 통하고, 사용자의 프로젝트 권한 검사는 그대로 한다
        request.getHeader(HttpHeaders.AUTHORIZATION)?.takeIf { it.startsWith("Bearer ${dev.yona.domain.lfs.LfsTokens.PREFIX}") }?.let { return resolveBearer(owner, rawProject, it.removePrefix("Bearer "), write) }
        val credentials = BasicCredentials.parse(request)
        if (credentials === BasicCredentials.MALFORMED) return Context(null, BasicIdentity(null), error(HttpStatus.BAD_REQUEST, "Malformed credentials"))
        val identity = metrics.span("vcs.authenticate", "vcs" to "git_lfs") { basicAuth.identify(request, response, credentials) }
        if (identity.blocked) return Context(null, identity, error(HttpStatus.TOO_MANY_REQUESTS, "Too many failed authentication attempts"))
        val project = projectService.findByOwnerAndProjectName(owner, rawProject.removeSuffix(".git"))?.takeIf { lfs.available(it) }
            ?: return Context(null, identity, error(HttpStatus.NOT_FOUND, "Repository or object not found"))
        return Context(project, identity, authorize(identity, project, write))
    }

    private fun resolveBearer(owner: String, rawProject: String, token: String, write: Boolean): Context {
        val claims = tokens.verify(token) ?: return Context(null, BasicIdentity(null), error(HttpStatus.UNAUTHORIZED, "Invalid or expired LFS token", "WWW-Authenticate" to "Basic realm=\"Yobi\""))
        val project = projectService.findByOwnerAndProjectName(owner, rawProject.removeSuffix(".git"))?.takeIf { lfs.available(it) }
            ?: return Context(null, BasicIdentity(null), error(HttpStatus.NOT_FOUND, "Repository or object not found"))
        val user = users.findById(claims.userId).orElse(null)
        if (project.id != claims.projectId || user == null || (write && !claims.write)) return Context(null, BasicIdentity(user), error(HttpStatus.FORBIDDEN, "The LFS token does not allow this request"))
        val identity = BasicIdentity(user)
        return Context(project, identity, authorize(identity, project, write))
    }

    /** 권한이 없으면 응답을 돌려준다. 인증이 없으면 401과 인증 요청, 로그인했는데 권한이 없으면 403이다. */
    fun authorize(identity: BasicIdentity, project: Project, write: Boolean): ResponseEntity<*>? {
        if (!basicAuth.allowsRepository(identity, project, write)) return error(HttpStatus.FORBIDDEN, "The token scope does not allow repository access")
        var operation = if (write) Operation.UPDATE else Operation.READ
        if (project.isCodeAccessibleMemberOnly == true && !projectService.hasMember(project, identity.user)) operation = Operation.UPDATE
        val allowed = accessControl.isAllowed(identity.user, ProjectResource(requireNotNull(project.id).toString(), ResourceType.CODE, project), operation)
        if (allowed) return null
        if (identity.user == null) {
            return error(HttpStatus.UNAUTHORIZED, if (identity.passwordRefused) BasicIdentity.PASSWORD_REFUSED_MESSAGE else "Invalid username or password", "WWW-Authenticate" to "Basic realm=\"Yobi\"")
        }
        return error(HttpStatus.FORBIDDEN, "You do not have permission for this repository")
    }

    /** [user]가 프로젝트 설정을 고칠 수 있는 관리자이면 `true`이다(잠금 강제 풀기의 조건). */
    fun isManager(user: dev.yona.domain.user.User?, project: Project): Boolean =
        user != null && accessControl.isAllowed(user, project.asResource(), Operation.UPDATE)

    fun lfsJson(status: HttpStatus, body: Any): ResponseEntity<Any> = ResponseEntity.status(status).contentType(LFS_JSON).body(body)

    fun error(status: HttpStatus, message: String, vararg headers: Pair<String, String>): ResponseEntity<Any> =
        ResponseEntity.status(status).contentType(LFS_JSON).headers(HttpHeaders().also { h -> headers.forEach { h.add(it.first, it.second) } }).body(mapOf("message" to message))

    /** 요청의 시간과 결과를 지표로 남긴다(`vcs=git_lfs`). */
    fun measured(service: String, block: () -> ResponseEntity<*>): ResponseEntity<*> {
        var out: ResponseEntity<*>? = null
        metrics.request("git_lfs", service, "lfs") { req ->
            out = block()
            req.result = dev.yona.observability.VcsMetrics.resultOf(out?.statusCode?.value() ?: 200)
        }
        return requireNotNull(out)
    }

    companion object {
        /** LFS 규격의 미디어 타입이다. */
        val LFS_JSON: MediaType = MediaType.parseMediaType("application/vnd.git-lfs+json")
    }
}
