package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.support.StatisticsService
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional
import io.mockk.clearMocks

class StatisticsControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val userRepository = mockk<UserRepository>()
    val statisticsService = mockk<StatisticsService>()

    val statisticsController = StatisticsController(
        projectRepository,
        userRepository,
        statisticsService
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(statisticsController).build()

    beforeTest {
        clearMocks(projectRepository, userRepository, statisticsService)
    }

    describe("StatisticsController 통계 기능 TDD 검증") {
        val project = Project(id = 1L, name = "TestProject", owner = "owner")
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")

        it("프로젝트 통계 페이지 요청 시 200 OK와 statistics 뷰를 반환해야 한다 (TDD Red 예상)") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProject") } returns Optional.of(project)

            mockMvc.perform(
                get("/projects/owner/TestProject/statistics")
            )
                .andExpect(status().isOk)
                .andExpect(view().name("project/statistics"))
                .andExpect(model().attributeExists("project"))
        }

        it("유저별 활동 통계 API 요청 시 200 OK와 통계 데이터 JSON을 반환해야 한다 (TDD Red 예상)") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { statisticsService.getUserStatistics(10L) } returns UserStatisticsResponse(
                issue = 10,
                posting = 5,
                assignedIssue = 2,
                issueComment = 4,
                postingComment = 1,
                issueVoter = 0,
                issueCommentVoter = 0
            )

            mockMvc.perform(
                get("/api/users/testuser/statistics")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.issue").value(10))
                .andExpect(jsonPath("$.posting").value(5))
        }

        it("존재하지 않는 프로젝트면 error/404 뷰를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "nosuch") } returns Optional.empty()

            mockMvc.perform(
                get("/projects/owner/nosuch/statistics")
            )
                .andExpect(status().isOk)
                .andExpect(view().name("error/404"))
        }

        it("존재하지 않는 로그인ID면 404를 반환해야 한다") {
            every { userRepository.findByLoginId("nosuch") } returns Optional.empty()

            mockMvc.perform(
                get("/api/users/nosuch/statistics")
            )
                .andExpect(status().isNotFound)
        }
    }
})

