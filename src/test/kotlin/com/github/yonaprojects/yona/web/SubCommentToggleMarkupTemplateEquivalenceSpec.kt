package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingComment
import com.github.yonaprojects.yona.domain.board.PostingCommentRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueComment
import com.github.yonaprojects.yona.domain.issue.IssueCommentRepository
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
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.Instant

// P3-53 항목2: common/childComments.html의 대댓글 입력창(.add-a-comment/.child-comment-input-form)이
// yobi.css의 숨김 규칙(.board-comment-wrap .comments .comment ...)과 매치되지 않아 페이지 로드 시부터
// 상시 펼쳐진 채로 보이는 UI 회귀. legacy v1.6(app/views/issue|board/partial_comments.scala.html)은
// <ul class="comments"><li class="comment">...</li></ul> 구조로 .comments가 .comment의 직계
// 조상이었지만, 이식된 issue/view.html·board/view.html은 #comments.board-comment-wrap → #timeline →
// .timeline-list(→ .comment) 구조라 그 .comments 조상이 없다. .timeline-list에 comments 클래스를
// 추가해 legacy와 동치인 조상 관계를 복원하고, 토글 담당 스크립트 yona.SubComment.js를 로드한다.
class SubCommentToggleMarkupTemplateEquivalenceSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val issueRepository: IssueRepository,
    private val issueCommentRepository: IssueCommentRepository,
    private val postingRepository: PostingRepository,
    private val postingCommentRepository: PostingCommentRepository
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
        }

        describe("P3-53 항목2: 대댓글 입력창은 yobi.css의 숨김 선택자가 매치되는 조상 구조로 렌더링되고 토글 스크립트를 로드해야 한다") {
            val author = userRepository.findByLoginId("subcomment-author").orElseGet {
                userRepository.save(User(loginId = "subcomment-author", name = "대댓글작성자", email = "subcomment-author@yona.io"))
            }
            val authorDetails = YonaUserDetails(
                id = author.id!!,
                loginId = author.loginId,
                passwordVal = "hashed",
                passwordSalt = "salt",
                authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
            )

            val project = projectRepository.findAll().find { it.name == "subcomment-proj" && it.owner == "subcomment-author" }
                ?: projectRepository.save(
                    Project(
                        name = "subcomment-proj",
                        owner = "subcomment-author",
                        projectScope = ProjectScope.PUBLIC,
                        isCodeAccessibleMemberOnly = false,
                        vcs = "GIT"
                    )
                )

            val issue = issueRepository.findAll().find { it.project.id == project.id && it.title == "대댓글 이슈" }
                ?: issueRepository.save(
                    Issue(
                        title = "대댓글 이슈", body = "본문", project = project, number = 1L,
                        authorId = author.id, authorLoginId = author.loginId
                    )
                )

            issueCommentRepository.findAll().find { it.issue.id == issue.id && it.contents == "대댓글 테스트용 이슈 댓글" }
                ?: issueCommentRepository.save(
                    IssueComment(
                        contents = "대댓글 테스트용 이슈 댓글", createdDate = Instant.now(),
                        authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                        projectId = project.id, issue = issue
                    )
                )

            val posting = postingRepository.findAll().find { it.project.id == project.id && it.title == "대댓글 게시글" }
                ?: postingRepository.save(
                    Posting(
                        title = "대댓글 게시글", body = "본문", project = project, number = 1L,
                        authorId = author.id, authorLoginId = author.loginId
                    )
                )

            postingCommentRepository.findAll().find { it.posting.id == posting.id && it.contents == "대댓글 테스트용 게시글 댓글" }
                ?: postingCommentRepository.save(
                    PostingComment(
                        contents = "대댓글 테스트용 게시글 댓글", createdDate = Instant.now(),
                        authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                        projectId = project.id, posting = posting
                    )
                )

            it("issue/view.html의 .comment는 legacy와 동치인 .comments 조상 아래에서 렌더링돼 yobi.css 숨김 선택자가 매치돼야 한다") {
                val doc = Jsoup.parse(
                    mockMvc.perform(
                        get("/${project.owner}/${project.name}/issue/${issue.number}")
                            .with(SecurityMockMvcRequestPostProcessors.user(authorDetails))
                    ).andExpect(status().isOk).andReturn().response.contentAsString
                )

                doc.select("#comments .comments .comment .add-a-comment").isEmpty() shouldBe false
                doc.select("#comments .comments .comment .child-comment-input-form").isEmpty() shouldBe false
            }

            it("board/view.html의 .comment도 동일하게 .comments 조상 아래에서 렌더링돼야 한다") {
                val doc = Jsoup.parse(
                    mockMvc.perform(
                        get("/${project.owner}/${project.name}/post/${posting.number}")
                            .with(SecurityMockMvcRequestPostProcessors.user(authorDetails))
                    ).andExpect(status().isOk).andReturn().response.contentAsString
                )

                doc.select("#comments .comments .comment .add-a-comment").isEmpty() shouldBe false
                doc.select("#comments .comments .comment .child-comment-input-form").isEmpty() shouldBe false
            }

            it("issue/view.html·board/view.html은 대댓글 입력창 토글을 담당하는 yona.SubComment.js를 로드해야 한다") {
                val issueBody = mockMvc.perform(
                    get("/${project.owner}/${project.name}/issue/${issue.number}")
                        .with(SecurityMockMvcRequestPostProcessors.user(authorDetails))
                ).andExpect(status().isOk).andReturn().response.contentAsString
                val boardBody = mockMvc.perform(
                    get("/${project.owner}/${project.name}/post/${posting.number}")
                        .with(SecurityMockMvcRequestPostProcessors.user(authorDetails))
                ).andExpect(status().isOk).andReturn().response.contentAsString

                issueBody.contains("/javascripts/common/yona.SubComment.js") shouldBe true
                boardBody.contains("/javascripts/common/yona.SubComment.js") shouldBe true
            }
        }
    }
}
