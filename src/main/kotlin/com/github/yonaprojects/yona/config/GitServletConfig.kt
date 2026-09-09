package com.github.yonaprojects.yona.config

import com.github.yonaprojects.yona.config.git.GitProjectVisitRecorder
import com.github.yonaprojects.yona.domain.branchprotection.ProtectedBranchRepository
import com.github.yonaprojects.yona.domain.gpgkey.GpgSignatureVerifier
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.BranchProtectionPreReceiveHook
import com.github.yonaprojects.yona.domain.vcs.PushedBranchRepository
import com.github.yonaprojects.yona.domain.vcs.RejectPushToReservedRefsPreReceiveHook
import com.github.yonaprojects.yona.domain.vcs.YonaPostReceiveHook
import io.micrometer.core.instrument.MeterRegistry
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.http.server.GitServlet
import org.eclipse.jgit.lfs.server.LfsProtocolServlet
import org.eclipse.jgit.lfs.server.LargeFileRepository
import org.eclipse.jgit.lfs.server.fs.FileLfsRepository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.PreReceiveHook
import org.eclipse.jgit.transport.PreReceiveHookChain
import org.eclipse.jgit.transport.ReceivePack
import org.eclipse.jgit.transport.resolver.ReceivePackFactory
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.servlet.ServletRegistrationBean
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.io.File
import java.util.regex.Pattern
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

