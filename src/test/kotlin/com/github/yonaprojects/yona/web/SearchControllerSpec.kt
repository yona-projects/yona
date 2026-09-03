package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.enumeration.SearchType
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationUser
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.support.SearchResult
import com.github.yonaprojects.yona.domain.support.SearchService
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional
import io.mockk.clearMocks

class SearchControllerSpec : DescribeSpec({
    val searchService = mockk<SearchService>()
    val userRepository = mockk<UserRepository>()
    val projectRepository = mockk<ProjectRepository>()
    val organizationRepository = mockk<OrganizationRepository>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()

    val searchController = SearchController(
        searchService,
        userRepository,
        projectRepository,
        organizationRepository,
        organizationUserRepository
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(searchController).build()

    beforeTest {
        clearMocks(searchService, userRepository, projectRepository, organizationRepository, organizationUserRepository)
    }

    describe("SearchController 웹 진입점 테스트") {
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")
        val searchResult = SearchResult(keyword = "yona", searchType = SearchType.ISSUE)

        it("GET /search - 전역 검색 시 200 OK와 search/list 뷰를 반환해야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { searchService.searchInAll("yona", SearchType.ISSUE, user, any()) } returns searchResult

            mockMvc.perform(
                get("/search")
                    .param("keyword", "yona")
                    .param("searchType", "issue")
                    .principal(userAuth)
            )
                .andExpect(status().isOk)
                .andExpect(view().name("search/list"))
                .andExpect(model().attributeExists("keyword", "searchResult", "currentUser"))
        }

        it("GET /search - 익명 사용자가 전역 검색 시 200 OK와 search/list 뷰를 반환해야 한다") {
            every { searchService.searchInAll("yona", SearchType.ISSUE, null, any()) } returns searchResult

            mockMvc.perform(
                get("/search")
                    .param("keyword", "yona")
                    .param("searchType", "issue")
            )
                .andExpect(status().isOk)
                .andExpect(view().name("search/list"))
                .andExpect(model().attributeExists("keyword", "searchResult"))
                .andExpect(model().attribute("currentUser", null))
        }

        it("GET /search - 검색어가 비어있을 때 400 Bad Request를 반환해야 한다") {
            mockMvc.perform(
                get("/search")
                    .param("keyword", " ")
                    .param("searchType", "issue")
            )
                .andExpect(status().isBadRequest)
        }

        it("GET /search - searchType이 인식 불가(NA로 폴백)이면 400 Bad Request를 반환해야 한다 (P2-31, yona SearchApp.java:56-58)") {
            mockMvc.perform(
                get("/search")
                    .param("keyword", "yona")
                    .param("searchType", "not-a-real-type")
            )
                .andExpect(status().isBadRequest)
        }

        it("GET /org/{organizationName}/search - 조직 검색 시 200 OK와 search/list 뷰를 반환해야 한다") {
            val org = Organization(id = 5L, name = "testorg")
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { organizationRepository.findByName("testorg") } returns Optional.of(org)
            every { searchService.searchInAGroup("yona", SearchType.ISSUE, user, org, any()) } returns searchResult

            mockMvc.perform(
                get("/org/testorg/search")
                    .param("keyword", "yona")
                    .param("searchType", "issue")
                    .principal(userAuth)
            )
                .andExpect(status().isOk)
                .andExpect(view().name("search/list"))
                .andExpect(model().attributeExists("keyword", "searchResult", "currentUser", "org"))
        }

        it("GET /organizations/{organizationName}/search - 조직 검색 (전체 URL) 시 200 OK와 search/list 뷰를 반환해야 한다") {
            val org = Organization(id = 5L, name = "testorg")
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { organizationRepository.findByName("testorg") } returns Optional.of(org)
            every { searchService.searchInAGroup("yona", SearchType.ISSUE, user, org, any()) } returns searchResult

            mockMvc.perform(
                get("/organizations/testorg/search")
                    .param("keyword", "yona")
                    .param("searchType", "issue")
                    .principal(userAuth)
            )
                .andExpect(status().isOk)
                .andExpect(view().name("search/list"))
                .andExpect(model().attributeExists("keyword", "searchResult", "currentUser", "org"))
        }

        it("GET /org/{organizationName}/search - 익명 사용자도 200 OK를 반환해야 한다") {
            val org = Organization(id = 5L, name = "testorg")
            every { organizationRepository.findByName("testorg") } returns Optional.of(org)
            every { searchService.searchInAGroup("yona", SearchType.ISSUE, null, org, any()) } returns searchResult

            mockMvc.perform(
                get("/org/testorg/search")
                    .param("keyword", "yona")
                    .param("searchType", "issue")
            )
                .andExpect(status().isOk)
                .andExpect(model().attribute("currentUser", null))
        }

        it("GET /org/{organizationName}/search - 검색어가 비어있을 때 400 Bad Request를 반환해야 한다") {
            mockMvc.perform(
                get("/org/testorg/search")
                    .param("keyword", " ")
                    .param("searchType", "issue")
            )
                .andExpect(status().isBadRequest)
        }

        it("GET /org/{organizationName}/search - 존재하지 않는 조직이면 404를 반환해야 한다") {
            every { organizationRepository.findByName("nosuch") } returns Optional.empty()

            mockMvc.perform(
                get("/org/nosuch/search")
                    .param("keyword", "yona")
                    .param("searchType", "issue")
            )
                .andExpect(status().isNotFound)
        }

        it("GET /org/{organizationName}/search - searchType이 인식 불가(NA로 폴백)이면 400 Bad Request를 반환해야 한다 (P2-31, yona SearchApp.java:134-136)") {
            val org = Organization(id = 5L, name = "testorg")
            every { organizationRepository.findByName("testorg") } returns Optional.of(org)

            mockMvc.perform(
                get("/org/testorg/search")
                    .param("keyword", "yona")
                    .param("searchType", "not-a-real-type")
            )
                .andExpect(status().isBadRequest)
        }

        it("GET /{owner}/{projectName}/search - 프로젝트 검색 시 200 OK와 search/list 뷰를 반환해야 한다") {
            val project = Project(id = 1L, name = "TestProj", owner = "owner")
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
            every { searchService.searchInAProject("yona", SearchType.ISSUE, user, project, any()) } returns searchResult

            mockMvc.perform(
                get("/owner/TestProj/search")
                    .param("keyword", "yona")
                    .param("searchType", "issue")
                    .principal(userAuth)
            )
                .andExpect(status().isOk)
                .andExpect(view().name("search/list"))
                .andExpect(model().attributeExists("keyword", "searchResult", "currentUser", "project"))
        }

        it("GET /{owner}/{projectName}/search - searchType이 인식 불가(NA로 폴백)이면 400 Bad Request를 반환해야 한다 (P2-31, yona SearchApp.java:209-211)") {
            val project = Project(id = 1L, name = "TestProj", owner = "owner")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)

            mockMvc.perform(
                get("/owner/TestProj/search")
                    .param("keyword", "yona")
                    .param("searchType", "not-a-real-type")
            )
                .andExpect(status().isBadRequest)
        }

        it("GET /{owner}/{projectName}/search - searchType=project는 프로젝트 범위 검색에서 무의미해 400 Bad Request를 반환해야 한다 (P2-31, yona SearchApp.java:209-211)") {
            val project = Project(id = 1L, name = "TestProj", owner = "owner")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)

            mockMvc.perform(
                get("/owner/TestProj/search")
                    .param("keyword", "yona")
                    .param("searchType", "project")
            )
                .andExpect(status().isBadRequest)
        }

        it("GET /{owner}/{projectName}/search - 익명 사용자도 200 OK를 반환해야 한다") {
            val project = Project(id = 1L, name = "TestProj", owner = "owner")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
            every { searchService.searchInAProject("yona", SearchType.ISSUE, null, project, any()) } returns searchResult

            mockMvc.perform(
                get("/owner/TestProj/search")
                    .param("keyword", "yona")
                    .param("searchType", "issue")
            )
                .andExpect(status().isOk)
                .andExpect(model().attribute("currentUser", null))
        }

        it("GET /{owner}/{projectName}/search - 검색어가 비어있을 때 400 Bad Request를 반환해야 한다") {
            mockMvc.perform(
                get("/owner/TestProj/search")
                    .param("keyword", " ")
                    .param("searchType", "issue")
            )
                .andExpect(status().isBadRequest)
        }

        it("GET /{owner}/{projectName}/search - 존재하지 않는 프로젝트면 404를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "nosuch") } returns Optional.empty()

            mockMvc.perform(
                get("/owner/nosuch/search")
                    .param("keyword", "yona")
                    .param("searchType", "issue")
            )
                .andExpect(status().isNotFound)
        }
    }

    // yona SearchApp.java:126-130 대응 (P0-23) — HIDE_PROJECT_LISTING이 켜져 있을 때의 조직 검색 게이트.
    describe("HIDE_PROJECT_LISTING=true일 때 GET /org/{organizationName}/search") {
        val hiddenSearchController = SearchController(
            searchService, userRepository, projectRepository, organizationRepository,
            organizationUserRepository, hideProjectListing = true
        )
        val hiddenMockMvc = MockMvcBuilders.standaloneSetup(hiddenSearchController).build()

        it("ORG_MEMBER이면서 ORG_ADMIN이기도 한 사용자가 존재할 수 없어 항상 400을 반환해야 한다(legacy 원문 그대로)") {
            val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
            val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")
            val org = Organization(id = 5L, name = "testorg")
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { organizationRepository.findByName("testorg") } returns Optional.of(org)
            every { organizationUserRepository.findByOrganizationIdAndUserId(5L, 10L) } returns
                Optional.of(OrganizationUser(user = user, organization = org, role = Role(id = RoleType.ORG_ADMIN.roleType)))

            hiddenMockMvc.perform(
                get("/org/testorg/search")
                    .param("keyword", "yona")
                    .param("searchType", "issue")
                    .principal(userAuth)
            )
                .andExpect(status().isBadRequest)
        }

        it("ORG_MEMBER이지만 ORG_ADMIN은 아닌 사용자도 400을 반환해야 한다(OR 조건의 두 번째 피연산자 분기)") {
            val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
            val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")
            val org = Organization(id = 5L, name = "testorg")
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { organizationRepository.findByName("testorg") } returns Optional.of(org)
            every { organizationUserRepository.findByOrganizationIdAndUserId(5L, 10L) } returns
                Optional.of(OrganizationUser(user = user, organization = org, role = Role(id = RoleType.ORG_MEMBER.roleType)))

            hiddenMockMvc.perform(
                get("/org/testorg/search")
                    .param("keyword", "yona")
                    .param("searchType", "issue")
                    .principal(userAuth)
            )
                .andExpect(status().isBadRequest)
        }

        it("비로그인 사용자는 400을 반환해야 한다") {
            val org = Organization(id = 5L, name = "testorg")
            every { organizationRepository.findByName("testorg") } returns Optional.of(org)

            hiddenMockMvc.perform(
                get("/org/testorg/search")
                    .param("keyword", "yona")
                    .param("searchType", "issue")
            )
                .andExpect(status().isBadRequest)
        }

        // 로그인 사용자를 찾았어도 id가 없으면(orgUser 조회 자체를 생략) loginUser?.id?.let{} 체인의
        // id-null 분기를 태운다.
        it("로그인 사용자의 id가 없으면 조직 멤버십 조회 없이 400을 반환해야 한다") {
            val user = User(id = null, loginId = "noidyet", name = "아이디없음")
            val userAuth = UsernamePasswordAuthenticationToken("noidyet", "password")
            val org = Organization(id = 5L, name = "testorg")
            every { userRepository.findByLoginId("noidyet") } returns Optional.of(user)
            every { organizationRepository.findByName("testorg") } returns Optional.of(org)

            hiddenMockMvc.perform(
                get("/org/testorg/search")
                    .param("keyword", "yona")
                    .param("searchType", "issue")
                    .principal(userAuth)
            )
                .andExpect(status().isBadRequest)
        }

        // orgUser는 조회되지만 role.id가 null이면(미영속 Role) isMember/isAdmin 둘 다 "역할 불일치"가
        // 아니라 "id 자체가 없어서" false가 되는 별도 분기를 태운다. role 자체는 OrganizationUser의
        // 필수(non-null) 프로퍼티라 role이 null인 경우는 없다(구조적으로 도달 불가능).
        it("조직 멤버십은 있지만 역할의 id가 없으면 400을 반환해야 한다") {
            val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
            val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")
            val org = Organization(id = 5L, name = "testorg")
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { organizationRepository.findByName("testorg") } returns Optional.of(org)
            every { organizationUserRepository.findByOrganizationIdAndUserId(5L, 10L) } returns
                Optional.of(OrganizationUser(user = user, organization = org, role = Role(id = null)))

            hiddenMockMvc.perform(
                get("/org/testorg/search")
                    .param("keyword", "yona")
                    .param("searchType", "issue")
                    .principal(userAuth)
            )
                .andExpect(status().isBadRequest)
        }
    }
})
