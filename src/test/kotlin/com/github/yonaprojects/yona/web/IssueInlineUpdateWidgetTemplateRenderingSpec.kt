package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.milestone.Milestone
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
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
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext

// yona issue/partial_assignee.scala.html 대응 (그룹7 #127, TASK-0256). 이슈 상세화면
// (issue/view.html)의 담당자/마일스톤/마감일이 매니저(isAllowedUpdate)에게는 인라인 수정 select2/
// calendar 위젯으로, 그 외에는 정적 텍스트로 렌더링되는지, 그리고 massUpdate 엔드포인트가 AJAX(JSON
// Accept 헤더) 요청에는 리다이렉트 대신 JSON으로 응답하는지 실제 렌더링/요청으로 확인한다.
@Transactional
class IssueInlineUpdateWidgetTemplateRenderingSpec @Autowired constructor(
    private val webApplicationContext: WebApplicationContext,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val issueRepository: IssueRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val roleRepository: RoleRepository,
    private val milestoneRepository: MilestoneRepository
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
        describe("이슈 상세화면 담당자/마일스톤/마감일 인라인 위젯 렌더링") {
            it("매니저에게는 담당자 select2 hidden input과 마일스톤 select가 렌더링돼야 한다") {
                val manager = userRepository.save(User(loginId = "tmpl-widget-manager", name = "매니저", email = "tmpl-widget-manager@yona.io"))
                val project = projectRepository.save(Project(name = "tmpl-widget-proj", owner = "tmpl-widget-owner", projectScope = ProjectScope.PUBLIC))
                projectUserRepository.save(ProjectUser(project = project, user = manager, role = managerRole()))
                milestoneRepository.save(Milestone(title = "위젯 마일스톤", project = project, state = State.OPEN))

                val issue = issueRepository.save(
                    Issue(title = "위젯 테스트 이슈", body = "본문", project = project, number = 1L, authorId = manager.id, authorLoginId = manager.loginId, authorName = manager.name, state = State.OPEN)
                )

                val details = YonaUserDetails(id = manager.id ?: 0L, loginId = manager.loginId, passwordVal = "h", passwordSalt = "s", authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE"))

                val body = mockMvc.perform(get("/${project.owner}/${project.name}/issue/${issue.number}").with(user(details)))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain "id=\"assignee\""
                body shouldContain "id=\"milestone\""
                body shouldContain "위젯 마일스톤"
                body shouldContain "data-toggle=\"calendar\""
                body shouldContain "yonaAssgineeModule"
            }

            it("massUpdate가 JSON Accept 헤더 요청에는 리다이렉트 대신 JSON으로 응답해야 한다") {
                val manager = userRepository.save(User(loginId = "tmpl-widget-manager2", name = "매니저2", email = "tmpl-widget-manager2@yona.io"))
                val project = projectRepository.save(Project(name = "tmpl-widget-proj2", owner = "tmpl-widget-owner2", projectScope = ProjectScope.PUBLIC))
                projectUserRepository.save(ProjectUser(project = project, user = manager, role = managerRole()))

                val issue = issueRepository.save(
                    Issue(title = "매스업데이트 이슈", body = "본문", project = project, number = 1L, authorId = manager.id, authorLoginId = manager.loginId, authorName = manager.name, state = State.OPEN)
                )

                val details = YonaUserDetails(id = manager.id ?: 0L, loginId = manager.loginId, passwordVal = "h", passwordSalt = "s", authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE"))

                val res = mockMvc.perform(
                    post("/${project.owner}/${project.name}/issues/massupdate")
                        .with(user(details))
                        .accept(MediaType.APPLICATION_JSON)
                        .param("issues[0].id", issue.id.toString())
                        .param("isDueDateChanged", "true")
                        .param("dueDate", "2099-01-01")
                ).andExpect(status().isOk).andReturn()

                val updated = issueRepository.findById(issue.id!!).orElseThrow()
                updated.dueDate shouldNotBe null
            }
        }
    }
}
