package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.enumeration.Operation
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommitComment
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.support.CodeRange
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserIdent
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.time.Instant

@RestController
@RequestMapping("/api/vcs/{owner}/{projectName}")
class CodeHistoryController(
    private val projectRepository: ProjectRepository,
    private val repositoryService: RepositoryService,
    private val commitCommentRepository: CommitCommentRepository,
    private val userRepository: UserRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val accessControl: AccessControl
) {

    private fun getLoginUser(authentication: Authentication?): User? {
        if (authentication == null) return null
        return userRepository.findByLoginId(authentication.name).orElse(null)
    }

    @GetMapping("/history")
    fun getHistory(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "20") size: Int,
        @RequestParam(required = false, defaultValue = "HEAD") branch: String,
        @RequestParam(required = false) path: String?
    ): ResponseEntity<List<CommitResponse>> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        val repository = repositoryService.getRepository(project)
        val history = repository.getHistory(page, size, branch, path)

        val responseList = history.map { commit ->
            CommitResponse(
                id = commit.getId(),
                shortId = commit.getShortId(),
                message = commit.getMessage(),
                shortMessage = commit.getShortMessage(),
                authorName = commit.getAuthorName(),
                authorEmail = commit.getAuthorEmail(),
                authorDate = commit.getAuthorDate()?.time ?: 0L,
                committerName = commit.getCommitterName(),
                committerEmail = commit.getCommitterEmail(),
                committerDate = commit.getCommitterDate()?.time ?: 0L
            )
        }

        return ResponseEntity.ok(responseList)
    }

    @GetMapping("/commit/{commitId}")
    fun getCommit(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable commitId: String
    ): ResponseEntity<CommitResponse> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        val repository = repositoryService.getRepository(project)
        val commit = repository.getCommit(commitId)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        val response = CommitResponse(
            id = commit.getId(),
            shortId = commit.getShortId(),
            message = commit.getMessage(),
            shortMessage = commit.getShortMessage(),
            authorName = commit.getAuthorName(),
            authorEmail = commit.getAuthorEmail(),
            authorDate = commit.getAuthorDate()?.time ?: 0L,
            committerName = commit.getCommitterName(),
            committerEmail = commit.getCommitterEmail(),
            committerDate = commit.getCommitterDate()?.time ?: 0L
        )

        return ResponseEntity.ok(response)
    }

    // yona CodeHistoryApp.newComment 대응 (커밋 단위 댓글 생성)
    @PostMapping("/commit/{commitId}/comments")
    fun createComment(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable commitId: String,
        @RequestBody request: CreateCommitCommentRequest,
        authentication: Authentication?
    ): ResponseEntity<CommitComment> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!accessControl.isProjectResourceCreatable(user, project, ResourceType.COMMIT_COMMENT)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        if (repositoryService.getRepository(project).getCommit(commitId) == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }

        val comment = CommitComment(
            project = project,
            commitId = commitId,
            contents = request.contents,
            path = request.path,
            line = request.line,
            side = request.side,
            author = UserIdent(user),
            createdDate = Instant.now()
        )
        val saved = commitCommentRepository.save(comment)

        return ResponseEntity.status(HttpStatus.CREATED).body(saved)
    }

    // yona CodeHistoryApp.deleteComment 대응
    @DeleteMapping("/commit/{commitId}/comments/{id}")
    fun deleteComment(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable commitId: String,
        @PathVariable id: Long,
        authentication: Authentication?
    ): ResponseEntity<Void> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val comment = commitCommentRepository.findById(id).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        if (!accessControl.isAllowed(user, project, comment, Operation.DELETE)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        commitCommentRepository.delete(comment)
        return ResponseEntity.ok().build()
    }

    // 커밋에 달린 댓글 목록 조회 (newComment/deleteComment와 함께 동작하려면 필요)
    @GetMapping("/commit/{commitId}/comments")
    fun listComments(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable commitId: String
    ): ResponseEntity<List<CommitComment>> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        return ResponseEntity.ok(
            commitCommentRepository.findByProjectAndCommitIdOrderByCreatedDateAsc(project, commitId)
        )
    }
}

data class CreateCommitCommentRequest(
    val contents: String,
    val path: String? = null,
    val line: Int? = null,
    val side: CodeRange.Side? = null
)

data class CommitResponse(
    val id: String,
    val shortId: String,
    val message: String?,
    val shortMessage: String,
    val authorName: String?,
    val authorEmail: String?,
    val authorDate: Long,
    val committerName: String?,
    val committerEmail: String?,
    val committerDate: Long
)
