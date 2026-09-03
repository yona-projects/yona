package com.github.yonaprojects.yona.config.svn

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectService
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletResponse
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl
import java.util.Optional

class SvnAuthorizationFilterExtraSpec : DescribeSpec({
    val projectService = mockk<ProjectService>()
    val userRepository = mockk<UserRepository>()
    val accessControl = mockk<AccessControl>()
    val filter = SvnAuthorizationFilter(projectService, userRepository, accessControl)
    val filterChain = mockk<FilterChain>(relaxed = true)

    beforeTest {
        clearMocks(projectService, userRepository, accessControl, filterChain)
        SecurityContextHolder.clearContext()
    }

    describe("SvnAuthorizationFilter Extra Coverage") {
        it("요청된 프로젝트의 vcs가 subversion/svn이 아닌 경우 400을 반환해야 한다") {
            val request = MockHttpServletRequest("PROPFIND", "/svn/gildong/git-repo/trunk")
            val response = MockHttpServletResponse()
            val project = Project(owner = "gildong", name = "git-repo", vcs = "GIT", projectScope = ProjectScope.PUBLIC)
            every { projectService.findByOwnerAndName("gildong", "git-repo") } returns project

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_BAD_REQUEST
            verify(exactly = 0) { filterChain.doFilter(any(), any()) }
        }

        it("요청된 프로젝트의 vcs가 null인 경우 400을 반환해야 한다") {
            val request = MockHttpServletRequest("PROPFIND", "/svn/gildong/null-vcs-repo/trunk")
            val response = MockHttpServletResponse()
            val project = Project(owner = "gildong", name = "null-vcs-repo", vcs = null, projectScope = ProjectScope.PUBLIC)
            every { projectService.findByOwnerAndName("gildong", "null-vcs-repo") } returns project

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_BAD_REQUEST
            verify(exactly = 0) { filterChain.doFilter(any(), any()) }
        }

        it("인증이 필요한 요청에서 Authentication이 null인 경우 401을 반환해야 한다") {
            val request = MockHttpServletRequest("PROPFIND", "/svn/gildong/private-repo/trunk")
            val response = MockHttpServletResponse()
            val project = Project(owner = "gildong", name = "private-repo", vcs = "SVN", projectScope = ProjectScope.PRIVATE)
            every { projectService.findByOwnerAndName("gildong", "private-repo") } returns project
            
            SecurityContextHolder.getContext().authentication = null

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_UNAUTHORIZED
            response.getHeader("WWW-Authenticate") shouldBe "Basic realm=\"SVN Repository\""
            verify(exactly = 0) { filterChain.doFilter(any(), any()) }
        }
        
        it("인증이 필요한 요청에서 Authentication이 isAuthenticated == false인 경우 401을 반환해야 한다") {
            val request = MockHttpServletRequest("PROPFIND", "/svn/gildong/private-repo/trunk")
            val response = MockHttpServletResponse()
            val project = Project(owner = "gildong", name = "private-repo", vcs = "SVN", projectScope = ProjectScope.PRIVATE)
            every { projectService.findByOwnerAndName("gildong", "private-repo") } returns project
            
            val auth = UsernamePasswordAuthenticationToken("chulsoo", "password", AuthorityUtils.createAuthorityList("ROLE_ACTIVE"))
            auth.isAuthenticated = false
            SecurityContextHolder.setContext(SecurityContextImpl(auth))

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_UNAUTHORIZED
            verify(exactly = 0) { filterChain.doFilter(any(), any()) }
        }

        it("프로젝트 멤버 확인 시 프로젝트 ID가 null이면 403을 반환해야 한다") {
            val request = MockHttpServletRequest("PROPFIND", "/svn/gildong/private-repo/trunk")
            val response = MockHttpServletResponse()
            // id가 null
            val project = Project(id = null, owner = "gildong", name = "private-repo", vcs = "SVN", projectScope = ProjectScope.PRIVATE)
            every { projectService.findByOwnerAndName("gildong", "private-repo") } returns project
            
            val auth = UsernamePasswordAuthenticationToken("chulsoo", "password", AuthorityUtils.createAuthorityList("ROLE_ACTIVE"))
            SecurityContextHolder.setContext(SecurityContextImpl(auth))

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_FORBIDDEN
            verify(exactly = 0) { filterChain.doFilter(any(), any()) }
        }

        it("프로젝트 멤버 확인 시 유저를 찾을 수 없으면 403을 반환해야 한다") {
            val request = MockHttpServletRequest("PROPFIND", "/svn/gildong/private-repo/trunk")
            val response = MockHttpServletResponse()
            val project = Project(id = 1L, owner = "gildong", name = "private-repo", vcs = "SVN", projectScope = ProjectScope.PRIVATE)
            every { projectService.findByOwnerAndName("gildong", "private-repo") } returns project
            every { projectService.isMember(1L, "chulsoo") } returns false
            every { userRepository.findByLoginId("chulsoo") } returns Optional.empty()
            
            val auth = UsernamePasswordAuthenticationToken("chulsoo", "password", AuthorityUtils.createAuthorityList("ROLE_ACTIVE"))
            SecurityContextHolder.setContext(SecurityContextImpl(auth))

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_FORBIDDEN
            verify(exactly = 0) { filterChain.doFilter(any(), any()) }
        }
        
        it("PUBLIC 프로젝트의 쓰기 요청 시 Authentication이 null이면 401을 반환해야 한다") {
            val request = MockHttpServletRequest("PUT", "/svn/gildong/public-repo/trunk/a.txt")
            val response = MockHttpServletResponse()
            val project = Project(owner = "gildong", name = "public-repo", vcs = "SUBVERSION", projectScope = ProjectScope.PUBLIC)
            every { projectService.findByOwnerAndName("gildong", "public-repo") } returns project
            
            SecurityContextHolder.getContext().authentication = null

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_UNAUTHORIZED
            verify(exactly = 0) { filterChain.doFilter(any(), any()) }
        }
        
        it("isCodeAccessibleMemberOnly가 true인 경우 인증 없이 401 반환") {
            val request = MockHttpServletRequest("PROPFIND", "/svn/gildong/public-repo/trunk")
            val response = MockHttpServletResponse()
            val project = Project(owner = "gildong", name = "public-repo", vcs = "SUBVERSION", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true)
            every { projectService.findByOwnerAndName("gildong", "public-repo") } returns project
            
            SecurityContextHolder.getContext().authentication = null

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_UNAUTHORIZED
            verify(exactly = 0) { filterChain.doFilter(any(), any()) }
        }

        // svnUriPattern(^/svn/([^/]+)/([^/]+?)(?:/.*)?$)에 매치되지 않는 요청(SVN 경로가 아님)은
        // projectService를 조회하지도 않고 그대로 필터 체인을 통과시켜야 한다 — 기존 테스트는 전부
        // /svn/owner/name 형태만 다뤄서 "매치 안 됨" 분기가 한 번도 실행된 적 없었다.
        it("SVN URI 패턴에 매치되지 않는 요청은 프로젝트 조회 없이 그대로 통과시켜야 한다") {
            val request = MockHttpServletRequest("GET", "/api/other/path")
            val response = MockHttpServletResponse()

            filter.doFilter(request, response, filterChain)

            verify(exactly = 0) { projectService.findByOwnerAndName(any(), any()) }
            verify(exactly = 1) { filterChain.doFilter(any(), any()) }
        }

        // requiresAuth=true 경로(PRIVATE/PROTECTED/쓰기요청)에서 authentication이 null도 아니고
        // isAuthenticated=false도 아니지만 AnonymousAuthenticationToken인 경우 —
        // `authentication == null || !authentication.isAuthenticated || isAnonymous(authentication)`의
        // 세 번째 서브 분기(isAnonymous=true)가 이 경로에서는 한 번도 실행된 적 없었다(기존 익명
        // 토큰 테스트는 전부 requiresAuth=false인 PUBLIC 읽기 경로에서만 쓰였음).
        it("인증이 필요한 요청에서 AnonymousAuthenticationToken이면 401을 반환해야 한다") {
            val request = MockHttpServletRequest("PROPFIND", "/svn/gildong/private-repo/trunk")
            val response = MockHttpServletResponse()
            val project = Project(owner = "gildong", name = "private-repo", vcs = "SVN", projectScope = ProjectScope.PRIVATE)
            every { projectService.findByOwnerAndName("gildong", "private-repo") } returns project

            val auth = org.springframework.security.authentication.AnonymousAuthenticationToken(
                "key", "anonymous", org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")
            )
            SecurityContextHolder.setContext(SecurityContextImpl(auth))

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_UNAUTHORIZED
            verify(exactly = 0) { filterChain.doFilter(any(), any()) }
        }

        // requiresAuth=false 경로(PUBLIC 읽기)에서 authentication이 아예 null이면 —
        // `authentication != null && ...`의 첫 서브 분기(false)가 이 경로에서는 한 번도 실행된
        // 적 없었다(기존 PUBLIC 읽기 테스트는 전부 AnonymousAuthenticationToken을 명시적으로
        // 설정해 authentication이 non-null이었음).
        it("인증이 필요 없는 요청에서 authentication이 null이면 게스트 검사 없이 통과해야 한다") {
            val request = MockHttpServletRequest("PROPFIND", "/svn/gildong/public-repo/trunk")
            val response = MockHttpServletResponse()
            val project = Project(owner = "gildong", name = "public-repo", vcs = "SUBVERSION", projectScope = ProjectScope.PUBLIC)
            every { projectService.findByOwnerAndName("gildong", "public-repo") } returns project

            SecurityContextHolder.getContext().authentication = null

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_OK
            verify(exactly = 0) { userRepository.findByLoginId(any()) }
            verify(exactly = 1) { filterChain.doFilter(any(), any()) }
        }
    }
})
