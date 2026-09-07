package com.github.yonaprojects.yona.config.git

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.config.vcs.RepoAccessPolicy
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationUser
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectService
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
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
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository

// yona git 스마트 HTTP 경로도 SvnApp.java와 동일한 AccessControl.isAllowed 규칙을 쓴다는 전제로,
// SvnAuthorizationFilter(P1-23)와 동일한 두 가지 축소를 여기서도 수정한다 (P1-45):
// 1) PROTECTED 프로젝트가 PUBLIC과 동일하게 인증 없이 clone 가능했던 문제
// 2) 게스트(isGuest) 계정이 공개 프로젝트라도 거부당해야 하는데 그 검사가 전혀 없던 문제
class GitAuthorizationFilterSpec : DescribeSpec({
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
    // yona-wiki P3-03 Step6 — GitAuthorizationFilter가 접근 판정 로직을 RepoAccessPolicy로 위임하도록
    // 리팩터링됨(SshAuthServiceImpl/SvnAuthorizationFilter와 공유하기 위함, 2026-09-07 이름 변경).
    // 이 스펙이 검증하는 판정 결과 자체는 동일하다.
    val repoAccessPolicy = RepoAccessPolicy(projectService, userRepository, accessControl)
    val filter = GitAuthorizationFilter(repoAccessPolicy)
    val filterChain = mockk<FilterChain>(relaxed = true)

    beforeTest {
        clearMocks(projectService, userRepository, filterChain)
        SecurityContextHolder.clearContext()
    }

    describe("GitAuthorizationFilter") {
        it("존재하지 않는 프로젝트 요청 시 404를 응답해야 한다") {
            // Given
            val request = MockHttpServletRequest("GET", "/git/gildong/non-exist.git")
            val response = MockHttpServletResponse()
            every { projectService.findByOwnerAndName("gildong", "non-exist") } returns null

            // When
            filter.doFilter(request, response, filterChain)

            // Then
            response.status shouldBe HttpServletResponse.SC_NOT_FOUND
            verify(exactly = 0) { filterChain.doFilter(any(), any()) }
        }

        it("PUBLIC 프로젝트의 clone 요청은 익명 사용자도 통과되어야 한다") {
            // Given
            val request = MockHttpServletRequest("GET", "/git/gildong/public-repo.git/info/refs")
            request.setParameter("service", "git-upload-pack")
            val response = MockHttpServletResponse()

            val project = Project(owner = "gildong", name = "public-repo", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = false)
            every { projectService.findByOwnerAndName("gildong", "public-repo") } returns project

            val auth = AnonymousAuthenticationToken("key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"))
            SecurityContextHolder.setContext(SecurityContextImpl(auth))

            // When
            filter.doFilter(request, response, filterChain)

            // Then
            response.status shouldBe HttpServletResponse.SC_OK
            verify(exactly = 1) { filterChain.doFilter(any(), any()) }
        }

        it("PUBLIC 프로젝트라도 push(write) 요청 시 익명 사용자는 401을 응답해야 한다") {
            // Given
            val request = MockHttpServletRequest("POST", "/git/gildong/public-repo.git/git-receive-pack")
            val response = MockHttpServletResponse()

            val project = Project(owner = "gildong", name = "public-repo", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = false)
            every { projectService.findByOwnerAndName("gildong", "public-repo") } returns project

            // When
            filter.doFilter(request, response, filterChain)

            // Then
            response.status shouldBe HttpServletResponse.SC_UNAUTHORIZED
            response.getHeader("WWW-Authenticate") shouldBe "Basic realm=\"Git Repository\""
            verify(exactly = 0) { filterChain.doFilter(any(), any()) }
        }

        it("PRIVATE 프로젝트의 clone 요청 시 익명 사용자는 401을 응답해야 한다") {
            // Given
            val request = MockHttpServletRequest("GET", "/git/gildong/private-repo.git/info/refs")
            request.setParameter("service", "git-upload-pack")
            val response = MockHttpServletResponse()

            val project = Project(owner = "gildong", name = "private-repo", projectScope = ProjectScope.PRIVATE)
            every { projectService.findByOwnerAndName("gildong", "private-repo") } returns project

            // When
            filter.doFilter(request, response, filterChain)

            // Then
            response.status shouldBe HttpServletResponse.SC_UNAUTHORIZED
            verify(exactly = 0) { filterChain.doFilter(any(), any()) }
        }

        it("PRIVATE 프로젝트 요청 시 멤버가 아닌 인증된 유저는 403을 응답해야 한다") {
            // Given
            val request = MockHttpServletRequest("GET", "/git/gildong/private-repo.git/info/refs")
            request.setParameter("service", "git-upload-pack")
            val response = MockHttpServletResponse()

            val project = Project(id = 1L, owner = "gildong", name = "private-repo", projectScope = ProjectScope.PRIVATE)
            every { projectService.findByOwnerAndName("gildong", "private-repo") } returns project
            every { projectService.isMember(1L, "chulsoo") } returns false
            every { userRepository.findByLoginId("chulsoo") } returns Optional.of(User(id = 9L, loginId = "chulsoo", name = "철수"))

            val auth = UsernamePasswordAuthenticationToken("chulsoo", "password", AuthorityUtils.createAuthorityList("ROLE_ACTIVE"))
            SecurityContextHolder.setContext(SecurityContextImpl(auth))

            // When
            filter.doFilter(request, response, filterChain)

            // Then
            response.status shouldBe HttpServletResponse.SC_FORBIDDEN
            verify(exactly = 0) { filterChain.doFilter(any(), any()) }
        }

        // yona isAllowedIfGroupMember()는 PRIVATE 프로젝트에는 적용되지 않는다(PUBLIC/PROTECTED만) —
        // 조직 그룹멤버라도 PRIVATE 저장소는 여전히 직접 멤버만 접근 가능해야 한다.
        it("PRIVATE 프로젝트는 조직 그룹멤버라도 직접 멤버가 아니면 여전히 403을 응답해야 한다 (P1-64)") {
            val request = MockHttpServletRequest("GET", "/git/gildong/private-repo.git/info/refs")
            request.setParameter("service", "git-upload-pack")
            val response = MockHttpServletResponse()

            val org = Organization(id = 1L, name = "org1")
            val project = Project(id = 4L, owner = "gildong", name = "private-repo", projectScope = ProjectScope.PRIVATE, organization = org)
            val user = User(id = 9L, loginId = "chulsoo", name = "철수")
            org.organizationUsers.add(OrganizationUser(id = 101L, user = user, organization = org, role = Role(id = RoleType.ORG_MEMBER.roleType)))

            every { projectService.findByOwnerAndName("gildong", "private-repo") } returns project
            every { projectService.isMember(4L, "chulsoo") } returns false
            every { userRepository.findByLoginId("chulsoo") } returns Optional.of(user)

            val auth = UsernamePasswordAuthenticationToken("chulsoo", "password", AuthorityUtils.createAuthorityList("ROLE_ACTIVE"))
            SecurityContextHolder.setContext(SecurityContextImpl(auth))

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_FORBIDDEN
            verify(exactly = 0) { filterChain.doFilter(any(), any()) }
        }

        it("PRIVATE 프로젝트 요청 시 멤버인 인증된 유저는 통과되어야 한다") {
            // Given
            val request = MockHttpServletRequest("GET", "/git/gildong/private-repo.git/info/refs")
            request.setParameter("service", "git-upload-pack")
            val response = MockHttpServletResponse()

            val project = Project(id = 1L, owner = "gildong", name = "private-repo", projectScope = ProjectScope.PRIVATE)

            every { projectService.findByOwnerAndName("gildong", "private-repo") } returns project
            every { projectService.isMember(1L, "chulsoo") } returns true

            val auth = UsernamePasswordAuthenticationToken("chulsoo", "password", AuthorityUtils.createAuthorityList("ROLE_ACTIVE"))
            SecurityContextHolder.setContext(SecurityContextImpl(auth))

            // When
            filter.doFilter(request, response, filterChain)

            // Then
            response.status shouldBe HttpServletResponse.SC_OK
            verify(exactly = 1) { filterChain.doFilter(any(), any()) }
        }

        it("PROTECTED 프로젝트의 clone 요청 시 익명 사용자는 401을 응답해야 한다 (P1-45)") {
            val request = MockHttpServletRequest("GET", "/git/gildong/protected-repo.git/info/refs")
            request.setParameter("service", "git-upload-pack")
            val response = MockHttpServletResponse()

            val project = Project(id = 2L, owner = "gildong", name = "protected-repo", projectScope = ProjectScope.PROTECTED)
            every { projectService.findByOwnerAndName("gildong", "protected-repo") } returns project

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_UNAUTHORIZED
            verify(exactly = 0) { filterChain.doFilter(any(), any()) }
        }

        it("PROTECTED 프로젝트 요청 시 멤버가 아닌 인증된 유저는 403을 응답해야 한다 (P1-45)") {
            val request = MockHttpServletRequest("GET", "/git/gildong/protected-repo.git/info/refs")
            request.setParameter("service", "git-upload-pack")
            val response = MockHttpServletResponse()

            val project = Project(id = 2L, owner = "gildong", name = "protected-repo", projectScope = ProjectScope.PROTECTED)
            every { projectService.findByOwnerAndName("gildong", "protected-repo") } returns project
            every { projectService.isMember(2L, "chulsoo") } returns false
            every { userRepository.findByLoginId("chulsoo") } returns Optional.of(User(id = 9L, loginId = "chulsoo", name = "철수"))

            val auth = UsernamePasswordAuthenticationToken("chulsoo", "password", AuthorityUtils.createAuthorityList("ROLE_ACTIVE"))
            SecurityContextHolder.setContext(SecurityContextImpl(auth))

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_FORBIDDEN
            verify(exactly = 0) { filterChain.doFilter(any(), any()) }
        }

        // yona AccessControl.isAllowedIfGroupMember() 대응 (P1-64). PROTECTED 프로젝트는 직접 멤버가
        // 아니어도 그 프로젝트가 속한 조직의 구성원이면 Git clone/push가 허용돼야 한다.
        it("PROTECTED 프로젝트 요청 시 직접 멤버는 아니지만 조직 그룹멤버인 인증된 유저는 통과되어야 한다 (P1-64)") {
            val request = MockHttpServletRequest("GET", "/git/gildong/protected-repo.git/info/refs")
            request.setParameter("service", "git-upload-pack")
            val response = MockHttpServletResponse()

            val org = Organization(id = 1L, name = "org1")
            val project = Project(id = 5L, owner = "gildong", name = "protected-repo", projectScope = ProjectScope.PROTECTED, organization = org)
            val user = User(id = 9L, loginId = "chulsoo", name = "철수")
            org.organizationUsers.add(OrganizationUser(id = 100L, user = user, organization = org, role = Role(id = RoleType.ORG_MEMBER.roleType)))

            every { projectService.findByOwnerAndName("gildong", "protected-repo") } returns project
            every { projectService.isMember(5L, "chulsoo") } returns false
            every { userRepository.findByLoginId("chulsoo") } returns Optional.of(user)

            val auth = UsernamePasswordAuthenticationToken("chulsoo", "password", AuthorityUtils.createAuthorityList("ROLE_ACTIVE"))
            SecurityContextHolder.setContext(SecurityContextImpl(auth))

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_OK
            verify(exactly = 1) { filterChain.doFilter(any(), any()) }
        }

        it("PROTECTED 프로젝트 요청 시 멤버인 인증된 유저는 통과되어야 한다 (P1-45)") {
            val request = MockHttpServletRequest("GET", "/git/gildong/protected-repo.git/info/refs")
            request.setParameter("service", "git-upload-pack")
            val response = MockHttpServletResponse()

            val project = Project(id = 2L, owner = "gildong", name = "protected-repo", projectScope = ProjectScope.PROTECTED)
            every { projectService.findByOwnerAndName("gildong", "protected-repo") } returns project
            every { projectService.isMember(2L, "chulsoo") } returns true

            val auth = UsernamePasswordAuthenticationToken("chulsoo", "password", AuthorityUtils.createAuthorityList("ROLE_ACTIVE"))
            SecurityContextHolder.setContext(SecurityContextImpl(auth))

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_OK
            verify(exactly = 1) { filterChain.doFilter(any(), any()) }
        }

        it("PUBLIC 프로젝트라도 게스트 계정으로 인증된 요청은 403을 응답해야 한다 (P1-45, yona !user.isGuest 대응)") {
            val request = MockHttpServletRequest("GET", "/git/gildong/public-repo.git/info/refs")
            request.setParameter("service", "git-upload-pack")
            val response = MockHttpServletResponse()

            val project = Project(owner = "gildong", name = "public-repo", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = false)
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

        it("PUBLIC 프로젝트를 게스트가 아닌 인증된 유저가 요청하면 통과되어야 한다 (P1-45)") {
            val request = MockHttpServletRequest("GET", "/git/gildong/public-repo.git/info/refs")
            request.setParameter("service", "git-upload-pack")
            val response = MockHttpServletResponse()

            val project = Project(owner = "gildong", name = "public-repo", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = false)
            every { projectService.findByOwnerAndName("gildong", "public-repo") } returns project
            every { userRepository.findByLoginId("chulsoo") } returns Optional.of(
                User(loginId = "chulsoo", name = "철수", email = "chulsoo@yona.io", isGuest = false)
            )

            val auth = UsernamePasswordAuthenticationToken("chulsoo", "password", AuthorityUtils.createAuthorityList("ROLE_ACTIVE"))
            SecurityContextHolder.setContext(SecurityContextImpl(auth))

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_OK
            verify(exactly = 1) { filterChain.doFilter(any(), any()) }
        }
        it("Git 경로가 아닌 경우 그냥 통과해야 한다") {
            val request = MockHttpServletRequest("GET", "/some-other-path")
            val response = MockHttpServletResponse()

            filter.doFilter(request, response, filterChain)

            verify(exactly = 1) { filterChain.doFilter(request, response) }
        }

        it("PUBLIC 프로젝트라도 isCodeAccessibleMemberOnly가 true면 익명 사용자는 401을 응답해야 한다") {
            val request = MockHttpServletRequest("GET", "/git/gildong/public-repo.git/info/refs")
            val response = MockHttpServletResponse()

            val project = Project(owner = "gildong", name = "public-repo", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true)
            every { projectService.findByOwnerAndName("gildong", "public-repo") } returns project

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_UNAUTHORIZED
            verify(exactly = 0) { filterChain.doFilter(any(), any()) }
        }

        it("URI가 /git-receive-pack으로 끝나면 write 요청으로 간주한다") {
            val request = MockHttpServletRequest("GET", "/git/gildong/public-repo.git/git-receive-pack")
            val response = MockHttpServletResponse()
            val project = Project(owner = "gildong", name = "public-repo", projectScope = ProjectScope.PUBLIC)
            every { projectService.findByOwnerAndName("gildong", "public-repo") } returns project

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_UNAUTHORIZED
            verify(exactly = 0) { filterChain.doFilter(any(), any()) }
        }
        
        it("HTTP PUT 메소드면 write 요청으로 간주한다") {
            val request = MockHttpServletRequest("PUT", "/git/gildong/public-repo.git/info/refs")
            val response = MockHttpServletResponse()
            val project = Project(owner = "gildong", name = "public-repo", projectScope = ProjectScope.PUBLIC)
            every { projectService.findByOwnerAndName("gildong", "public-repo") } returns project

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_UNAUTHORIZED
            verify(exactly = 0) { filterChain.doFilter(any(), any()) }
        }

        it("project.id가 null인 경우 isMember는 false를 반환하고 403을 응답해야 한다") {
            val request = MockHttpServletRequest("GET", "/git/gildong/private-repo.git/info/refs")
            val response = MockHttpServletResponse()

            val project = Project(id = null, owner = "gildong", name = "private-repo", projectScope = ProjectScope.PRIVATE)
            every { projectService.findByOwnerAndName("gildong", "private-repo") } returns project

            val auth = UsernamePasswordAuthenticationToken("chulsoo", "password", AuthorityUtils.createAuthorityList("ROLE_ACTIVE"))
            SecurityContextHolder.setContext(SecurityContextImpl(auth))

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_FORBIDDEN
        }

        it("isMember에서 userRepository에 유저가 없으면 false를 반환하고 403을 응답해야 한다") {
            val request = MockHttpServletRequest("GET", "/git/gildong/private-repo.git/info/refs")
            val response = MockHttpServletResponse()

            val project = Project(id = 1L, owner = "gildong", name = "private-repo", projectScope = ProjectScope.PRIVATE)
            every { projectService.findByOwnerAndName("gildong", "private-repo") } returns project
            every { projectService.isMember(1L, "chulsoo") } returns false
            every { userRepository.findByLoginId("chulsoo") } returns Optional.empty()

            val auth = UsernamePasswordAuthenticationToken("chulsoo", "password", AuthorityUtils.createAuthorityList("ROLE_ACTIVE"))
            SecurityContextHolder.setContext(SecurityContextImpl(auth))

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_FORBIDDEN
        }

        it("PUBLIC 프로젝트 요청 시 authentication이 null이면 통과되어야 한다") {
            val request = MockHttpServletRequest("GET", "/git/gildong/public-repo.git/info/refs")
            val response = MockHttpServletResponse()

            val project = Project(owner = "gildong", name = "public-repo", projectScope = ProjectScope.PUBLIC)
            every { projectService.findByOwnerAndName("gildong", "public-repo") } returns project

            SecurityContextHolder.clearContext()

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_OK
            verify(exactly = 1) { filterChain.doFilter(any(), any()) }
        }

        // requiresAuth=true 경로의 authentication==null||!isAuthenticated||isAnonymous(...) 3항 OR는
        // 기존엔 전부 authentication==null(단락평가)로만 401을 태워, 뒤 두 서브 분기가 비어 있었다.
        it("PRIVATE 프로젝트 요청 시 익명 토큰(AnonymousAuthenticationToken)이면 401을 응답해야 한다") {
            val request = MockHttpServletRequest("GET", "/git/gildong/private-repo.git/info/refs")
            val response = MockHttpServletResponse()

            val project = Project(id = 1L, owner = "gildong", name = "private-repo", projectScope = ProjectScope.PRIVATE)
            every { projectService.findByOwnerAndName("gildong", "private-repo") } returns project

            val auth = AnonymousAuthenticationToken("key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"))
            SecurityContextHolder.setContext(SecurityContextImpl(auth))

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_UNAUTHORIZED
            verify(exactly = 0) { filterChain.doFilter(any(), any()) }
        }

        it("PRIVATE 프로젝트 요청 시 인증되지 않은(isAuthenticated=false) 토큰이면 401을 응답해야 한다") {
            val request = MockHttpServletRequest("GET", "/git/gildong/private-repo.git/info/refs")
            val response = MockHttpServletResponse()

            val project = Project(id = 1L, owner = "gildong", name = "private-repo", projectScope = ProjectScope.PRIVATE)
            every { projectService.findByOwnerAndName("gildong", "private-repo") } returns project

            val auth = UsernamePasswordAuthenticationToken("chulsoo", "password")
            auth.isAuthenticated = false
            SecurityContextHolder.setContext(SecurityContextImpl(auth))

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_UNAUTHORIZED
            verify(exactly = 0) { filterChain.doFilter(any(), any()) }
        }

        // isGuestUser()의 userRepository.findByLoginId(...).map{...}.orElse(false)는 기존엔 항상
        // present(게스트/비게스트)만 다뤄서, empty(orElse) 쪽 서브 분기가 else/게스트체크 경로에서 비어 있었다.
        it("PUBLIC 프로젝트 요청 시 인증된 유저가 userRepository에 없으면 게스트 아님으로 간주해 통과해야 한다") {
            val request = MockHttpServletRequest("GET", "/git/gildong/public-repo.git/info/refs")
            val response = MockHttpServletResponse()

            val project = Project(owner = "gildong", name = "public-repo", projectScope = ProjectScope.PUBLIC)
            every { projectService.findByOwnerAndName("gildong", "public-repo") } returns project
            every { userRepository.findByLoginId("ghost") } returns Optional.empty()

            val auth = UsernamePasswordAuthenticationToken("ghost", "password", AuthorityUtils.createAuthorityList("ROLE_ACTIVE"))
            SecurityContextHolder.setContext(SecurityContextImpl(auth))

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_OK
            verify(exactly = 1) { filterChain.doFilter(any(), any()) }
        }

        it("PUBLIC 프로젝트 요청 시 인증되지 않은 authentication이면 통과되어야 한다") {
            val request = MockHttpServletRequest("GET", "/git/gildong/public-repo.git/info/refs")
            val response = MockHttpServletResponse()

            val project = Project(owner = "gildong", name = "public-repo", projectScope = ProjectScope.PUBLIC)
            every { projectService.findByOwnerAndName("gildong", "public-repo") } returns project

            val auth = UsernamePasswordAuthenticationToken("chulsoo", "password")
            auth.isAuthenticated = false
            SecurityContextHolder.setContext(SecurityContextImpl(auth))

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_OK
            verify(exactly = 1) { filterChain.doFilter(any(), any()) }
        }
    }
})
