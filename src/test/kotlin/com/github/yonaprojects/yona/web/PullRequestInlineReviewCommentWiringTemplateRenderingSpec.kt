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

// pullrequest/view.html의 changes 탭이 새 인라인 리뷰 댓글 작성에 필요한 권한 플래그/POST
// URL/클릭 핸들러 스크립트를 실제로 내려주는지 확인한다. 실제 diff 줄 렌더링은
// PullRequestDiffLineCommentUiFragmentRenderingSpec이 프래그먼트 단위로 별도 검증한다.
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

            it("리뷰 댓글 작성 권한이 있는 멤버에게는 #changes에 canReviewComment=true와 POST URL이 내려가고 CodeCommentBox 팝업이 포함돼야 한다") {
                val result = mockMvc.perform(
                    get("/${project.owner}/${project.name}/pull/${pr.number}/changes").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                ).andReturn()

                val body = result.response.contentAsString
                val doc = Jsoup.parse(body)

                doc.select("#changes").attr("data-can-review-comment") shouldBe "true"
                doc.select("#changes").attr("data-review-comment-post-url") shouldBe
                    "/${project.owner}/${project.name}/pullRequest/${pr.id}/comments"

                // 매번 새 <textarea> 폼을 tr 뒤에 끼워 넣던 이전 구현(pr-comment-form-tr)을
                // legacy와 동일한 "단일 팝업 재사용"(CodeCommentBox) 아키텍처로 교체했다.
                body shouldContain "add-comment-btn-cell"
                body shouldContain "id=\"review-form\""
                body shouldContain "canReviewComment"
                body shouldNotContain "pr-comment-form-tr"
            }

            // 라인별(ranged) 리뷰 댓글 팝업(common/reviewForm.html)은 Vue 3 SFC
            // (<yona-review-form>)로 구현돼 있다 - 에디터(<yona-markdown-editor-vue>)/
            // 첨부파일(<yona-attachments>) 마크업은 서버 렌더링 시점이 아니라 그 커스텀
            // 엘리먼트가 클라이언트에서 마운트될 때 생성되므로, 서버 응답 HTML에는
            // <yona-review-form> 태그와 데이터 속성만 남는다(components/vue-widgets/src/
            // review-form/YonaReviewForm.vue의 onMounted가 host.getAttribute(...)로 읽는
            // 계약과 일치하는지 확인한다). 실제 드래그 범위선택/팝업 표시/제출 흐름은
            // Jsoup/MockMvc로 검증할 수 없어(브라우저 런타임 필요) Playwright로 별도 검증했다.
            it("새 라인/범위 댓글 팝업(review-form)은 <yona-review-form> 커스텀 엘리먼트로 렌더링되고 필요한 data 속성을 내려줘야 한다") {
                val result = mockMvc.perform(
                    get("/${project.owner}/${project.name}/pull/${pr.number}/changes").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                ).andReturn()

                val body = result.response.contentAsString
                val doc = Jsoup.parse(body)
                val reviewForm = doc.select("yona-review-form#review-form")

                reviewForm.isEmpty() shouldBe false
                reviewForm.attr("data-resource-type") shouldBe "REVIEW_COMMENT"
                reviewForm.attr("data-action") shouldNotContain "null"
                reviewForm.hasAttr("data-csrf-param") shouldBe true
                reviewForm.hasAttr("data-csrf-token") shouldBe true
                body shouldNotContain "textarea name=\"contents\""
            }
        }
    }
}
