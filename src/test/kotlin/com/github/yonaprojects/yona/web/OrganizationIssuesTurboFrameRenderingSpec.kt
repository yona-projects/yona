package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import jakarta.persistence.EntityManagerFactory
import org.hibernate.SessionFactory
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

// P3-75 Step 5 — 조직 이슈(/org/{orgName}/issues) 2단 보기의 iframe(pageslide)을 Turbo Frames로 전환.
// 조직 이슈는 조직 아래 여러 프로젝트의 이슈를 한 목록에 보여주므로 내 이슈와 같은 복합 키
// `?detail=issue:<owner>/<project>/<번호>`를 쓰고, CrossProjectDetailResolver가 기존 이슈 상세 로직에
// 위임하므로 프로젝트 읽기 권한 게이트가 그대로 적용돼야 한다. 조직 화면이므로 조직 밖 프로젝트의 키도 거부한다.
@Transactional
class OrganizationIssuesTurboFrameRenderingSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val organizationRepository: OrganizationRepository,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val issueRepository: IssueRepository,
    private val entityManagerFactory: EntityManagerFactory
) : AbstractIntegrationTest() {

    private lateinit var mockMvc: MockMvc

    private fun authOf(u: User) = user(
        YonaUserDetails(
            id = u.id ?: 0L,
            loginId = u.loginId,
            passwordVal = "hashed",
            passwordSalt = "salt",
            authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
        )
    )

    // 각 it가 자기 트랜잭션 안에서 픽스처를 만든다(container 본문은 트랜잭션 밖이라 detached 엔티티가 된다).
    private inner class Fixture {
        val suffix = System.nanoTime().toString()
        val viewer = userRepository.save(User(loginId = "orgiss-viewer-$suffix", name = "조회자", email = "orgiss-viewer-$suffix@yona.io"))
        val org = organizationRepository.save(Organization(name = "orgiss-org-$suffix"))
        val projectA = orgProject("orgiss-a-$suffix", ProjectScope.PUBLIC)
        val projectB = orgProject("orgiss-b-$suffix", ProjectScope.PUBLIC)

        fun orgProject(name: String, scope: ProjectScope): Project {
            val project = projectRepository.save(Project(name = name, owner = org.name, organization = org, projectScope = scope))
            org.projects.add(project)
            return project
        }

        fun issue(project: Project, number: Long, title: String, body: String) = issueRepository.save(
            Issue(title = title, body = body, project = project, number = number, authorId = viewer.id,
                authorLoginId = viewer.loginId, authorName = viewer.name, state = State.OPEN)
        )

        init {
            issue(projectA, 1L, "OrgIssues A listed $suffix", "A listed body")
            issue(projectB, 1L, "OrgIssues B selected $suffix", "OrgIssues B selected detail body")
        }

        val key = "issue:${org.name}/${projectB.name}/1"
        val url = "/org/${org.name}/issues"
    }

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
        }

        describe("조직 이슈 2단 보기(Turbo Frames)") {
            it("헤더 없는 요청은 목록과 상세 프레임을 함께 렌더링하고 서로 다른 프로젝트 행 링크에 복합 키를 싣는다") {
                with(Fixture()) {
                    val body = mockMvc.perform(get(url).queryParam("detail", key).with(authOf(viewer)))
                        .andExpect(status().isOk).andReturn().response.contentAsString

                    body shouldContain "id=\"issue-list\""
                    body shouldContain "id=\"issue-detail\""
                    body shouldContain "OrgIssues B selected detail body"
                    body shouldContain "OrgIssues A listed $suffix"
                    body shouldContain "data-selection-url=\"$url?detail=issue:${org.name}/${projectA.name}/1\""
                    body shouldContain "data-selection-url=\"$url?detail=issue:${org.name}/${projectB.name}/1\""
                    body shouldContain "data-detail-url=\"/${org.name}/${projectA.name}/issue/1\""
                    body shouldContain "name=\"detail\""
                }
            }

            it("Turbo-Frame: issue-detail 요청은 목록 없이 상세 fragment만 반환한다") {
                with(Fixture()) {
                    val body = mockMvc.perform(
                        get(url).queryParam("detail", key).header("Turbo-Frame", "issue-detail").with(authOf(viewer))
                    ).andExpect(status().isOk).andReturn().response.contentAsString

                    body shouldContain "id=\"issue-detail\""
                    body shouldContain "OrgIssues B selected detail body"
                    body shouldNotContain "<!DOCTYPE html>"
                    body shouldNotContain "id=\"issue-list\""
                    body shouldNotContain "OrgIssues A listed $suffix"
                }
            }

            it("Turbo-Frame 요청은 목록 전체 요청보다 SQL을 적게 실행한다") {
                with(Fixture()) {
                    val statistics = entityManagerFactory.unwrap(SessionFactory::class.java).statistics
                    statistics.setStatisticsEnabled(true)

                    statistics.clear()
                    mockMvc.perform(get(url).queryParam("detail", key).with(authOf(viewer))).andExpect(status().isOk)
                    val fullPageSqlCount = statistics.prepareStatementCount

                    statistics.clear()
                    mockMvc.perform(get(url).queryParam("detail", key).header("Turbo-Frame", "issue-detail").with(authOf(viewer)))
                        .andExpect(status().isOk)
                    val frameSqlCount = statistics.prepareStatementCount

                    frameSqlCount shouldBeLessThan fullPageSqlCount
                }
            }

            it("Turbo-Frame 요청은 같은 이슈의 단독 상세 조회보다 SQL을 더 실행하지 않는다") {
                with(Fixture()) {
                    val statistics = entityManagerFactory.unwrap(SessionFactory::class.java).statistics
                    statistics.setStatisticsEnabled(true)

                    statistics.clear()
                    mockMvc.perform(get("/${org.name}/${projectB.name}/issue/1").with(authOf(viewer))).andExpect(status().isOk)
                    val plainDetailSqlCount = statistics.prepareStatementCount

                    statistics.clear()
                    mockMvc.perform(get(url).queryParam("detail", key).header("Turbo-Frame", "issue-detail").with(authOf(viewer)))
                        .andExpect(status().isOk)
                    val frameSqlCount = statistics.prepareStatementCount

                    // 조직 조회가 한 번 더 있으므로 그만큼의 여유만 허용한다(목록 조회 SQL은 훨씬 크다).
                    frameSqlCount shouldBeLessThanOrEqualTo plainDetailSqlCount + 2
                }
            }

            it("읽기 권한이 없는 비공개 프로젝트의 키는 상세를 노출하지 않는다") {
                with(Fixture()) {
                    val secret = orgProject("orgiss-secret-$suffix", ProjectScope.PRIVATE)
                    issue(secret, 1L, "OrgIssues secret title $suffix", "OrgIssues secret body $suffix")

                    val body = mockMvc.perform(
                        get(url).queryParam("detail", "issue:${org.name}/${secret.name}/1")
                            .header("Turbo-Frame", "issue-detail").with(authOf(viewer))
                    ).andReturn().response.contentAsString

                    body shouldNotContain "OrgIssues secret body $suffix"
                    body shouldNotContain "OrgIssues secret title $suffix"
                }
            }

            it("이 조직에 속하지 않은 프로젝트의 키는 읽을 수 있어도 조직 화면에 상세를 띄우지 않는다") {
                with(Fixture()) {
                    val foreign = projectRepository.save(Project(name = "orgiss-foreign-$suffix", owner = "orgiss-foreign-owner-$suffix", projectScope = ProjectScope.PUBLIC))
                    issue(foreign, 1L, "OrgIssues foreign title $suffix", "OrgIssues foreign body $suffix")

                    val response = mockMvc.perform(
                        get(url).queryParam("detail", "issue:${foreign.owner}/${foreign.name}/1")
                            .header("Turbo-Frame", "issue-detail").with(authOf(viewer))
                    ).andReturn().response

                    response.status shouldBeLessThan 500
                    response.contentAsString shouldNotContain "OrgIssues foreign body $suffix"
                    response.contentAsString shouldContain "turbo-visit-control"
                }
            }

            it("형식이 잘못된 키나 존재하지 않는 항목은 상세를 노출하지 않고 프레임 오류로 처리한다") {
                with(Fixture()) {
                    listOf("garbage", "issue:${org.name}/${projectB.name}/999", "pull:${org.name}/${projectB.name}/1").forEach { bad ->
                        val response = mockMvc.perform(
                            get(url).queryParam("detail", bad).header("Turbo-Frame", "issue-detail").with(authOf(viewer))
                        ).andReturn().response

                        response.status shouldBeLessThan 500
                        response.contentAsString shouldNotContain "OrgIssues B selected detail body"
                        response.contentAsString shouldContain "turbo-visit-control"
                    }
                }
            }
        }
    }
}
