package dev.yona.web.access

import dev.yona.access.AccessControl
import dev.yona.access.ProjectResource
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectService
import dev.yona.domain.user.User
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import java.util.Locale

/**
 * 저장소 HTTP 서빙(Git smart HTTP, 이어서 Mercurial)이 프로토콜 처리에 앞서 하는 일이다. v1.6 `GitApp`의 `BasicAuthAction`과 권한 검사에 해당한다.
 * 프로토콜(광고, 본문 처리, 내용 형식)은 종류별 컨트롤러가 맡고, 누가 어떤 저장소에 접근할 수 있는가는 이 앞단이 종류와 무관하게 정한다
 * (Mercurial 도입을 위한 공통화 4단계, D-86).
 *
 * 1. [enter]: HTTP Basic 자격을 읽고 사용자를 정한다. 형식이 틀리면 `400`, 같은 주소에서 인증에 연달아 실패해 막았으면 `429`이다.
 * 2. [admit]: 프로젝트를 찾은 뒤에 토큰 범위(`403`)와 권한(익명 `401`, 로그인한 사용자 `403`)을 확인한다. 복제와 가져오기는 읽기 권한, 푸시는 수정 권한이
 *    필요하고, 코드를 구성원에게만 여는 프로젝트는 구성원이 아니면 읽기도 수정 권한을 요구한다. 위키 저장소는 위키 페이지 권한을 보고 그 설정은 적용하지 않는다.
 *
 * 인증은 HTTP Basic(`loginId:토큰`)이다. 비밀번호 칸에는 개인용 API 토큰만 받는다(D-70).
 */
