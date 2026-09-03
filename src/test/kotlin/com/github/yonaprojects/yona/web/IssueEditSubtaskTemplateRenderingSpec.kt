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
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
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

// yona issue/partial_select_subtask.scala.html 대응 (그룹7 #125, TASK-0256). 수정화면은
// currentIssueId(자기 자신)가 항상 존재하므로 showOption이 항상 참이어야 하고(부모가 아직 없어도
// 위젯이 펼쳐지고 활성화돼 있어야 함), 이미 하위이슈를 가진 이슈는 "이미 부모 이슈입니다."로
// placeholder가 바뀌고 다른 이슈의 하위이슈가 될 수 없어야 한다.
@Transactional
class IssueEditSubtaskTemplateRenderingSpec @Autowired constructor(
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
        describe("이슈 수정화면 하위태스크 위젯 렌더링") {
            it("부모가 없는 이슈를 수정할 때도 하위태스크 위젯이 펼쳐지고 활성화돼 있어야 한다") {
                val author = userRepository.save(User(loginId = "tmpl-subtask-author", name = "작성자", email = "tmpl-subtask-author@yona.io"))
                val project = projectRepository.save(Project(name = "tmpl-subtask-proj", owner = "tmpl-subtask-owner", projectScope = ProjectScope.PUBLIC))
                projectUserRepository.save(ProjectUser(project = project, user = author, role = memberRole()))

                val issue = issueRepository.save(
                    Issue(title = "부모없는 이슈", body = "본문", project = project, number = 1L, authorId = author.id, authorLoginId = author.loginId, authorName = author.name, state = State.OPEN)
                )

                val details = YonaUserDetails(id = author.id ?: 0L, loginId = author.loginId, passwordVal = "h", passwordSalt = "s", authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE"))

                val body = mockMvc.perform(get("/${project.owner}/${project.name}/issue/${issue.number}/editform").with(user(details)))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain "subtask-wrap show"
                body shouldContain "id=\"parentId\""
                body shouldNotContain "select2\" data-container-css-class=\"fullsize\" disabled"
            }

            it("이미 하위이슈를 가진 이슈는 부모이슈 후보 대신 안내 문구만 노출해야 한다") {
                val author = userRepository.save(User(loginId = "tmpl-subtask-author2", name = "작성자2", email = "tmpl-subtask-author2@yona.io"))
                val project = projectRepository.save(Project(name = "tmpl-subtask-proj2", owner = "tmpl-subtask-owner2", projectScope = ProjectScope.PUBLIC))
                projectUserRepository.save(ProjectUser(project = project, user = author, role = memberRole()))

                val parent = issueRepository.save(
                    Issue(title = "이미 부모인 이슈", body = "본문", project = project, number = 1L, authorId = author.id, authorLoginId = author.loginId, authorName = author.name, state = State.OPEN)
                )
                issueRepository.save(
                    Issue(title = "그 하위이슈", body = "본문", project = project, number = 2L, authorId = author.id, authorLoginId = author.loginId, authorName = author.name, state = State.OPEN, parent = parent)
                )
                issueRepository.save(
                    Issue(title = "후보가 될 수 있었던 이슈", body = "본문", project = project, number = 3L, authorId = author.id, authorLoginId = author.loginId, authorName = author.name, state = State.OPEN)
                )

                val details = YonaUserDetails(id = author.id ?: 0L, loginId = author.loginId, passwordVal = "h", passwordSalt = "s", authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE"))

                val body = mockMvc.perform(get("/${project.owner}/${project.name}/issue/${parent.number}/editform").with(user(details)))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain "이미 부모 이슈입니다."
                body shouldNotContain "후보가 될 수 있었던 이슈"
            }
        }
    }
}
