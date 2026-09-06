package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequest
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestReview
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestService
import com.github.yonaprojects.yona.domain.role.Role
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

// yona-wiki P3-15(PR 승인/변경요청 워크플로) — PullRequestViewController.addCommonPrAttributes()가
// 채우는 reviews/approvalCount/changesRequestedCount/latestReviewStateByReviewerId/
// canApproveOrRequestChanges 모델 속성이 실제로 pullrequest/view.html + partial_info.html에
// 렌더링되는지(리뷰 이력 목록, 승인/변경요청 요약 뱃지, 자기 PR에는 Approve/Request changes 버튼이
// 숨겨지는지) end-to-end로 검증한다 — 단위 mockk 테스트로는 실제 Thymeleaf 프래그먼트 렌더링을
// 함께 검증할 수 없다(PullRequestAssigneeAndLabelTemplateRenderingSpec과 동일한 패턴 — 물리 git
// 저장소 없이 PullRequestRepository.save()로 직접 PR을 만들어 JGit 의존 없이 화면 렌더링만 검증).
@Transactional
class PullRequestReviewTemplateRenderingSpec @Autowired constructor(
    private val webApplicationContext: WebApplicationContext,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val pullRequestService: PullRequestService
) : AbstractIntegrationTest() {

    private lateinit var mockMvc: MockMvc

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
        }

        describe("PR 상세 화면의 리뷰 판정(Approve/Request changes/Comment) 렌더링") {
            fun authOf(u: User) = user(
                YonaUserDetails(
                    id = u.id ?: 0L,
                    loginId = u.loginId,
                    passwordVal = "hashed",
                    passwordSalt = "salt",
                    authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
                )
            )

            it("승인 판정이 있으면 리뷰 목록/승인 요약 뱃지가 렌더링되고, 다른 사용자에게는 Approve/Request changes 버튼이 노출되어야 한다") {
                val suffix = System.currentTimeMillis().toString()
                val contributor = userRepository.save(User(loginId = "prv-author-$suffix", name = "PR작성자", email = "prv-author-$suffix@yona.io"))
                val reviewer = userRepository.save(User(loginId = "prv-reviewer-$suffix", name = "리뷰어", email = "prv-reviewer-$suffix@yona.io"))
                val project = projectRepository.save(Project(name = "prv-repo-$suffix", owner = "prv-org-$suffix", projectScope = ProjectScope.PUBLIC))
                project.projectUsers.add(projectUserRepository.save(ProjectUser(user = contributor, project = project, role = Role(id = RoleType.MANAGER.roleType))))
                project.projectUsers.add(projectUserRepository.save(ProjectUser(user = reviewer, project = project, role = Role(id = RoleType.MEMBER.roleType))))

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "리뷰 렌더링 검증 PR", body = "본문",
                        toProject = project, fromProject = project,
                        toBranch = "master", fromBranch = "feature",
                        contributor = contributor, state = State.OPEN, number = 1L
                    )
                )
                pullRequestService.submitReview(pr.id!!, reviewer, PullRequestReview.ReviewState.APPROVE, "LGTM, 승인합니다")

                val body = mockMvc.perform(
                    get("/${project.owner}/${project.name}/pull/${pr.number}").with(authOf(reviewer))
                ).andExpect(status().isOk).andReturn().response.contentAsString

                body shouldContain "id=\"pr-reviews\""
                body shouldContain "LGTM, 승인합니다"
                body shouldContain "review-verdict-summary-approve"
                body shouldContain "id=\"btn-review-approve\""
                body shouldContain "id=\"btn-review-request-changes\""
            }

            it("PR 작성자 본인에게는 Approve/Request changes 버튼이 숨겨지고 Comment 버튼만 남아야 한다") {
                val suffix = System.currentTimeMillis().toString() + "-2"
                val contributor = userRepository.save(User(loginId = "prv-author2-$suffix", name = "PR작성자2", email = "prv-author2-$suffix@yona.io"))
                val project = projectRepository.save(Project(name = "prv-repo2-$suffix", owner = "prv-org2-$suffix", projectScope = ProjectScope.PUBLIC))
                project.projectUsers.add(projectUserRepository.save(ProjectUser(user = contributor, project = project, role = Role(id = RoleType.MANAGER.roleType))))

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "자기 PR 렌더링 검증", body = "본문",
                        toProject = project, fromProject = project,
                        toBranch = "master", fromBranch = "feature2",
                        contributor = contributor, state = State.OPEN, number = 1L
                    )
                )

                val body = mockMvc.perform(
                    get("/${project.owner}/${project.name}/pull/${pr.number}").with(authOf(contributor))
                ).andExpect(status().isOk).andReturn().response.contentAsString

                body shouldNotContain "id=\"btn-review-approve\""
                body shouldNotContain "id=\"btn-review-request-changes\""
                body shouldContain "id=\"btn-review-comment\""
            }

            it("리뷰가 없으면 승인/변경요청 요약 뱃지가 렌더링되지 않아야 한다") {
                val suffix = System.currentTimeMillis().toString() + "-3"
                val contributor = userRepository.save(User(loginId = "prv-author3-$suffix", name = "PR작성자3", email = "prv-author3-$suffix@yona.io"))
                val reviewer = userRepository.save(User(loginId = "prv-reviewer3-$suffix", name = "리뷰어3", email = "prv-reviewer3-$suffix@yona.io"))
                val project = projectRepository.save(Project(name = "prv-repo3-$suffix", owner = "prv-org3-$suffix", projectScope = ProjectScope.PUBLIC))
                project.projectUsers.add(projectUserRepository.save(ProjectUser(user = contributor, project = project, role = Role(id = RoleType.MANAGER.roleType))))
                project.projectUsers.add(projectUserRepository.save(ProjectUser(user = reviewer, project = project, role = Role(id = RoleType.MEMBER.roleType))))

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "빈 리뷰 상태 검증", body = "본문",
                        toProject = project, fromProject = project,
                        toBranch = "master", fromBranch = "feature3",
                        contributor = contributor, state = State.OPEN, number = 1L
                    )
                )

                val body = mockMvc.perform(
                    get("/${project.owner}/${project.name}/pull/${pr.number}").with(authOf(reviewer))
                ).andExpect(status().isOk).andReturn().response.contentAsString

                body shouldNotContain "review-verdict-summary-approve"
                body shouldNotContain "review-verdict-summary-request-changes"
            }
        }
    }
}
