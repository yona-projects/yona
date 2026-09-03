package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.milestone.Milestone
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import io.kotest.extensions.spring.SpringExtension
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

import com.github.yonaprojects.yona.domain.project.ProjectUserService
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder

class ProjectViewControllerIntegrationSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val projectUserService: ProjectUserService,
    private val milestoneRepository: MilestoneRepository
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
        }

        describe("ProjectViewController 통합 렌더링 테스트 (isCodeAccessibleMemberOnly)") {
            val ownerName = "owner-dev"
            val projName = "member-only-code"

            beforeTest {
                // 다른 스펙과 같은 테스트 컨테이너 DB를 공유하므로 전역 deleteAll()을 쓰면 안 된다 —
                // 그룹11 PullRequestListTemplateEquivalenceSpec 등 타 스펙이 만든 project/pull_request를
                // 건드려 FK 위반이 발생한다(전체 회귀에서 실제로 재현됨). 이 스펙 소유 데이터(고정
                // 이름의 owner/project/user들)만 매회 정리한다.
                projectRepository.findByOwnerAndName(ownerName, projName).ifPresent { existing ->
                    projectUserRepository.findByProjectId(existing.id!!).forEach { projectUserRepository.delete(it) }
                    milestoneRepository.findByProject(existing).forEach { milestoneRepository.delete(it) }
                    projectRepository.delete(existing)
                }
                listOf(ownerName, "member1", "nonmember", "enrollee1").forEach { loginId ->
                    userRepository.findByLoginId(loginId).ifPresent { userRepository.delete(it) }
                }

                // 테스트용 유저 및 프로젝트 매회 신규 생성 후 영속 객체 반환값을 변수에 할당
                val projectOwner = userRepository.save(User(loginId = ownerName, name = "소유자", email = "owner@yona.io"))
                val memberUser = userRepository.save(User(loginId = "member1", name = "멤버1", email = "member1@yona.io"))
                val nonMemberUser = userRepository.save(User(loginId = "nonmember", name = "비멤버", email = "nonmember@yona.io"))

                val project = Project(
                    owner = ownerName,
                    name = projName,
                    projectScope = ProjectScope.PUBLIC,
                    isCodeAccessibleMemberOnly = true,
                    vcs = "GIT"
                )
                val savedProject = projectRepository.save(project)

                // 소유자 및 멤버 가입 시 ID가 확보된 영속 객체(savedProject, 영속 유저들) 할당
                val managerRole = Role(id = RoleType.MANAGER.roleType)
                val memberRole = Role(id = RoleType.MEMBER.roleType)
                projectUserRepository.save(ProjectUser(user = projectOwner, project = savedProject, role = managerRole))
                projectUserRepository.save(ProjectUser(user = memberUser, project = savedProject, role = memberRole))
            }

            it("공개 프로젝트이지만 isCodeAccessibleMemberOnly가 true일 때, 비멤버는 프로젝트 홈에서 코드/PR/리뷰 메뉴 탭이 렌더링되지 않아야 한다") {
                val nonMemberUserObj = userRepository.findByLoginId("nonmember").get()
                val nonMemberDetails = YonaUserDetails(
                    id = nonMemberUserObj.id ?: 99L,
                    loginId = nonMemberUserObj.loginId,
                    passwordVal = "hashed",
                    passwordSalt = "salt",
                    authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
                )

                mockMvc.perform(
                    get("/$ownerName/$projName").with(user(nonMemberDetails))
                )
                    .andExpect(status().isOk)
                    .andExpect(content().string(not(containsString("/$ownerName/$projName/code"))))
                    .andExpect(content().string(not(containsString("/$ownerName/$projName/pulls"))))
                    .andExpect(content().string(not(containsString("/$ownerName/$projName/reviews"))))
            }

            it("isCodeAccessibleMemberOnly가 true일 때, 프로젝트 멤버는 프로젝트 홈에서 코드/PR/리뷰 메뉴 탭이 정상적으로 렌더링되어 보여야 한다") {
                val memberUserObj = userRepository.findByLoginId("member1").get()
                val memberDetails = YonaUserDetails(
                    id = memberUserObj.id ?: 88L,
                    loginId = memberUserObj.loginId,
                    passwordVal = "hashed",
                    passwordSalt = "salt",
                    authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
                )

                mockMvc.perform(
                    get("/$ownerName/$projName").with(user(memberDetails))
                )
                    .andExpect(status().isOk)
                    .andExpect(content().string(containsString("/$ownerName/$projName/code")))
                    .andExpect(content().string(containsString("/$ownerName/$projName/pulls")))
                    .andExpect(content().string(containsString("/$ownerName/$projName/reviews")))
            }

            it("프로젝트 관리자(Manager)는 프로젝트 홈에서 설정 톱니바퀴 메뉴가 정상적으로 렌더링되어 보여야 한다") {
                val ownerUserObj = userRepository.findByLoginId(ownerName).get()
                val ownerDetails = YonaUserDetails(
                    id = ownerUserObj.id ?: 77L,
                    loginId = ownerUserObj.loginId,
                    passwordVal = "hashed",
                    passwordSalt = "salt",
                    authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
                )

                mockMvc.perform(
                    get("/$ownerName/$projName").with(user(ownerDetails))
                )
                    .andExpect(status().isOk)
                    .andExpect(content().string(containsString("/$ownerName/$projName/setting")))
            }

            it("일반 프로젝트 멤버는 프로젝트 홈에서 설정 톱니바퀴 메뉴가 렌더링되지 않아야 한다") {
                val memberUserObj = userRepository.findByLoginId("member1").get()
                val memberDetails = YonaUserDetails(
                    id = memberUserObj.id ?: 88L,
                    loginId = memberUserObj.loginId,
                    passwordVal = "hashed",
                    passwordSalt = "salt",
                    authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
                )

                mockMvc.perform(
                    get("/$ownerName/$projName").with(user(memberDetails))
                )
                    .andExpect(status().isOk)
                    .andExpect(content().string(not(containsString("/$ownerName/$projName/setting"))))
            }

            // yona project/home.scala.html:112-118 대응 (milestone/partial_status, #153) — 사이드바에
            // 가장 기한이 임박한 열린 마일스톤의 진행 상황 카드가 렌더링돼야 한다.
            it("열린 마일스톤이 있을 때, 프로젝트 홈 사이드바에 가장 기한이 임박한 마일스톤의 진행 상황 카드가 렌더링되어야 한다") {
                val projObj = projectRepository.findByOwnerAndName(ownerName, projName).get()
                milestoneRepository.save(
                    Milestone(
                        title = "먼 마일스톤", project = projObj, state = State.OPEN,
                        dueDate = Instant.now().plus(30, ChronoUnit.DAYS)
                    )
                )
                val nearMilestone = milestoneRepository.save(
                    Milestone(
                        title = "임박한 마일스톤", project = projObj, state = State.OPEN,
                        dueDate = Instant.now().plus(3, ChronoUnit.DAYS)
                    )
                )

                val ownerUserObj = userRepository.findByLoginId(ownerName).get()
                val ownerDetails = YonaUserDetails(
                    id = ownerUserObj.id ?: 77L,
                    loginId = ownerUserObj.loginId,
                    passwordVal = "hashed",
                    passwordSalt = "salt",
                    authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
                )

                mockMvc.perform(
                    get("/$ownerName/$projName").with(user(ownerDetails))
                )
                    .andExpect(status().isOk)
                    .andExpect(content().string(containsString("milestone-info")))
                    .andExpect(content().string(containsString(nearMilestone.title)))
                    .andExpect(content().string(not(containsString("먼 마일스톤"))))
            }

            describe("GET /$ownerName/$projName/members") {
                it("가입 신청 대기 유저가 존재할 때, 가입 신청 대기 목록과 승인 버튼이 멤버 관리 뷰에 렌더링되어야 한다") {
                    // Given 가입 대기 유저 셋업
                    val ownerUserObj = userRepository.findByLoginId(ownerName).get()
                    val projObj = projectRepository.findByOwnerAndName(ownerName, projName).get()
                    
                    val enrollee = userRepository.save(User(loginId = "enrollee1", name = "가입신청자", email = "enrollee1@yona.io"))
                    projectUserService.enroll(projObj.id!!, enrollee.id!!)

                    val ownerDetails = YonaUserDetails(
                        id = ownerUserObj.id ?: 77L,
                        loginId = ownerUserObj.loginId,
                        passwordVal = "hashed",
                        passwordSalt = "salt",
                        authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
                    )

                    // When & Then
                    mockMvc.perform(
                        get("/$ownerName/$projName/members").with(user(ownerDetails))
                    )
                        .andExpect(status().isOk)
                        .andExpect(content().string(containsString("가입 신청 대기 목록")))
                        .andExpect(content().string(containsString("enrollAcceptBtn")))
                        .andExpect(content().string(containsString("class=\"members project row-fluid\"")))
                }
            }
        }
    }
}
