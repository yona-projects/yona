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
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder

// yona issue/partial_list_draft.scala.html 대응 (그룹7 #119, TASK-0256). 이슈 목록 첫 페이지
// (검색조건 없음, CLOSED 탭 아님)에 로그인한 본인의 초안(State.DRAFT) 이슈만 최상단에 노출되고,
// 번호 대신 "#초안"으로 표시되는지, 그리고 타인의 초안은 노출되지 않는지 실제 렌더링으로 확인한다.
@Transactional
class IssueDraftListTemplateRenderingSpec @Autowired constructor(
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

    init {
        describe("이슈 목록 초안 영역 렌더링") {
            it("로그인한 본인의 초안 이슈가 목록 최상단에 #초안 으로 노출되고 타인의 초안은 보이지 않아야 한다") {
                val me = userRepository.save(User(loginId = "tmpl-draft-me", name = "나", email = "tmpl-draft-me@yona.io"))
                val other = userRepository.save(User(loginId = "tmpl-draft-other", name = "타인", email = "tmpl-draft-other@yona.io"))
                val project = projectRepository.save(Project(name = "tmpl-draft-proj", owner = "tmpl-draft-owner", projectScope = ProjectScope.PUBLIC))

                val role = roleRepository.findById(RoleType.MEMBER.roleType).orElseGet {
                    roleRepository.save(Role(id = RoleType.MEMBER.roleType, name = "MEMBER"))
                }
                projectUserRepository.save(ProjectUser(project = project, user = me, role = role))

                issueRepository.save(
                    Issue(title = "일반 이슈", body = "본문", project = project, number = 1L, authorId = me.id, authorLoginId = me.loginId, authorName = me.name, state = State.OPEN)
                )
                issueRepository.save(
                    Issue(title = "내 초안 이슈", body = "본문", project = project, number = 2L, authorId = me.id, authorLoginId = me.loginId, authorName = me.name, state = State.DRAFT, isDraft = true)
                )
                issueRepository.save(
                    Issue(title = "타인의 초안 이슈", body = "본문", project = project, number = 3L, authorId = other.id, authorLoginId = other.loginId, authorName = other.name, state = State.DRAFT, isDraft = true)
                )

                val meDetails = YonaUserDetails(
                    id = me.id ?: 0L,
                    loginId = me.loginId,
                    passwordVal = "hashed",
                    passwordSalt = "salt",
                    authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
                )

                val body = mockMvc.perform(
                    get("/${project.owner}/${project.name}/issues")
                        .param("state", "OPEN")
                        .with(user(meDetails))
                )
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain "내 초안 이슈"
                body shouldContain "draft-number"
                body shouldNotContain "타인의 초안 이슈"
            }
        }
    }
}
