package dev.yona.web.git

import dev.yona.domain.project.ProjectService
import dev.yona.domain.project.RecentVisitService
import dev.yona.repository.GitHttpService
import dev.yona.web.access.VcsServingGate
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import java.util.zip.GZIPInputStream

/**
 * Git 클라이언트(`git clone`, `fetch`, `push`)가 쓰는 smart-HTTP 주소이다. v1.6 `GitApp`(`app/controllers/GitApp.java`)에 해당한다.
 *
 * 인증은 HTTP Basic(`loginId:토큰`)이다. 비밀번호 칸에는 개인용 API 토큰만 받는다(D-70). 자격이 없거나 틀리면 익명으로 본다. 익명이 권한이 없으면 `401`로 로그인을 요구하고
 * (`WWW-Authenticate: Basic realm="Yona"`), 로그인한 사용자가 권한이 없으면 `403`과 안내 문구이다.
 * 복제와 가져오기는 읽기 권한, 푸시는 수정 권한이 필요하다. 코드를 구성원에게만 여는 프로젝트는 읽기도 수정 권한을 요구한다.
 * 프로젝트 이름이 바뀌었으면 이전 이름으로도 찾는다.
 */
@Controller
@ResponseBody
class GitHttpController(
    private val projectService: ProjectService,
    private val gate: VcsServingGate,
    private val gitHttp: GitHttpService,
    private val recentVisits: RecentVisitService,
    private val properties: dev.yona.config.YonaProperties,
    private val wikiService: dev.yona.domain.wiki.WikiService,
    private val metrics: dev.yona.observability.VcsMetrics
) {

    /** 서비스(`service=git-upload-pack` 또는 `git-receive-pack`)가 광고하는 ref 목록이다. */
    @GetMapping("/{ownerName}/{project}/info/refs")
    fun advertise(
        @PathVariable ownerName: String, @PathVariable("project") projectName: String, @RequestParam(required = false) service: String?,
        request: HttpServletRequest, response: HttpServletResponse
    ): Any? {
        // v1.6은 BasicAuthAction이 가장 바깥이라 이후의 403, 404 응답에도 세션 쿠키가 실린다
        val identity = when (val entry = gate.enter("git", request, response)) {
            is VcsServingGate.Entry.Passed -> entry.identity
            is VcsServingGate.Entry.Stopped -> return entry.response
        }
        // 서비스를 정하지 않으면 getanyfile 서비스이다. 지원하지 않는다.
        if (service == null) return text(HttpStatus.FORBIDDEN, "Unsupported service: getanyfile")
        // 서비스 이름은 요청 매개변수라 지표 태그로 쓰지 않고 `info_refs`로 고정한다(카디널리티)
        return measured("info_refs", response) { handle(ownerName, projectName, service, advertise = true, identity, request, response, it) }
    }

    /** 복제나 가져오기, 푸시의 요청 본문을 처리한다. */
    @PostMapping("/{ownerName}/{project}/{service:git-upload-pack|git-receive-pack}")
    fun rpc(
        @PathVariable ownerName: String, @PathVariable("project") projectName: String, @PathVariable service: String,
        request: HttpServletRequest, response: HttpServletResponse
    ): Any? {
        val identity = when (val entry = gate.enter("git", request, response)) {
            is VcsServingGate.Entry.Passed -> entry.identity
            is VcsServingGate.Entry.Stopped -> return entry.response
        }
        return measured(service.removePrefix("git-").replace('-', '_'), response) { handle(ownerName, projectName, service, advertise = false, identity, request, response, it) }
    }

    /**
     * 저장소 서비스 요청의 시간과 결과를 지표로 남긴다(X-10 C5c). 결과는 응답 상태(`ResponseEntity`이면 그 상태)에서 정하고, 본문을 직접 쓴 경우에도 처리 중
     * `IOException`이 있었으면 `error`이다. 위키 저장소이면 대상(`target`)을 `wiki`로 바꾼다.
     */
    private fun measured(service: String, response: HttpServletResponse, block: (dev.yona.observability.VcsMetrics.Request) -> Any?): Any? {
        var out: Any? = null
        metrics.request("git", service) { req ->
            out = block(req)
            if (req.result == dev.yona.observability.VcsMetrics.OK) req.result = dev.yona.observability.VcsMetrics.resultOf((out as? ResponseEntity<*>)?.statusCode?.value() ?: response.status)
        }
        return out
    }

    private fun handle(
        ownerName: String, projectName: String, service: String, advertise: Boolean, identity: dev.yona.web.access.BasicIdentity, request: HttpServletRequest, response: HttpServletResponse,
        req: dev.yona.observability.VcsMetrics.Request
    ): Any? {
        val user = identity.user
        if (!GitHttpService.isSupported(service)) return text(HttpStatus.FORBIDDEN, "Unsupported service: '$service'")
        val project = projectService.findByOwnerAndProjectName(ownerName, projectName)
            ?: return handleWiki(ownerName, projectName, service, advertise, identity, request, response, req)
        if (project.vcs != "GIT") return ResponseEntity.notFound().build<Void>()
        // 토큰 범위와 권한은 공통 앞단이 확인한다. 안내 문구에는 요청한 이름을 적는다(프로젝트 이름이 바뀐 뒤 이전 주소로 들어왔을 수 있다)
        gate.admit(project, identity, VcsServingGate.Access(write = service != GitHttpService.UPLOAD_PACK), "git", request, response, ownerName, projectName)?.let { return it }

        if (advertise) {
            response.contentType = "application/x-$service-advertisement"
            gitHttp.advertise(project, service, metrics.countedOutput("git", "code", response.outputStream))
        } else {
            // v1.6 `GitApp.service`는 `isMaxSizeExceeded()`가 참이면 413이지만, Play가 이 요청(`application/x-git-*-request`)을 원시 본문으로 받아
            // 한도가 사실상 없다. 공식 v1.16.0에 130MB를 푸시해 성공을 확인했다. 폼 본문의 한도(`parsers.text.maxLength`)와 다르다.
            val raw = request.inputStream
            recentVisits.visits(user, project)
            response.contentType = "application/x-$service-result"
            // 클라이언트가 보낸 바이트(압축을 푼 뒤)와 서버가 쓴 바이트를 센다
            val input = metrics.countedInput("git", "code", if ("gzip".equals(request.getHeader("Content-Encoding"), ignoreCase = true)) GZIPInputStream(raw) else raw)
            if (!gitHttp.rpc(project, service, input, metrics.countedOutput("git", "code", response.outputStream), user)) req.result = "error"
        }
        // 응답 본문을 직접 썼으므로 더 쓸 것이 없다.
        return null
    }

    /**
     * 프로젝트 위키 저장소의 복제와 푸시이다(X-17 W7, D-60). 주소는 Forgejo 방식으로 `<이름>.wiki.git`이고 코드 저장소 주소처럼 `.git` 없는 `<이름>.wiki`도 받는다.
     * 같은 이름의 프로젝트가 있으면 코드 저장소가 우선이라 이 함수에 오지 않는다. 복제는 프로젝트 읽기 권한, 푸시는 구성원(`WIKI_PAGE`의 `UPDATE`)이며, 코드를 구성원에게만 여는
     * 설정은 위키에 적용하지 않는다. 프로젝트의 VCS와 무관하게 항상 git이다.
     */
    private fun handleWiki(
        ownerName: String, repositoryName: String, service: String, advertise: Boolean, identity: dev.yona.web.access.BasicIdentity, request: HttpServletRequest, response: HttpServletResponse,
        req: dev.yona.observability.VcsMetrics.Request
    ): Any? {
        val baseName = WIKI_SUFFIXES.firstOrNull { repositoryName.endsWith(it) }?.let { repositoryName.removeSuffix(it) }?.takeIf { it.isNotEmpty() }
            ?: return ResponseEntity.notFound().build<Void>()
        val project = projectService.findByOwnerAndProjectName(ownerName, baseName) ?: return ResponseEntity.notFound().build<Void>()
        // 위키를 끈 프로젝트는 저장소가 있어도 없는 것처럼 404이다(D-61)
        if (!wikiService.enabled(project)) return ResponseEntity.notFound().build<Void>()
        req.target = "wiki"
        gate.admit(project, identity, VcsServingGate.Access(write = service != GitHttpService.UPLOAD_PACK, wiki = true), "git", request, response, ownerName, repositoryName)?.let { return it }
        if (!wikiService.available(project)) return text(HttpStatus.CONFLICT, "The wiki of this project is not available")
        if (advertise) {
            response.contentType = "application/x-$service-advertisement"
            gitHttp.advertiseWiki(project, service, metrics.countedOutput("git", "wiki", response.outputStream))
        } else {
            val raw = request.inputStream
            response.contentType = "application/x-$service-result"
            val input = metrics.countedInput("git", "wiki", if ("gzip".equals(request.getHeader("Content-Encoding"), ignoreCase = true)) GZIPInputStream(raw) else raw)
            if (!gitHttp.rpcWiki(project, service, input, metrics.countedOutput("git", "wiki", response.outputStream))) req.result = "error"
        }
        return null
    }

    private fun text(status: HttpStatus, body: String): ResponseEntity<String> =
        ResponseEntity.status(status).header("Content-Type", "text/plain;charset=UTF-8").body(body)

    private companion object {
        /** 위키 저장소 주소의 끝이다. 긴 쪽을 먼저 본다. */
        val WIKI_SUFFIXES = listOf(".wiki.git", ".wiki")
    }
}
