package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

// yona-wiki P3-02 4라운드(Step8.5 서버 보강) — OrganizationRestApiController(/api/v1/organizations,
// /api/v1/organizations/{name}). 목록/조회 위임과 게스트 차단만 검증한다(getVisibleProjects 자체
// 로직은 AccessControlSpec에서 이미 검증됨).
class OrganizationRestApiControllerSpec : DescribeSpec({
    val organizationRepository = mockk<OrganizationRepository>()
    val userRepository = mockk<UserRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    every { organizationUserRepository.findByOrganizationIdAndUserId(any(), any()) } returns Optional.empty()
    val issueRepository = mockk<IssueRepository>()
    val postingRepository = mockk<PostingRepository>()
    val reviewCommentRepository = mockk<ReviewCommentRepository>()
    val commitCommentRepository = mockk<CommitCommentRepository>()
    val milestoneRepository = mockk<MilestoneRepository>()
    val accessControl = AccessControl(
        projectUserRepository, organizationUserRepository,
        userRepository, organizationRepository,
        issueRepository, postingRepository,
        reviewCommentRepository, commitCommentRepository,
        milestoneRepository
    )

    val controller = OrganizationRestApiController(organizationRepository, userRepository, accessControl, false)
    val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    beforeTest {
        clearMocks(organizationRepository, userRepository)
    }

    val auth = UsernamePasswordAuthenticationToken("tester", "password")
    val user = User(id = 1L, loginId = "tester", name = "테스터")

    describe("GET /api/v1/organizations") {
        it("조직 목록을 반환한다") {
            val org = Organization(id = 1L, name = "acme", descr = "설명")
            // PageImpl(content) 단일 인자 생성자는 Pageable.unpaged()를 써서 Page의 Jackson
            // 직렬화가 500(HttpMessageNotWritableException)으로 죽는다(SearchRestApiControllerSpec
            // 주석 참고) - 반드시 실제 Pageable + total을 준 3-인자 생성자를 쓴다.
            every {
                organizationRepository.findByNameContainingIgnoreCaseOrDescrContainingIgnoreCase(any(), any(), any())
            } returns PageImpl(listOf(org), PageRequest.of(0, 30), 1)

            mockMvc.perform(get("/api/v1/organizations"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[0].name").value("acme"))
        }

        it("게스트 사용자는 403을 반환한다") {
            val guest = User(id = 2L, loginId = "guest", name = "게스트", isGuest = true)
            every { userRepository.findByLoginId("tester") } returns Optional.of(guest)

            mockMvc.perform(get("/api/v1/organizations").principal(auth))
                .andExpect(status().isForbidden)
        }
    }

    describe("GET /api/v1/organizations/{name}") {
        it("조직이 없으면 404를 반환한다") {
            every { organizationRepository.findByName("unknown") } returns Optional.empty()

            mockMvc.perform(get("/api/v1/organizations/unknown"))
                .andExpect(status().isNotFound)
        }

        it("조직 정보와 보이는 프로젝트 목록을 반환한다") {
            val project = Project(id = 1L, owner = "acme", name = "repo1")
            val org = Organization(id = 1L, name = "acme", descr = "설명", projects = mutableListOf(project))
            every { organizationRepository.findByName("acme") } returns Optional.of(org)

            mockMvc.perform(get("/api/v1/organizations/acme"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value("acme"))
        }
    }
})
