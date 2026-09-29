package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.issue.Assignee
import com.github.yonaprojects.yona.domain.issue.AssigneeRepository
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueRepository
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

// P3-75 Step 4 — 내 이슈(/user/issues) 2단 보기의 iframe(pageslide)을 Turbo Frames로 전환.
// 이 화면은 여러 프로젝트에 걸친 목록이라 `?selected=<번호>`로는 항목을 특정할 수 없어 복합 키
// `?detail=issue:<owner>/<project>/<번호>`(TwoColumnSelection.Key)를 쓴다. 서버는 키를 파싱해
// 기존 이슈 상세 로직(viewIssue)에 위임하므로 프로젝트 읽기 권한 게이트가 그대로 적용돼야 한다.
@Transactional
class MyIssuesTurboFrameRenderingSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val issueRepository: IssueRepository,
    private val assigneeRepository: AssigneeRepository,
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

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
        }

        describe("내 이슈 2단 보기(Turbo Frames)") {
            // 각 it가 자기 트랜잭션 안에서 픽스처를 만든다(container 본문은 트랜잭션 밖이라 detached 엔티티가 된다).
            class Fixture {
                val suffix = System.nanoTime().toString()
                val me = userRepository.save(User(loginId = "myiss-me-$suffix", name = "나", email = "myiss-me-$suffix@yona.io"))
                val other = userRepository.save(User(loginId = "myiss-other-$suffix", name = "타인", email = "myiss-other-$suffix@yona.io"))
                val project = projectRepository.save(Project(name = "myiss-proj-$suffix", owner = "myiss-owner-$suffix", projectScope = ProjectScope.PUBLIC))
                val assignee = assigneeRepository.save(Assignee(user = me, project = project))

                fun issue(number: Long, title: String, body: String, target: Project = project, assigned: Assignee? = assignee) =
                    issueRepository.save(
                        Issue(title = title, body = body, project = target, number = number, authorId = other.id,
                            authorLoginId = other.loginId, authorName = other.name, state = State.OPEN, assignee = assigned)
                    )

                init {
                    issue(1L, "MyIssues listed other $suffix", "listed other body")
                    issue(2L, "MyIssues selected $suffix", "MyIssues selected detail body")
                }

                val key = "issue:${project.owner}/${project.name}/2"
            }

            it("헤더 없는 요청은 목록 프레임과 상세 프레임을 함께 렌더링하고 행 링크에 복합 키를 싣는다") {
                with(Fixture()) {
                val body = mockMvc.perform(get("/user/issues").queryParam("detail", key).with(authOf(me)))
                    .andExpect(status().isOk).andReturn().response.contentAsString

                body shouldContain "id=\"issue-list\""
                body shouldContain "id=\"issue-detail\""
                body shouldContain "MyIssues selected detail body"
                body shouldContain "MyIssues listed other $suffix"
                body shouldContain "data-selection-url=\"/user/issues?detail=issue:${project.owner}/${project.name}/1\""
                body shouldContain "data-detail-url=\"/${project.owner}/${project.name}/issue/1\""
                }
            }

            it("Turbo-Frame: issue-detail 요청은 목록 없이 상세 fragment만 반환한다") {
                with(Fixture()) {
                val body = mockMvc.perform(
                    get("/user/issues").queryParam("detail", key).header("Turbo-Frame", "issue-detail").with(authOf(me))
                ).andExpect(status().isOk).andReturn().response.contentAsString

                body shouldContain "id=\"issue-detail\""
                body shouldContain "MyIssues selected detail body"
                body shouldNotContain "<!DOCTYPE html>"
                body shouldNotContain "id=\"issue-list\""
                body shouldNotContain "MyIssues listed other $suffix"
                }
            }

            it("Turbo-Frame 요청은 같은 이슈의 단독 상세 조회보다 SQL을 더 실행하지 않는다") {
                with(Fixture()) {
                val statistics = entityManagerFactory.unwrap(SessionFactory::class.java).statistics
                statistics.setStatisticsEnabled(true)

                statistics.clear()
                mockMvc.perform(get("/${project.owner}/${project.name}/issue/2").with(authOf(me))).andExpect(status().isOk)
                val plainDetailSqlCount = statistics.prepareStatementCount

                statistics.clear()
                mockMvc.perform(
                    get("/user/issues").queryParam("detail", key).header("Turbo-Frame", "issue-detail").with(authOf(me))
                ).andExpect(status().isOk)
                val frameSqlCount = statistics.prepareStatementCount

                // 로그인 사용자 조회가 한 번 더 있으므로 그만큼의 여유만 허용한다(목록 조회 SQL은 훨씬 크다).
                frameSqlCount shouldBeLessThanOrEqualTo plainDetailSqlCount + 1
                }
            }

            it("읽기 권한이 없는 비공개 프로젝트의 키는 상세를 노출하지 않는다") {
                with(Fixture()) {
                val secret = projectRepository.save(Project(name = "myiss-secret-$suffix", owner = "myiss-secret-owner-$suffix", projectScope = ProjectScope.PRIVATE))
                val secretAssignee = assigneeRepository.save(Assignee(user = me, project = secret))
                issue(1L, "MyIssues secret title $suffix", "MyIssues secret body $suffix", secret, secretAssignee)

                val body = mockMvc.perform(
                    get("/user/issues")
                        .queryParam("detail", "issue:${secret.owner}/${secret.name}/1")
                        .header("Turbo-Frame", "issue-detail")
                        .with(authOf(userRepository.save(User(loginId = "myiss-stranger-$suffix", name = "낯선이", email = "myiss-stranger-$suffix@yona.io"))))
                ).andReturn().response.contentAsString

                body shouldNotContain "MyIssues secret body $suffix"
                body shouldNotContain "MyIssues secret title $suffix"
                }
            }

            it("형식이 잘못된 키나 존재하지 않는 항목은 상세를 노출하지 않고 프레임 오류로 처리한다") {
                with(Fixture()) {
                listOf("garbage", "issue:nobody/nothing/9", "issue:${project.owner}/${project.name}/999", "pull:${project.owner}/${project.name}/2").forEach { bad ->
                    val response = mockMvc.perform(
                        get("/user/issues").queryParam("detail", bad).header("Turbo-Frame", "issue-detail").with(authOf(me))
                    ).andReturn().response
                    // 제네릭 404 뷰는 404 상태로, 컨텍스트 인지형 오류 뷰는 200으로 렌더링된다 - 5xx만 아니면 된다.
                    response.status shouldBeLessThan 500
                    val body = response.contentAsString

                    body shouldNotContain "MyIssues selected detail body"
                    body shouldContain "turbo-visit-control"
                }
                }
            }

            it("로그인하지 않으면 키가 있어도 로그인 화면으로 보낸다") {
                with(Fixture()) {
                mockMvc.perform(get("/user/issues").queryParam("detail", key))
                    .andExpect { result ->
                        val location = result.response.getHeader("Location").orEmpty()
                        location shouldContain "login"
                    }
                }
            }
        }
    }
}
