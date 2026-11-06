package dev.yona.web.hg

import dev.yona.domain.project.ProjectService
import dev.yona.domain.project.RecentVisitService
import dev.yona.observability.VcsMetrics
import dev.yona.repository.hg.HgHooks
import dev.yona.repository.hg.HgRepositories
import dev.yona.repository.hg.HgRevisions
import dev.yona.web.access.BasicIdentity
import dev.yona.web.access.VcsServingGate
import io.github.search5.hg4j.transport.HgHttpWireServer
import io.github.search5.hg4j.transport.wireprotov1.Wire1Commands
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Mercurial 클라이언트(`hg clone`, `pull`, `push`)가 쓰는 HTTP 주소이다(X-7 6단계, D-86). 주소는 프로젝트 주소(`/소유자/이름`) 그대로이고 명령은 `?cmd=`
 * 쿼리로 온다(Mercurial 와이어 프로토콜 v1).
 *
 * 프로젝트 홈 화면(`GET /소유자/이름`)과 같은 경로라 스프링 라우트로 두면 `cmd`가 없는 모든 요청에서 매개변수 조건 오류(500)가 나므로, **필터**로 둔다.
 * `cmd`가 있는 두 단계 경로이고 그 프로젝트가 Mercurial일 때만 가로채고, 나머지(Git과 Subversion 프로젝트, 없는 프로젝트, 다른 경로)는 v1.6처럼
 * 아무 일 없이 다음 처리로 넘긴다. 이 필터는 보안 필터 뒤에서 돈다.
 *
 * 프로토콜 처리는 hg4j의 [HgHttpWireServer]가 한다. 이 필터는 Git의 [dev.yona.web.git.GitHttpController]처럼 프로토콜 앞에서 누가 어떤 저장소에 접근할 수
 * 있는지를 공통 앞단([VcsServingGate])으로 정한다: HTTP Basic 인증(비밀번호 칸에는 개인용 API 토큰만, D-70), 토큰 범위, 읽기 권한(복제, 가져오기)과 수정
 * 권한(푸시), 코드를 구성원에게만 여는 프로젝트의 읽기 승격, 익명 `401`과 로그인한 사용자 `403`. Mercurial 클라이언트에는 Git 2.1.0 같은 문자 집합
 * 제약이 없어 `403` 안내는 항상 UTF-8이다.
 */
@Component
class HgHttpFilter(
    private val projectService: ProjectService,
    private val gate: VcsServingGate,
    private val hgRepositories: HgRepositories,
    private val hooks: HgHooks,
    private val recentVisits: RecentVisitService,
    private val metrics: VcsMetrics
) : OncePerRequestFilter() {

    /**
     * `cmd`가 없거나 주소가 두 단계가 아니면 건드리지 않는다. 쿼리 문자열은 직접 훑는다: 컨테이너의 매개변수 해석은 퍼센트 인코딩이 잘못되면 예외를 던지는데,
     * 그런 요청은 건드리지 않고 넘겨 [dev.yona.web.LenientParametersFilter]와 기존 처리(400)가 그대로 답하게 한다.
     */
    override fun shouldNotFilter(request: HttpServletRequest): Boolean = commandOf(request) == null || segments(request) == null

    /** 쿼리 문자열에서 `cmd`의 값이다. 없으면 `null`이다. 값은 와이어 프로토콜 명령 이름이라 퍼센트 해석 없이 그대로 읽는다. */
    private fun commandOf(request: HttpServletRequest): String? =
        request.queryString?.split('&')?.firstOrNull { it.startsWith("cmd=") }?.substring(4)

    /** 컨텍스트 경로를 뺀 요청 경로가 `소유자/이름`(끝 슬래시는 있어도 된다)이면 그 둘이고 아니면 `null`이다. */
    private fun segments(request: HttpServletRequest): Pair<String, String>? {
        val path = request.requestURI.removePrefix(request.contextPath.orEmpty()).trim('/')
        val parts = path.split('/')
        if (parts.size != 2 || parts.any { it.isEmpty() }) return null
        // 경로의 퍼센트 인코딩이 잘못되면 이 필터의 일이 아니다
        return try { java.net.URLDecoder.decode(parts[0], Charsets.UTF_8) to java.net.URLDecoder.decode(parts[1], Charsets.UTF_8) } catch (e: IllegalArgumentException) { null }
    }

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val (owner, name) = requireNotNull(segments(request))
        // Mercurial 프로젝트가 아니면 이 필터의 일이 아니다. 인증을 시작하기 전에 넘겨야 다른 종류의 요청에 영향이 없다
        val project = projectService.findByOwnerAndProjectName(owner, name)?.takeIf { it.vcs == "Mercurial" }
        if (project == null) { chain.doFilter(request, response); return }
        // v1.6의 Git 서빙처럼 BasicAuth가 가장 바깥이라 이후의 403 응답에도 세션 쿠키가 실린다
        val identity = when (val entry = gate.enter("hg", request, response)) {
            is VcsServingGate.Entry.Passed -> entry.identity
            is VcsServingGate.Entry.Stopped -> { write(entry.response, response); return }
        }
        val cmd = commandOf(request).orEmpty()
        // 명령 이름은 요청 매개변수라 지표 태그로는 알려진 명령만 쓴다(카디널리티)
        val service = if (Wire1Commands.isKnownCommand(cmd)) cmd else "other"
        metrics.request("hg", service) { measured ->
            serve(project, owner, name, cmd, identity, request, response)
            if (measured.result == VcsMetrics.OK) measured.result = VcsMetrics.resultOf(response.status)
        }
    }

    private fun serve(
        project: dev.yona.domain.project.Project, owner: String, name: String, cmd: String, identity: BasicIdentity, request: HttpServletRequest, response: HttpServletResponse
    ) {
        // 모르는 명령은 읽기로 본다. 서블릿이 400으로 답한다
        val write = Wire1Commands.isKnownCommand(cmd) && Wire1Commands.requiresPush(cmd)
        gate.admit(project, identity, VcsServingGate.Access(write = write), "hg", request, response, owner, name, charsetByUserAgent = false)?.let { write(it, response); return }
        if (cmd == "getbundle" || cmd == "unbundle") recentVisits.visits(identity.user, project)
        val hg = hgRepositories.open(owner, name)
        val session = hooks.session(project, identity.user)
        try {
            // 프로젝트의 읽기와 쓰기는 위 앞단이 정했으므로 서블릿의 `[web]` 규칙은 쓰지 않는다(설정 파일을 직접 고쳐 푸시를 허용하는 일을 막는다)
            session.attachTo(HgHttpWireServer(hg.repository).setEnforceWebPermissions(false)).service(request, response)
            // 푸시 뒤 처리는 요청이 끝난 뒤 한 번 한다(체인지그룹과 이름표 이동을 합쳐야 해서)
            if (write) session.finish(HgRevisions.changelog(hg))
        } finally {
            hg.close()
        }
    }

    /** 앞단이 돌려준 응답([ResponseEntity])을 서블릿 응답에 쓴다. */
    private fun write(entity: ResponseEntity<*>, response: HttpServletResponse) {
        response.status = entity.statusCode.value()
        entity.headers.forEach { key, values -> values.forEach { value -> response.addHeader(key, value) } }
        val body = when (val b = entity.body) { null -> null; is ByteArray -> b; else -> b.toString().toByteArray(Charsets.UTF_8) }
        if (body != null) { response.setContentLength(body.size); response.outputStream.write(body) }
    }
}