@Configuration
class GitServletConfig(
    @Value("\${yona.git.base-dir:/tmp/yona/git}")
    private val baseDir: String,
    @Value("\${yona.lfs.base-dir:/tmp/yona/lfs}")
    private val lfsBaseDir: String,
    @Value("\${yona.lfs.url:http://localhost:8080/git-lfs}")
    private val lfsUrl: String,
    private val projectRepository: ProjectRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val userRepository: UserRepository,
    private val pushedBranchRepository: PushedBranchRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val gitProjectVisitRecorder: GitProjectVisitRecorder,
    private val meterRegistry: MeterRegistry,
    // BranchProtectionPreReceiveHook 구성에 필요.
    private val protectedBranchRepository: ProtectedBranchRepository,
    private val projectUserRepository: ProjectUserRepository,
    // BranchProtectionPreReceiveHook이 require_signed_commits를 실제로 검사하는 데 필요.
    private val gpgSignatureVerifier: GpgSignatureVerifier,
    // 위키(P3-42) 저장소를 git clone/push로 처음 접근할 때(웹 UI로 페이지를 한 번도 저장하지
    // 않은 상태) 지연 초기화하는 데 쓴다 — WikiServiceImpl이 첫 페이지 저장 시 만드는 것과 동일한
    // 기본 브랜치로 맞춘다. 기존 포지셔널 생성자 호출부(GitServletConfigSpec 등)를 깨지 않도록
    // 맨 뒤에 둔다.
    // Kotlin 기본값을 주면(= "main") 컴파일러가 synthetic 마스크 생성자를 추가로 만들어 이
    // 클래스에 생성자가 2개가 되고, Spring의 @Configuration CGLIB 프록시 생성자 해석이
    // 어느 쪽을 써야 할지 못 찾아 "NoSuchMethodException: <init>()"로 컨텍스트 로딩이 깨진다
    // (직접 겪은 회귀 — 다른 파라미터들처럼 기본값 없이 필수 인자로 유지).
    @Value("\${yona.git.default-branch:main}")
    private val gitDefaultBranch: String
) {
    private val logger = LoggerFactory.getLogger(GitServletConfig::class.java)

    // GitAuthorizationFilter의 URI 패턴과 동일하게 맞춰, 동일한 요청에서
    // 인가 필터와 push 훅이 같은 프로젝트를 가리키도록 보장한다.
    private val gitUriPattern = Pattern.compile("^/(git|git-lfs)/([^/]+)/([^/]+?)(?:\\.git)?(?:/.*)?$")

    @Bean
    fun gitServletRegistrationBean(): ServletRegistrationBean<HttpServlet> {
        val gitBaseDir = File(baseDir)
        if (!gitBaseDir.exists()) {
            gitBaseDir.mkdirs()
        }

        val gitServlet = GitServlet().apply {
            setRepositoryResolver { _, name ->
                // 이 코드베이스 전역에서 물리 bare 저장소는
                // 항상 "owner/name.git"로 생성된다(GitServiceImpl.createRepository(),
                // GitRepository.create() 등). 그런데 이 리졸버는 JGit이 URL에서 파싱해 넘겨주는
                // name을 가공 없이 그대로 파일 경로로 썼다 — 클라이언트가 ".git" 접미어 없이
                // clone/push URL을 쓰면(예: "git clone http://host/git/owner/repo", GitHub 등에서
                // 흔한 축약 표기) 존재하지 않는 "owner/repo" 경로로 잘못 resolve됐다. clone(읽기)은
                // 존재하지 않는 저장소를 빈 저장소처럼 조용히 취급해 성공한 것처럼 보이지만, push
                // (쓰기)는 ObjectDirectoryPackParser.parse()가 그 존재하지 않는 objects
                // 디렉터리에 임시 팩 파일을 만들려다 IOException을 던져 "unpacker error"로
                // 거절됐다(GitSmartHttpProtocolIntegrationSpec에 회귀 테스트 고정). ".git" 접미어를
                // 정규화해 항상 같은 경로로 resolve한다.
                val normalizedName = if (name.endsWith(".git")) name else "$name.git"
                val repoFile = File(gitBaseDir, normalizedName)

                // 위키 저장소(P3-42, "<owner>/<project>.wiki.git")는 웹 UI에서 첫 페이지를 저장할
                // 때만 만들어진다(WikiServiceImpl.ensureRepository()) — 아직 페이지를 하나도 만들지
                // 않은 상태에서 "git clone"/"git push"로 먼저 접근하면(요구사항 5번, 로컬에서 직접
                // 편집) 저장소 자체가 없어 push가 "unpacker error"로 거절된다(위 normalizedName
                // 정규화 배경 주석 참고 — 존재하지 않는 디렉터리는 clone은 조용히 빈 저장소처럼
                // 성공하지만 push는 실패). 실제로 그런 이름의 프로젝트가 있을 때만(임의 경로로
                // 빈 저장소를 마구 생성하지 않도록) 빈 bare 저장소를 지연 생성해 이 문제를 없앤다.
                val nameWithoutExt = normalizedName.removeSuffix(".git")
                if (nameWithoutExt.endsWith(".wiki") && !repoFile.exists()) {
                    val segments = nameWithoutExt.split("/")
                    if (segments.size == 2) {
                        val ownerSegment = segments[0]
                        val projectSegment = segments[1].removeSuffix(".wiki")
                        val projectExists = projectRepository.findByOwnerAndNameOrPreviousPlace(ownerSegment, projectSegment).isPresent
                        if (projectExists) {
                            repoFile.parentFile?.mkdirs()
                            Git.init().setDirectory(repoFile).setBare(true).setInitialBranch(gitDefaultBranch).call().close()
                        }
                    }
                }

                val builder = FileRepositoryBuilder()
                builder.setGitDir(repoFile).build()
            }
            setReceivePackFactory(ReceivePackFactory { req, repo ->
                val receivePack = ReceivePack(repo)

                val project = resolveProject(req)
                val pusher = resolveCurrentUser()

                // refs/yobi/* 예약 ref 거부(항상 적용)에 이어
                // 브랜치 보호 규칙 검사를 체이닝한다. project를 못 찾으면(레포 해석 실패 등, 드묾)
                // 브랜치 보호 규칙을 조회할 대상 자체가 없으므로 예약 ref 거부만 적용한다. pusher는
                // null(익명 push)이어도 BranchProtectionPreReceiveHook 자체는 동작해야 한다 —
                // restrict_push_to가 설정된 브랜치는 익명 push도 당연히 거부돼야 하기 때문이다.
                val preReceiveHooks = mutableListOf<PreReceiveHook>(RejectPushToReservedRefsPreReceiveHook())
                if (project != null) {
                    preReceiveHooks.add(
                        BranchProtectionPreReceiveHook(
                            project, pusher, protectedBranchRepository, projectUserRepository, gpgSignatureVerifier
                        )
                    )
                }
                receivePack.setPreReceiveHook(PreReceiveHookChain.newChain(preReceiveHooks))

                if (project != null && pusher != null) {
                    receivePack.setPostReceiveHook(
                        YonaPostReceiveHook(
                            project, pusher, projectRepository, pullRequestRepository, pushedBranchRepository, eventPublisher, meterRegistry
                        )
                    )
                } else {
                    logger.warn(
                        "git push post-receive hook skipped: project or pusher could not be resolved (path='${req.pathInfo}')"
                    )
                }
                receivePack
            })
        }

        val lfsServlet = object : LfsProtocolServlet() {
            override fun getLargeFileRepository(request: LfsRequest, path: String, action: String): LargeFileRepository {
                var cleanPath = path
                if (cleanPath.startsWith("/git/")) {
                    cleanPath = cleanPath.substring("/git/".length)
                } else if (cleanPath.startsWith("/")) {
                    cleanPath = cleanPath.substring(1)
                }
                
                // 뒤쪽 info/lfs/objects/batch 부분 제거
                val suffixIndex = cleanPath.indexOf("/info/lfs/")
                if (suffixIndex != -1) {
                    cleanPath = cleanPath.substring(0, suffixIndex)
                }
                
                val parts = cleanPath.split("/")
                val owner = parts.getOrNull(0) ?: "default"
                val project = parts.getOrNull(1) ?: "default"

                val projectLfsDir = File(lfsBaseDir, "$owner/$project")
                if (!projectLfsDir.exists()) {
                    projectLfsDir.mkdirs()
                }

                val projectLfsUrl = "$lfsUrl/$owner/$project"
                return FileLfsRepository(projectLfsUrl, projectLfsDir.toPath())
            }
        }

        // 단일 진입점 디스패처 서블릿 정의
        val dispatcherServlet = object : HttpServlet() {
            // 근본원인: gitServlet/lfsServlet은 컨테이너에 직접 등록되지 않고
            // 이 디스패처의 service()에서 수동으로 .service()만 호출돼 왔다. 그런데
            // GitServlet(JGit)은 MetaServlet을 상속하며, 내부 GitFilter가 URL 파이프라인
            // (upload-pack/receive-pack/info-refs 등)을 구성하는 시점이 바로 init(ServletConfig)다.
            // init()이 한 번도 호출되지 않으면 GitFilter의 bindings가 비어 있는 채로 남아,
            // 모든 요청이 매치 실패로 기본 체인(chain.doFilter)에 떨어져 조용히 404를 반환한다
            // (RepositoryResolver까지 도달하지도 못함 — 그래서 예외 스택트레이스가 안 남았다).
            // 컨테이너가 이 디스패처 서블릿 자체에 대해 보장하는 init(ServletConfig) 호출을
            // 그대로 위임해 gitServlet/lfsServlet도 정상적인 서블릿 생명주기를 타도록 한다.
            override fun init() {
                gitServlet.init(servletConfig)
                lfsServlet.init(servletConfig)
            }

            override fun service(req: HttpServletRequest, res: HttpServletResponse) {
                if (req.requestURI.contains("/info/lfs/")) {
                    lfsServlet.service(req, res)
                } else {
                    // yona GitApp.recordVisit() 대응 — git 프로토콜로만 접근하는 사용자도
                    // "최근 방문 프로젝트"에 기록되도록 실제 RPC 처리 전에 방문을 남긴다.
                    gitProjectVisitRecorder.recordIfApplicable(req)
                    gitServlet.service(req, res)
                }
            }
        }

        val registrationBean = ServletRegistrationBean<HttpServlet>(dispatcherServlet, "/git/*")
        registrationBean.setName("GitDispatcherServlet")
        return registrationBean
    }

    private fun resolveProject(req: HttpServletRequest): Project? {
        val matcher = gitUriPattern.matcher(req.requestURI)
        if (!matcher.matches()) {
            return null
        }
        val owner = matcher.group(2)
        val projectName = matcher.group(3)
        // yona GitApp.findByPreviousPlaceOf() 폴백 대응 — 프로젝트가
        // 이전/개명된 뒤에도 기존 git remote URL이 계속 동작해야 한다.
        return projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
    }

    private fun resolveCurrentUser(): User? {
        val authentication = SecurityContextHolder.getContext().authentication ?: return null
        if (!authentication.isAuthenticated ||
            authentication is AnonymousAuthenticationToken
        ) {
            return null
        }
        return userRepository.findByLoginId(authentication.name).orElse(null)
    }
}
