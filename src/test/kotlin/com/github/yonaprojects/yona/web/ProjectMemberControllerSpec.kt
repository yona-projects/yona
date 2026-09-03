package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationUser
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.project.ProjectUserService
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.UserState
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.MessageSource
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import io.mockk.clearMocks

// yona AccessControl.isAllowedIfGroupMember() 대응 (P1-57) 회귀 테스트.
class ProjectMemberControllerSpec : DescribeSpec({
    val projectUserService = mockk<ProjectUserService>()
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val messageSource = mockk<MessageSource>()
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

    val projectMemberController = ProjectMemberController(
        projectUserService,
        projectRepository,
        projectUserRepository,
        userRepository,
        messageSource,
        accessControl,
        organizationUserRepository
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(projectMemberController).build()

    beforeTest {
        clearMocks(projectUserService, projectRepository, projectUserRepository, userRepository, messageSource)
    }

    describe("GET /api/projects/{projectId}/assignableUsers") {
        val user = User(id = 10L, loginId = "groupuser", name = "그룹멤버")
        val userAuth = UsernamePasswordAuthenticationToken("groupuser", "password")

        it("직접 멤버가 아니면 403 Forbidden을 반환해야 한다") {
            val project = Project(id = 1L, name = "TestProj", owner = "owner", projectScope = ProjectScope.PRIVATE)

            every { userRepository.findByLoginId("groupuser") } returns Optional.of(user)
            every { userRepository.findById(10L) } returns Optional.of(user)
            every { projectRepository.findById(1L) } returns Optional.of(project)
            every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns false

            mockMvc.perform(get("/api/projects/1/assignableUsers").principal(userAuth))
                .andExpect(status().isForbidden)
        }

        it("직접 멤버가 아니어도 프로젝트가 속한 조직의 멤버라면 200 OK를 반환해야 한다") {
            val groupOrg = Organization(id = 1L, name = "org")
            groupOrg.organizationUsers.add(
                OrganizationUser(id = 1L, user = user, organization = groupOrg, role = Role(id = RoleType.ORG_MEMBER.roleType))
            )
            val groupProject = Project(id = 9L, name = "group-project", owner = "owner", projectScope = ProjectScope.PROTECTED, organization = groupOrg)

            every { userRepository.findByLoginId("groupuser") } returns Optional.of(user)
            every { userRepository.findById(10L) } returns Optional.of(user)
            every { projectRepository.findById(9L) } returns Optional.of(groupProject)
            every { projectUserRepository.existsByProjectIdAndUserId(9L, 10L) } returns false
            every { projectUserRepository.findByProjectId(9L) } returns emptyList()
            every { organizationUserRepository.findByOrganizationId(1L) } returns
                listOf(OrganizationUser(id = 1L, user = user, organization = groupOrg, role = Role(id = RoleType.ORG_MEMBER.roleType)))
            every { userRepository.findAllById(any()) } returns listOf(user)
            every { messageSource.getMessage("issue.assignToMe", null, "나에게 할당하기", any()) } returns "나에게 할당하기"

            mockMvc.perform(get("/api/projects/9/assignableUsers").principal(userAuth))
                .andExpect(status().isOk)
        }

        // yona Project.java:566-568 getAssignableUsers() → User.java:446-478
        // findUsersByProjectAndOrganization() 대응 (P1-117).
        it("PRIVATE 프로젝트가 속한 조직이면 조직 관리자만 후보에 포함하고 일반 조직멤버는 제외해야 한다") {
            val groupOrg = Organization(id = 2L, name = "private-org")
            val orgAdmin = User(id = 20L, loginId = "orgadmin", name = "조직관리자")
            val project = Project(id = 11L, name = "private-group-project", owner = "owner", projectScope = ProjectScope.PRIVATE, organization = groupOrg)

            val requester = User(id = 40L, loginId = "requester", name = "요청자")
            requester.projectUsers.add(
                ProjectUser(id = 401L, user = requester, project = project, role = Role(id = RoleType.MEMBER.roleType))
            )
            val requesterAuth = UsernamePasswordAuthenticationToken("requester", "password")

            every { userRepository.findByLoginId("requester") } returns Optional.of(requester)
            every { userRepository.findById(40L) } returns Optional.of(requester)
            every { projectRepository.findById(11L) } returns Optional.of(project)
            every { projectUserRepository.findByProjectId(11L) } returns
                listOf(ProjectUser(id = 401L, user = requester, project = project, role = Role(id = RoleType.MEMBER.roleType)))
            every { organizationUserRepository.findByOrganizationIdAndRoleId(2L, RoleType.ORG_ADMIN.roleType) } returns
                listOf(OrganizationUser(id = 2L, user = orgAdmin, organization = groupOrg, role = Role(id = RoleType.ORG_ADMIN.roleType)))
            every { userRepository.findAllById(setOf(40L, 20L)) } returns listOf(requester, orgAdmin)
            every { messageSource.getMessage("issue.assignToMe", null, "나에게 할당하기", any()) } returns "나에게 할당하기"

            val result = mockMvc.perform(get("/api/projects/11/assignableUsers").principal(requesterAuth))
                .andExpect(status().isOk)
                .andReturn()

            val body = result.response.contentAsString
            (body.contains("orgadmin")) shouldBe true
            (body.contains("orgmember")) shouldBe false
            verify(exactly = 0) { organizationUserRepository.findByOrganizationId(2L) }

            // yona-wiki P3-02 13라운드(TASK-0430) — yona-cli가 loginId를 assigneeId(숫자)로
            // 변환할 방법이 없던 갭 해소를 위해 userId 필드를 추가했다. "나에게 할당하기" 항목
            // (요청자 40L)과 조직관리자 멤버 항목(20L) 둘 다 userId를 포함해야 한다.
            (body.contains("\"userId\":40")) shouldBe true
            (body.contains("\"userId\":20")) shouldBe true
        }

        it("사이트관리자는 프로젝트/조직 멤버가 아니어도 후보에 포함되어야 한다") {
            val siteManager = User(id = 30L, loginId = "siteadmin", name = "사이트관리자", state = UserState.SITE_ADMIN)
            val siteManagerAuth = UsernamePasswordAuthenticationToken("siteadmin", "password")
            val project = Project(id = 12L, name = "no-group-project", owner = "owner", projectScope = ProjectScope.PRIVATE)

            every { userRepository.findByLoginId("siteadmin") } returns Optional.of(siteManager)
            every { userRepository.findById(30L) } returns Optional.of(siteManager)
            every { projectRepository.findById(12L) } returns Optional.of(project)
            every { projectUserRepository.existsByProjectIdAndUserId(12L, 30L) } returns false
            every { projectUserRepository.findByProjectId(12L) } returns emptyList()
            every { userRepository.findAllById(setOf(30L)) } returns listOf(siteManager)
            every { messageSource.getMessage("issue.assignToMe", null, "나에게 할당하기", any()) } returns "나에게 할당하기"

            val result = mockMvc.perform(get("/api/projects/12/assignableUsers").principal(siteManagerAuth))
                .andExpect(status().isOk)
                .andReturn()

            result.response.contentAsString.contains("siteadmin") shouldBe true
        }
    }

    // getLoginUserId()의 두 예외 분기(인증 없음 / 로그인 사용자 미존재) 커버.
    // TASK-0417 — 예전엔 IllegalArgumentException을 그대로 던져 @RestController 기본 예외 처리기가
    // 500으로 응답했다("Unauthorized"라는 메시지와 전혀 안 맞는 상태 코드 — yona-cli
    // `admin permission add`를 실제 서버에 대고 재현해 발견). ResponseStatusException(401)으로
    // 바꿔 Spring MVC가 자동으로 401을 응답하도록 고쳤다.
    describe("getLoginUserId 예외 분기") {
        it("인증 정보가 없으면 ResponseStatusException(401 Unauthorized)을 던져야 한다") {
            val exception = shouldThrow<ResponseStatusException> {
                projectMemberController.enroll(1L, null)
            }
            exception.statusCode shouldBe HttpStatus.UNAUTHORIZED
            exception.reason shouldBe "Unauthorized"
        }

        it("로그인 아이디에 해당하는 사용자를 찾을 수 없으면 ResponseStatusException(401 Unauthorized)을 던져야 한다") {
            val auth = UsernamePasswordAuthenticationToken("ghost", "password")
            every { userRepository.findByLoginId("ghost") } returns Optional.empty()

            val exception = shouldThrow<ResponseStatusException> {
                projectMemberController.enroll(1L, auth)
            }
            exception.statusCode shouldBe HttpStatus.UNAUTHORIZED
            exception.reason shouldBe "User not found"
        }
    }

    describe("POST /api/projects/{projectId}/members - addMember") {
        it("매니저가 아니면(멤버 레코드 자체가 없으면) 403 Forbidden을 반환해야 한다") {
            val auth = UsernamePasswordAuthenticationToken("notmanager1", "password")
            val requester = User(id = 300L, loginId = "notmanager1", name = "일반유저")
            every { userRepository.findByLoginId("notmanager1") } returns Optional.of(requester)
            every { projectUserRepository.findByProjectIdAndUserId(1L, 300L) } returns Optional.empty()

            val result = projectMemberController.addMember(1L, "newuser", auth)

            result.statusCode shouldBe HttpStatus.FORBIDDEN
        }

        it("매니저이면 신규 멤버를 추가하고 200 OK를 반환해야 한다") {
            val auth = UsernamePasswordAuthenticationToken("manager1", "password")
            val manager = User(id = 301L, loginId = "manager1", name = "매니저")
            val project = Project(id = 1L, owner = "owner")
            every { userRepository.findByLoginId("manager1") } returns Optional.of(manager)
            every { projectUserRepository.findByProjectIdAndUserId(1L, 301L) } returns Optional.of(
                ProjectUser(id = 1L, user = manager, project = project, role = Role(id = RoleType.MANAGER.roleType))
            )
            every { projectUserService.addMember(1L, "newuser", 301L) } just Runs

            val result = projectMemberController.addMember(1L, "newuser", auth)

            result.statusCode shouldBe HttpStatus.OK
            result.body shouldBe mapOf("status" to "success")
            verify { projectUserService.addMember(1L, "newuser", 301L) }
        }

        it("서비스에서 메시지가 있는 예외가 발생하면 400과 그 메시지를 반환해야 한다") {
            val auth = UsernamePasswordAuthenticationToken("manager1b", "password")
            val manager = User(id = 302L, loginId = "manager1b", name = "매니저")
            val project = Project(id = 1L, owner = "owner")
            every { userRepository.findByLoginId("manager1b") } returns Optional.of(manager)
            every { projectUserRepository.findByProjectIdAndUserId(1L, 302L) } returns Optional.of(
                ProjectUser(id = 1L, user = manager, project = project, role = Role(id = RoleType.MANAGER.roleType))
            )
            every { projectUserService.addMember(1L, "dupuser", 302L) } throws RuntimeException("이미 등록된 사용자입니다")

            val result = projectMemberController.addMember(1L, "dupuser", auth)

            result.statusCode shouldBe HttpStatus.BAD_REQUEST
            result.body shouldBe mapOf("error" to "이미 등록된 사용자입니다")
        }

        it("서비스에서 메시지 없는 예외가 발생하면 400과 기본 에러 메시지를 반환해야 한다") {
            val auth = UsernamePasswordAuthenticationToken("manager1c", "password")
            val manager = User(id = 303L, loginId = "manager1c", name = "매니저")
            val project = Project(id = 1L, owner = "owner")
            every { userRepository.findByLoginId("manager1c") } returns Optional.of(manager)
            every { projectUserRepository.findByProjectIdAndUserId(1L, 303L) } returns Optional.of(
                ProjectUser(id = 1L, user = manager, project = project, role = Role(id = RoleType.MANAGER.roleType))
            )
            every { projectUserService.addMember(1L, "nouser", 303L) } throws RuntimeException()

            val result = projectMemberController.addMember(1L, "nouser", auth)

            result.statusCode shouldBe HttpStatus.BAD_REQUEST
            result.body shouldBe mapOf("error" to "Failed to add member")
        }
    }

    describe("PUT /api/projects/{projectId}/members/{userId}/role - updateMemberRole") {
        it("매니저가 아니면(역할 id가 null인 레코드) 403 Forbidden을 반환해야 한다") {
            val auth = UsernamePasswordAuthenticationToken("notmanager2", "password")
            val requester = User(id = 800L, loginId = "notmanager2", name = "일반유저2")
            val project = Project(id = 2L, owner = "owner")
            every { userRepository.findByLoginId("notmanager2") } returns Optional.of(requester)
            every { projectUserRepository.findByProjectIdAndUserId(2L, 800L) } returns Optional.of(
                ProjectUser(id = 1L, user = requester, project = project, role = Role(id = null))
            )

            val result = projectMemberController.updateMemberRole(2L, 900L, RoleType.MEMBER.roleType, auth)

            result.statusCode shouldBe HttpStatus.FORBIDDEN
        }

        it("매니저이면 역할을 변경하고 200 OK를 반환해야 한다") {
            val auth = UsernamePasswordAuthenticationToken("manager2", "password")
            val manager = User(id = 801L, loginId = "manager2", name = "매니저2")
            val project = Project(id = 2L, owner = "owner")
            every { userRepository.findByLoginId("manager2") } returns Optional.of(manager)
            every { projectUserRepository.findByProjectIdAndUserId(2L, 801L) } returns Optional.of(
                ProjectUser(id = 2L, user = manager, project = project, role = Role(id = RoleType.MANAGER.roleType))
            )
            every { projectUserService.updateMemberRole(2L, 900L, RoleType.MEMBER.roleType, 801L) } just Runs

            val result = projectMemberController.updateMemberRole(2L, 900L, RoleType.MEMBER.roleType, auth)

            result.statusCode shouldBe HttpStatus.OK
            verify { projectUserService.updateMemberRole(2L, 900L, RoleType.MEMBER.roleType, 801L) }
        }

        it("서비스에서 메시지가 있는 예외가 발생하면 400과 그 메시지를 반환해야 한다") {
            val auth = UsernamePasswordAuthenticationToken("manager2b", "password")
            val manager = User(id = 802L, loginId = "manager2b", name = "매니저2b")
            val project = Project(id = 2L, owner = "owner")
            every { userRepository.findByLoginId("manager2b") } returns Optional.of(manager)
            every { projectUserRepository.findByProjectIdAndUserId(2L, 802L) } returns Optional.of(
                ProjectUser(id = 3L, user = manager, project = project, role = Role(id = RoleType.MANAGER.roleType))
            )
            every { projectUserService.updateMemberRole(2L, 900L, 999L, 802L) } throws RuntimeException("유효하지 않은 역할입니다")

            val result = projectMemberController.updateMemberRole(2L, 900L, 999L, auth)

            result.statusCode shouldBe HttpStatus.BAD_REQUEST
            result.body shouldBe mapOf("error" to "유효하지 않은 역할입니다")
        }

        it("서비스에서 메시지 없는 예외가 발생하면 400과 기본 에러 메시지를 반환해야 한다") {
            val auth = UsernamePasswordAuthenticationToken("manager2c", "password")
            val manager = User(id = 803L, loginId = "manager2c", name = "매니저2c")
            val project = Project(id = 2L, owner = "owner")
            every { userRepository.findByLoginId("manager2c") } returns Optional.of(manager)
            every { projectUserRepository.findByProjectIdAndUserId(2L, 803L) } returns Optional.of(
                ProjectUser(id = 4L, user = manager, project = project, role = Role(id = RoleType.MANAGER.roleType))
            )
            every { projectUserService.updateMemberRole(2L, 900L, 999L, 803L) } throws RuntimeException()

            val result = projectMemberController.updateMemberRole(2L, 900L, 999L, auth)

            result.statusCode shouldBe HttpStatus.BAD_REQUEST
            result.body shouldBe mapOf("error" to "Failed to update role")
        }
    }

    describe("DELETE /api/projects/{projectId}/members/{userId} - removeMember") {
        it("본인 탈퇴는 매니저가 아니어도 200 OK를 반환해야 한다(isProjectManager 호출되지 않음)") {
            val auth = UsernamePasswordAuthenticationToken("selfuser", "password")
            val self = User(id = 400L, loginId = "selfuser", name = "본인")
            every { userRepository.findByLoginId("selfuser") } returns Optional.of(self)
            every { projectUserService.removeMember(1L, 400L, 400L) } just Runs

            val result = projectMemberController.removeMember(1L, 400L, auth)

            result.statusCode shouldBe HttpStatus.OK
            verify { projectUserService.removeMember(1L, 400L, 400L) }
            // 본인 탈퇴는 currentUserId == userId 로 단락 평가되어 isProjectManager 조회 자체가 없어야 한다.
            verify(exactly = 0) { projectUserRepository.findByProjectIdAndUserId(any(), any()) }
        }

        it("본인이 아니고 매니저이면 다른 멤버를 제거하고 200 OK를 반환해야 한다") {
            val auth = UsernamePasswordAuthenticationToken("manager3", "password")
            val manager = User(id = 401L, loginId = "manager3", name = "매니저3")
            val project = Project(id = 1L, owner = "owner")
            every { userRepository.findByLoginId("manager3") } returns Optional.of(manager)
            every { projectUserRepository.findByProjectIdAndUserId(1L, 401L) } returns Optional.of(
                ProjectUser(id = 1L, user = manager, project = project, role = Role(id = RoleType.MANAGER.roleType))
            )
            every { projectUserService.removeMember(1L, 999L, 401L) } just Runs

            val result = projectMemberController.removeMember(1L, 999L, auth)

            result.statusCode shouldBe HttpStatus.OK
        }

        it("본인이 아니고 매니저도 아니면 403 Forbidden을 반환해야 한다") {
            val auth = UsernamePasswordAuthenticationToken("notmanager3", "password")
            val requester = User(id = 402L, loginId = "notmanager3", name = "일반유저3")
            val project = Project(id = 1L, owner = "owner")
            every { userRepository.findByLoginId("notmanager3") } returns Optional.of(requester)
            every { projectUserRepository.findByProjectIdAndUserId(1L, 402L) } returns Optional.of(
                ProjectUser(id = 2L, user = requester, project = project, role = Role(id = RoleType.MEMBER.roleType))
            )

            val result = projectMemberController.removeMember(1L, 999L, auth)

            result.statusCode shouldBe HttpStatus.FORBIDDEN
        }

        it("서비스에서 메시지가 있는 예외가 발생하면 400과 그 메시지를 반환해야 한다") {
            val auth = UsernamePasswordAuthenticationToken("selfuser2", "password")
            val self = User(id = 403L, loginId = "selfuser2", name = "본인2")
            every { userRepository.findByLoginId("selfuser2") } returns Optional.of(self)
            every { projectUserService.removeMember(1L, 403L, 403L) } throws RuntimeException("존재하지 않는 멤버입니다")

            val result = projectMemberController.removeMember(1L, 403L, auth)

            result.statusCode shouldBe HttpStatus.BAD_REQUEST
            result.body shouldBe mapOf("error" to "존재하지 않는 멤버입니다")
        }

        it("서비스에서 메시지 없는 예외가 발생하면 400과 기본 에러 메시지를 반환해야 한다") {
            val auth = UsernamePasswordAuthenticationToken("selfuser3", "password")
            val self = User(id = 404L, loginId = "selfuser3", name = "본인3")
            every { userRepository.findByLoginId("selfuser3") } returns Optional.of(self)
            every { projectUserService.removeMember(1L, 404L, 404L) } throws RuntimeException()

            val result = projectMemberController.removeMember(1L, 404L, auth)

            result.statusCode shouldBe HttpStatus.BAD_REQUEST
            result.body shouldBe mapOf("error" to "Failed to remove member")
        }
    }

    describe("POST /api/projects/{projectId}/enroll - enroll") {
        it("정상적으로 참여 신청하면 200 OK를 반환해야 한다") {
            val auth = UsernamePasswordAuthenticationToken("enrolluser1", "password")
            val user = User(id = 500L, loginId = "enrolluser1", name = "신청자1")
            every { userRepository.findByLoginId("enrolluser1") } returns Optional.of(user)
            every { projectUserService.enroll(1L, 500L) } just Runs

            val result = projectMemberController.enroll(1L, auth)

            result.statusCode shouldBe HttpStatus.OK
            result.body shouldBe mapOf("status" to "success")
        }

        it("서비스에서 메시지가 있는 예외가 발생하면 400과 그 메시지를 반환해야 한다") {
            val auth = UsernamePasswordAuthenticationToken("enrolluser2", "password")
            val user = User(id = 501L, loginId = "enrolluser2", name = "신청자2")
            every { userRepository.findByLoginId("enrolluser2") } returns Optional.of(user)
            every { projectUserService.enroll(1L, 501L) } throws RuntimeException("이미 신청했습니다")

            val result = projectMemberController.enroll(1L, auth)

            result.statusCode shouldBe HttpStatus.BAD_REQUEST
            result.body shouldBe mapOf("error" to "이미 신청했습니다")
        }

        it("서비스에서 메시지 없는 예외가 발생하면 400과 기본 에러 메시지를 반환해야 한다") {
            val auth = UsernamePasswordAuthenticationToken("enrolluser3", "password")
            val user = User(id = 502L, loginId = "enrolluser3", name = "신청자3")
            every { userRepository.findByLoginId("enrolluser3") } returns Optional.of(user)
            every { projectUserService.enroll(1L, 502L) } throws RuntimeException()

            val result = projectMemberController.enroll(1L, auth)

            result.statusCode shouldBe HttpStatus.BAD_REQUEST
            result.body shouldBe mapOf("error" to "Failed to enroll")
        }
    }

    describe("POST /api/projects/{projectId}/enroll/cancel - cancelEnroll") {
        it("정상적으로 참여 신청을 취소하면 200 OK를 반환해야 한다") {
            val auth = UsernamePasswordAuthenticationToken("cancelenroll1", "password")
            val user = User(id = 510L, loginId = "cancelenroll1", name = "취소자1")
            every { userRepository.findByLoginId("cancelenroll1") } returns Optional.of(user)
            every { projectUserService.cancelEnroll(1L, 510L) } just Runs

            val result = projectMemberController.cancelEnroll(1L, auth)

            result.statusCode shouldBe HttpStatus.OK
            result.body shouldBe mapOf("status" to "success")
        }

        it("서비스에서 메시지가 있는 예외가 발생하면 400과 그 메시지를 반환해야 한다") {
            val auth = UsernamePasswordAuthenticationToken("cancelenroll2", "password")
            val user = User(id = 511L, loginId = "cancelenroll2", name = "취소자2")
            every { userRepository.findByLoginId("cancelenroll2") } returns Optional.of(user)
            every { projectUserService.cancelEnroll(1L, 511L) } throws RuntimeException("신청 내역이 없습니다")

            val result = projectMemberController.cancelEnroll(1L, auth)

            result.statusCode shouldBe HttpStatus.BAD_REQUEST
            result.body shouldBe mapOf("error" to "신청 내역이 없습니다")
        }

        it("서비스에서 메시지 없는 예외가 발생하면 400과 기본 에러 메시지를 반환해야 한다") {
            val auth = UsernamePasswordAuthenticationToken("cancelenroll3", "password")
            val user = User(id = 512L, loginId = "cancelenroll3", name = "취소자3")
            every { userRepository.findByLoginId("cancelenroll3") } returns Optional.of(user)
            every { projectUserService.cancelEnroll(1L, 512L) } throws RuntimeException()

            val result = projectMemberController.cancelEnroll(1L, auth)

            result.statusCode shouldBe HttpStatus.BAD_REQUEST
            result.body shouldBe mapOf("error" to "Failed to cancel enroll")
        }
    }

    describe("POST /api/projects/{projectId}/members/{userId}/accept - acceptMemberRequest") {
        it("매니저가 아니면 403 Forbidden을 반환해야 한다") {
            val auth = UsernamePasswordAuthenticationToken("notmanager4", "password")
            val requester = User(id = 520L, loginId = "notmanager4", name = "일반유저4")
            val project = Project(id = 3L, owner = "owner")
            every { userRepository.findByLoginId("notmanager4") } returns Optional.of(requester)
            every { projectUserRepository.findByProjectIdAndUserId(3L, 520L) } returns Optional.of(
                ProjectUser(id = 1L, user = requester, project = project, role = Role(id = RoleType.MEMBER.roleType))
            )

            val result = projectMemberController.acceptMemberRequest(3L, 900L, auth)

            result.statusCode shouldBe HttpStatus.FORBIDDEN
        }

        it("매니저이면 가입 요청을 수락하고 200 OK를 반환해야 한다") {
            val auth = UsernamePasswordAuthenticationToken("manager4", "password")
            val manager = User(id = 521L, loginId = "manager4", name = "매니저4")
            val project = Project(id = 3L, owner = "owner")
            every { userRepository.findByLoginId("manager4") } returns Optional.of(manager)
            every { projectUserRepository.findByProjectIdAndUserId(3L, 521L) } returns Optional.of(
                ProjectUser(id = 2L, user = manager, project = project, role = Role(id = RoleType.MANAGER.roleType))
            )
            every { projectUserService.acceptMemberRequest(3L, 900L, 521L) } just Runs

            val result = projectMemberController.acceptMemberRequest(3L, 900L, auth)

            result.statusCode shouldBe HttpStatus.OK
            verify { projectUserService.acceptMemberRequest(3L, 900L, 521L) }
        }

        it("서비스에서 메시지가 있는 예외가 발생하면 400과 그 메시지를 반환해야 한다") {
            val auth = UsernamePasswordAuthenticationToken("manager4b", "password")
            val manager = User(id = 522L, loginId = "manager4b", name = "매니저4b")
            val project = Project(id = 3L, owner = "owner")
            every { userRepository.findByLoginId("manager4b") } returns Optional.of(manager)
            every { projectUserRepository.findByProjectIdAndUserId(3L, 522L) } returns Optional.of(
                ProjectUser(id = 3L, user = manager, project = project, role = Role(id = RoleType.MANAGER.roleType))
            )
            every { projectUserService.acceptMemberRequest(3L, 900L, 522L) } throws RuntimeException("가입 요청이 없습니다")

            val result = projectMemberController.acceptMemberRequest(3L, 900L, auth)

            result.statusCode shouldBe HttpStatus.BAD_REQUEST
            result.body shouldBe mapOf("error" to "가입 요청이 없습니다")
        }

        it("서비스에서 메시지 없는 예외가 발생하면 400과 기본 에러 메시지를 반환해야 한다") {
            val auth = UsernamePasswordAuthenticationToken("manager4c", "password")
            val manager = User(id = 523L, loginId = "manager4c", name = "매니저4c")
            val project = Project(id = 3L, owner = "owner")
            every { userRepository.findByLoginId("manager4c") } returns Optional.of(manager)
            every { projectUserRepository.findByProjectIdAndUserId(3L, 523L) } returns Optional.of(
                ProjectUser(id = 4L, user = manager, project = project, role = Role(id = RoleType.MANAGER.roleType))
            )
            every { projectUserService.acceptMemberRequest(3L, 900L, 523L) } throws RuntimeException()

            val result = projectMemberController.acceptMemberRequest(3L, 900L, auth)

            result.statusCode shouldBe HttpStatus.BAD_REQUEST
            result.body shouldBe mapOf("error" to "Failed to accept member request")
        }
    }

    describe("POST /api/projects/{projectId}/members/{userId}/reject - rejectMemberRequest") {
        it("매니저가 아니면 403 Forbidden을 반환해야 한다") {
            val auth = UsernamePasswordAuthenticationToken("notmanager5", "password")
            val requester = User(id = 530L, loginId = "notmanager5", name = "일반유저5")
            val project = Project(id = 4L, owner = "owner")
            every { userRepository.findByLoginId("notmanager5") } returns Optional.of(requester)
            every { projectUserRepository.findByProjectIdAndUserId(4L, 530L) } returns Optional.of(
                ProjectUser(id = 1L, user = requester, project = project, role = Role(id = RoleType.MEMBER.roleType))
            )

            val result = projectMemberController.rejectMemberRequest(4L, 900L, auth)

            result.statusCode shouldBe HttpStatus.FORBIDDEN
        }

        it("매니저이면 가입 요청을 거부하고 200 OK를 반환해야 한다") {
            val auth = UsernamePasswordAuthenticationToken("manager5", "password")
            val manager = User(id = 531L, loginId = "manager5", name = "매니저5")
            val project = Project(id = 4L, owner = "owner")
            every { userRepository.findByLoginId("manager5") } returns Optional.of(manager)
            every { projectUserRepository.findByProjectIdAndUserId(4L, 531L) } returns Optional.of(
                ProjectUser(id = 2L, user = manager, project = project, role = Role(id = RoleType.MANAGER.roleType))
            )
            every { projectUserService.rejectMemberRequest(4L, 900L, 531L) } just Runs

            val result = projectMemberController.rejectMemberRequest(4L, 900L, auth)

            result.statusCode shouldBe HttpStatus.OK
            verify { projectUserService.rejectMemberRequest(4L, 900L, 531L) }
        }

        it("서비스에서 메시지가 있는 예외가 발생하면 400과 그 메시지를 반환해야 한다") {
            val auth = UsernamePasswordAuthenticationToken("manager5b", "password")
            val manager = User(id = 532L, loginId = "manager5b", name = "매니저5b")
            val project = Project(id = 4L, owner = "owner")
            every { userRepository.findByLoginId("manager5b") } returns Optional.of(manager)
            every { projectUserRepository.findByProjectIdAndUserId(4L, 532L) } returns Optional.of(
                ProjectUser(id = 3L, user = manager, project = project, role = Role(id = RoleType.MANAGER.roleType))
            )
            every { projectUserService.rejectMemberRequest(4L, 900L, 532L) } throws RuntimeException("가입 요청이 없습니다")

            val result = projectMemberController.rejectMemberRequest(4L, 900L, auth)

            result.statusCode shouldBe HttpStatus.BAD_REQUEST
            result.body shouldBe mapOf("error" to "가입 요청이 없습니다")
        }

        it("서비스에서 메시지 없는 예외가 발생하면 400과 기본 에러 메시지를 반환해야 한다") {
            val auth = UsernamePasswordAuthenticationToken("manager5c", "password")
            val manager = User(id = 533L, loginId = "manager5c", name = "매니저5c")
            val project = Project(id = 4L, owner = "owner")
            every { userRepository.findByLoginId("manager5c") } returns Optional.of(manager)
            every { projectUserRepository.findByProjectIdAndUserId(4L, 533L) } returns Optional.of(
                ProjectUser(id = 4L, user = manager, project = project, role = Role(id = RoleType.MANAGER.roleType))
            )
            every { projectUserService.rejectMemberRequest(4L, 900L, 533L) } throws RuntimeException()

            val result = projectMemberController.rejectMemberRequest(4L, 900L, auth)

            result.statusCode shouldBe HttpStatus.BAD_REQUEST
            result.body shouldBe mapOf("error" to "Failed to reject member request")
        }
    }

    // assignableUsers()의 나머지 분기(프로젝트 미존재/currentUser null/방어적 null-id 스킵/
    // 사이트관리자 id null/query 검색어 매칭/messageSource 폴백) 커버.
    describe("GET /api/projects/{projectId}/assignableUsers 추가 분기") {
        it("프로젝트가 존재하지 않으면 404 Not Found를 반환해야 한다") {
            val auth = UsernamePasswordAuthenticationToken("someuser", "password")
            val someUser = User(id = 600L, loginId = "someuser", name = "유저")
            every { userRepository.findByLoginId("someuser") } returns Optional.of(someUser)
            every { projectRepository.findById(999L) } returns Optional.empty()

            val result = projectMemberController.assignableUsers(999L, "", auth)

            result.statusCode shouldBe HttpStatus.NOT_FOUND
        }

        it("currentUser를 조회하지 못해도(null) public 프로젝트면 200 OK를 반환해야 한다") {
            val auth = UsernamePasswordAuthenticationToken("pubuser", "password")
            val authUser = User(id = 601L, loginId = "pubuser", name = "공개유저")
            val project = Project(id = 21L, name = "pub", owner = "owner", projectScope = ProjectScope.PUBLIC)
            every { userRepository.findByLoginId("pubuser") } returns Optional.of(authUser)
            every { projectRepository.findById(21L) } returns Optional.of(project)
            every { userRepository.findById(601L) } returns Optional.empty()
            every { projectUserRepository.findByProjectId(21L) } returns emptyList()
            every { userRepository.findAllById(emptySet<Long>()) } returns emptyList()

            val result = projectMemberController.assignableUsers(21L, "", auth)

            result.statusCode shouldBe HttpStatus.OK
            result.body shouldBe emptyList()
            verify(exactly = 0) { messageSource.getMessage(any(), any(), any(), any()) }
        }

        it("findByProjectId 결과에 user.id가 null인 멤버가 있어도 예외 없이 무시해야 한다") {
            val auth = UsernamePasswordAuthenticationToken("requester2", "password")
            val requester = User(id = 602L, loginId = "requester2", name = "요청자2")
            val project = Project(id = 22L, name = "proj22", owner = "owner", projectScope = ProjectScope.PUBLIC)
            val orphanUser = User(id = null, loginId = "orphan", name = "고아유저")
            val validMember = User(id = 603L, loginId = "valid", name = "정상유저")

            every { userRepository.findByLoginId("requester2") } returns Optional.of(requester)
            every { projectRepository.findById(22L) } returns Optional.of(project)
            every { userRepository.findById(602L) } returns Optional.of(requester)
            every { projectUserRepository.findByProjectId(22L) } returns listOf(
                ProjectUser(id = 1L, user = orphanUser, project = project, role = Role(id = RoleType.MEMBER.roleType)),
                ProjectUser(id = 2L, user = validMember, project = project, role = Role(id = RoleType.MEMBER.roleType))
            )
            every { userRepository.findAllById(setOf(603L)) } returns listOf(validMember)
            every { messageSource.getMessage("issue.assignToMe", null, "나에게 할당하기", any()) } returns "나에게 할당하기"

            val result = projectMemberController.assignableUsers(22L, "", auth)

            result.statusCode shouldBe HttpStatus.OK
        }

        it("조직 멤버 중 user.id가 null인 사용자가 있어도 예외 없이 무시해야 한다") {
            val auth = UsernamePasswordAuthenticationToken("requester3", "password")
            val requester = User(id = 604L, loginId = "requester3", name = "요청자3")
            val org = Organization(id = 5L, name = "org5")
            // isAllowedIfGroupMember는 organization.organizationUsers 인메모리 컬렉션을 보므로,
            // 접근 허용을 위해 요청자를 그 컬렉션에도 추가해둔다.
            org.organizationUsers.add(
                OrganizationUser(id = 100L, user = requester, organization = org, role = Role(id = RoleType.ORG_MEMBER.roleType))
            )
            val project = Project(id = 23L, name = "proj23", owner = "owner", projectScope = ProjectScope.PROTECTED, organization = org)
            val orphanOrgUser = User(id = null, loginId = "orgOrphan", name = "조직고아")
            val validOrgUser = User(id = 605L, loginId = "orgvalid", name = "조직정상")

            every { userRepository.findByLoginId("requester3") } returns Optional.of(requester)
            every { projectRepository.findById(23L) } returns Optional.of(project)
            every { userRepository.findById(604L) } returns Optional.of(requester)
            every { projectUserRepository.findByProjectId(23L) } returns emptyList()
            every { organizationUserRepository.findByOrganizationId(5L) } returns listOf(
                OrganizationUser(id = 1L, user = orphanOrgUser, organization = org, role = Role(id = RoleType.ORG_MEMBER.roleType)),
                OrganizationUser(id = 2L, user = validOrgUser, organization = org, role = Role(id = RoleType.ORG_MEMBER.roleType))
            )
            every { userRepository.findAllById(setOf(605L)) } returns listOf(validOrgUser)
            every { messageSource.getMessage("issue.assignToMe", null, "나에게 할당하기", any()) } returns "나에게 할당하기"

            val result = projectMemberController.assignableUsers(23L, "", auth)

            result.statusCode shouldBe HttpStatus.OK
        }

        it("사이트관리자의 currentUser.id가 null이어도 예외 없이 처리해야 한다") {
            val auth = UsernamePasswordAuthenticationToken("siteadmin2", "password")
            val authUser = User(id = 99L, loginId = "siteadmin2", name = "관리자로그인")
            // findById가 반환하는 currentUser는 별도 인스턴스로, id가 null인 방어적 케이스를 재현한다.
            val currentUserWithNullId = User(id = null, loginId = "siteadmin2", name = "관리자", state = UserState.SITE_ADMIN)
            val project = Project(id = 24L, name = "proj24", owner = "owner", projectScope = ProjectScope.PRIVATE)

            every { userRepository.findByLoginId("siteadmin2") } returns Optional.of(authUser)
            every { projectRepository.findById(24L) } returns Optional.of(project)
            every { userRepository.findById(99L) } returns Optional.of(currentUserWithNullId)
            every { projectUserRepository.findByProjectId(24L) } returns emptyList()
            every { userRepository.findAllById(emptySet<Long>()) } returns emptyList()
            every { messageSource.getMessage("issue.assignToMe", null, "나에게 할당하기", any()) } returns "나에게 할당하기"

            val result = projectMemberController.assignableUsers(24L, "", auth)

            result.statusCode shouldBe HttpStatus.OK
            result.body?.size shouldBe 1
        }

        it("query가 주어지면 loginId/name/englishName 일치 여부로 필터링하고 나에게 할당하기 항목은 제외해야 한다") {
            val auth = UsernamePasswordAuthenticationToken("searcher1", "password")
            val requester = User(id = 700L, loginId = "searcher1", name = "검색자")
            val project = Project(id = 25L, name = "proj25", owner = "owner", projectScope = ProjectScope.PUBLIC)

            val byLoginId = User(id = 701L, loginId = "abcuser1", name = "사용자1")
            val byName = User(id = 702L, loginId = "user702", name = "ABC유저")
            val byEnglishName = User(id = 703L, loginId = "user703", name = "유저703", englishName = "Team-ABC")
            val noEnglishName = User(id = 704L, loginId = "user704", name = "유저704", englishName = null)
            val nonMatchingEnglishName = User(id = 705L, loginId = "user705", name = "유저705", englishName = "other")

            every { userRepository.findByLoginId("searcher1") } returns Optional.of(requester)
            every { projectRepository.findById(25L) } returns Optional.of(project)
            every { userRepository.findById(700L) } returns Optional.of(requester)
            every { projectUserRepository.findByProjectId(25L) } returns listOf(
                ProjectUser(id = 1L, user = byLoginId, project = project, role = Role(id = RoleType.MEMBER.roleType)),
                ProjectUser(id = 2L, user = byName, project = project, role = Role(id = RoleType.MEMBER.roleType)),
                ProjectUser(id = 3L, user = byEnglishName, project = project, role = Role(id = RoleType.MEMBER.roleType)),
                ProjectUser(id = 4L, user = noEnglishName, project = project, role = Role(id = RoleType.MEMBER.roleType)),
                ProjectUser(id = 5L, user = nonMatchingEnglishName, project = project, role = Role(id = RoleType.MEMBER.roleType))
            )
            every { userRepository.findAllById(setOf(701L, 702L, 703L, 704L, 705L)) } returns
                listOf(byLoginId, byName, byEnglishName, noEnglishName, nonMatchingEnglishName)

            val result = projectMemberController.assignableUsers(25L, "abc", auth)

            result.statusCode shouldBe HttpStatus.OK
            val body = result.body!!
            body.size shouldBe 3
            (body.any { it["loginId"] == "abcuser1" }) shouldBe true
            (body.any { it["loginId"] == "user702" }) shouldBe true
            (body.any { it["loginId"] == "user703" }) shouldBe true
            (body.any { it["name"] == "나에게 할당하기" }) shouldBe false
            verify(exactly = 0) { messageSource.getMessage(any(), any(), any(), any()) }
        }

        it("messageSource에 매핑된 메시지가 없으면(null) 기본 텍스트를 사용해야 한다") {
            val auth = UsernamePasswordAuthenticationToken("requester4", "password")
            val project = Project(id = 26L, name = "proj26", owner = "owner", projectScope = ProjectScope.PUBLIC)
            val requester = User(id = 606L, loginId = "requester4", name = "요청자4")

            every { userRepository.findByLoginId("requester4") } returns Optional.of(requester)
            every { projectRepository.findById(26L) } returns Optional.of(project)
            every { userRepository.findById(606L) } returns Optional.of(requester)
            every { projectUserRepository.findByProjectId(26L) } returns listOf(
                ProjectUser(id = 1L, user = requester, project = project, role = Role(id = RoleType.MEMBER.roleType))
            )
            every { userRepository.findAllById(setOf(606L)) } returns listOf(requester)
            every { messageSource.getMessage("issue.assignToMe", null, "나에게 할당하기", any()) } returns null

            val result = projectMemberController.assignableUsers(26L, "", auth)

            result.statusCode shouldBe HttpStatus.OK
            val body = result.body!!
            (body.any { it["name"] == "나에게 할당하기" }) shouldBe true
        }
    }

    // yona-wiki P3-02 Step8.6 항목1(2026-09-01) — `yona admin permission list`용 신규 목록 API.
    describe("GET /api/projects/{projectId}/members") {
        val manager = User(id = 300L, loginId = "manager", name = "매니저")
        val managerAuth = UsernamePasswordAuthenticationToken("manager", "password")
        val member = User(id = 301L, loginId = "member1", name = "멤버1")
        val managerRole = Role(id = RoleType.MANAGER.roleType, name = "manager")
        val memberRole = Role(id = RoleType.MEMBER.roleType, name = "member")
        val project = Project(id = 50L, name = "proj50", owner = "owner")

        it("프로젝트 매니저는 현재 멤버+역할 목록을 조회할 수 있다") {
            every { userRepository.findByLoginId("manager") } returns Optional.of(manager)
            every { projectUserRepository.findByProjectIdAndUserId(50L, 300L) } returns
                Optional.of(ProjectUser(id = 1L, user = manager, project = project, role = managerRole))
            every { projectUserRepository.findByProjectId(50L) } returns listOf(
                ProjectUser(id = 1L, user = manager, project = project, role = managerRole),
                ProjectUser(id = 2L, user = member, project = project, role = memberRole)
            )

            val result = mockMvc.perform(get("/api/projects/50/members").principal(managerAuth))
                .andExpect(status().isOk)
                .andReturn()

            val body = result.response.contentAsString
            (body.contains("manager")) shouldBe true
            (body.contains("member1")) shouldBe true
        }

        it("프로젝트 매니저가 아닌 로그인 사용자는 403으로 거부된다") {
            val stranger = User(id = 302L, loginId = "stranger", name = "이방인")
            val strangerAuth = UsernamePasswordAuthenticationToken("stranger", "password")

            every { userRepository.findByLoginId("stranger") } returns Optional.of(stranger)
            every { projectUserRepository.findByProjectIdAndUserId(50L, 302L) } returns Optional.empty()

            mockMvc.perform(get("/api/projects/50/members").principal(strangerAuth))
                .andExpect(status().isForbidden)
        }

        it("비로그인 사용자는 401로 거부된다") {
            mockMvc.perform(get("/api/projects/50/members"))
                .andExpect(status().isUnauthorized)
        }
    }
})
