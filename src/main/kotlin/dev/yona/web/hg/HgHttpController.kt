package dev.yona.web.hg

import dev.yona.domain.project.ProjectService
import dev.yona.domain.project.RecentVisitService
import dev.yona.repository.hg.HgRepositories
import dev.yona.web.access.VcsServingGate
import io.github.search5.hg4j.api.Hg
import io.github.search5.hg4j.transport.HgHttpWireServer
import io.github.search5.hg4j.transport.wireprotov1.Wire1Commands
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.ResponseBody

/**
 * Mercurial 클라이언트(`hg clone`, `pull`, `push`)가 쓰는 HTTP 주소이다(X-7 6단계, D-86). 주소는 프로젝트 주소(`/소유자/이름`) 그대로이고 명령은 `?cmd=`
 * 쿼리로 온다(Mercurial 와이어 프로토콜 v1). 프로젝트 홈 화면(`GET /소유자/이름`)과 같은 경로이지만 `cmd` 매개변수가 있는 요청만 이 컨트롤러가 받는다.
 *
 * 프로토콜 처리는 hg4j의 [HgHttpWireServer]가 한다. 이 컨트롤러는 Git의 [dev.yona.web.git.GitHttpController]처럼 프로토콜 앞에서 누가 어떤 저장소에
 * 접근할 수 있는지를 공통 앞단([VcsServingGate])으로 정한다: HTTP Basic 인증(비밀번호 칸에는 개인용 API 토큰만, D-70), 토큰 범위, 읽기 권한(복제, 가져오기)과
 * 수정 권한(푸시), 코드를 구성원에게만 여는 프로젝트의 읽기 승격, 익명 `401`과 로그인한 사용자 `403`. Mercurial 클라이언트에는 Git 2.1.0 같은 문자 집합 제약이
 * 없어 `403` 안내는 항상 UTF-8이다. 접근을 막지 않은 요청만 서블릿에 넘긴다.
 */
@Controller
@ResponseBody
class HgHttpController(
    private val projectService: ProjectService,
    private val gate: VcsServingGate,
    private val hgRepositories: HgRepositories,
    private val recentVisits: RecentVisitService,
    private val metrics: dev.yona.observability.VcsMetrics
) {

    /** 와이어 프로토콜 명령(`?cmd=`)을 처리한다. 주소 끝의 슬래시는 있어도 없어도 된다. */
    @RequestMapping("/{ownerName}/{project}", "/{ownerName}/{project}/", method = [RequestMethod.GET, RequestMethod.POST], params = ["cmd"])
    fun wire(
        @PathVariable ownerName: String, @PathVariable("project") projectName: String, request: HttpServletRequest, response: HttpServletResponse
    ): Any? {
        // v1.6의 Git 서빙처럼 BasicAuth가 가장 바깥이라 이후의 403, 404 응답에도 세션 쿠키가 실린다
        val identity = when (val entry = gate.enter("hg", request, response)) {
            is VcsServingGate.Entry.Passed -> entry.identity
            is VcsServingGate.Entry.Stopped -> return entry.response
        }
        val cmd = request.getParameter("cmd").orEmpty()
        // 명령 이름은 요청 매개변수라 지표 태그로는 알려진 명령만 쓴다(카디널리티)
        val service = if (Wire1Commands.isKnownCommand(cmd)) cmd else "other"
        var out: Any? = null
        metrics.request("hg", service) { req ->
            out = handle(ownerName, projectName, cmd, identity, request, response)
            if (req.result == dev.yona.observability.VcsMetrics.OK) req.result = dev.yona.observability.VcsMetrics.resultOf((out as? ResponseEntity<*>)?.statusCode?.value() ?: response.status)
        }
        return out
    }

    private fun handle(
        ownerName: String, projectName: String, cmd: String, identity: dev.yona.web.access.BasicIdentity, request: HttpServletRequest, response: HttpServletResponse
    ): Any? {
        val project = projectService.findByOwnerAndProjectName(ownerName, projectName) ?: return ResponseEntity.notFound().build<Void>()
        if (project.vcs != "Mercurial") return ResponseEntity.notFound().build<Void>()
        // 모르는 명령은 읽기로 본다. 서블릿이 400으로 답한다
        val write = Wire1Commands.isKnownCommand(cmd) && Wire1Commands.requiresPush(cmd)
        gate.admit(project, identity, VcsServingGate.Access(write = write), "hg", request, response, ownerName, projectName, charsetByUserAgent = false)?.let { return it }
        if (cmd == "getbundle" || cmd == "unbundle") recentVisits.visits(identity.user, project)
        val hg: Hg = hgRepositories.open(requireNotNull(project.owner), requireNotNull(project.name))
        try {
            // 프로젝트의 읽기와 쓰기는 위 앞단이 정했으므로 서블릿의 `[web]` 규칙은 쓰지 않는다(설정 파일을 직접 고쳐 푸시를 허용하는 일을 막는다)
            HgHttpWireServer(hg.repository).setEnforceWebPermissions(false).service(request, response)
        } finally {
            hg.close()
        }
        // 응답 본문을 서블릿이 직접 썼으므로 더 쓸 것이 없다
        return null
    }

}
