package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.comment.CommentService
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingComment
import com.github.yonaprojects.yona.domain.board.PostingCommentRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.enumeration.Operation
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueComment
import com.github.yonaprojects.yona.domain.issue.IssueCommentRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.support.isModifiedByOthers
import com.github.yonaprojects.yona.domain.support.isModifiedByOthersLegacyChecksum
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
class CommentController(
    private val commentService: CommentService,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val userRepository: UserRepository,
    private val issueRepository: IssueRepository,
    private val postingRepository: PostingRepository,
    private val issueCommentRepository: IssueCommentRepository,
    private val postingCommentRepository: PostingCommentRepository,
    private val accessControl: AccessControl
) {

    private fun getLoginUser(authentication: Authentication?): User? {
        if (authentication == null) return null
        return userRepository.findByLoginId(authentication.name).orElse(null)
    }

    private fun checkReadPermission(project: Project, user: User?): Boolean {
        return accessControl.isAllowed(user, project, Operation.READ)
    }

    // 이슈 댓글 생성
    // yona IssueApp.java:959-973 newComment() 대응 (P2-34) — 프로젝트 READ 권한이 아니라 [GL-controllers_IssueApp-046;GL-controllers_IssueApp-047]
    // AccessControl.isResourceCreatable()(ISSUE_COMMENT 케이스)로 판단한다. 프로젝트 멤버가 아니어도
    // 그 이슈의 작성자/담당자/공유대상이면 댓글을 달 수 있다(legacy isAllowedIfAuthor/isAllowedIfAssignee/
    // isAllowedIfSharer 우회) — 이 판단에 이슈 자체가 필요해 이슈 조회를 권한체크보다 먼저 한다(legacy와 동일 순서).
    @PostMapping("/api/projects/{projectId}/issues/{number}/comments")
    fun createIssueComment(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        @RequestBody request: CommentRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val issue = issueRepository.findByProjectAndNumber(project, number)
            ?: return ResponseEntity.notFound().build()

        if (!accessControl.isIssueCommentCreatable(user, project, issue)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val savedComment = commentService.createIssueComment(issue.id!!, request.contents, user, request.parentCommentId)
        // P3-30(2026-09-09 코디네이터 발견/수정) — raw IssueComment 엔티티를 그대로 반환하면
        // issue->project->projectUsers->user 순환 직렬화로 User.password/passwordSalt까지
        // 노출된다(P3-26/P3-28/IssueController와 동일한 근본원인).
        return ResponseEntity.status(HttpStatus.CREATED).body(savedComment.toResponse())
    }

    // 이슈 댓글 수정
    // yona IssueApi.java:594-634 updateIssueComment() 대응 (P1-102). request.original이 전달되면 [GL-controllers_api_IssueApi-032;GL-controllers_api_IssueApi-033]
    // 저장 직전 화면 원문과 현재 DB 값을 비교해 그 사이 다른 사용자가 이미 수정했는지 확인, 다르면
    // 409(conflicted)로 거부한다 — updateIssueContent(이슈 본문)와 동일한 패턴.
    @PutMapping("/api/projects/{projectId}/issues/{number}/comments/{commentId}")
    fun updateIssueComment(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        @PathVariable commentId: Long,
        @RequestBody request: CommentRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!checkReadPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val comment = issueCommentRepository.findById(commentId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val isManager = projectUserRepository.findByProjectIdAndUserId(project.id!!, user.id!!)
            .map { it.role.id == RoleType.MANAGER.roleType }
            .orElse(false)

        if (comment.authorId != user.id && !isManager) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val original = request.original
        if (original != null && isModifiedByOthers(comment.contents, original)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "Already modified by someone.", "storedContent" to comment.contents))
        }

        val updated = commentService.updateIssueComment(commentId, request.contents, user)
        // P3-30 — 동일한 순환 직렬화/비밀번호 노출 문제 대응.
        return ResponseEntity.ok(updated.toResponse())
    }

    // 이슈 댓글 삭제
    @DeleteMapping("/api/projects/{projectId}/issues/{number}/comments/{commentId}")
    fun deleteIssueComment(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        @PathVariable commentId: Long,
        authentication: Authentication?
    ): ResponseEntity<Unit> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!checkReadPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val comment = issueCommentRepository.findById(commentId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val isManager = projectUserRepository.findByProjectIdAndUserId(project.id!!, user.id!!)
            .map { it.role.id == RoleType.MANAGER.roleType }
            .orElse(false)

        if (comment.authorId != user.id && !isManager) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        commentService.deleteIssueComment(commentId, user)
        return ResponseEntity.ok().build()
    }

    // 게시판 댓글 생성
    // yona BoardApp.java:396-425 newComment() 대응 (P2-34) — 프로젝트 READ 권한이 아니라 [GL-controllers_BoardApp-019]
    // AccessControl.isResourceCreatable()(NONISSUE_COMMENT 케이스)로 판단한다. 프로젝트 멤버가
    // 아니어도 그 게시글의 작성자면 댓글을 달 수 있다(legacy isAllowedIfAuthor 우회) — 이 판단에
    // 게시글 자체가 필요해 게시글 조회를 권한체크보다 먼저 한다(legacy와 동일 순서).
    @PostMapping("/api/projects/{projectId}/posts/{number}/comments")
    fun createPostingComment(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        @RequestBody request: CommentRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val posting = postingRepository.findByProjectAndNumber(project, number)
            ?: return ResponseEntity.notFound().build()

        if (!accessControl.isPostingCommentCreatable(user, project, posting)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val savedComment = commentService.createPostingComment(posting.id!!, request.contents, user, request.parentCommentId)
        // P3-30 — 동일한 순환 직렬화/비밀번호 노출 문제 대응.
        return ResponseEntity.status(HttpStatus.CREATED).body(savedComment.toResponse())
    }

    // 게시판 댓글 수정
    // yona BoardApi.java:198-238 updatePostingComment() 대응 (P1-107). request.original이 전달되면 [GL-controllers_api_BoardApi-008]
    // 저장 직전 화면 원문과 현재 DB 값을 비교해 그 사이 다른 사용자가 이미 수정했는지 확인, 다르면
    // 409(conflicted)로 거부한다 — updateIssueComment(P1-102)와 동일한 패턴.
    @PutMapping("/api/projects/{projectId}/posts/{number}/comments/{commentId}")
    fun updatePostingComment(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        @PathVariable commentId: Long,
        @RequestBody request: CommentRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!checkReadPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val comment = postingCommentRepository.findById(commentId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val isManager = projectUserRepository.findByProjectIdAndUserId(project.id!!, user.id!!)
            .map { it.role.id == RoleType.MANAGER.roleType }
            .orElse(false)

        if (comment.authorId != user.id && !isManager) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val original = request.original
        if (original != null && isModifiedByOthers(comment.contents, original)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "Already modified by someone.", "storedContent" to comment.contents))
        }

        val updated = commentService.updatePostingComment(commentId, request.contents, user)
        // P3-30 — 동일한 순환 직렬화/비밀번호 노출 문제 대응.
        return ResponseEntity.ok(updated.toResponse())
    }

    // 게시판 댓글 삭제
    @DeleteMapping("/api/projects/{projectId}/posts/{number}/comments/{commentId}")
    fun deletePostingComment(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        @PathVariable commentId: Long,
        authentication: Authentication?
    ): ResponseEntity<Unit> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!checkReadPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val comment = postingCommentRepository.findById(commentId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val isManager = projectUserRepository.findByProjectIdAndUserId(project.id!!, user.id!!)
            .map { it.role.id == RoleType.MANAGER.roleType }
            .orElse(false)

        if (comment.authorId != user.id && !isManager) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        commentService.deletePostingComment(commentId, user)
        return ResponseEntity.ok().build()
    }

    // yona controllers/api/IssueApi.java newIssueComment() 대응 (P2-56). legacy Open API
    // 네임스페이스는 필드명이 다르다(`comment` 단일 문자열) — createIssueComment()와 동일한
    // commentService를 재사용하되 요청 DTO만 legacy 필드명에 맞춘다.
    @PostMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}/comments")
    fun newIssueCommentLegacyPath(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable number: Long,
        @RequestBody request: LegacyIssueCommentRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val issue = issueRepository.findByProjectAndNumber(project, number)
            ?: return ResponseEntity.notFound().build()

        if (!accessControl.isIssueCommentCreatable(user, project, issue)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val savedComment = commentService.createIssueComment(issue.id!!, request.comment, user, null)
        // P3-30 — 동일한 순환 직렬화/비밀번호 노출 문제 대응.
        return ResponseEntity.status(HttpStatus.CREATED).body(savedComment.toResponse())
    }

    // yona controllers/api/IssueApi.java updateIssueComment() 대응 (P2-56). legacy 필드명은
    // `content`/`sha1`(원문 체크섬) — updateIssueComment()와 동일한 로직을 재사용한다.
    @PutMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}/comments/{commentId}")
    fun updateIssueCommentLegacyPath(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable number: Long,
        @PathVariable commentId: Long,
        @RequestBody request: LegacyUpdateCommentRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!checkReadPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val comment = issueCommentRepository.findById(commentId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val isManager = projectUserRepository.findByProjectIdAndUserId(project.id!!, user.id!!)
            .map { it.role.id == RoleType.MANAGER.roleType }
            .orElse(false)

        if (comment.authorId != user.id && !isManager) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        if (isModifiedByOthersLegacyChecksum(comment.contents, request.sha1)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "Already modified by someone.", "storedContent" to comment.contents))
        }

        val updated = commentService.updateIssueComment(commentId, request.content, user)
        // P3-30 — 동일한 순환 직렬화/비밀번호 노출 문제 대응.
        return ResponseEntity.ok(updated.toResponse())
    }

    // yona controllers/api/BoardApi.java newPostingComment() 대응 (P2-57). legacy 필드명은 `body`.
    @PostMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/posts/{number}/comments")
    fun newPostingCommentLegacyPath(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable number: Long,
        @RequestBody request: LegacyPostingCommentRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val posting = postingRepository.findByProjectAndNumber(project, number)
            ?: return ResponseEntity.notFound().build()

        if (!accessControl.isPostingCommentCreatable(user, project, posting)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val savedComment = commentService.createPostingComment(posting.id!!, request.body, user, null)
        // P3-30 — 동일한 순환 직렬화/비밀번호 노출 문제 대응.
        return ResponseEntity.status(HttpStatus.CREATED).body(savedComment.toResponse())
    }

    // yona controllers/api/BoardApi.java updatePostingComment() 대응 (P2-57). legacy 필드명은
    // `content`/`sha1`(원문 체크섬).
    @PutMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/posts/{number}/comments/{commentId}")
    fun updatePostingCommentLegacyPath(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable number: Long,
        @PathVariable commentId: Long,
        @RequestBody request: LegacyUpdateCommentRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!checkReadPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val comment = postingCommentRepository.findById(commentId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val isManager = projectUserRepository.findByProjectIdAndUserId(project.id!!, user.id!!)
            .map { it.role.id == RoleType.MANAGER.roleType }
            .orElse(false)

        if (comment.authorId != user.id && !isManager) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        if (isModifiedByOthersLegacyChecksum(comment.contents, request.sha1)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "Already modified by someone.", "storedContent" to comment.contents))
        }

        val updated = commentService.updatePostingComment(commentId, request.content, user)
        // P3-30 — 동일한 순환 직렬화/비밀번호 노출 문제 대응.
        return ResponseEntity.ok(updated.toResponse())
    }

    data class LegacyIssueCommentRequest(val comment: String = "")
    data class LegacyPostingCommentRequest(val body: String = "")
    data class LegacyUpdateCommentRequest(val content: String = "", val sha1: String = "")

    data class CommentRequest(
        val contents: String = "",
        // yona IssueApi.java:594-634 updateIssueComment()의 isModifiedByOthers() 대응 (P1-102). [GL-controllers_api_IssueApi-032;GL-controllers_api_IssueApi-033]
        // 클라이언트가 저장 직전 화면에 있던 원문을 함께 보내면 동시편집 충돌을 감지한다 — null이면
        // 기존 호출자(원문을 안 보내는 클라이언트)와의 하위호환을 위해 충돌 검사를 건너뛴다.
        val original: String? = null,
        // yona models/Comment.java:45 parentCommentId 대응 (P1-112). CommentService의
        // createIssueComment/createPostingComment는 이미 parentCommentId 파라미터를 받아 대댓글을
        // 만들 수 있었지만, 이 DTO에 필드가 없어 API로 노출되지 않고 있었다.
        val parentCommentId: Long? = null
    )
}
