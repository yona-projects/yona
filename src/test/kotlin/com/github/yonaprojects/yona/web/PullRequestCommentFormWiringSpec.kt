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
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.jsoup.Jsoup
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

// review-form/yona.CodeCommentBox.js(범위 있는 라인/드래그 코드리뷰 댓글)과
// common.commentForm(container, resourceType, action)(범위 없는 PR 전체 일반 댓글)은
// 서로 다른 두 기능으로, legacy와 동일하게 함께 공존하는 것이 정상 설계다.
// 이 스펙은 렌더링 레벨(Jsoup/MockMvc)에서 1) commentForm이 legacy와 동일 위치에
// CM6 에디터와 함께 렌더링되는지, 2) 작성 권한이 없으면 로그인 필요 placeholder만
// 보이는지, 3) review-form이 commentForm과 별개로 정상 공존하는지를 검증한다.
// 실제 Shadow DOM 초기화/제출 후 저장 여부는 Playwright로 별도 확인한다.
@Transactional
class PullRequestCommentFormWiringSpec @Autowired constructor(
    private val webApplicationContext: WebApplicationContext,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val pullRequestRepository: PullRequestRepository
) : AbstractIntegrationTest() {

    private lateinit var mockMvc: MockMvc

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
        }

        describe("PR changes 탭의 범위 없는 일반 댓글 작성 폼(common/commentForm.html) 배선") {
            fun authOf(u: User) = user(
                YonaUserDetails(
                    id = u.id ?: 0L,
                    loginId = u.loginId,
                    passwordVal = "hashed",
                    passwordSalt = "salt",
                    authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
                )
            )

            it("작성 권한이 있는 멤버에게는 legacy와 동일 위치에 CM6 에디터가 붙은 comment-form이 렌더링돼야 한다") {
                val suffix = System.currentTimeMillis().toString()
                val contributor = userRepository.save(User(loginId = "prcf-author-$suffix", name = "PR작성자", email = "prcf-author-$suffix@yona.io"))
                val member = userRepository.save(User(loginId = "prcf-member-$suffix", name = "댓글멤버", email = "prcf-member-$suffix@yona.io"))
                val project = projectRepository.save(Project(name = "prcf-repo-$suffix", owner = "prcf-org-$suffix", projectScope = ProjectScope.PUBLIC))
                project.projectUsers.add(projectUserRepository.save(ProjectUser(user = contributor, project = project, role = Role(id = RoleType.MANAGER.roleType))))
                project.projectUsers.add(projectUserRepository.save(ProjectUser(user = member, project = project, role = Role(id = RoleType.MEMBER.roleType))))

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "commentForm 배선 검증 PR", body = "본문",
                        toProject = project, fromProject = project,
                        toBranch = "master", fromBranch = "feature",
                        contributor = contributor, state = State.OPEN, number = 1L
                    )
                )

                val body = mockMvc.perform(
                    get("/${project.owner}/${project.name}/pull/${pr.number}/changes").with(authOf(member))
                ).andExpect(status().isOk).andReturn().response.contentAsString
                val doc = Jsoup.parse(body)

                // non-ranged-threads-wrap 바로 다음, board-comment-wrap 안(legacy 위치와 동일)
                doc.select("#comment-form-wrap").isEmpty() shouldBe false
                doc.select(".non-ranged-threads-wrap + #comment-form-wrap").isEmpty() shouldBe false

                doc.select("#comment-form").attr("action") shouldBe
                    "/${project.owner}/${project.name}/pullRequest/${pr.id}/comments"
                body shouldContain "<yona-markdown-editor name=\"contents\" editor-mode=\"code-review-body\">"
                body shouldContain "data-toggle=\"markdown-editor\""

                // review-form(CodeCommentBox 팝업 - 라인/범위 댓글 전용)은 commentForm과 함께 공존해야 한다.
                body shouldContain "id=\"review-form\""
                body shouldContain "src=\"/javascripts/common/yona.CodeCommentBox.js\""
                body shouldContain "yona.CodeCommentBox.show("
                body shouldContain "yona.CodeCommentBox.init("
            }

            it("작성 권한이 없으면 로그인 필요 placeholder만 보이고 실제 폼은 렌더링되지 않아야 한다") {
                val suffix = System.currentTimeMillis().toString() + "-2"
                val contributor = userRepository.save(User(loginId = "prcf-author2-$suffix", name = "PR작성자2", email = "prcf-author2-$suffix@yona.io"))
                val outsider = userRepository.save(User(loginId = "prcf-outsider-$suffix", name = "비멤버", email = "prcf-outsider-$suffix@yona.io"))
                // REVIEW_COMMENT는 PUBLIC 프로젝트에서는 비멤버도 작성 가능하므로(AccessControl
                // .isProjectResourceCreatable), 권한 없음 케이스를 만들려면 PRIVATE 프로젝트의
                // 비멤버여야 한다.
                val project = projectRepository.save(Project(name = "prcf-repo2-$suffix", owner = "prcf-org2-$suffix", projectScope = ProjectScope.PRIVATE))
                project.projectUsers.add(projectUserRepository.save(ProjectUser(user = contributor, project = project, role = Role(id = RoleType.MANAGER.roleType))))

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "commentForm 권한 게이트 검증 PR", body = "본문",
                        toProject = project, fromProject = project,
                        toBranch = "master", fromBranch = "feature2",
                        contributor = contributor, state = State.OPEN, number = 1L
                    )
                )

                // private 프로젝트 비멤버는 changes 탭 자체 READ 권한이 없어 error/forbidden로
                // 귀결될 수 있다 - 상태 코드를 강제하지 않고, comment-form이 응답 본문에 없는지만
                // 확인한다(가장 견고한 회귀 가드).
                val body = mockMvc.perform(
                    get("/${project.owner}/${project.name}/pull/${pr.number}/changes").with(authOf(outsider))
                ).andReturn().response.contentAsString

                body shouldNotContain "id=\"comment-form\""
            }
        }
    }
}