@Component
class VcsServingGate(
    private val basicAuth: BasicAuthResolver,
    private val accessControl: AccessControl,
    private val projectService: ProjectService,
    private val messages: MessageSource,
    private val metrics: dev.yona.observability.VcsMetrics
) {

    /** [enter]의 결과이다. 통과하면 식별한 사용자이고, 멈추면 그대로 돌려줄 응답이다. */
    sealed interface Entry {
        class Passed(val identity: BasicIdentity) : Entry
        class Stopped(val response: ResponseEntity<*>) : Entry
    }

    /**
     * 저장소에 접근하는 방식이다.
     *
     * @property write 푸시나 커밋이면 `true`, 복제와 가져오기는 `false`
     * @property wiki 프로젝트의 위키 저장소이면 `true`. 위키 페이지 권한을 보고 코드 열람 제한은 적용하지 않는다
     */
    class Access(val write: Boolean, val wiki: Boolean = false)

    /**
     * [request]의 HTTP Basic 자격으로 사용자를 정한다. v1.6은 BasicAuthAction이 가장 바깥이라 이후의 403, 404 응답에도 세션 쿠키가 실린다.
     *
     * @param vcsTag 추적 구간에 쓰는 종류 이름(`git`, `hg`)
     */
    fun enter(vcsTag: String, request: HttpServletRequest, response: HttpServletResponse): Entry {
        val credentials = BasicCredentials.parse(request)
        if (credentials === BasicCredentials.MALFORMED) return Entry.Stopped(malformed(response))
        val identity = metrics.span("vcs.authenticate", "vcs" to vcsTag) { basicAuth.identify(request, response, credentials) }
        if (identity.blocked) return Entry.Stopped(text(HttpStatus.TOO_MANY_REQUESTS, "Too many failed authentication attempts"))
        return Entry.Passed(identity)
    }

    /**
     * [identity]가 [project]의 저장소에 [access]로 접근할 수 있는지 확인한다. 통과하면 `null`이고, 아니면 그대로 돌려줄 응답이다(토큰 범위 `403`, 익명 `401`
     * 과 `WWW-Authenticate`, 로그인한 사용자 `403`과 안내 문구).
     *
     * @param vcsTag 추적 구간에 쓰는 종류 이름(`git`, `hg`)
     * @param ownerName `403` 안내에 적을 소유자 이름이다. 프로젝트 이름이 바뀐 뒤 이전 주소로 들어왔으면 요청한 이름을 그대로 적는다
     * @param repositoryName `403` 안내에 적을 저장소 이름이다. 위키 주소(`<이름>.wiki.git`)이면 요청한 이름이다
     * @param charsetByUserAgent `403` 안내를 `User-Agent`의 클라이언트 버전으로 문자 집합을 정해 낼지이다. Git은 2.1.0부터 `Content-Type`의 문자 집합을 허용해서
     *   그 이전 클라이언트에는 문자 집합 없는 안내를 낸다(v1.6). 그런 제약이 없는 종류는 `false`이면 항상 UTF-8이다
     */
    fun admit(
        project: Project, identity: BasicIdentity, access: Access, vcsTag: String, request: HttpServletRequest, response: HttpServletResponse,
        ownerName: String = project.owner.orEmpty(), repositoryName: String = project.name.orEmpty(), charsetByUserAgent: Boolean = true
    ): ResponseEntity<*>? {
        // 토큰으로 인증했으면 범위가 이 프로젝트의 저장소를 허용해야 한다(읽기는 `repo`나 `read:repo`, 공개 프로젝트는 `public_repo`, 푸시는 `repo`나 공개 프로젝트의 `public_repo`)
        if (!basicAuth.allowsRepository(identity, project, write = access.write)) return text(HttpStatus.FORBIDDEN, "The token scope does not allow repository access")
        val user = identity.user
        val tags = if (access.wiki) arrayOf("vcs" to vcsTag, "target" to "wiki") else arrayOf("vcs" to vcsTag)
        if (metrics.span("vcs.authorize", *tags) { isAllowed(project, user, access) }) return null
        if (user == null) {
            response.setHeader("WWW-Authenticate", "Basic realm=\"Yona\"")
            return text(HttpStatus.UNAUTHORIZED, unauthorizedMessage(identity))
        }
        return forbidden(user, ownerName, repositoryName, request.getHeader("User-Agent"), charsetByUserAgent)
    }

    /** 복제는 읽기, 푸시는 수정 권한이다. 코드를 구성원에게만 여는 프로젝트는 구성원이 아니면 수정 권한을 요구한다. 위키는 위키 페이지 권한이다. */
    private fun isAllowed(project: Project, user: User?, access: Access): Boolean {
        var operation = if (access.write) Operation.UPDATE else Operation.READ
        if (access.wiki) return accessControl.isAllowed(user, ProjectResource(requireNotNull(project.id).toString(), ResourceType.WIKI_PAGE, project), operation)
        if (project.isCodeAccessibleMemberOnly == true && !projectService.hasMember(project, user)) operation = Operation.UPDATE
        return accessControl.isAllowed(user, ProjectResource(requireNotNull(project.id).toString(), ResourceType.CODE, project), operation)
    }

    /** 401 본문이다. 비밀번호 칸에 토큰이 아닌 값을 보냈으면 토큰을 쓰라고 안내한다(D-70). */
    private fun unauthorizedMessage(identity: BasicIdentity) =
        if (identity.passwordRefused) BasicIdentity.PASSWORD_REFUSED_MESSAGE else "Invalid username or password"

    /**
     * 자격 형식이 틀리면 본문 없는 400이다. v1.6 `BasicAuthAction`은 기본 액션 밖에서 직접 `badRequest()`를 내므로 `Cache-Control`이 붙지 않는다.
     */
    private fun malformed(response: HttpServletResponse): ResponseEntity<Void> {
        response.setHeader("Cache-Control", null)
        return ResponseEntity.badRequest().build()
    }

    /**
     * 로그인한 사용자의 권한 없음 응답이다. `git.error.permission`은 요청의 언어(`Accept-Language`)로, `git.error.notAllowedCharset`은
     * 서버 JVM의 기본 로케일로 적는다 (v1.6 `GitApp`: 앞은 `Messages.get(키, …)`, 뒤는 `Messages.get(Lang.defaultLang(), 키, …)`).
     */
    private fun forbidden(user: User, owner: String, project: String, userAgent: String?, charsetByUserAgent: Boolean): ResponseEntity<String> {
        return if (!charsetByUserAgent || isCharsetAllowed(userAgent)) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).header("Content-Type", "text/plain;charset=UTF-8")
                .body(messages.getMessage("git.error.permission", arrayOf<Any>(user.loginId.orEmpty(), owner, project), LocaleContextHolder.getLocale()))
        } else {
            ResponseEntity.status(HttpStatus.FORBIDDEN).header("Content-Type", "text/plain")
                .body(messages.getMessage("git.error.notAllowedCharset", arrayOf<Any>(user.loginId.orEmpty(), owner, project), Locale.getDefault()))
        }
    }

    /** Git 2.1.0부터 Content-Type에 문자 집합을 허용한다. `User-Agent`의 버전이 올바른 세 자리 버전이 아니면 허용하지 않는다 (v1.6 `isCharsetAllowed`). */
    private fun isCharsetAllowed(userAgent: String?): Boolean {
        if (userAgent == null) return false
        // v1.6은 `java-semver`의 관대한 파서로 읽고, 읽지 못하면 거짓이다
        val version = dev.yona.util.LenientVersion.parse(semverize(userAgent.substringAfter('/'))) ?: return false
        return version >= dev.yona.util.LenientVersion.of(2, 1, 0)
    }

    /** v1.6 `Config.semverize`: `v` 접두어 제거, `.pre` 같은 문자 단계는 `-pre`로, 두 자리는 세 자리로 맞춘다. */
    private fun semverize(version: String): String {
        var v = version.replaceFirst(Regex("^v"), "")
        v = v.replace(Regex("\\.([^\\d]+)"), "-$1")
        return v.replaceFirst(Regex("^(\\d+\\.\\d+)($|-)"), "$1.0$2")
    }

    private fun text(status: HttpStatus, body: String): ResponseEntity<String> =
        ResponseEntity.status(status).header("Content-Type", "text/plain;charset=UTF-8").body(body)
}
