package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.board.PostingCommentRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.issue.IssueCommentRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.support.MarkdownService
import com.github.yonaprojects.yona.domain.support.TranslationService
import com.github.yonaprojects.yona.domain.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class TranslationController(
    private val translationService: TranslationService,
    private val projectRepository: ProjectRepository,
    private val issueRepository: IssueRepository,
    private val postingRepository: PostingRepository,
    private val issueCommentRepository: IssueCommentRepository,
    private val postingCommentRepository: PostingCommentRepository,
    private val markdownService: MarkdownService,
    private val userRepository: UserRepository
) {
    private val newline = "\r\n"

    @PostMapping("/-_-api/v1/translation")
    fun translate(
        @RequestBody request: TranslationRequest,
        authentication: Authentication?
    ): ResponseEntity<Map<String, Any>> {
        // 로그인 체크
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        val user = userRepository.findByLoginId(authentication.name).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(request.owner, request.projectName).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        var text = ""
        when (request.type) {
            "issue" -> {
                val issue = issueRepository.findByProjectAndNumber(project, request.number)
                    ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
                text = "Title: ${issue.title}$newline$newline${issue.body}"
            }
            "posting" -> {
                val posting = postingRepository.findByProjectAndNumber(project, request.number)
                    ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
                text = "Title: ${posting.title}$newline$newline${posting.body}"
            }
            "issue-comment" -> {
                val comment = issueCommentRepository.findById(request.number).orElse(null)
                    ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
                text = comment.contents
            }
            "post-comment" -> {
                val comment = postingCommentRepository.findById(request.number).orElse(null)
                    ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
                text = comment.contents
            }
            else -> {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
            }
        }

        val translated = translationService.translate(text)
            ?: return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body(mapOf("error" to "Precondition Failed"))

        val html = markdownService.render(translated, true, project)

        return ResponseEntity.ok(mapOf("translated" to html))
    }
}

data class TranslationRequest(
    val owner: String,
    val projectName: String,
    val type: String,
    val number: Long
)
