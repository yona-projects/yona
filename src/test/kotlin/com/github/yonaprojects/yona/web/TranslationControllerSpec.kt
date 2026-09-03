package com.github.yonaprojects.yona.web

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
import com.github.yonaprojects.yona.domain.support.MarkdownService
import com.github.yonaprojects.yona.domain.support.TranslationService
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.*
import io.mockk.clearMocks

class TranslationControllerSpec : DescribeSpec({
    val translationService = mockk<TranslationService>()
    val projectRepository = mockk<ProjectRepository>()
    val issueRepository = mockk<IssueRepository>()
    val postingRepository = mockk<PostingRepository>()
    val issueCommentRepository = mockk<IssueCommentRepository>()
    val postingCommentRepository = mockk<PostingCommentRepository>()
    val markdownService = mockk<MarkdownService>()
    val userRepository = mockk<UserRepository>()

    val controller = TranslationController(
        translationService,
        projectRepository,
        issueRepository,
        postingRepository,
        issueCommentRepository,
        postingCommentRepository,
        markdownService,
        userRepository
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    beforeTest {
        clearMocks(
            translationService,
            projectRepository,
            issueRepository,
            postingRepository,
            issueCommentRepository,
            postingCommentRepository,
            markdownService,
            userRepository
        )
    }

    describe("TranslationController 단위 테스트") {
        val user = User(id = 1L, loginId = "testuser", name = "테스트유저", email = "test@example.com")
        val auth = UsernamePasswordAuthenticationToken("testuser", "password")
        val project = Project(id = 10L, name = "testproject", owner = "testowner")

        describe("POST /-_-api/v1/translation") {
            it("이슈 번역 요청을 정상적으로 처리해야 한다") {
                val issue = Issue(id = 100L, title = "한글 타이틀", body = "한글 내용", project = project)

                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 1L) } returns issue
                every { translationService.translate(any()) } returns "Translated Title\n\nTranslated Body"
                every { markdownService.render("Translated Title\n\nTranslated Body", true, project) } returns "<p>Translated Title</p><p>Translated Body</p>"

                val requestJson = """
                    {
                        "owner": "testowner",
                        "projectName": "testproject",
                        "type": "issue",
                        "number": 1
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/-_-api/v1/translation")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.translated").value("<p>Translated Title</p><p>Translated Body</p>"))
            }

            it("번역 API 연동 설정이 안되어 있다면 412 status를 응답해야 한다") {
                val issue = Issue(id = 100L, title = "한글 타이틀", body = "한글 내용", project = project)

                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 1L) } returns issue
                every { translationService.translate(any()) } returns null

                val requestJson = """
                    {
                        "owner": "testowner",
                        "projectName": "testproject",
                        "type": "issue",
                        "number": 1
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/-_-api/v1/translation")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                )
                    .andExpect(status().isPreconditionFailed)
                    .andExpect(jsonPath("$.error").value("Precondition Failed"))
            }

            it("이슈 댓글 번역 요청을 정상적으로 처리해야 한다") {
                val comment = IssueComment(id = 200L, contents = "댓글 내용", issue = mockk())

                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { issueCommentRepository.findById(200L) } returns Optional.of(comment)
                every { translationService.translate("댓글 내용") } returns "Translated Comment"
                every { markdownService.render("Translated Comment", true, project) } returns "<p>Translated Comment</p>"

                val requestJson = """
                    {
                        "owner": "testowner",
                        "projectName": "testproject",
                        "type": "issue-comment",
                        "number": 200
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/-_-api/v1/translation")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.translated").value("<p>Translated Comment</p>"))
            }

            it("게시글 번역 요청을 정상적으로 처리해야 한다") {
                val posting = Posting(id = 300L, title = "포스트 제목", body = "포스트 내용", project = project)

                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { postingRepository.findByProjectAndNumber(project, 1L) } returns posting
                every { translationService.translate(any()) } returns "Translated Posting"
                every { markdownService.render("Translated Posting", true, project) } returns "<p>Translated Posting</p>"

                val requestJson = """
                    {
                        "owner": "testowner",
                        "projectName": "testproject",
                        "type": "posting",
                        "number": 1
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/-_-api/v1/translation")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.translated").value("<p>Translated Posting</p>"))
            }

            it("게시글 댓글 번역 요청을 정상적으로 처리해야 한다") {
                val posting = Posting(id = 300L, title = "포스트 제목", body = "포스트 내용", project = project)
                val comment = PostingComment(id = 400L, contents = "포스트 댓글", posting = posting)

                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { postingCommentRepository.findById(400L) } returns Optional.of(comment)
                every { translationService.translate("포스트 댓글") } returns "Translated Post Comment"
                every { markdownService.render("Translated Post Comment", true, project) } returns "<p>Translated Post Comment</p>"

                val requestJson = """
                    {
                        "owner": "testowner",
                        "projectName": "testproject",
                        "type": "post-comment",
                        "number": 400
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/-_-api/v1/translation")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.translated").value("<p>Translated Post Comment</p>"))
            }

            it("인증되지 않은 요청은 401을 반환해야 한다") {
                val requestJson = """
                    {
                        "owner": "testowner",
                        "projectName": "testproject",
                        "type": "issue",
                        "number": 1
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/-_-api/v1/translation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                ).andExpect(status().isUnauthorized)
            }

            it("로그인 ID에 해당하는 사용자가 없으면 401을 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.empty()

                val requestJson = """
                    {
                        "owner": "testowner",
                        "projectName": "testproject",
                        "type": "issue",
                        "number": 1
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/-_-api/v1/translation")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                ).andExpect(status().isUnauthorized)
            }

            it("존재하지 않는 프로젝트면 404를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "nosuch") } returns Optional.empty()

                val requestJson = """
                    {
                        "owner": "testowner",
                        "projectName": "nosuch",
                        "type": "issue",
                        "number": 1
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/-_-api/v1/translation")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                ).andExpect(status().isNotFound)
            }

            it("존재하지 않는 이슈면 404를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 999L) } returns null

                val requestJson = """
                    {
                        "owner": "testowner",
                        "projectName": "testproject",
                        "type": "issue",
                        "number": 999
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/-_-api/v1/translation")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                ).andExpect(status().isNotFound)
            }

            it("존재하지 않는 게시글이면 404를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { postingRepository.findByProjectAndNumber(project, 999L) } returns null

                val requestJson = """
                    {
                        "owner": "testowner",
                        "projectName": "testproject",
                        "type": "posting",
                        "number": 999
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/-_-api/v1/translation")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                ).andExpect(status().isNotFound)
            }

            it("존재하지 않는 이슈 댓글이면 404를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { issueCommentRepository.findById(999L) } returns Optional.empty()

                val requestJson = """
                    {
                        "owner": "testowner",
                        "projectName": "testproject",
                        "type": "issue-comment",
                        "number": 999
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/-_-api/v1/translation")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                ).andExpect(status().isNotFound)
            }

            it("존재하지 않는 게시글 댓글이면 404를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { postingCommentRepository.findById(999L) } returns Optional.empty()

                val requestJson = """
                    {
                        "owner": "testowner",
                        "projectName": "testproject",
                        "type": "post-comment",
                        "number": 999
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/-_-api/v1/translation")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                ).andExpect(status().isNotFound)
            }

            it("지원하지 않는 type이면 400을 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)

                val requestJson = """
                    {
                        "owner": "testowner",
                        "projectName": "testproject",
                        "type": "unknown-type",
                        "number": 1
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/-_-api/v1/translation")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                ).andExpect(status().isBadRequest)
            }
        }
    }
})
