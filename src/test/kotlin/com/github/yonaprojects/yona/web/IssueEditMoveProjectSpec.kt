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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext

// yona IssueApp.editIssue()의 hasTargetProject()/moveIssueToOtherProject() 대응 (P1-66 재검토로 발견한
// 실버그). issue/edit.html의 targetProjectId select는 이미 있었지만 IssueForm에 이 필드가 없어 폼을
// 제출해도 이슈가 실제로 이동하지 않는 죽은 UI였다 — moveIssue()(P1-48)는 legacy와 동일하게 구현돼
// 있었으나 아무 데서도 호출되지 않고 있었다.
@Transactional
class IssueEditMoveProjectSpec @Autowired constructor(
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

    private fun memberRole() = roleRepository.findById(RoleType.MEMBER.roleType).orElseGet {
        roleRepository.save(Role(id = RoleType.MEMBER.roleType, name = "MEMBER"))
    }

    init {
        describe("이슈 수정 폼의 다른 프로젝트로 이동(targetProjectId)") {
            it("멤버인 다른 프로젝트를 targetProjectId로 제출하면 이슈가 실제로 이동해야 한다") {
                val author = userRepository.save(User(loginId = "move-author", name = "작성자", email = "move-author@yona.io"))
                val fromProject = projectRepository.save(Project(name = "move-from", owner = "move-owner", projectScope = ProjectScope.PUBLIC))
                val toProject = projectRepository.save(Project(name = "move-to", owner = "move-owner", projectScope = ProjectScope.PUBLIC))
                projectUserRepository.save(ProjectUser(project = fromProject, user = author, role = memberRole()))
                projectUserRepository.save(ProjectUser(project = toProject, user = author, role = memberRole()))

                val issue = issueRepository.save(
                    Issue(title = "이동될 이슈", body = "본문", project = fromProject, number = 1L, authorId = author.id, authorLoginId = author.loginId, authorName = author.name, state = State.OPEN)
                )

                val details = YonaUserDetails(id = author.id ?: 0L, loginId = author.loginId, passwordVal = "h", passwordSalt = "s", authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE"))

                val result = mockMvc.perform(
                    post("/${fromProject.owner}/${fromProject.name}/issue/${issue.number}/edit")
                        .with(user(details))
                        .with(csrf())
                        .param("title", "이동될 이슈")
                        .param("body", "본문")
                        .param("targetProjectId", toProject.id.toString())
                ).andExpect(status().is3xxRedirection).andReturn()

                result.response.redirectedUrl?.startsWith("/${toProject.owner}/${toProject.name}/issue/") shouldBe true

                val moved = issueRepository.findById(issue.id!!).orElseThrow()
                moved.project.id shouldBe toProject.id
            }

            it("targetProjectId로 보낸 프로젝트에 생성 권한이 없으면 이동을 거부하고 원래 프로젝트에 그대로 남아야 한다") {
                val author = userRepository.save(User(loginId = "move-author2", name = "작성자2", email = "move-author2@yona.io"))
                val fromProject = projectRepository.save(Project(name = "move-from2", owner = "move-owner2", projectScope = ProjectScope.PUBLIC))
                val toProject = projectRepository.save(Project(name = "move-to2", owner = "move-owner2", projectScope = ProjectScope.PRIVATE))
                projectUserRepository.save(ProjectUser(project = fromProject, user = author, role = memberRole()))
                // author는 toProject의 멤버가 아니며 toProject는 PRIVATE이므로 이슈 생성 권한이 없다.

                val issue = issueRepository.save(
                    Issue(title = "이동 거부될 이슈", body = "본문", project = fromProject, number = 1L, authorId = author.id, authorLoginId = author.loginId, authorName = author.name, state = State.OPEN)
                )

                val details = YonaUserDetails(id = author.id ?: 0L, loginId = author.loginId, passwordVal = "h", passwordSalt = "s", authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE"))

                // 이 저장소의 기존 컨벤션(다른 error/forbidden 반환 지점들과 동일, ProjectViewControllerSpec
                // 등에서도 view name만 검증)대로 HTTP 상태 코드는 별도로 검사하지 않고 뷰 이름만 확인한다.
                mockMvc.perform(
                    post("/${fromProject.owner}/${fromProject.name}/issue/${issue.number}/edit")
                        .with(user(details))
                        .with(csrf())
                        .param("title", "이동 거부될 이슈")
                        .param("body", "본문")
                        .param("targetProjectId", toProject.id.toString())
                ).andExpect(view().name("error/forbidden"))

                val notMoved = issueRepository.findById(issue.id!!).orElseThrow()
                notMoved.project.id shouldBe fromProject.id
            }
        }
    }
}
