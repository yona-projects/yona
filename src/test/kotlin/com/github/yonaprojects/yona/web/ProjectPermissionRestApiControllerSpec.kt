package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.ResponseEntity
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

// yona-wiki P3-02 Step8.6 항목1(2026-09-01, 우선순위 1위) — `yona admin permission list`용 신규
// JSON REST API. owner/project 이름을 숫자 projectId로 바꿔 ProjectMemberController.listMembers()에
// 위임하는 얇은 어댑터인지만 검증한다.
class ProjectPermissionRestApiControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val projectMemberController = mockk<ProjectMemberController>()

    val controller = ProjectPermissionRestApiController(projectRepository, projectMemberController)
    val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    beforeTest {
        clearMocks(projectRepository, projectMemberController)
    }

    describe("GET /api/v1/projects/{owner}/{project}/permissions") {
        it("owner/project를 projectId로 바꿔 ProjectMemberController.listMembers에 위임한다") {
            val project = Project(id = 7L, owner = "yona", name = "yona")
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every { projectMemberController.listMembers(7L, any()) } returns ResponseEntity.ok(emptyList<Any>())

            mockMvc.perform(get("/api/v1/projects/yona/yona/permissions"))
                .andExpect(status().isOk)

            verify(exactly = 1) { projectMemberController.listMembers(7L, any()) }
        }

        it("존재하지 않는 프로젝트면 404를 반환한다") {
            every { projectRepository.findByOwnerAndName("yona", "nosuch") } returns Optional.empty()

            mockMvc.perform(get("/api/v1/projects/yona/nosuch/permissions"))
                .andExpect(status().isNotFound)

            verify(exactly = 0) { projectMemberController.listMembers(any(), any()) }
        }
    }
})
