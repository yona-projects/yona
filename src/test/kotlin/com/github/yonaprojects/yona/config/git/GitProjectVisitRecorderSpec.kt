package com.github.yonaprojects.yona.config.git

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectService
import com.github.yonaprojects.yona.domain.project.RecentProjectRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.*
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl
import java.util.Optional

// yona GitApp.java:88-137 service()의 user.visits(project) 대응 (P2-09). git 프로토콜(clone/fetch/push)로만 [GL-controllers_GitApp-005]
// 접근하는 사용자는 웹 UI를 거치지 않아 "최근 방문 프로젝트"에 전혀 기록되지 않던 결손을 해결한다.
// yona는 advertise 단계(GET .../info/refs)가 아니라 실제 RPC(POST git-upload-pack/git-receive-pack)
// 처리 단계에서만 방문을 기록하므로 동일하게 재현한다.
class GitProjectVisitRecorderSpec : DescribeSpec({
    val projectService = mockk<ProjectService>()
    val userRepository = mockk<UserRepository>()
    val recentProjectRepository = mockk<RecentProjectRepository>()
    val recorder = GitProjectVisitRecorder(projectService, userRepository, recentProjectRepository)

    beforeTest {
        clearMocks(projectService, userRepository, recentProjectRepository)
        SecurityContextHolder.clearContext()
    }

    describe("GitProjectVisitRecorder") {
        val project = Project(id = 1L, name = "sample-repo", owner = "gildong")
        val user = User(id = 10L, loginId = "gildong", name = "길동")

        it("git-upload-pack(clone/fetch) RPC 요청이면 방문을 기록해야 한다") {
            val request = MockHttpServletRequest("POST", "/git/gildong/sample-repo.git/git-upload-pack")
            every { projectService.findByOwnerAndName("gildong", "sample-repo") } returns project
            every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
            every { recentProjectRepository.recordVisit(user, project) } just Runs
            val auth = UsernamePasswordAuthenticationToken("gildong", "password", AuthorityUtils.createAuthorityList("ROLE_ACTIVE"))
            SecurityContextHolder.setContext(SecurityContextImpl(auth))

            recorder.recordIfApplicable(request)

            verify(exactly = 1) { recentProjectRepository.recordVisit(user, project) }
        }

        it("git-receive-pack(push) RPC 요청이면 방문을 기록해야 한다") {
            val request = MockHttpServletRequest("POST", "/git/gildong/sample-repo.git/git-receive-pack")
            every { projectService.findByOwnerAndName("gildong", "sample-repo") } returns project
            every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
            every { recentProjectRepository.recordVisit(user, project) } just Runs
            val auth = UsernamePasswordAuthenticationToken("gildong", "password", AuthorityUtils.createAuthorityList("ROLE_ACTIVE"))
            SecurityContextHolder.setContext(SecurityContextImpl(auth))

            recorder.recordIfApplicable(request)

            verify(exactly = 1) { recentProjectRepository.recordVisit(user, project) }
        }

        it("advertise 단계(GET info/refs)에서는 방문을 기록하지 않아야 한다") {
            val request = MockHttpServletRequest("GET", "/git/gildong/sample-repo.git/info/refs")
            request.setParameter("service", "git-upload-pack")
            val auth = UsernamePasswordAuthenticationToken("gildong", "password", AuthorityUtils.createAuthorityList("ROLE_ACTIVE"))
            SecurityContextHolder.setContext(SecurityContextImpl(auth))

            recorder.recordIfApplicable(request)

            verify(exactly = 0) { recentProjectRepository.recordVisit(any(), any()) }
        }

        it("익명 사용자의 RPC 요청은 방문을 기록하지 않아야 한다") {
            val request = MockHttpServletRequest("POST", "/git/gildong/sample-repo.git/git-upload-pack")
            every { projectService.findByOwnerAndName("gildong", "sample-repo") } returns project
            val auth = AnonymousAuthenticationToken("key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"))
            SecurityContextHolder.setContext(SecurityContextImpl(auth))

            recorder.recordIfApplicable(request)

            verify(exactly = 0) { recentProjectRepository.recordVisit(any(), any()) }
        }

        it("존재하지 않는 프로젝트면 방문을 기록하지 않아야 한다") {
            val request = MockHttpServletRequest("POST", "/git/gildong/non-exist.git/git-upload-pack")
            every { projectService.findByOwnerAndName("gildong", "non-exist") } returns null
            val auth = UsernamePasswordAuthenticationToken("gildong", "password", AuthorityUtils.createAuthorityList("ROLE_ACTIVE"))
            SecurityContextHolder.setContext(SecurityContextImpl(auth))

            recorder.recordIfApplicable(request)

            verify(exactly = 0) { recentProjectRepository.recordVisit(any(), any()) }
        }

        it("POST 요청이지만 RPC URI가 아니면 방문을 기록하지 않아야 한다") {
            val request = MockHttpServletRequest("POST", "/git/gildong/sample-repo.git/something-else")
            recorder.recordIfApplicable(request)
            verify(exactly = 0) { projectService.findByOwnerAndName(any(), any()) }
        }

        it("RPC 요청이지만 git URI 패턴에 맞지 않으면 방문을 기록하지 않아야 한다") {
            val request = MockHttpServletRequest("POST", "/invalid-uri/git-upload-pack")
            recorder.recordIfApplicable(request)
            verify(exactly = 0) { projectService.findByOwnerAndName(any(), any()) }
        }

        it("SecurityContext에 Authentication이 없으면 방문을 기록하지 않아야 한다") {
            val request = MockHttpServletRequest("POST", "/git/gildong/sample-repo.git/git-upload-pack")
            every { projectService.findByOwnerAndName("gildong", "sample-repo") } returns project
            SecurityContextHolder.clearContext()

            recorder.recordIfApplicable(request)

            verify(exactly = 0) { recentProjectRepository.recordVisit(any(), any()) }
        }

        it("Authentication이 isAuthenticated=false이면 방문을 기록하지 않아야 한다") {
            val request = MockHttpServletRequest("POST", "/git/gildong/sample-repo.git/git-upload-pack")
            every { projectService.findByOwnerAndName("gildong", "sample-repo") } returns project
            val auth = UsernamePasswordAuthenticationToken("gildong", "password")
            auth.isAuthenticated = false
            SecurityContextHolder.setContext(SecurityContextImpl(auth))

            recorder.recordIfApplicable(request)

            verify(exactly = 0) { recentProjectRepository.recordVisit(any(), any()) }
        }

        it("인증된 사용자지만 DB에 존재하지 않으면 방문을 기록하지 않아야 한다") {
            val request = MockHttpServletRequest("POST", "/git/gildong/sample-repo.git/git-upload-pack")
            every { projectService.findByOwnerAndName("gildong", "sample-repo") } returns project
            every { userRepository.findByLoginId("gildong") } returns Optional.empty()
            val auth = UsernamePasswordAuthenticationToken("gildong", "password", AuthorityUtils.createAuthorityList("ROLE_ACTIVE"))
            SecurityContextHolder.setContext(SecurityContextImpl(auth))

            recorder.recordIfApplicable(request)

            verify(exactly = 0) { recentProjectRepository.recordVisit(any(), any()) }
        }
    }
})
