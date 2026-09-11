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
import com.github.yonaprojects.yona.domain.role.RoleRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.jsoup.Jsoup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

// P3-52 항목4 (프론트엔드 배선 레벨) — pullrequest/view.html의 changes 탭이 새 인라인 리뷰
// 댓글 작성에 필요한 권한 플래그/POST URL/클릭 핸들러 스크립트를 실제로 내려주는지 확인한다.
// 실제 diff 줄 렌더링 자체는 PullRequestDiffLineCommentUiFragmentRenderingSpec이 프래그먼트
// 단위로 별도 검증한다(실제 git 저장소 diff 없이도 이 화면 자체가 200으로 렌더링됨을 확인).
class PullRequestInlineReviewCommentWiringTemplateRenderingSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val roleRepository: RoleRepository,
    private val pullRequestRepository: PullRequestRepository
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
        }

        describe("PR changes 탭의 인라인 리뷰 댓글 작성 배선") {
            val member = userRepository.findByLoginId("prdiffui-member").orElseGet {
                userRepository.save(User(loginId = "prdiffui-member", name = "PR인라인멤버", email = "prdiffui-member@yona.io"))
            }
            val roleMember = roleRepository.findById(RoleType.MEMBER.roleType).orElseGet {
                roleRepository.save(Role(id = RoleType.MEMBER.roleType, name = "MEMBER"))
            }
            val project = projectRepository.findAll().find { it.name == "prdiffui-proj" } ?: projectRepository.save(
                Project(name = "prdiffui-proj", owner = "prdiffui-owner", projectScope = ProjectScope.PUBLIC, vcs = "GIT")
            )
            if (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, member.id!!)) {
                projectUserRepository.save(ProjectUser(project = project, user = member, role = roleMember))
            }
            val memberDetails = YonaUserDetails(
                id = member.id!!,
                loginId = member.loginId,
                passwordVal = "hashed",
                passwordSalt = "salt",
                authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
            )
            val pr = pullRequestRepository.findAll().find { it.title == "인라인UI테스트PR" } ?: pullRequestRepository.save(
                PullRequest(
                    title = "인라인UI테스트PR", body = "본문", toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature-inline", contributor = member, state = State.OPEN, number = 1L
                )
            )

            it("리뷰 댓글 작성 권한이 있는 멤버에게는 #changes에 canReviewComment=true와 POST URL이 내려가고 클릭 핸들러 스크립트가 포함돼야 한다") {
                val result = mockMvc.perform(
                    get("/${project.owner}/${project.name}/pull/${pr.number}/changes").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                ).andReturn()

                val body = result.response.contentAsString
                val doc = Jsoup.parse(body)

                doc.select("#changes").attr("data-can-review-comment") shouldBe "true"
                doc.select("#changes").attr("data-review-comment-post-url") shouldBe
                    "/${project.owner}/${project.name}/pullRequest/${pr.id}/comments"

                body shouldContain "add-comment-btn-cell"
                body shouldContain "pr-comment-form-tr"
                body shouldContain "canReviewComment"
            }

            // P3-55: 라인별(ranged) 리뷰 댓글 삽입 폼이 순수 <textarea>가 아니라
            // <yona-markdown-editor>(CM6 에디터, P3-46에서 16개 화면에 이미 붙인 컴포넌트)를
            // 쓰도록 교체됐는지 확인한다. 실제 Shadow DOM 초기화/툴바/멘션/미리보기 동작 자체는
            // Jsoup/MockMvc로 검증할 수 없어(브라우저 런타임 필요) 완료 시점에 Playwright로
            // 1회 별도 확인한다 - 여기서는 순수 문자열 <textarea name="contents">가 더 이상
            // 남아있지 않고, markdownEditor 프래그먼트와 동일한 마크업 계약
            // (data-toggle="markdown-editor" + render-url/mention-url + <yona-markdown-editor>)이
            // 인라인 스크립트에 내려가는지만 확인한다.
            it("라인별 댓글 삽입 폼에는 순수 textarea 대신 yona-markdown-editor가 쓰여야 한다") {
                val result = mockMvc.perform(
                    get("/${project.owner}/${project.name}/pull/${pr.number}/changes").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                ).andReturn()

                val body = result.response.contentAsString

                body shouldContain "<yona-markdown-editor name=\"contents\" editor-mode=\"code-review-body\">"
                body shouldContain "data-toggle=\"markdown-editor\""
                body shouldContain "markdownRenderUrl"
                body shouldContain "mentionUrl"
                body shouldNotContain "textarea name=\"contents\""
            }
        }
    }
}
