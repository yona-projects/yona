package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.attachment.Attachment
import com.github.yonaprojects.yona.domain.attachment.AttachmentRepository
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingCommentRepository
import com.github.yonaprojects.yona.domain.board.PostingComment
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
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

// P3-50: common/commentUpdateForm.html이 새 댓글 작성 폼과 동일한 범용 common/uploadForm
// 프래그먼트를 재사용해, 이미 첨부된 파일이 있는 댓글을 수정하려고 열어도 그 파일들이 전혀
// 보이지 않았다(삭제도 불가능, 몇 개나 첨부돼 있는지도 알 수 없었음). v1.6 원본
// commentUpdateForm.scala.html은 전용 마크업(.file-upload__input/.temporaryUploadFiles/
// .attachment-files, attachmentFile(...) 파샬)을 쓰고 이미 이식된 yona.CommentAttachmentsUpdate.js가
// 그 마크업의 클릭/드래그드롭/붙여넣기/삭제 이벤트를 담당한다.
class CommentUpdateFormAttachmentTemplateEquivalenceSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val issueRepository: IssueRepository,
    private val issueCommentRepository: IssueCommentRepository,
    private val postingRepository: PostingRepository,
    private val postingCommentRepository: PostingCommentRepository,
    private val attachmentRepository: AttachmentRepository
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
        }

        describe("P3-50: 첨부파일이 있는 댓글의 수정 폼은 legacy 마크업으로 그 파일 목록을 보여줘야 한다") {
            val author = userRepository.findByLoginId("cmtattach-author").orElseGet {
                userRepository.save(User(loginId = "cmtattach-author", name = "댓글첨부작성자", email = "cmtattach-author@yona.io"))
            }
            val authorDetails = YonaUserDetails(
                id = author.id!!,
                loginId = author.loginId,
                passwordVal = "hashed",
                passwordSalt = "salt",
                authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
            )

            val project = projectRepository.findAll().find { it.name == "cmtattach-proj" && it.owner == "cmtattach-author" }
                ?: projectRepository.save(
                    Project(
                        name = "cmtattach-proj",
                        owner = "cmtattach-author",
                        projectScope = ProjectScope.PUBLIC,
                        isCodeAccessibleMemberOnly = false,
                        vcs = "GIT"
                    )
                )

            val issue = issueRepository.findAll().find { it.project.id == project.id && it.title == "댓글첨부 이슈" }
                ?: issueRepository.save(
                    Issue(
                        title = "댓글첨부 이슈", body = "본문", project = project, number = 1L,
                        authorId = author.id, authorLoginId = author.loginId
                    )
                )

            val issueComment = issueCommentRepository.findAll().find { it.issue.id == issue.id && it.contents == "첨부파일 있는 이슈 댓글" }
                ?: issueCommentRepository.save(
                    IssueComment(
                        contents = "첨부파일 있는 이슈 댓글", createdDate = Instant.now(),
                        authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                        projectId = project.id, issue = issue
                    )
                )

            // 주의: AttachmentRepository.findByContainerTypeAndContainerId는 @Cacheable이다.
            // 여기서 "이미 있는지" 조회부터 하면(멱등성 가드) 그 조회 자체가 빈 결과를 캐시해버려
            // (raw repository.save()는 서비스 계층과 달리 @CacheEvict가 없다) 바로 뒤이은 save()가
            // 무의미해진다(컨트롤러가 나중에 같은 캐시를 읽어 항상 빈 목록을 봄 — 실제로 이렇게
            // 재현해 발견). 이슈/댓글마다 매번 새 ID이므로 멱등성 가드 없이 바로 저장한다.
            attachmentRepository.save(
                Attachment(
                    name = "issue-comment-file.png", hash = "hash-issue-comment-1",
                    containerType = ResourceType.ISSUE_COMMENT, containerId = issueComment.id.toString(),
                    mimeType = "image/png", size = 1234L, createdDate = Instant.now(), ownerLoginId = author.loginId
                )
            )

            val posting = postingRepository.findAll().find { it.project.id == project.id && it.title == "댓글첨부 게시글" }
                ?: postingRepository.save(
                    Posting(
                        title = "댓글첨부 게시글", body = "본문", project = project, number = 1L,
                        authorId = author.id, authorLoginId = author.loginId
                    )
                )

            val postingComment = postingCommentRepository.findAll().find { it.posting.id == posting.id && it.contents == "첨부파일 있는 게시글 댓글" }
                ?: postingCommentRepository.save(
                    PostingComment(
                        contents = "첨부파일 있는 게시글 댓글", createdDate = Instant.now(),
                        authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                        projectId = project.id, posting = posting
                    )
                )

            attachmentRepository.save(
                Attachment(
                    name = "board-comment-file.png", hash = "hash-board-comment-1",
                    containerType = ResourceType.NONISSUE_COMMENT, containerId = postingComment.id.toString(),
                    mimeType = "image/png", size = 5678L, createdDate = Instant.now(), ownerLoginId = author.loginId
                )
            )

            it("issue/view의 댓글 수정 폼은 이미 첨부된 파일을 legacy 마크업(.attachment-files/.attached-file-marker)으로 보여줘야 한다") {
                val doc = Jsoup.parse(
                    mockMvc.perform(
                        get("/${project.owner}/${project.name}/issue/${issue.number}")
                            .with(SecurityMockMvcRequestPostProcessors.user(authorDetails))
                    ).andExpect(status().isOk).andReturn().response.contentAsString
                )

                val editForm = doc.select("#comment-editform-${issueComment.id}")
                editForm.select(".attachment-files .attached-file-marker").size shouldBe 1
                editForm.select(".attachment-files .attached-file-marker .name").text() shouldBe "issue-comment-file.png"
                editForm.select(".file-upload__input").size shouldBe 1
                editForm.select("input.temporaryUploadFiles").size shouldBe 1
            }

            it("board/view의 댓글 수정 폼도 동일하게 이미 첨부된 파일을 보여줘야 한다") {
                val doc = Jsoup.parse(
                    mockMvc.perform(
                        get("/${project.owner}/${project.name}/post/${posting.number}")
                            .with(SecurityMockMvcRequestPostProcessors.user(authorDetails))
                    ).andExpect(status().isOk).andReturn().response.contentAsString
                )

                val editForm = doc.select("#comment-editform-${postingComment.id}")
                editForm.select(".attachment-files .attached-file-marker").size shouldBe 1
                editForm.select(".attachment-files .attached-file-marker .name").text() shouldBe "board-comment-file.png"
                editForm.select(".file-upload__input").size shouldBe 1
                editForm.select("input.temporaryUploadFiles").size shouldBe 1
            }

            it("issue/view.html·board/view.html은 yona.CommentAttachmentsUpdate.js를 로드해야 한다") {
                val issueBody = mockMvc.perform(
                    get("/${project.owner}/${project.name}/issue/${issue.number}")
                        .with(SecurityMockMvcRequestPostProcessors.user(authorDetails))
                ).andExpect(status().isOk).andReturn().response.contentAsString
                val boardBody = mockMvc.perform(
                    get("/${project.owner}/${project.name}/post/${posting.number}")
                        .with(SecurityMockMvcRequestPostProcessors.user(authorDetails))
                ).andExpect(status().isOk).andReturn().response.contentAsString

                issueBody.contains("/javascripts/common/yona.CommentAttachmentsUpdate.js") shouldBe true
                boardBody.contains("/javascripts/common/yona.CommentAttachmentsUpdate.js") shouldBe true
            }
        }
    }
}
