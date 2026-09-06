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
import com.github.yonaprojects.yona.domain.vcs.PushedBranchRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.*
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.springframework.context.ApplicationEventPublisher
import java.util.Optional
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl
import java.io.File
import jakarta.servlet.ServletConfig
import jakarta.servlet.ServletContext
import java.util.Enumeration

class GitServletConfigSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val pullRequestRepository = mockk<PullRequestRepository>()
    val userRepository = mockk<UserRepository>()
    val pushedBranchRepository = mockk<PushedBranchRepository>()
    val eventPublisher = mockk<ApplicationEventPublisher>()
    val gitProjectVisitRecorder = mockk<GitProjectVisitRecorder>(relaxed = true)
    // yona-wiki P3-04(브랜치 보호) — BranchProtectionPreReceiveHook 구성에 필요한 신규 의존성.
    // 이 스펙의 project 목(mock)들은 id를 설정하지 않으므로(project.id == null)
    // BranchProtectionPreReceiveHook.onPreReceive()가 조회 없이 즉시 반환해 실제로 호출되지
    // 않는다 — 그래도 GitServletConfig 생성자에는 값을 전달해야 하므로 mock만 준비한다.
    val protectedBranchRepository = mockk<ProtectedBranchRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    // yona-wiki P3-03/P3-04 연결 작업 — BranchProtectionPreReceiveHook 구성에 필요한 신규 의존성.
    val gpgSignatureVerifier = mockk<GpgSignatureVerifier>()

    val tempBaseDir = File.createTempFile("git-temp", "").apply { delete(); mkdirs() }
    val tempLfsBaseDir = File.createTempFile("lfs-temp", "").apply { delete(); mkdirs() }

    val config = GitServletConfig(
        tempBaseDir.absolutePath,
        tempLfsBaseDir.absolutePath,
        "http://localhost:8080/git-lfs",
        projectRepository,
        pullRequestRepository,
        userRepository,
        pushedBranchRepository,
        eventPublisher,
        gitProjectVisitRecorder,
        SimpleMeterRegistry(),
        protectedBranchRepository,
        projectUserRepository,
        gpgSignatureVerifier
    )

    beforeTest {
        clearMocks(projectRepository, pullRequestRepository, userRepository, pushedBranchRepository, eventPublisher, gitProjectVisitRecorder)
        SecurityContextHolder.clearContext()
    }

    describe("GitServletConfig 추가 커버리지 테스트") {
        it("ServletRegistrationBean 생성 및 service() 호출") {
            val bean = config.gitServletRegistrationBean()
            bean shouldNotBe null
            
            val servlet = bean.servlet!!
            
            val servletConfig = mockk<ServletConfig>(relaxed = true)
            val servletContext = mockk<ServletContext>(relaxed = true)
            every { servletConfig.servletContext } returns servletContext
            every { servletConfig.servletName } returns "git"
            every { servletConfig.initParameterNames } returns mockk<Enumeration<String>> {
                every { hasMoreElements() } returns false
            }
            servlet.init(servletConfig)

            // git URI 테스트
            val request = mockk<HttpServletRequest>(relaxed = true)
            val response = mockk<HttpServletResponse>(relaxed = true)
            every { request.requestURI } returns "/git/owner/project"
            every { request.method } returns "GET"
            
            try {
                servlet.service(request, response)
            } catch (e: Exception) {}
            
            verify { gitProjectVisitRecorder.recordIfApplicable(request) }
            
            // LFS URI 테스트 — 실제 LfsProtocolServlet.service()는 relaxed mock 요청으로는
            // 내부에서 예외 없이 완주하기 어려우므로(JSON 본문 파싱 등), 캡처된 $lfsServlet
            // 필드를 mock으로 교체해 디스패치 분기(line 126) 자체가 실행되는지만 확인한다.
            val lfsField = servlet.javaClass.getDeclaredField("\$lfsServlet")
            lfsField.isAccessible = true
            val mockLfsServlet = mockkClass(lfsField.type.kotlin, relaxed = true) as HttpServlet
            lfsField.set(servlet, mockLfsServlet)

            val lfsRequest = mockk<HttpServletRequest>(relaxed = true)
            val lfsResponse = mockk<HttpServletResponse>(relaxed = true)
            every { lfsRequest.requestURI } returns "/git/owner/project/info/lfs/objects/batch"
            every { lfsRequest.method } returns "POST"

            servlet.service(lfsRequest, lfsResponse)

            verify { mockLfsServlet.service(lfsRequest, lfsResponse) }
        }
        
        it("private 메서드 resolveProject, resolveCurrentUser 호출 커버리지") {
            val resolveProject = config.javaClass.getDeclaredMethod("resolveProject", HttpServletRequest::class.java)
            resolveProject.isAccessible = true
            
            val req1 = mockk<HttpServletRequest>(relaxed = true)
            every { req1.requestURI } returns "/invalid/url"
            resolveProject.invoke(config, req1)
            
            val req2 = mockk<HttpServletRequest>(relaxed = true)
            every { req2.requestURI } returns "/git/owner/projectName.git/info/refs"
            val project = Project(owner = "owner", name = "projectName", vcs = "GIT")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "projectName") } returns Optional.of(project)
            resolveProject.invoke(config, req2)
            
            val resolveCurrentUser = config.javaClass.getDeclaredMethod("resolveCurrentUser")
            resolveCurrentUser.isAccessible = true
            
            // 1
            SecurityContextHolder.getContext().authentication = null
            resolveCurrentUser.invoke(config)
            
            // 2
            val anonAuth = AnonymousAuthenticationToken("key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"))
            SecurityContextHolder.getContext().authentication = anonAuth
            resolveCurrentUser.invoke(config)
            
            // 3
            val notAuth = UsernamePasswordAuthenticationToken("user", "pass")
            notAuth.isAuthenticated = false
            SecurityContextHolder.getContext().authentication = notAuth
            resolveCurrentUser.invoke(config)
            
            // 4 - UsernamePasswordAuthenticationToken(principal, credentials) 2-인자 생성자는
            // authenticated=false로 초기화되므로, 3-인자(authorities 포함) 생성자로 실제
            // "인증됨 + 익명 아님" 분기(userRepository 조회까지 도달)를 태운다.
            val auth = UsernamePasswordAuthenticationToken("user", "pass", AuthorityUtils.createAuthorityList("ROLE_USER"))
            SecurityContextHolder.getContext().authentication = auth
            val user = User(loginId = "user")
            every { userRepository.findByLoginId("user") } returns Optional.of(user)
            val found = resolveCurrentUser.invoke(config)
            found shouldNotBe null
            
            // 5
            every { userRepository.findByLoginId("user") } returns Optional.empty()
            val notFound = resolveCurrentUser.invoke(config)
            notFound shouldBe null
        }

        it("gitServletRegistrationBean() - baseDir가 존재하지 않으면 새로 생성해야 한다") {
            val freshBaseDir = File.createTempFile("git-fresh", "").apply { delete() }
            val freshConfig = GitServletConfig(
                freshBaseDir.absolutePath, tempLfsBaseDir.absolutePath, "http://localhost:8080/git-lfs",
                projectRepository, pullRequestRepository, userRepository, pushedBranchRepository,
                eventPublisher, gitProjectVisitRecorder, SimpleMeterRegistry(),
                protectedBranchRepository, projectUserRepository, gpgSignatureVerifier
            )

            freshBaseDir.exists() shouldBe false
            freshConfig.gitServletRegistrationBean()
            freshBaseDir.exists() shouldBe true

            freshBaseDir.deleteRecursively()
        }

        it("ReceivePackFactory 람다 - project/pusher 조합별 post-receive hook 설정 분기") {
            val lambda = GitServletConfig::class.java.getDeclaredMethod(
                "gitServletRegistrationBean\$lambda\$0\$1",
                GitServletConfig::class.java, HttpServletRequest::class.java, Repository::class.java
            )
            lambda.isAccessible = true

            val bareRepoDir = File.createTempFile("bare-repo", "").apply { delete(); mkdirs() }
            val repo = FileRepositoryBuilder().setGitDir(bareRepoDir).setBare().build().apply { create(true) }

            // (1) project == null (URI가 gitUriPattern에 안 맞음) -> else 분기(경고 로그)
            val reqInvalid = mockk<HttpServletRequest>(relaxed = true)
            every { reqInvalid.requestURI } returns "/invalid"
            SecurityContextHolder.clearContext()
            val rp1 = lambda.invoke(null, config, reqInvalid, repo)
            rp1 shouldNotBe null

            // (2) project != null, pusher == null (인증 정보 없음) -> else 분기
            val reqValid = mockk<HttpServletRequest>(relaxed = true)
            every { reqValid.requestURI } returns "/git/owner/projectName.git/info/refs"
            val project = Project(owner = "owner", name = "projectName", vcs = "GIT")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "projectName") } returns Optional.of(project)
            SecurityContextHolder.clearContext()
            val rp2 = lambda.invoke(null, config, reqValid, repo)
            rp2 shouldNotBe null

            // (3) project != null, pusher != null -> if 분기(post-receive hook 설정)
            val authedUser = UsernamePasswordAuthenticationToken("user", "pass", AuthorityUtils.createAuthorityList("ROLE_USER"))
            SecurityContextHolder.getContext().authentication = authedUser
            every { userRepository.findByLoginId("user") } returns Optional.of(User(loginId = "user"))
            val rp3 = lambda.invoke(null, config, reqValid, repo)
            rp3 shouldNotBe null

            repo.close()
            bareRepoDir.deleteRecursively()
        }

        it("LfsProtocolServlet.getLargeFileRepository - path/action 파싱 및 디렉터리 생성 분기") {
            val bean = config.gitServletRegistrationBean()
            val dispatcherServlet = bean.servlet!!
            val lfsField = dispatcherServlet.javaClass.getDeclaredField("\$lfsServlet")
            lfsField.isAccessible = true
            val lfsServlet = lfsField.get(dispatcherServlet)

            val lfsRequestClass = Class.forName("org.eclipse.jgit.lfs.server.LfsProtocolServlet\$LfsRequest")
            val lfsRequestCtor = lfsRequestClass.getDeclaredConstructor().apply { isAccessible = true }
            val lfsRequest = lfsRequestCtor.newInstance()

            val method = lfsServlet.javaClass.getDeclaredMethod(
                "getLargeFileRepository", lfsRequestClass, String::class.java, String::class.java
            )
            method.isAccessible = true

            // "/git/" 접두어 제거 + info/lfs 접미어 제거 + 정상 owner/project 2-파트
            method.invoke(lfsServlet, lfsRequest, "/git/owner/project/info/lfs/objects/batch", "download") shouldNotBe null
            // 같은 owner/project 재호출 -> projectLfsDir가 이미 존재하는 분기(mkdirs 생략)
            method.invoke(lfsServlet, lfsRequest, "/git/owner/project/info/lfs/objects/batch", "download") shouldNotBe null
            // "/"로 시작(하지만 "/git/"는 아님) 케이스
            method.invoke(lfsServlet, lfsRequest, "/owner2/project2", "download") shouldNotBe null
            // "/"로 시작 안 하는 케이스(else, 그대로 사용) + info/lfs 접미어 없음
            method.invoke(lfsServlet, lfsRequest, "owner3/project3", "upload") shouldNotBe null
            // 파트가 부족해 owner/project 모두 기본값("default")으로 폴백
            method.invoke(lfsServlet, lfsRequest, "", "download") shouldNotBe null
        }

        it("저장소 리졸버 람다 - FileRepositoryBuilder로 Repository를 생성해야 한다") {
            val lambda = GitServletConfig::class.java.getDeclaredMethod(
                "gitServletRegistrationBean\$lambda\$0\$0",
                File::class.java, HttpServletRequest::class.java, String::class.java
            )
            lambda.isAccessible = true

            val gitBaseDir = File.createTempFile("resolver-base", "").apply { delete(); mkdirs() }
            val req = mockk<HttpServletRequest>(relaxed = true)

            val repo = lambda.invoke(null, gitBaseDir, req, "some-repo.git") as Repository
            repo shouldNotBe null
            repo.close()

            gitBaseDir.deleteRecursively()
        }

        // yona-wiki P3-02 12라운드(2026-09-01) 회귀 방지 — ".git" 접미어가 없는 name으로 호출해도
        // 접미어가 있는 name과 동일한 디렉터리로 resolve돼야 한다(실제 bare 저장소는 항상
        // "owner/name.git"로 생성되므로, 여기서만 접미어를 안 붙이면 존재하지 않는 경로를 가리키게
        // 된다 — 실서버+실 git push 바이너리로 재현한 실제 버그, 상세 경위는
        // GitSmartHttpProtocolIntegrationSpec 참고).
        it("저장소 리졸버 람다 - name에 \".git\" 접미어가 없어도 접미어를 붙인 것과 같은 경로로 resolve해야 한다") {
            val lambda = GitServletConfig::class.java.getDeclaredMethod(
                "gitServletRegistrationBean\$lambda\$0\$0",
                File::class.java, HttpServletRequest::class.java, String::class.java
            )
            lambda.isAccessible = true

            val gitBaseDir = File.createTempFile("resolver-base-nosuffix", "").apply { delete(); mkdirs() }
            val req = mockk<HttpServletRequest>(relaxed = true)

            val repoWithSuffix = lambda.invoke(null, gitBaseDir, req, "owner/some-repo.git") as Repository
            val resolvedWithSuffix = repoWithSuffix.directory
            repoWithSuffix.close()

            val repoWithoutSuffix = lambda.invoke(null, gitBaseDir, req, "owner/some-repo") as Repository
            val resolvedWithoutSuffix = repoWithoutSuffix.directory
            repoWithoutSuffix.close()

            resolvedWithoutSuffix shouldBe resolvedWithSuffix

            gitBaseDir.deleteRecursively()
        }
    }
})
