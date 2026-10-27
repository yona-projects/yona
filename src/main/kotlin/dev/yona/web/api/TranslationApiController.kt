package dev.yona.web.api

import dev.yona.config.YonaProperties
import dev.yona.domain.board.PostingCommentRepository
import dev.yona.domain.board.PostingRepository
import dev.yona.domain.comment.IssueCommentRepository
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.project.ProjectService
import dev.yona.markdown.ProjectMarkdownRenderer
import dev.yona.security.CurrentUserProvider
import dev.yona.translation.TranslationClient
import dev.yona.web.access.AnonymousCheck
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseBody
import tools.jackson.databind.json.JsonMapper

/**
 * 이슈, 글, 댓글을 영어로 번역한다. v1.6 `IssueApi.translate`(`app/controllers/api/IssueApi.java:977-1004`)에 해당한다.
 *
 * 본문 `{owner, projectName, type, number}`의 `type`은 `issue`, `posting`(번호는 글 번호), `issue-comment`, `post-comment`(번호는 댓글 번호)이다.
 * 번역 API를 설정하지 않았으면 `412`이다. 응답은 `{"translated": 마크다운으로 그린 HTML}`이다. 로그인이 필요하다.
 */
@Controller
@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
@ResponseBody
class TranslationApiController(
    private val properties: YonaProperties,
    private val client: TranslationClient,
    private val projectService: ProjectService,
    private val issues: IssueRepository,
    private val postings: PostingRepository,
    private val issueComments: IssueCommentRepository,
    private val postingComments: PostingCommentRepository,
    private val markdown: ProjectMarkdownRenderer,
    private val currentUserProvider: CurrentUserProvider
) {
    private val mapper = JsonMapper.builder().build()

    @PostMapping("/-_-api/v1/translation")
    @Transactional(readOnly = true)
    fun translate(request: HttpServletRequest): ResponseEntity<Any> {
        if (!properties.translationApiConfigured) return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body("Precondition Failed")
        val json = try { mapper.readTree(request.inputStream) } catch (e: Exception) { null } ?: return ResponseEntity.badRequest().build()
        val project = projectService.findByOwnerAndProjectName(json.path("owner").asString(""), json.path("projectName").asString("")) ?: return ResponseEntity.notFound().build()
        val number = json.path("number").asLong(0)
        val nl = TranslationClient.NEWLINE
        val text = when (json.path("type").asString("")) {
            "issue" -> issues.findByProjectIdAndNumber(requireNotNull(project.id), number)?.let { "Title: ${it.title}$nl$nl${it.body}" }
            "posting" -> postings.findByProjectIdAndNumber(requireNotNull(project.id), number)?.let { "Title: ${it.title}$nl$nl${it.body}" }
            "issue-comment" -> issueComments.findById(number).orElse(null)?.contents
            "post-comment" -> postingComments.findById(number).orElse(null)?.contents
            else -> ""
        } ?: return ResponseEntity.notFound().build()
        // v1.6 Java split은 끝의 빈 줄을 버린다
        val translated = client.translateLines(dev.yona.util.JavaStrings.split(text.replace("&", "%26"), nl))
        val lang = currentUserProvider.get().user?.lang
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(mapOf("translated" to markdown.render(translated, project, lang)))
    }
}
