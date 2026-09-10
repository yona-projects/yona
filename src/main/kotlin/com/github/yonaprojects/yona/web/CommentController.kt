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

    // 이슈 댓글 생성 — 프로젝트 READ 권한이 아니라 AccessControl.isIssueCommentCreatable()로
    // 판단한다. 프로젝트 멤버가 아니어도 그 이슈의 작성자/담당자/공유대상이면 댓글을 달 수 있다 —
    // 이 판단에 이슈 자체가 필요해 이슈 조회를 권한체크보다 먼저 한다.
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
        // raw IssueComment 엔티티를 그대로 반환하면 issue->project->projectUsers->user 순환
        // 직렬화로 User.password/passwordSalt까지 노출된다.
        return ResponseEntity.status(HttpStatus.CREATED).body(savedComment.toResponse())
    }

    // 이슈 댓글 수정 — request.original이 전달되면 저장 직전 화면 원문과 현재 DB 값을 비교해 그
    // 사이 다른 사용자가 이미 수정했는지 확인, 다르면 409(conflicted)로 거부한다.
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

        val updated = commentService.updateIssueComment(commentId, request.contents, user, request.sendNotificationMail)
        // raw 엔티티 반환 시의 순환 직렬화/비밀번호 노출 방지.
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

    // 게시판 댓글 생성 — 프로젝트 READ 권한이 아니라 AccessControl.isPostingCommentCreatable()로
    // 판단한다. 프로젝트 멤버가 아니어도 그 게시글의 작성자면 댓글을 달 수 있다 — 이 판단에 게시글
    // 자체가 필요해 게시글 조회를 권한체크보다 먼저 한다.
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
        // raw 엔티티 반환 시의 순환 직렬화/비밀번호 노출 방지.
        return ResponseEntity.status(HttpStatus.CREATED).body(savedComment.toResponse())
    }

    // 게시판 댓글 수정 — request.original이 전달되면 저장 직전 화면 원문과 현재 DB 값을 비교해 그
    // 사이 다른 사용자가 이미 수정했는지 확인, 다르면 409(conflicted)로 거부한다(updateIssueComment와
    // 동일한 패턴).
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

        val updated = commentService.updatePostingComment(commentId, request.contents, user, request.sendNotificationMail)
        // raw 엔티티 반환 시의 순환 직렬화/비밀번호 노출 방지.
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

    // legacy Open API 네임스페이스는 필드명이 다르다(`comment` 단일 문자열) —
    // createIssueComment()와 동일한 commentService를 재사용하되 요청 DTO만 legacy 필드명에 맞춘다.
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
        // raw 엔티티 반환 시의 순환 직렬화/비밀번호 노출 방지.
        return ResponseEntity.status(HttpStatus.CREATED).body(savedComment.toResponse())
    }

    // legacy 필드명은 `content`/`original`(원문 그 자체 — 미리 계산한 해시 아님, v1.6
    // `yona.Tasklist.js`의 실제 AJAX 요청 바디로 재확인) — updateIssueComment()와 동일한 로직을
    // 재사용한다. v1.6 원본은 이 메서드(controllers.api.IssueApi.updateIssueComment)를 두 경로로 이중 매핑해뒀다
    // — 공식 `-_-api/v1` 경로(PUT)와, 아마도 웹 UI 자체 AJAX용으로 보이는 bare 경로
    // `/:owner/:project/issue/:number/comments/:commentId`(PATCH). 후자가 이식에서 빠져있었다.
    @RequestMapping(
        value = [
            "/-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}/comments/{commentId}",
            "/{owner}/{projectName}/issue/{number}/comments/{commentId}"
        ],
        method = [RequestMethod.PUT, RequestMethod.PATCH]
    )
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

        if (isModifiedByOthers(comment.contents, request.original)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "Already modified by someone.", "storedContent" to comment.contents))
        }

        val updated = commentService.updateIssueComment(commentId, request.content, user)
        // raw 엔티티 반환 시의 순환 직렬화/비밀번호 노출 방지.
        return ResponseEntity.ok(updated.toResponse())
    }

    // legacy 필드명은 `body`.
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
        // raw 엔티티 반환 시의 순환 직렬화/비밀번호 노출 방지.
        return ResponseEntity.status(HttpStatus.CREATED).body(savedComment.toResponse())
    }

    // legacy 필드명은 `content`/`original`(원문 그 자체). v1.6 원본은 이 메서드
    // (controllers.api.BoardApi.updatePostingComment)를 공식 `-_-api/v1` 경로(PUT)와 bare 경로
    // `/:owner/:project/post/:number/comment/:commentId`(PATCH, "post"/"comment" 단수 — 이슈 쪽과
    // 다름, 원본 그대로) 둘 다로 매핑해뒀다. 후자가 이식에서 빠져있었다.
    @RequestMapping(
        value = [
            "/-_-api/v1/owners/{owner}/projects/{projectName}/posts/{number}/comments/{commentId}",
            "/{owner}/{projectName}/post/{number}/comment/{commentId}"
        ],
        method = [RequestMethod.PUT, RequestMethod.PATCH]
    )
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

        if (isModifiedByOthers(comment.contents, request.original)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "Already modified by someone.", "storedContent" to comment.contents))
        }

        val updated = commentService.updatePostingComment(commentId, request.content, user)
        // raw 엔티티 반환 시의 순환 직렬화/비밀번호 노출 방지.
        return ResponseEntity.ok(updated.toResponse())
    }

    data class LegacyIssueCommentRequest(val comment: String = "")
    data class LegacyPostingCommentRequest(val body: String = "")
    // legacy 필드명은 `content`/`original`(원문 그 자체 — 미리 계산한 SHA-1 해시가 아니다, v1.6
    // 원본 IssueApi.java/BoardApi.java와 실제 클라이언트 JS(yona.Tasklist.js)로 재확인). 서버가
    // 현재 값과 이 원문을 각각 해시해서 비교한다(isModifiedByOthers()) — 한때 별도의
    // "클라이언트가 이미 해시를 보낸다"는 잘못된 함수를 썼던 결함을 정정했다.
    data class LegacyUpdateCommentRequest(val content: String = "", val original: String = "")

    data class CommentRequest(
        val contents: String = "",
        // 클라이언트가 저장 직전 화면에 있던 원문을 함께 보내면 동시편집 충돌을 감지한다 — null이면
        // 기존 호출자(원문을 안 보내는 클라이언트)와의 하위호환을 위해 충돌 검사를 건너뛴다.
        val original: String? = null,
        val parentCommentId: Long? = null,
        // P3-50: legacy commentUpdateForm.scala.html의 "알림 메일 받기" 체크박스 대응 — 댓글
        // 생성 요청에서는 쓰이지 않고 updateIssueComment/updatePostingComment 쪽에서만 참조한다.
        val sendNotificationMail: Boolean = false
    )
}
