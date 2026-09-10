package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.jsoup.Jsoup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

// P3-48 화면별 재현 세션에서 발견: issue/view.html·board/view.html의 새 댓글 등록 폼
// (#comment-form)이 형제 폼들(.comment-update-form/.child-comment-form)과 달리 AJAX로 전환되지
// 않은 채 legacy 스타일 풀페이지 multipart 제출로 남아있었는데, 그 th:action이 가리키는
// "/{owner}/{projectName}/issue/{issueNumber}/comment" · ".../post/{postNumber}/comment" 경로
// 자체가 어느 컨트롤러에도 매핑돼 있지 않아 실제로 댓글을 등록해보면 항상 404였다(Playwright로
// 실제 재현). CSRF와는 무관한 별도 결함이지만 P3-48이 요구하는 화면별 실사용 검증 도중 발견해
// 그 자리에서 함께 고쳤다 — 형제 폼들과 동일하게 REST(POST .../comments)로 전환.
class NewCommentFormAjaxWiringTemplateEquivalenceSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val issueRepository: IssueRepository,
    private val postingRepository: PostingRepository
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
        }

        describe("P3-48: 새 댓글 등록 폼(#comment-form)은 실제 존재하는 REST 엔드포인트로 배선돼야 한다") {
            val owner = userRepository.findByLoginId("newcomment-owner").orElseGet {
                userRepository.save(User(loginId = "newcomment-owner", name = "새댓글소유자", email = "newcomment-owner@yona.io"))
            }
            val ownerDetails = YonaUserDetails(
                id = owner.id!!,
                loginId = owner.loginId,
                passwordVal = "hashed",
                passwordSalt = "salt",
                authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
            )

            val project = projectRepository.findAll().find { it.name == "newcomment-proj" && it.owner == "newcomment-owner" }
                ?: projectRepository.save(
                    Project(
                        name = "newcomment-proj",
                        owner = "newcomment-owner",
                        projectScope = ProjectScope.PUBLIC,
                        isCodeAccessibleMemberOnly = false,
                        vcs = "GIT"
                    )
                )

            val issue = issueRepository.findAll().find { it.project.id == project.id && it.title == "새댓글 이슈" }
                ?: issueRepository.save(
                    Issue(
                        title = "새댓글 이슈", body = "본문", project = project, number = 1L,
                        authorId = owner.id, authorLoginId = owner.loginId
                    )
                )

            val posting = postingRepository.findAll().find { it.project.id == project.id && it.title == "새댓글 게시글" }
                ?: postingRepository.save(
                    Posting(
                        title = "새댓글 게시글", body = "본문", project = project, number = 1L,
                        authorId = owner.id, authorLoginId = owner.loginId
                    )
                )

            it("issue/view의 #comment-form은 data-api-base로 실제 댓글 생성 API를 가리키고 그 API가 실제로 동작해야 한다") {
                val doc = Jsoup.parse(
                    mockMvc.perform(
                        get("/${project.owner}/${project.name}/issue/${issue.number}")
                            .with(SecurityMockMvcRequestPostProcessors.user(ownerDetails))
                    ).andExpect(status().isOk).andReturn().response.contentAsString
                )

                val apiBase = doc.select("form#comment-form").attr("data-api-base")
                apiBase shouldBe "/api/projects/${project.id}/issues/${issue.number}/comments"

                mockMvc.perform(
                    post(apiBase)
                        .with(SecurityMockMvcRequestPostProcessors.user(ownerDetails))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"contents":"실제 새 댓글 등록 테스트"}""")
                ).andExpect(status().isCreated)
            }

            it("board/view의 #comment-form은 data-api-base로 실제 댓글 생성 API를 가리키고 그 API가 실제로 동작해야 한다") {
                val doc = Jsoup.parse(
                    mockMvc.perform(
                        get("/${project.owner}/${project.name}/post/${posting.number}")
                            .with(SecurityMockMvcRequestPostProcessors.user(ownerDetails))
                    ).andExpect(status().isOk).andReturn().response.contentAsString
                )

                val apiBase = doc.select("form#comment-form").attr("data-api-base")
                apiBase shouldBe "/api/projects/${project.id}/posts/${posting.number}/comments"

                mockMvc.perform(
                    post(apiBase)
                        .with(SecurityMockMvcRequestPostProcessors.user(ownerDetails))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"contents":"실제 새 댓글 등록 테스트"}""")
                ).andExpect(status().isCreated)
            }
        }
    }
}
