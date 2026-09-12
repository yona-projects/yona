package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.jsoup.Jsoup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext

// P3-66: legacy issue/view.scala.html의 이슈 공유자(Sharer) 위젯(hidden input #issueSharer +
// service/yona.issue.Sharer.js + yonaIssueSharerModule(...) 초기화)이 yona issue/view.html에는
// #issueSharer가 순수 텍스트 입력(type="text")으로 퇴화한 채, 스크립트 로드/초기화 호출이 아예
// 없었다(yona.issue.Sharer.js는 이미 Tom Select로 이식돼 있었지만 어느 템플릿에서도 호출되지
// 않는 죽은 코드였음 - P3-46 #5 당시 "범위 밖 발견"). 백엔드(IssueShareController의
// findSharer/sharableUsers/share)는 이미 준비돼 있으므로, 이 스펙은 담당자(Assginee) 위젯과
// 동일한 패턴으로 배선이 복원됐는지만 검증한다.
@Transactional
class IssueSharerWidgetWiringTemplateRenderingSpec @Autowired constructor(
    private val webApplicationContext: WebApplicationContext,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val issueRepository: IssueRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val roleRepository: RoleRepository
) : AbstractIntegrationTest() {

    private val mockMvc: MockMvc by lazy {
        MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
            .build()
    }

    private fun managerRole() = roleRepository.findById(RoleType.MANAGER.roleType).orElseGet {
        roleRepository.save(Role(id = RoleType.MANAGER.roleType, name = "MANAGER"))
    }

    init {
        describe("이슈 상세화면 공유자(Sharer) 위젯 배선") {
            it("매니저에게는 #issueSharer가 hidden input이고, yona.issue.Sharer.js 로드 + yonaIssueSharerModule 초기화 호출(3개 API URL 포함)이 렌더링돼야 한다") {
                val manager = userRepository.save(User(loginId = "sharer-widget-manager", name = "공유매니저", email = "sharer-widget-manager@yona.io"))
                val project = projectRepository.save(Project(name = "sharer-widget-proj", owner = "sharer-widget-owner", projectScope = ProjectScope.PUBLIC))
                projectUserRepository.save(ProjectUser(project = project, user = manager, role = managerRole()))

                val issue = issueRepository.save(
                    Issue(title = "공유자 위젯 테스트 이슈", body = "본문", project = project, number = 1L, authorId = manager.id, authorLoginId = manager.loginId, authorName = manager.name, state = State.OPEN)
                )

                val details = YonaUserDetails(id = manager.id ?: 0L, loginId = manager.loginId, passwordVal = "h", passwordSalt = "s", authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE"))

                val body = mockMvc.perform(get("/${project.owner}/${project.name}/issue/${issue.number}").with(user(details)))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                val doc = Jsoup.parse(body)
                val sharerInput = doc.select("#issueSharer")
                sharerInput.size shouldBe 1
                sharerInput.attr("type") shouldBe "hidden"

                body shouldContain "/javascripts/service/yona.issue.Sharer.js"
                body shouldContain "yonaIssueSharerModule("
                body shouldContain "/-_-api/v1/owners/${project.owner}/projects/${project.name}/issues/${issue.number}/findSharer"
                body shouldContain "/-_-api/v1/owners/${project.owner}/projects/${project.name}/issues/${issue.number}/sharableUsers"
                body shouldContain "/-_-api/v1/owners/${project.owner}/projects/${project.name}/issues/${issue.number}/share"
            }
        }
    }
}
