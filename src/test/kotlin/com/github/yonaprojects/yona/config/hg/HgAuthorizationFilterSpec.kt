package com.github.yonaprojects.yona.config.hg

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.config.vcs.RepoAccessPolicy
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectService
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletResponse
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl
import java.util.Optional

// yona-wiki P3-12(Mercurial 지원) 2라운드 — GitAuthorizationFilterSpec/SvnAuthorizationFilterSpec와
// 대칭인 단위 테스트. RepoAccessPolicy 자체의 판정 로직은 이미 그 스펙들이 충분히 검증하므로,
// 여기서는 (1) Hg 전용 URI 패턴/vcs 검증, (2) hg wire protocol 고유의 read/write 판정
// (cmd=unbundle / api/.../rw/...)이 올바르게 401/403/400/통과로 이어지는지만 집중적으로 본다.
class HgAuthorizationFilterSpec : DescribeSpec({
    val projectService = mockk<ProjectService>()
    val userRepository = mockk<UserRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>(relaxed = true)
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    every { organizationUserRepository.findByOrganizationIdAndUserId(any(), any()) } returns Optional.empty()
    val userRepositoryForAccessControl = mockk<UserRepository>()
    val organizationRepositoryForAccessControl = mockk<OrganizationRepository>()
    val issueRepositoryForAccessControl = mockk<IssueRepository>()
    val postingRepositoryForAccessControl = mockk<PostingRepository>()
    val reviewCommentRepositoryForAccessControl = mockk<ReviewCommentRepository>()
    val commitCommentRepositoryForAccessControl = mockk<CommitCommentRepository>()
    val milestoneRepositoryForAccessControl = mockk<MilestoneRepository>()
    val accessControl = AccessControl(
        projectUserRepository, organizationUserRepository,
        userRepositoryForAccessControl, organizationRepositoryForAccessControl,
        issueRepositoryForAccessControl, postingRepositoryForAccessControl,
        reviewCommentRepositoryForAccessControl, commitCommentRepositoryForAccessControl,
        milestoneRepositoryForAccessControl
    )
    val repoAccessPolicy = RepoAccessPolicy(projectService, userRepository, accessControl)
    val filter = HgAuthorizationFilter(repoAccessPolicy)
    val filterChain = mockk<FilterChain>(relaxed = true)

    beforeTest {
        clearMocks(projectService, userRepository, filterChain)
        SecurityContextHolder.clearContext()
    }

    describe("HgAuthorizationFilter") {
        it("존재하지 않는 프로젝트 요청 시 404를 응답해야 한다") {
            val request = MockHttpServletRequest("GET", "/hg/gildong/non-exist")
            val response = MockHttpServletResponse()
            every { projectService.findByOwnerAndName("gildong", "non-exist") } returns null

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_NOT_FOUND
            verify(exactly = 0) { filterChain.doFilter(any(), any()) }
        }

        it("Mercurial 프로젝트가 아니면 400을 응답해야 한다") {
            val request = MockHttpServletRequest("GET", "/hg/gildong/git-repo")
            request.queryString = "cmd=capabilities"
            val response = MockHttpServletResponse()
            val project = Project(owner = "gildong", name = "git-repo", vcs = "GIT", projectScope = ProjectScope.PUBLIC)
            every { projectService.findByOwnerAndName("gildong", "git-repo") } returns project

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_BAD_REQUEST
            verify(exactly = 0) { filterChain.doFilter(any(), any()) }
        }

        it("PUBLIC 프로젝트의 v1 읽기 명령(cmd=capabilities)은 익명 사용자도 통과되어야 한다") {
            val request = MockHttpServletRequest("GET", "/hg/gildong/public-repo")
            request.queryString = "cmd=capabilities"
            val response = MockHttpServletResponse()
            val project = Project(owner = "gildong", name = "public-repo", vcs = "MERCURIAL", projectScope = ProjectScope.PUBLIC)
            every { projectService.findByOwnerAndName("gildong", "public-repo") } returns project

            val auth = AnonymousAuthenticationToken("key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"))
            SecurityContextHolder.setContext(SecurityContextImpl(auth))

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_OK
            verify(exactly = 1) { filterChain.doFilter(any(), any()) }
        }

        it("PUBLIC 프로젝트라도 v1 쓰기 명령(cmd=unbundle)은 익명 사용자에게 401을 응답해야 한다") {
            val request = MockHttpServletRequest("POST", "/hg/gildong/public-repo")
            request.queryString = "cmd=unbundle"
            val response = MockHttpServletResponse()
            val project = Project(owner = "gildong", name = "public-repo", vcs = "MERCURIAL", projectScope = ProjectScope.PUBLIC)
            every { projectService.findByOwnerAndName("gildong", "public-repo") } returns project

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_UNAUTHORIZED
            verify(exactly = 0) { filterChain.doFilter(any(), any()) }
        }

        // wire protocol v2의 read/write는 URL의 "/api/<namespace>/<ro|rw>/<command>" 세그먼트로
        // 결정된다(cmd 쿼리파라미터가 아니라) — v1과 별개 경로로 판정되는지 확인한다.
        it("PUBLIC 프로젝트라도 v2 쓰기 명령(/api/.../rw/...)은 익명 사용자에게 401을 응답해야 한다") {
            val request = MockHttpServletRequest("POST", "/hg/gildong/public-repo/api/browse/rw/unbundle")
            val response = MockHttpServletResponse()
            val project = Project(owner = "gildong", name = "public-repo", vcs = "MERCURIAL", projectScope = ProjectScope.PUBLIC)
            every { projectService.findByOwnerAndName("gildong", "public-repo") } returns project

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_UNAUTHORIZED
            verify(exactly = 0) { filterChain.doFilter(any(), any()) }
        }

        it("PUBLIC 프로젝트의 v2 읽기 명령(/api/.../ro/...)은 익명 사용자도 통과되어야 한다") {
            val request = MockHttpServletRequest("POST", "/hg/gildong/public-repo/api/browse/ro/heads")
            val response = MockHttpServletResponse()
            val project = Project(owner = "gildong", name = "public-repo", vcs = "MERCURIAL", projectScope = ProjectScope.PUBLIC)
            every { projectService.findByOwnerAndName("gildong", "public-repo") } returns project

            val auth = AnonymousAuthenticationToken("key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"))
            SecurityContextHolder.setContext(SecurityContextImpl(auth))

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_OK
            verify(exactly = 1) { filterChain.doFilter(any(), any()) }
        }

        it("PRIVATE 프로젝트 요청 시 익명 사용자는 401을 응답해야 한다") {
            val request = MockHttpServletRequest("GET", "/hg/gildong/private-repo")
            request.queryString = "cmd=capabilities"
            val response = MockHttpServletResponse()
            val project = Project(owner = "gildong", name = "private-repo", vcs = "MERCURIAL", projectScope = ProjectScope.PRIVATE)
            every { projectService.findByOwnerAndName("gildong", "private-repo") } returns project

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_UNAUTHORIZED
            verify(exactly = 0) { filterChain.doFilter(any(), any()) }
        }

        it("PRIVATE 프로젝트 요청 시 멤버가 아닌 인증된 유저는 403을 응답해야 한다") {
            val request = MockHttpServletRequest("GET", "/hg/gildong/private-repo")
            request.queryString = "cmd=capabilities"
            val response = MockHttpServletResponse()
            val project = Project(id = 1L, owner = "gildong", name = "private-repo", vcs = "MERCURIAL", projectScope = ProjectScope.PRIVATE)
            every { projectService.findByOwnerAndName("gildong", "private-repo") } returns project
            every { projectService.isMember(1L, "chulsoo") } returns false
            every { userRepository.findByLoginId("chulsoo") } returns Optional.of(User(id = 9L, loginId = "chulsoo", name = "철수"))

            val auth = UsernamePasswordAuthenticationToken("chulsoo", "password", AuthorityUtils.createAuthorityList("ROLE_ACTIVE"))
            SecurityContextHolder.setContext(SecurityContextImpl(auth))

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_FORBIDDEN
            verify(exactly = 0) { filterChain.doFilter(any(), any()) }
        }

        it("PRIVATE 프로젝트 요청 시 멤버인 인증된 유저는 통과되어야 한다") {
            val request = MockHttpServletRequest("GET", "/hg/gildong/private-repo")
            request.queryString = "cmd=capabilities"
            val response = MockHttpServletResponse()
            val project = Project(id = 1L, owner = "gildong", name = "private-repo", vcs = "MERCURIAL", projectScope = ProjectScope.PRIVATE)
            every { projectService.findByOwnerAndName("gildong", "private-repo") } returns project
            every { projectService.isMember(1L, "chulsoo") } returns true

            val auth = UsernamePasswordAuthenticationToken("chulsoo", "password", AuthorityUtils.createAuthorityList("ROLE_ACTIVE"))
            SecurityContextHolder.setContext(SecurityContextImpl(auth))

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_OK
            verify(exactly = 1) { filterChain.doFilter(any(), any()) }
        }

        it("PUBLIC 프로젝트라도 게스트 계정으로 인증된 요청은 403을 응답해야 한다") {
            val request = MockHttpServletRequest("GET", "/hg/gildong/public-repo")
            request.queryString = "cmd=capabilities"
            val response = MockHttpServletResponse()
            val project = Project(owner = "gildong", name = "public-repo", vcs = "MERCURIAL", projectScope = ProjectScope.PUBLIC)
            every { projectService.findByOwnerAndName("gildong", "public-repo") } returns project
            every { userRepository.findByLoginId("guest-user") } returns Optional.of(
                User(loginId = "guest-user", name = "게스트", email = "guest@yona.io", isGuest = true)
            )

            val auth = UsernamePasswordAuthenticationToken("guest-user", "password", AuthorityUtils.createAuthorityList("ROLE_ACTIVE"))
            SecurityContextHolder.setContext(SecurityContextImpl(auth))

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_FORBIDDEN
            verify(exactly = 0) { filterChain.doFilter(any(), any()) }
        }
    }
})
