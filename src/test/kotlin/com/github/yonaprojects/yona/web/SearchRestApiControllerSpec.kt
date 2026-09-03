package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.enumeration.SearchType
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.pullrequest.PullRequest
import com.github.yonaprojects.yona.domain.support.SearchResult
import com.github.yonaprojects.yona.domain.support.SearchService
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

// yona-wiki P3-02 4라운드(Step8.5 서버 보강) — SearchRestApiController(/api/v1/search/issues,
// /api/v1/search/projects). SearchService.searchInAll에 위임하는 얇은 어댑터라는 것과 빈 검색어
// 400 처리만 검증한다(검색 랭킹/스니펫 등 SearchService 자체 로직은 SearchServiceSpec 참고).
class SearchRestApiControllerSpec : DescribeSpec({
    val searchService = mockk<SearchService>()
    val userRepository = mockk<UserRepository>()

    val controller = SearchRestApiController(searchService, userRepository)
    val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    beforeTest {
        clearMocks(searchService, userRepository)
    }

    val auth = UsernamePasswordAuthenticationToken("tester", "password")
    val user = User(id = 1L, loginId = "tester", name = "테스터")

    describe("GET /api/v1/search/issues") {
        it("검색어가 없으면 400을 반환한다") {
            mockMvc.perform(get("/api/v1/search/issues").param("q", ""))
                .andExpect(status().isBadRequest)
        }

        it("SearchService.searchInAll(ISSUE)에 위임한다") {
            val issue = Issue(id = 1L, number = 1L, title = "버그", project = Project(id = 1L, owner = "yona", name = "yona"))
            // PageImpl(content)의 단일 인자 생성자는 pageable로 Pageable.unpaged()를 쓰는데, Spring
            // Data의 Page Jackson 직렬화가 pageable.getPageNumber() 등을 호출해 unpaged()의
            // UnsupportedOperationException을 그대로 맞고 500(HttpMessageNotWritableException)으로
            // 죽는다(실측 확인) — 반드시 실제 Pageable + total을 준 3-인자 생성자를 써야 한다.
            val issuePage: Page<Issue> = PageImpl(listOf(issue), PageRequest.of(0, 20), 1)
            val result = SearchResult(keyword = "버그", searchType = SearchType.ISSUE, issues = issuePage)
            every { userRepository.findByLoginId("tester") } returns Optional.of(user)
            every { searchService.searchInAll("버그", SearchType.ISSUE, user, any()) } returns result

            mockMvc.perform(get("/api/v1/search/issues").param("q", "버그").principal(auth))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[0].title").value("버그"))

            verify(exactly = 1) { searchService.searchInAll("버그", SearchType.ISSUE, user, any()) }
        }

        it("비로그인 사용자도 검색할 수 있다") {
            val issuePage: Page<Issue> = PageImpl(emptyList(), PageRequest.of(0, 20), 0)
            val result = SearchResult(keyword = "버그", searchType = SearchType.ISSUE, issues = issuePage)
            every { searchService.searchInAll("버그", SearchType.ISSUE, null, any()) } returns result

            mockMvc.perform(get("/api/v1/search/issues").param("q", "버그"))
                .andExpect(status().isOk)
        }
    }

    describe("GET /api/v1/search/projects") {
        it("SearchService.searchInAll(PROJECT)에 위임한다") {
            val project = Project(id = 1L, owner = "yona", name = "yona", projectScope = ProjectScope.PUBLIC)
            val projectPage: Page<Project> = PageImpl(listOf(project), PageRequest.of(0, 20), 1)
            val result = SearchResult(keyword = "yona", searchType = SearchType.PROJECT, projects = projectPage)
            every { searchService.searchInAll("yona", SearchType.PROJECT, null, any()) } returns result

            mockMvc.perform(get("/api/v1/search/projects").param("q", "yona"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[0].name").value("yona"))
        }

        it("검색어가 없으면 400을 반환한다") {
            mockMvc.perform(get("/api/v1/search/projects").param("q", "  "))
                .andExpect(status().isBadRequest)
        }
    }

    // yona-wiki P3-02 Step8.6 항목3(2026-09-01, 우선순위 3위) — `yona search prs`.
    describe("GET /api/v1/search/prs") {
        it("SearchService.searchInAll(PULL_REQUEST)에 위임한다") {
            val project = Project(id = 1L, owner = "yona", name = "yona")
            val contributor = User(id = 2L, loginId = "contributor", name = "기여자")
            val pr = PullRequest(id = 3L, number = 1L, title = "버그 수정 PR", fromProject = project, toProject = project, contributor = contributor)
            val prPage: Page<PullRequest> = PageImpl(listOf(pr), PageRequest.of(0, 20), 1)
            val result = SearchResult(keyword = "버그", searchType = SearchType.PULL_REQUEST, pullRequests = prPage)
            every { searchService.searchInAll("버그", SearchType.PULL_REQUEST, null, any()) } returns result

            mockMvc.perform(get("/api/v1/search/prs").param("q", "버그"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[0].title").value("버그 수정 PR"))

            verify(exactly = 1) { searchService.searchInAll("버그", SearchType.PULL_REQUEST, null, any()) }
        }

        it("검색어가 없으면 400을 반환한다") {
            mockMvc.perform(get("/api/v1/search/prs").param("q", ""))
                .andExpect(status().isBadRequest)
        }
    }
})
