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
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.jsoup.Jsoup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

// P3-57: issue/view.html에는 키보드 단축키 도움말(help/keymap)이 아예 없었고, issue/list.html은
// TASK-0249(#236)가 파라미터화하기 이전 방식(하드코딩 복제)이 남아 있어 권한 게이트가 틀려있었다
// (PR 탭 노출조건이 project.vcs=='GIT' 대신 isCodeEnabled로 판단, 설정메뉴가 매니저 여부와 무관하게
// 항상 노출). board/view.html·board/list.html과 동일한 파라미터화된 fragment로 통일했는지 검증한다.
class IssueKeymapHelpTemplateRenderingSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val roleRepository: RoleRepository,
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

        describe("P3-57: 이슈 상세/목록의 키맵 도움말이 파라미터화된 help/keymap fragment를 써야 한다") {
            val manager = userRepository.findByLoginId("keymap-manager").orElseGet {
                userRepository.save(User(loginId = "keymap-manager", name = "키맵매니저", email = "keymap-manager@yona.io"))
            }
            val member = userRepository.findByLoginId("keymap-member").orElseGet {
                userRepository.save(User(loginId = "keymap-member", name = "키맵멤버", email = "keymap-member@yona.io"))
            }
            val roleManager = roleRepository.findById(RoleType.MANAGER.roleType).orElseGet {
                roleRepository.save(Role(id = RoleType.MANAGER.roleType, name = "MANAGER"))
            }
            val roleMember = roleRepository.findById(RoleType.MEMBER.roleType).orElseGet {
                roleRepository.save(Role(id = RoleType.MEMBER.roleType, name = "MEMBER"))
            }

            val gitProject = projectRepository.findAll().find { it.name == "keymap-git-proj" && it.owner == "keymap-manager" }
                ?: projectRepository.save(Project(name = "keymap-git-proj", owner = "keymap-manager", projectScope = ProjectScope.PUBLIC, vcs = "GIT"))
            val svnProject = projectRepository.findAll().find { it.name == "keymap-svn-proj" && it.owner == "keymap-manager" }
                ?: projectRepository.save(Project(name = "keymap-svn-proj", owner = "keymap-manager", projectScope = ProjectScope.PUBLIC, vcs = "SUBVERSION"))

            if (!projectUserRepository.existsByProjectIdAndUserId(gitProject.id!!, manager.id!!)) {
                projectUserRepository.save(ProjectUser(project = gitProject, user = manager, role = roleManager))
            }
            if (!projectUserRepository.existsByProjectIdAndUserId(gitProject.id!!, member.id!!)) {
                projectUserRepository.save(ProjectUser(project = gitProject, user = member, role = roleMember))
            }

            val issue = issueRepository.findAll().find { it.project?.id == gitProject.id && it.number == 1L }
                ?: issueRepository.save(
                    Issue(
                        title = "키맵 테스트 이슈", body = "본문", project = gitProject, number = 1L,
                        authorId = manager.id, authorLoginId = manager.loginId, authorName = manager.name, state = State.OPEN
                    )
                )

            fun details(u: User) = YonaUserDetails(
                id = u.id!!,
                loginId = u.loginId,
                passwordVal = "hashed",
                passwordSalt = "salt",
                authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
            )

            it("issue/view.html에 help/keymap fragment(#helpKeys, 'issueDetail' 섹션 제목)가 정확히 1개 렌더링돼야 한다") {
                val html = mockMvc.perform(get("/${gitProject.owner}/${gitProject.name}/issue/${issue.number}").header("Accept-Language", "ko"))
                    .andExpect(status().isOk).andReturn().response.contentAsString
                val doc = Jsoup.parse(html)

                doc.select("#helpKeys").size shouldBe 1
                doc.select("#helpKeys h5:contains(이슈 상세보기)").size shouldBe 1
            }

            it("issue/list.html에 help/keymap fragment(#helpKeys, 'issueList' 섹션 제목)가 정확히 1개 렌더링돼야 한다") {
                val html = mockMvc.perform(get("/${gitProject.owner}/${gitProject.name}/issues").header("Accept-Language", "ko").param("state", "OPEN"))
                    .andExpect(status().isOk).andReturn().response.contentAsString
                val doc = Jsoup.parse(html)

                doc.select("#helpKeys").size shouldBe 1
                doc.select("#helpKeys h5:contains(이슈 목록)").size shouldBe 1
            }

            it("GIT 프로젝트의 이슈 목록 키맵에는 코드 주고받기(P) 항목이 보여야 한다") {
                val html = mockMvc.perform(get("/${gitProject.owner}/${gitProject.name}/issues").header("Accept-Language", "ko").param("state", "OPEN"))
                    .andExpect(status().isOk).andReturn().response.contentAsString
                val doc = Jsoup.parse(html)

                doc.select("#helpKeys .span3:contains(코드 주고받기)").size shouldBe 1
            }

            it("SVN 프로젝트의 이슈 목록 키맵에는 코드 주고받기(P) 항목이 보이면 안 된다(project.vcs=='GIT' 게이트)") {
                val html = mockMvc.perform(get("/${svnProject.owner}/${svnProject.name}/issues").header("Accept-Language", "ko").param("state", "OPEN"))
                    .andExpect(status().isOk).andReturn().response.contentAsString
                val doc = Jsoup.parse(html)

                doc.select("#helpKeys .span3:contains(코드 주고받기)").size shouldBe 0
            }

            it("매니저가 이슈 목록을 보면 키맵에 설정(Q) 항목이 보여야 한다") {
                val html = mockMvc.perform(
                    get("/${gitProject.owner}/${gitProject.name}/issues").header("Accept-Language", "ko").param("state", "OPEN")
                        .with(SecurityMockMvcRequestPostProcessors.user(details(manager)))
                ).andExpect(status().isOk).andReturn().response.contentAsString
                val doc = Jsoup.parse(html)

                doc.select("#helpKeys .span3:contains(설정)").size shouldBe 1
            }

            it("매니저가 아닌 멤버가 이슈 목록을 보면 키맵에 설정(Q) 항목이 보이면 안 된다") {
                val html = mockMvc.perform(
                    get("/${gitProject.owner}/${gitProject.name}/issues").header("Accept-Language", "ko").param("state", "OPEN")
                        .with(SecurityMockMvcRequestPostProcessors.user(details(member)))
                ).andExpect(status().isOk).andReturn().response.contentAsString
                val doc = Jsoup.parse(html)

                doc.select("#helpKeys .span3:contains(설정)").size shouldBe 0
            }
        }
    }
}
