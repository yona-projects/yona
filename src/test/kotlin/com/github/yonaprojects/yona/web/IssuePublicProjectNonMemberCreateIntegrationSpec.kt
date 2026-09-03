package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

// yona-wiki P3-02 14라운드(TASK-0436) — 실서버(H2)+실 yona-cli 골든패스 탐색 중, 조직(acmeorg)
// 소속이 아닌 순수 PUBLIC 프로젝트에 프로젝트 멤버가 아닌 로그인 사용자(bob)가
// `yona issue create`를 호출하면 항상 403 Forbidden으로 거절됨을 발견했다. legacy
// AccessControl.isProjectResourceCreatable()(app/utils/AccessControl.java:48-78)은 "PUBLIC
// 프로젝트면 사이트관리자/조직관리자/멤버가 아니어도 로그인한 사용자는 이슈/게시글을 만들 수
// 있다"는 분기를 명시하고 있고, 이 저장소의 세션 기반 웹 UI(IssueViewController.newIssue() 등)도
// 이미 그 로직(config.security.AccessControl.isProjectResourceCreatable, 완전히 포팅됨)을 쓰고
// 있어 문제가 없었다 — 오직 REST API(`IssueController.createIssue()`가 쓰던 손수 구현한
// checkWritePermission(), "프로젝트 멤버 or 조직 그룹멤버"만 확인하고 PUBLIC 분기가 없었음)만
// 이 버그가 있었다. IssueControllerSpec(mockk)에도 동일 회귀 테스트를 추가했지만, 그 스펙은
// AccessControl 자체는 실제 인스턴스를 쓰되 Project/User 그래프는 전부 손으로 구성한 목이라
// "실제 DB에 저장된 PUBLIC 프로젝트 + 실제 인증 주체"의 전체 스택(시큐리티 필터 체인 포함)까지
// 검증하지 못한다 — 이 스펙은 AbstractIntegrationTest(실제 DB) + MockMvc(webAppContextSetup,
// 실제 시큐리티 필터 체인)로 실제 HTTP 요청을 끝까지 흘려보내 재현한다.
class IssuePublicProjectNonMemberCreateIntegrationSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val issueRepository: IssueRepository
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc

    private val ownerName = "p14-public-owner"
    private val nonMemberName = "p14-public-nonmember"
    private val projName = "p14-public-repo"

    private lateinit var owner: User
    private lateinit var nonMember: User
    private lateinit var project: Project

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
        }

        beforeTest {
            // 다른 스펙과 테스트 컨테이너 DB를 공유하므로 이 스펙 소유 데이터만 매회 정리한다.
            projectRepository.findByOwnerAndName(ownerName, projName).ifPresent { existing ->
                issueRepository.findByProject(existing).forEach { issueRepository.delete(it) }
                projectUserRepository.findByProjectId(existing.id!!).forEach { projectUserRepository.delete(it) }
                projectRepository.delete(existing)
            }
            userRepository.findByLoginId(ownerName).ifPresent { userRepository.delete(it) }
            userRepository.findByLoginId(nonMemberName).ifPresent { userRepository.delete(it) }

            owner = userRepository.save(User(loginId = ownerName, name = "P14공개owner", email = "$ownerName@example.com"))
            nonMember = userRepository.save(
                User(loginId = nonMemberName, name = "P14공개비멤버", email = "$nonMemberName@example.com")
            )
            // 조직 소속 없는 순수 PUBLIC 프로젝트 — organization=null이라 isAllowedIfGroupMember()도
            // 항상 false. isProjectResourceCreatable()의 "PUBLIC이면 로그인 사용자는 누구나"
            // 분기만이 이 사용자를 통과시킬 수 있다.
            project = projectRepository.save(Project(owner = ownerName, name = projName, projectScope = ProjectScope.PUBLIC))
        }

        fun nonMemberDetails() = YonaUserDetails(
            id = nonMember.id!!,
            loginId = nonMember.loginId,
            passwordVal = "hashed",
            passwordSalt = "salt",
            authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
        )

        describe("POST /api/v1/projects/{owner}/{project}/issues — 비멤버의 PUBLIC 프로젝트 이슈 생성") {
            it("조직 소속이 아닌 PUBLIC 프로젝트라도 비멤버 로그인 사용자가 이슈를 생성할 수 있어야 한다") {
                val jsonContent = """{ "title": "비멤버 이슈", "body": "본문" }"""

                val result = mockMvc.perform(
                    post("/api/v1/projects/$ownerName/$projName/issues")
                        .with(user(nonMemberDetails()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                ).andReturn()

                result.response.status shouldBe 201

                val saved = issueRepository.findByProject(project)
                saved shouldHaveSize 1
                saved.first().title shouldBe "비멤버 이슈"
                saved.first().authorId shouldBe nonMember.id
            }
        }
    }
}
