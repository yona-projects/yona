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

// P3-60: 이슈/게시글 댓글 본문에 GFM 체크리스트 진행률 바(.tasklist 셸)와 data-allowed-update
// 속성이 본문에는 있지만 댓글에는 빠져 있었다(대칭적 격차). issue/view.html·board/view.html의
// 댓글 렌더링 블록에 본문과 동일한 .tasklist 셸을 추가하고 댓글별 data-allowed-update를
// 배선했는지, 그리고 체크박스 클릭 시 실제 PATCH 흐름(commentUpdateForm의 action)이 올바른
// 엔드포인트를 가리키는지 검증한다.
class CommentTasklistProgressBarTemplateRenderingSpec @Autowired constructor(
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

        describe("P3-60: 댓글 본문에도 .tasklist 셸과 data-allowed-update가 배선돼야 한다") {
            val author = userRepository.findByLoginId("cmttask-author").orElseGet {
                userRepository.save(User(loginId = "cmttask-author", name = "댓글작성자", email = "cmttask-author@yona.io"))
            }
            val outsider = userRepository.findByLoginId("cmttask-outsider").orElseGet {
                userRepository.save(User(loginId = "cmttask-outsider", name = "외부인", email = "cmttask-outsider@yona.io"))
            }

            fun details(u: User) = YonaUserDetails(
                id = u.id!!,
                loginId = u.loginId,
                passwordVal = "hashed",
                passwordSalt = "salt",
                authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
            )

            val project = projectRepository.findAll().find { it.name == "cmttask-proj" && it.owner == "cmttask-author" }
                ?: projectRepository.save(
                    Project(name = "cmttask-proj", owner = "cmttask-author", projectScope = ProjectScope.PUBLIC, vcs = "GIT")
                )

            val issue = issueRepository.findAll().find { it.project.id == project.id && it.title == "체크리스트 이슈" }
                ?: issueRepository.save(
                    Issue(title = "체크리스트 이슈", body = "본문", project = project, number = 1L, authorId = author.id, authorLoginId = author.loginId)
                )

            val issueComment = issueCommentRepository.findAll().find { it.issue.id == issue.id && it.contents.contains("체크리스트 댓글") }
                ?: issueCommentRepository.save(
                    IssueComment(
                        contents = "체크리스트 댓글\n- [ ] task one\n- [x] task two", createdDate = Instant.now(),
                        authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                        projectId = project.id, issue = issue
                    )
                )

            val posting = postingRepository.findAll().find { it.project.id == project.id && it.title == "체크리스트 게시글" }
                ?: postingRepository.save(
                    Posting(title = "체크리스트 게시글", body = "본문", project = project, number = 1L, authorId = author.id, authorLoginId = author.loginId)
                )

            val postingComment = postingCommentRepository.findAll().find { it.posting.id == posting.id && it.contents.contains("체크리스트 댓글") }
                ?: postingCommentRepository.save(
                    PostingComment(
                        contents = "체크리스트 댓글\n- [ ] task one\n- [x] task two", createdDate = Instant.now(),
                        authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                        projectId = project.id, posting = posting
                    )
                )

            it("issue/view.html 댓글 블록에 .tasklist 셸이 markdown-wrap 바로 앞에 렌더링돼야 한다") {
                val doc = Jsoup.parse(
                    mockMvc.perform(
                        get("/${project.owner}/${project.name}/issue/${issue.number}")
                            .with(SecurityMockMvcRequestPostProcessors.user(details(author)))
                    ).andExpect(status().isOk).andReturn().response.contentAsString
                )

                val commentBody = doc.select("#comment-body-${issueComment.id}")
                commentBody.select("> .tasklist").size shouldBe 1
                val children = commentBody.first()!!.children()
                children[0].hasClass("tasklist") shouldBe true
                children[1].hasClass("markdown-wrap") shouldBe true
            }

            it("issue/view.html 댓글 markdown-wrap의 data-allowed-update는 작성자 본인=true, 무관한 사용자=false여야 한다") {
                val docAsAuthor = Jsoup.parse(
                    mockMvc.perform(
                        get("/${project.owner}/${project.name}/issue/${issue.number}")
                            .with(SecurityMockMvcRequestPostProcessors.user(details(author)))
                    ).andExpect(status().isOk).andReturn().response.contentAsString
                )
                docAsAuthor.select("#comment-body-${issueComment.id} .markdown-wrap").attr("data-allowed-update") shouldBe "true"

                val docAsOutsider = Jsoup.parse(
                    mockMvc.perform(
                        get("/${project.owner}/${project.name}/issue/${issue.number}")
                            .with(SecurityMockMvcRequestPostProcessors.user(details(outsider)))
                    ).andExpect(status().isOk).andReturn().response.contentAsString
                )
                docAsOutsider.select("#comment-body-${issueComment.id} .markdown-wrap").attr("data-allowed-update") shouldBe "false"
            }

            it("board/view.html 댓글 블록에도 동일하게 .tasklist 셸과 data-allowed-update가 배선돼야 한다") {
                val doc = Jsoup.parse(
                    mockMvc.perform(
                        get("/${project.owner}/${project.name}/post/${posting.number}")
                            .with(SecurityMockMvcRequestPostProcessors.user(details(author)))
                    ).andExpect(status().isOk).andReturn().response.contentAsString
                )

                val commentBody = doc.select("#comment-body-${postingComment.id}")
                commentBody.select("> .tasklist").size shouldBe 1
                commentBody.select(".markdown-wrap").attr("data-allowed-update") shouldBe "true"
            }

            it("이슈 댓글 수정 폼의 action은 체크박스 클릭 시 PATCH가 실제로 도달하는 legacy 호환 엔드포인트를 가리켜야 한다") {
                val doc = Jsoup.parse(
                    mockMvc.perform(
                        get("/${project.owner}/${project.name}/issue/${issue.number}")
                            .with(SecurityMockMvcRequestPostProcessors.user(details(author)))
                    ).andExpect(status().isOk).andReturn().response.contentAsString
                )

                val form = doc.select("#comment-update-form-${issueComment.id}")
                form.attr("action") shouldBe "/${project.owner}/${project.name}/issue/${issue.number}/comments/${issueComment.id}"
            }

            it("게시글 댓글 수정 폼의 action도 legacy 호환 엔드포인트(post/.../comment/{id}, 단수)를 가리켜야 한다") {
                val doc = Jsoup.parse(
                    mockMvc.perform(
                        get("/${project.owner}/${project.name}/post/${posting.number}")
                            .with(SecurityMockMvcRequestPostProcessors.user(details(author)))
                    ).andExpect(status().isOk).andReturn().response.contentAsString
                )

                val form = doc.select("#comment-update-form-${postingComment.id}")
                form.attr("action") shouldBe "/${project.owner}/${project.name}/post/${posting.number}/comment/${postingComment.id}"
            }
        }
    }
}
