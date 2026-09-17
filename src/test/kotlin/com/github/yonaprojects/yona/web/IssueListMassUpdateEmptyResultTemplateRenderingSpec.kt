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
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import io.kotest.extensions.spring.SpringExtension
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
import org.springframework.web.context.WebApplicationContext

// legacy는 검색 결과가 있을 때만 issue.MassUpdate 초기화 스크립트를 마크업과 함께 내보냈다.
// yona는 이 스크립트를 페이지 하단 전역 블록으로 옮기며 무조건 실행하게 됐는데, 결과 0건이면
// #mass-update-form이 DOM에 없어 $('.mass-update-wrap').offset() 호출 시 TypeError가 난다.
// 해당 마크업은 프로젝트 멤버에게만 노출되므로 두 케이스 모두 멤버 인증으로 검증한다.
class IssueListMassUpdateEmptyResultTemplateRenderingSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val issueRepository: IssueRepository
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
        }

        describe("이슈 검색 결과 0건일 때 issue.MassUpdate 모듈을 초기화하면 안 된다") {
            val suffix = System.currentTimeMillis().toString()
            val author = userRepository.save(User(loginId = "massupdate-empty-author-$suffix", name = "작성자", email = "massupdate-empty-author-$suffix@yona.io"))
            val project = projectRepository.save(Project(name = "massupdate-empty-proj-$suffix", owner = "massupdate-empty-author-$suffix", projectScope = ProjectScope.PUBLIC, vcs = "GIT"))
            projectUserRepository.save(ProjectUser(user = author, project = project, role = Role(id = RoleType.MANAGER.roleType)))

            fun authOf(u: User) = user(
                YonaUserDetails(
                    id = u.id ?: 0L,
                    loginId = u.loginId,
                    passwordVal = "hashed",
                    passwordSalt = "salt",
                    authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
                )
            )

            it("결과가 없으면(존재하지 않는 필터 텍스트) 응답 HTML에 issue.MassUpdate 초기화 스크립트가 없어야 한다") {
                val html = mockMvc.perform(
                    get("/${project.owner}/${project.name}/issues")
                        .with(authOf(author))
                        .param("state", "OPEN")
                        .param("filter", "존재하지-않는-검색어-절대매칭안됨")
                ).andExpect(status().isOk).andReturn().response.contentAsString

                html shouldContain "error-wrap"
                html shouldNotContain "\$yona.loadModule(\"issue.MassUpdate\""
            }

            it("결과가 있으면 응답 HTML에 issue.MassUpdate 초기화 스크립트가 있어야 한다") {
                issueRepository.save(
                    Issue(
                        title = "일반 이슈", body = "본문", project = project, number = 1L,
                        authorId = author.id, authorLoginId = author.loginId, state = State.OPEN
                    )
                )

                val html = mockMvc.perform(
                    get("/${project.owner}/${project.name}/issues")
                        .with(authOf(author))
                        .param("state", "OPEN")
                ).andExpect(status().isOk).andReturn().response.contentAsString

                html shouldContain "\$yona.loadModule(\"issue.MassUpdate\""
            }
        }
    }
}
