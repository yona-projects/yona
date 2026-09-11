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

// 부수 발견(P3-61 완료 로그에서 보고, 이번에 코디네이터가 직접 조사·수정): 이슈 검색 결과가
// 0건이면 legacy issue/partial_list_wrap.scala.html:61-67은 @if(currentPage.getList.isEmpty)
// 블록으로 들어가 partial_massupdate(마크업+"$yobi.loadModule('issue.MassUpdate', ...)" 초기화
// 스크립트가 그 파일 안에 함께 있음) 자체를 렌더링하지 않는다 - 결과가 있을 때(else 블록)만
// 마크업과 초기화 스크립트가 함께 나온다. yona issue/list.html은 이 초기화 스크립트를
// 페이지 최하단 전역 스크립트 블록으로 옮기면서 조건 없이 항상 실행하게 됐다 - 결과 0건이면
// #mass-update-form이 DOM에 없는데 $('.mass-update-wrap').offset()을 호출해 TypeError가 난다.
// MassUpdate 스크립트는 로그인한 프로젝트 멤버에게만 노출되는 마크업(#mass-update-form)에
// 종속되므로, 두 케이스 모두 프로젝트 멤버로 인증한 요청으로 검증한다.
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
                html shouldNotContain "\$yobi.loadModule(\"issue.MassUpdate\""
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

                html shouldContain "\$yobi.loadModule(\"issue.MassUpdate\""
            }
        }
    }
}
