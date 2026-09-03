package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.issue.Assignee
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueFilterType
import com.github.yonaprojects.yona.domain.issue.IssueService
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.*
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.Optional
import io.mockk.clearMocks

class FavoriteControllerSpec : DescribeSpec({
    val favoriteService = mockk<FavoriteService>()
    val issueService = mockk<IssueService>()
    val userRepository = mockk<UserRepository>()

    val favoriteController = FavoriteController(favoriteService, issueService, userRepository, "https://yona.example.com")
    val mockMvc = MockMvcBuilders.standaloneSetup(favoriteController).build()

    beforeTest {
        clearMocks(favoriteService, issueService, userRepository)
    }

    describe("FavoriteController 단위 테스트") {
        val user = User(id = 1L, loginId = "testuser", name = "테스트유저", email = "test@example.com")
        val auth = UsernamePasswordAuthenticationToken("testuser", "password")

        val project = Project(id = 10L, name = "testproject", owner = "testowner")
        val org = Organization(id = 20L, name = "testorg")
        val issue = Issue(id = 30L, title = "testissue", project = project)

        describe("POST /-_-api/v1/favoriteProjects/{projectId}") {
            it("성공적으로 프로젝트 즐겨찾기 상태를 토글해야 한다(추가)") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { favoriteService.toggleFavoriteProject(1L, 10L) } returns true

                mockMvc.perform(post("/-_-api/v1/favoriteProjects/10").principal(auth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.projectId").value("10"))
                    .andExpect(jsonPath("$.favored").value(true))
            }

            it("즐겨찾기가 해제되면 favored는 false여야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { favoriteService.toggleFavoriteProject(1L, 10L) } returns false

                mockMvc.perform(post("/-_-api/v1/favoriteProjects/10").principal(auth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.favored").value(false))
            }

            it("인증되지 않은 요청은 401을 반환해야 한다") {
                mockMvc.perform(post("/-_-api/v1/favoriteProjects/10"))
                    .andExpect(status().isUnauthorized)
            }
        }

        describe("GET /-_-api/v1/favoriteProjects") {
            it("즐겨찾는 프로젝트 목록을 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                val favProject = FavoriteProject(user = user, project = project).apply { id = 5L }
                every { favoriteService.getFavoriteProjects(1L) } returns listOf(favProject)

                mockMvc.perform(get("/-_-api/v1/favoriteProjects").principal(auth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.projectIds[0]").value(10))
                    .andExpect(jsonPath("$.projects[0].projectName").value("testproject"))
            }

            it("인증되지 않은 요청은 401을 반환해야 한다") {
                mockMvc.perform(get("/-_-api/v1/favoriteProjects"))
                    .andExpect(status().isUnauthorized)
            }
        }

        describe("POST /-_-api/v1/favoriteIssues/{issueId}") {
            it("성공적으로 이슈 즐겨찾기 상태를 토글해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { favoriteService.toggleFavoriteIssue(1L, 30L) } returns true

                mockMvc.perform(post("/-_-api/v1/favoriteIssues/30").principal(auth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.issueId").value("30"))
                    .andExpect(jsonPath("$.favored").value(true))
                    .andExpect(jsonPath("$.message").value("이슈가 즐겨찾기에 추가되었습니다."))
            }

            it("즐겨찾기가 해제되면 해제 메시지를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { favoriteService.toggleFavoriteIssue(1L, 30L) } returns false

                mockMvc.perform(post("/-_-api/v1/favoriteIssues/30").principal(auth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.favored").value(false))
                    .andExpect(jsonPath("$.message").value("이슈가 즐겨찾기에서 삭제되었습니다."))
            }

            it("인증되지 않은 요청은 401을 반환해야 한다") {
                mockMvc.perform(post("/-_-api/v1/favoriteIssues/30"))
                    .andExpect(status().isUnauthorized)
            }
        }

        describe("GET /-_-api/v1/favoriteIssues") {
            it("즐겨찾는 이슈 목록을 반환해야 한다(작성자 있음)") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                val namedIssue = Issue(id = 31L, title = "namedissue", project = project, authorName = "작성자")
                val favIssue = FavoriteIssue(id = 6L, user = user, issue = namedIssue)
                every { favoriteService.getFavoriteIssues(1L) } returns listOf(favIssue)

                mockMvc.perform(get("/-_-api/v1/favoriteIssues").principal(auth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.projectIds[0]").value(31))
                    .andExpect(jsonPath("$.projects[0].issueTitle").value("namedissue"))
                    .andExpect(jsonPath("$.projects[0].issueAuthorName").value("작성자"))
            }

            it("작성자 정보가 없으면 알수없음으로 표시해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                val favIssue = FavoriteIssue(id = 6L, user = user, issue = issue)
                every { favoriteService.getFavoriteIssues(1L) } returns listOf(favIssue)

                mockMvc.perform(get("/-_-api/v1/favoriteIssues").principal(auth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.projects[0].issueAuthorName").value("알수없음"))
            }

            it("인증되지 않은 요청은 401을 반환해야 한다") {
                mockMvc.perform(get("/-_-api/v1/favoriteIssues"))
                    .andExpect(status().isUnauthorized)
            }
        }

        describe("POST /-_-api/v1/favoriteOrganizations/{organizationId}") {
            it("성공적으로 조직 즐겨찾기 상태를 토글해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { favoriteService.toggleFavoriteOrganization(1L, 20L) } returns true

                mockMvc.perform(post("/-_-api/v1/favoriteOrganizations/20").principal(auth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.organizationId").value("20"))
                    .andExpect(jsonPath("$.favored").value(true))
            }

            it("즐겨찾기가 해제되면 favored는 false여야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { favoriteService.toggleFavoriteOrganization(1L, 20L) } returns false

                mockMvc.perform(post("/-_-api/v1/favoriteOrganizations/20").principal(auth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.favored").value(false))
            }

            it("인증되지 않은 요청은 401을 반환해야 한다") {
                mockMvc.perform(post("/-_-api/v1/favoriteOrganizations/20"))
                    .andExpect(status().isUnauthorized)
            }
        }

        describe("GET /-_-api/v1/user/issues") {
            it("filter=assigned로 요청하면 담당 이슈 목록을 legacy와 동일한 JSON 형식으로 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                val assignee = Assignee(id = 40L, user = user, project = project)
                val assignedIssue = Issue(
                    id = 32L, number = 7L, title = "담당이슈", project = project, state = State.OPEN,
                    authorId = 99L, authorLoginId = "author99", authorName = "작성자99",
                    assignee = assignee, createdDate = Instant.parse("2026-01-01T00:00:00Z"),
                    updatedDate = Instant.parse("2026-01-02T00:00:00Z")
                )
                every { issueService.getIssuesByFilter(IssueFilterType.ASSIGNED, user) } returns listOf(assignedIssue)

                mockMvc.perform(get("/-_-api/v1/user/issues").param("filter", "assigned").principal(auth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.result[0].id").value(32))
                    .andExpect(jsonPath("$.result[0].number").value(7))
                    .andExpect(jsonPath("$.result[0].state").value("OPEN"))
                    .andExpect(jsonPath("$.result[0].title").value("담당이슈"))
                    .andExpect(jsonPath("$.result[0].author.id").value(99))
                    .andExpect(jsonPath("$.result[0].author.loginId").value("author99"))
                    .andExpect(jsonPath("$.result[0].assignee.id").value(40))
                    .andExpect(jsonPath("$.result[0].assignee.loginId").value("testuser"))
                    .andExpect(jsonPath("$.result[0].project.id").value(10))
                    .andExpect(jsonPath("$.result[0].project.name").value("testproject"))
                    .andExpect(jsonPath("$.result[0].owner").value("testowner"))
                    .andExpect(jsonPath("$.result[0].refUrl").value("https://yona.example.com/testowner/testproject/issue/7"))
            }

            it("담당자가 없으면 assignee는 빈 객체여야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                val noAssigneeIssue = Issue(id = 33L, number = 8L, title = "담당자없음", project = project)
                every { issueService.getIssuesByFilter(IssueFilterType.CREATED, user) } returns listOf(noAssigneeIssue)

                mockMvc.perform(get("/-_-api/v1/user/issues").param("filter", "created").principal(auth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.result[0].assignee").isEmpty)
            }

            it("filter 기본값은 assigned여야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { issueService.getIssuesByFilter(IssueFilterType.ASSIGNED, user) } returns emptyList()

                mockMvc.perform(get("/-_-api/v1/user/issues").principal(auth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.result").isArray)
            }

            it("page/pageNum으로 페이지네이션이 적용되어야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                val issues = (1..25).map { i ->
                    Issue(id = i.toLong(), number = i.toLong(), title = "이슈$i", project = project)
                }
                every { issueService.getIssuesByFilter(IssueFilterType.ALL, user) } returns issues

                mockMvc.perform(
                    get("/-_-api/v1/user/issues")
                        .param("filter", "all").param("page", "2").param("pageNum", "10")
                        .principal(auth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.result.length()").value(10))
                    .andExpect(jsonPath("$.result[0].id").value(11))
                    .andExpect(jsonPath("$.result[9].id").value(20))
            }

            it("지원하지 않는 filter 값이면 400을 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)

                mockMvc.perform(get("/-_-api/v1/user/issues").param("filter", "kakao").principal(auth))
                    .andExpect(status().isBadRequest)
            }

            it("인증되지 않은 요청은 401을 반환해야 한다") {
                mockMvc.perform(get("/-_-api/v1/user/issues"))
                    .andExpect(status().isUnauthorized)
            }
        }

        describe("GET /-_-api/v1/favoriteOrganizations") {
            it("즐겨찾는 조직 목록을 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                val favOrg = FavoriteOrganization(user = user, organization = org)
                every { favoriteService.getFavoriteOrganizations(1L) } returns listOf(favOrg)

                mockMvc.perform(get("/-_-api/v1/favoriteOrganizations").principal(auth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.organizationIds[0]").value(20))
                    .andExpect(jsonPath("$.organizations[0].organizationName").value("testorg"))
            }

            it("인증되지 않은 요청은 401을 반환해야 한다") {
                mockMvc.perform(get("/-_-api/v1/favoriteOrganizations"))
                    .andExpect(status().isUnauthorized)
            }
        }
    }
})
