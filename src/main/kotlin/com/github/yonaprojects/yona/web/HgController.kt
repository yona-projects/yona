package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.branchprotection.ProtectedBranchRepository
import com.github.yonaprojects.yona.domain.gpgkey.GpgSignatureVerifier
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.HgBranchProtectionPrePushkeyHook
import com.github.yonaprojects.yona.domain.vcs.HgYonaPostPushkeyHook
import com.github.yonaprojects.yona.domain.vcs.PushedBranchRepository
import io.github.search5.hg4j.api.HgHook
import io.github.search5.hg4j.lib.HgRepository
import io.github.search5.hg4j.transport.HgHttpWireServer
import io.micrometer.core.instrument.MeterRegistry
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.io.File
import java.util.concurrent.ConcurrentHashMap

// yona-wiki P3-12(Mercurial 지원) 2라운드 — hg4j의 HgHttpWireServer(JGit GitServlet에 대응하는
// 순수 HttpServlet, 실제 hg wire protocol v1 `?cmd=`/v2 `/api/` 를 그대로 처리)를 SvnController와
// 동일한 "프로젝트별 서블릿 인스턴스 캐시" 패턴으로 감싼다.
//
// SvnController와의 차이: SVNKit DAVServlet은 owner(SVNParentPath) 단위로 한 인스턴스면 그 밑의 여러
// 프로젝트를 다 서빙할 수 있지만, HgHttpWireServer는 생성자가 hg4j 자체의 `lib.HgRepository`
// 인스턴스 하나를 직접 받는 구조라(요청마다 "어느 저장소인지" 알려줄 방법이 없다) 물리 저장소 하나당
// 인스턴스 하나가 필요하다 — 그래서 캐시 키가 "$owner/$project"다. HgHttpWireServer.service()가
// 매 요청마다 `repository.refreshIfChangedOnDisk()`를 직접 호출해주므로, 이 컨트롤러가 디스크
// 변경사항을 감지해 재생성할 필요는 없다(SVN도 동일하게 DAVServlet을 한 번 만들면 재사용).
//
// OPTIONS 관련 별도 처리 불필요: SvnController는 WebDAV/DeltaV 클라이언트가 OPTIONS 응답의
// Allow/DAV 헤더로 서버 능력을 확인하기 때문에 Spring MVC의 기본 OPTIONS 가로채기를 우회해야
// 했지만, hg wire protocol은 순수 GET/POST(`?cmd=`) 기반이라 OPTIONS를 아예 쓰지 않는다
// (HgHttpWireServer.service()가 override하는 것도 GET/POST 둘뿐이다) — 확인 후 의도적으로 생략.
//
// push 인가는 이 컨트롤러가 아니라 HgAuthorizationFilter가 요청 단위로 이미 끝낸다: HTTP는
// SSH와 달리 "cmd=unbundle"(v1)/"/api/.../rw/..."(v2)로 요청 하나하나가 read/write를 URL·쿼리
// 시점에 명시적으로 드러내므로, git-receive-pack/PUT 요청을 GitServlet/DAVServlet 진입 전에
// GitAuthorizationFilter/SvnAuthorizationFilter가 걸러내는 것과 동일하게 필터 단계에서
// 멤버십/읽기전용 Deploy Key 검사가 끝난 뒤에만 이 컨트롤러가 호출된다 — 그래서
// HgSshProtocolHandler(SSH, 한 커넥션이 pull/push를 다 처리할 수 있어 명령줄만으로 미리 구분이
// 안 되므로 pre-changegroup 훅에서 강제)와 달리 여기는 registerPreChangegroupHook이 없어도 된다.
//
// yona-wiki P3-21/P3-22 — 브랜치 보호(require_pull_request 등)와 push 알림/웹훅/PushedBranch
// 추적을 hg4j에 새로 추가한 registerPrePushkeyHook/registerPostPushkeyHook(HgHttpWireServer)으로
// 연결한다. HgSshProtocolHandler(SSH)와 동일한 판정 로직(domain/vcs/HgPushHooks.kt)을 재사용해
// 두 경로가 정책 드리프트 없이 항상 동일하게 동작하도록 한다.
//
// 이 컨트롤러는 프로젝트당 HgHttpWireServer 인스턴스를 캐시해 재사용하므로(위 클래스 설명 참고),
// 훅 등록 자체는 이 인스턴스가 처음 만들어질 때 한 번만 일어난다 — 그런데 pusher(요청한 사용자)와
// project 엔티티(개명 가능)는 요청마다 달라질 수 있으므로, 등록하는 훅 람다 내부에서 매번 새로
// 조회한다(GitServletConfig가 요청마다 resolveProject()/resolveCurrentUser()를 다시 호출하는 것과
// 동일한 이유 — Hg 쪽은 요청 스레드가 그대로 HgHttpWireServer.service()→Wire1Commands.pushkey()→
// 이 훅까지 동기 호출로 이어지므로 SecurityContextHolder가 여전히 이 요청의 인증 정보를 담고 있다).
@RestController
class HgController(
    @Value("\${yona.hg.base-dir:/tmp/yona/hg}")
    private val baseDir: String,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val protectedBranchRepository: ProtectedBranchRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val gpgSignatureVerifier: GpgSignatureVerifier,
    private val pullRequestRepository: PullRequestRepository,
    private val pushedBranchRepository: PushedBranchRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(HgController::class.java)
    private val wireServerCache = ConcurrentHashMap<String, HgHttpWireServer>()

    @RequestMapping(value = ["/hg/{owner}/{project}", "/hg/{owner}/{project}/**"])
    fun service(
        @PathVariable owner: String,
        @PathVariable project: String,
        request: HttpServletRequest,
        response: HttpServletResponse
    ) {
        val key = "$owner/$project"
        val wireServer = wireServerCache.computeIfAbsent(key) {
            val repoDir = File(File(baseDir, owner), project)
            HgHttpWireServer(HgRepository(repoDir)).apply {
                registerPrePushkeyHook(
                    HgHook { context ->
                        val resolvedProject = resolveProject(owner, project) ?: return@HgHook true
                        val pusher = resolveCurrentUser()
                        HgBranchProtectionPrePushkeyHook(
                            resolvedProject, pusher, protectedBranchRepository, projectUserRepository, gpgSignatureVerifier
                        ).run(context)
                    }
                )
                registerPostPushkeyHook(
                    HgHook { context ->
                        val resolvedProject = resolveProject(owner, project) ?: return@HgHook true
                        val pusher = resolveCurrentUser() ?: return@HgHook true
                        HgYonaPostPushkeyHook(
                            resolvedProject, pusher, projectRepository, pullRequestRepository,
                            pushedBranchRepository, eventPublisher, meterRegistry
                        ).run(context)
                    }
                )
            }
        }

        val wrappedRequest = HgServletRequestWrapper(request, owner, project)

        // SvnController의 "catch (Exception e) { response.setStatus(500); ... }" 대응 — 저장소가
        // DB엔 존재해도 실제 디스크 경로가 없거나 손상된 경우 등 HgHttpWireServer 자체가 던지는
        // 예외를 잡아 스택트레이스가 그대로 노출되지 않게 하고 로그를 남긴다(HgHttpWireServer.service()
        // 자체에도 넓은 catch가 있어 대부분의 프로토콜 오류는 이미 "abort: ..." 응답으로 처리되지만,
        // 그 바깥에서 터질 수 있는 서블릿/IO 예외까지 방어한다).
        try {
            wireServer.service(wrappedRequest, response)
        } catch (e: Exception) {
            logger.error("Failed to process a Mercurial request: {}", request.requestURI, e)
            if (!response.isCommitted) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
            }
        }
    }

    // GitServletConfig.resolveProject()와 동일한 폴백(findByOwnerAndNameOrPreviousPlace) — 프로젝트가
    // 개명된 뒤에도 기존 clone/push URL이 계속 동작해야 한다.
    private fun resolveProject(owner: String, projectName: String) =
        projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)

    private fun resolveCurrentUser(): User? {
        val authentication = SecurityContextHolder.getContext().authentication ?: return null
        if (!authentication.isAuthenticated || authentication is AnonymousAuthenticationToken) {
            return null
        }
        return userRepository.findByLoginId(authentication.name).orElse(null)
    }
}
