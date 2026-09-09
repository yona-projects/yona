package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.enumeration.Operation
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.pullrequest.CodeReviewService
import com.github.yonaprojects.yona.domain.vcs.FileDiff
import com.github.yonaprojects.yona.domain.pullrequest.LackingReviewerException
import com.github.yonaprojects.yona.domain.pullrequest.PullRequest
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestEvent
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestEventRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestMergeResult
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestReview
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestService
import com.github.yonaprojects.yona.domain.pullrequest.ReviewComment
import com.github.yonaprojects.yona.domain.pullrequest.SelfReviewException
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/projects/{projectId}/pullrequests")
class PullRequestController(
    private val pullRequestService: PullRequestService,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val userRepository: UserRepository,
    private val pullRequestEventRepository: PullRequestEventRepository,
    private val accessControl: AccessControl,
    private val codeReviewService: CodeReviewService
) {

    companion object {
        // createPullRequest()의 pull_request(to_project_id, number) UNIQUE 제약 충돌 재시도 한도.
        private const val MAX_NUMBER_RETRY_ATTEMPTS = 10
    }

    private fun getLoginUser(authentication: Authentication?): User? {
        if (authentication == null) return null
        return userRepository.findByLoginId(authentication.name).orElse(null)
    }

    private fun checkReadPermission(project: Project, user: User?): Boolean {
        return accessControl.isAllowed(user, project, Operation.READ)
    }

    private fun checkWritePermission(project: Project, user: User?): Boolean {
        if (user == null) return false
        return projectUserRepository.existsByProjectIdAndUserId(project.id!!, user.id!!) ||
            accessControl.isAllowedIfGroupMember(project, user)
    }

    private fun isManagerOrContributor(project: Project, contributorId: Long?, user: User?): Boolean {
        if (user == null) return false
        if (contributorId == user.id) return true
        return projectUserRepository.findByProjectIdAndUserId(project.id!!, user.id!!)
            .map { it.role.id == RoleType.MANAGER.roleType }
            .orElse(false)
    }

    @GetMapping
    fun getPullRequests(
        @PathVariable projectId: Long,
        @RequestParam(required = false) state: State?,
        @RequestParam(required = false) author: String?,
        // PR 목록은 프로젝트당 크지 않아 신규 리포지토리 쿼리 없이 인메모리 필터링으로 처리한다.
        @RequestParam(required = false) assignee: String?,
        @RequestParam(required = false) label: String?,
        authentication: Authentication?
    ): ResponseEntity<List<PullRequest>> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication)
        if (!checkReadPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val pullRequests = pullRequestService.getPullRequests(projectId, state)
        val filtered = pullRequests
            .let { list -> if (author != null) list.filter { it.contributor.loginId == author } else list }
            .let { list -> if (assignee != null) list.filter { it.assignee?.user?.loginId == assignee } else list }
            .let { list -> if (label != null) list.filter { pr -> pr.labels.any { it.name == label } } else list }
        return ResponseEntity.ok(filtered)
    }

    @GetMapping("/{number}")
    fun getPullRequest(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication)
        if (!checkReadPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val pullRequest = pullRequestService.getPullRequest(projectId, number)
            ?: return ResponseEntity.notFound().build()

        // raw PullRequest 엔티티를 그대로 반환하면 toProject/fromProject/contributor 등이
        // Project<->User 양방향 관계를 끌고 들어와 순환 직렬화되며 User.password/passwordSalt까지
        // 노출된다.
        return ResponseEntity.ok(pullRequest.toResponse())
    }

    @GetMapping("/{number}/timeline")
    fun getTimeline(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<List<PullRequestEvent>> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication)
        if (!checkReadPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val pullRequest = pullRequestService.getPullRequest(projectId, number)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(pullRequest))
    }

    @PostMapping
    fun createPullRequest(
        @PathVariable projectId: Long,
        @RequestBody request: CreatePullRequestRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        // checkWritePermission(멤버/그룹멤버 전용)만 쓰면 공개 프로젝트에서도 비멤버 로그인
        // 사용자가 PR을 보낼 수 없어 과도하게 제한된다. isProjectResourceCreatable()이 이미 FORK를
        // 공개 프로젝트 비멤버 허용 타입에 포함하고 있어 그대로 재사용한다.
        if (!accessControl.isProjectResourceCreatable(user, project, ResourceType.FORK)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        // PullRequestServiceImpl.createPullRequest()는 전용 카운터 컬럼 없이 매번
        // findFirstByToProjectOrderByNumberDesc()로 최댓값을 조회해 +1하는 read-modify-write라,
        // 동시에 같은 프로젝트로 PR을 여러 개 만들면 두 트랜잭션이 같은 번호를 계산해
        // pull_request(to_project_id, number) UNIQUE 제약을 위반할 수 있다.
        // createPullRequest() 전체가 @Transactional이라 실패한 시도의 부수효과는 트랜잭션 롤백으로
        // 전부 되돌아가므로, 제약 위반 시 전체를 다시 호출해 재계산하면 안전하게 재시도할 수 있다
        // (SELECT FOR UPDATE 기반 잠금은 H2 AUTO_SERVER 파일 모드에서 실제로 블로킹하지 않아
        // 채택하지 않았다 — ProjectRepository.incrementLastIssueNumber() 참고).
        var attempt = 0
        while (true) {
            try {
                val pullRequest = pullRequestService.createPullRequest(
                    title = request.title,
                    body = request.body,
                    fromProjectId = request.fromProjectId,
                    toProjectId = projectId,
                    fromBranch = request.fromBranch,
                    toBranch = request.toBranch,
                    contributor = user
                )
                return ResponseEntity.status(HttpStatus.CREATED).body(pullRequest.toResponse())
            } catch (e: DataIntegrityViolationException) {
                attempt++
                if (attempt >= MAX_NUMBER_RETRY_ATTEMPTS) {
                    throw e
                }
            }
        }
    }

    @PutMapping("/{number}")
    fun updatePullRequest(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        @RequestBody request: UpdatePullRequestRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val pullRequest = pullRequestService.getPullRequest(projectId, number)
            ?: return ResponseEntity.notFound().build()

        if (!accessControl.isAllowed(user, project, pullRequest, Operation.UPDATE)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        // fromBranch/toBranch는 null이면 기존 값으로 폴백한다 — body도 동일하게 폴백해야 한다.
        // yona-cli의 `pr edit --title`(body 생략)은 body 필드 자체를 요청 JSON에서 생략하므로,
        // 그 null을 그대로 적용하면 title만 바꾸려 했는데 기존 본문이 통째로 지워진다.
        val updated = pullRequestService.updatePullRequest(
            pullRequestId = pullRequest.id!!,
            title = request.title,
            body = request.body ?: pullRequest.body,
            fromBranch = request.fromBranch ?: pullRequest.fromBranch,
            toBranch = request.toBranch ?: pullRequest.toBranch
        )

        return ResponseEntity.ok(updated.toResponse())
    }

    @PostMapping("/{number}/merge")
    fun mergePullRequest(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!checkWritePermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val pullRequest = pullRequestService.getPullRequest(projectId, number)
            ?: return ResponseEntity.notFound().build()

        // 이미 MERGED/CLOSED인 PR을 다시 머지하려 하면 PullRequestServiceImpl.merge()가
        // IllegalArgumentException을, 리뷰어 수가 부족하면 LackingReviewerException을 던진다 —
        // 여기서 잡지 않으면 둘 다 컨테이너 기본 500으로 튄다. 성공 시 raw PullRequestMergeResult를
        // 그대로 반환하면 내부의 raw PullRequest -> contributor: User가 순환 직렬화되며
        // password/passwordSalt가 노출되므로 PullRequestMergeResult.toResponse()로 감싼다.
        return try {
            val result = pullRequestService.merge(pullRequest.id!!, user)
            ResponseEntity.ok(result.toResponse())
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        } catch (e: LackingReviewerException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    // 이미 MERGED된 PR을 close/reopen하려 하면 PullRequestServiceImpl.changeState()가
    // IllegalArgumentException을 던진다(가드가 없으면 이미 병합 완료된 PR이 CLOSED/OPEN 사이를
    // 오가며 상태만 바뀐다) — 여기서 잡지 않으면 400 대신 컨테이너 기본 500으로 튄다. 성공 시에도
    // raw PullRequest 엔티티를 그대로 반환하면 순환 직렬화로 password/passwordSalt가 노출되므로
    // toResponse()로 감싼다.
    @PostMapping("/{number}/state")
    fun changeState(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        @RequestParam state: State,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val pullRequest = pullRequestService.getPullRequest(projectId, number)
            ?: return ResponseEntity.notFound().build()

        if (!isManagerOrContributor(project, pullRequest.contributor.id, user) && !checkWritePermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        return try {
            val updated = pullRequestService.changeState(pullRequest.id!!, state, user.loginId)
            ResponseEntity.ok(updated.toResponse())
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    // PullRequestViewController.viewChangesInternal()이 화면 렌더링에 쓰는 것과 동일한
    // pullRequestService.getDiff()를 JSON으로 그대로 노출한다.
    @GetMapping("/{number}/diff")
    // FileDiff(JGit RawText/EditList/FileMode를 그대로 들고 있는 값 객체) 목록을 그대로 반환하지
    // 않고 FileDiffResponse(단순 필드 + 서버가 조립한 unified diff 텍스트)로 변환해 내려준다.
    fun getDiff(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<List<FileDiffResponse>> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication)
        if (!checkReadPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val pullRequest = pullRequestService.getPullRequest(projectId, number)
            ?: return ResponseEntity.notFound().build()

        val diffs = try {
            pullRequestService.getDiff(pullRequest)
        } catch (e: Exception) {
            emptyList()
        }
        return ResponseEntity.ok(diffs.map { it.toResponse() })
    }

    // "PR 전체에 붙는 일반 댓글"과 "코드 라인 단위 리뷰 댓글"을 CodeReviewService.createReviewComment()
    // 한 메서드로 함께 처리한다(commitId/codeRange가 둘 다 null이면 PR 전체에 붙는
    // NonRangedCodeCommentThread로 귀결됨). ReviewViewController.newPullRequestComment()와 같은
    // 서비스를 재사용하되, 그 메서드는 브라우저 폼 제출 전용이라 이 REST API는 JSON 요청/응답에
    // 맞춰 새로 얇게 감싼다.
    @PostMapping("/{number}/comments")
    fun addComment(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        @RequestBody request: PullRequestCommentRequest,
        authentication: Authentication?
    ): ResponseEntity<ReviewComment> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        // ReviewViewController.newPullRequestComment()의 IsCreatable(ResourceType.REVIEW_COMMENT)
        // 권한 체크와 동일하다.
        if (!accessControl.isProjectResourceCreatable(user, project, ResourceType.REVIEW_COMMENT)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val pullRequest = pullRequestService.getPullRequest(projectId, number)
            ?: return ResponseEntity.notFound().build()

        val comment = codeReviewService.createReviewComment(
            project = project,
            pullRequest = pullRequest,
            commitId = null,
            contents = request.body,
            codeRange = null,
            threadId = null,
            currentUser = user
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(comment)
    }

    data class PullRequestCommentRequest(
        val body: String
    )

    // yona PullRequestApp.deleteFromBranch 대응
    @DeleteMapping("/{number}/fromBranch")
    fun deleteFromBranch(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val pullRequest = pullRequestService.getPullRequest(projectId, number)
            ?: return ResponseEntity.notFound().build()

        if (!isManagerOrContributor(project, pullRequest.contributor.id, user) && !checkWritePermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val updated = pullRequestService.deleteFromBranch(pullRequest.id!!)
        // raw 엔티티 반환 시의 순환 직렬화/비밀번호 노출 방지(getPullRequest() 참고).
        return ResponseEntity.ok(updated.toResponse())
    }

    // yona PullRequestApp.restoreFromBranch 대응
    @PostMapping("/{number}/fromBranch")
    fun restoreFromBranch(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val pullRequest = pullRequestService.getPullRequest(projectId, number)
            ?: return ResponseEntity.notFound().build()

        if (!isManagerOrContributor(project, pullRequest.contributor.id, user) && !checkWritePermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val updated = pullRequestService.restoreFromBranch(pullRequest.id!!)
        // raw 엔티티 반환 시의 순환 직렬화/비밀번호 노출 방지(getPullRequest() 참고).
        return ResponseEntity.ok(updated.toResponse())
    }

    data class CreatePullRequestRequest(
        val title: String,
        val body: String?,
        val fromProjectId: Long,
        val fromBranch: String,
        val toBranch: String
    )

    // 리뷰어 등록/해제는 ACCEPT 권한을 요구한다 — 프로젝트 멤버가 아니면 PUBLIC 프로젝트라도 불가.
    @PostMapping("/{number}/reviewers")
    fun addReviewer(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<Unit> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!checkWritePermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val pullRequest = pullRequestService.getPullRequest(projectId, number)
            ?: return ResponseEntity.notFound().build()

        pullRequestService.addReviewer(pullRequest.id!!, user)
        return ResponseEntity.ok().build()
    }

    @DeleteMapping("/{number}/reviewers")
    fun removeReviewer(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<Unit> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!checkWritePermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val pullRequest = pullRequestService.getPullRequest(projectId, number)
            ?: return ResponseEntity.notFound().build()

        pullRequestService.removeReviewer(pullRequest.id!!, user)
        return ResponseEntity.ok().build()
    }

    // 권한은 addReviewer/removeReviewer와 동일한 수준(checkWritePermission)을 요구한다 — 판정을
    // 남기는 것도 "리뷰에 참여"하는 행위의 일종이라 기존 자기등록과 동일한 문턱을 재사용한다.
    @PostMapping("/{number}/reviews")
    fun submitReview(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        @RequestBody request: SubmitPullRequestReviewRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!checkWritePermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val pullRequest = pullRequestService.getPullRequest(projectId, number)
            ?: return ResponseEntity.notFound().build()

        return try {
            val review = pullRequestService.submitReview(pullRequest.id!!, user, request.state, request.body)
            ResponseEntity.status(HttpStatus.CREATED).body(review.toResponse())
        } catch (e: SelfReviewException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @GetMapping("/{number}/reviews")
    fun getReviews(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication)
        if (!checkReadPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val pullRequest = pullRequestService.getPullRequest(projectId, number)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(pullRequestService.getReviews(pullRequest.id!!).map { it.toResponse() })
    }

    data class SubmitPullRequestReviewRequest(
        val state: PullRequestReview.ReviewState,
        val body: String? = null
    )


    // addReviewer/removeReviewer와 동일한 권한 체크(checkWritePermission) 패턴을 따른다.
    @PutMapping("/{number}/assignee")
    fun setAssignee(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        @RequestBody request: SetAssigneeRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!checkWritePermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val pullRequest = pullRequestService.getPullRequest(projectId, number)
            ?: return ResponseEntity.notFound().build()

        val assigneeUser = userRepository.findById(request.userId).orElse(null)
            ?: return ResponseEntity.badRequest().build()

        val updated = pullRequestService.setAssignee(pullRequest.id!!, assigneeUser)
        // raw 엔티티 반환 시의 순환 직렬화/비밀번호 노출 방지(getPullRequest() 참고).
        return ResponseEntity.ok(updated.toResponse())
    }

    @DeleteMapping("/{number}/assignee")
    fun removeAssignee(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!checkWritePermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val pullRequest = pullRequestService.getPullRequest(projectId, number)
            ?: return ResponseEntity.notFound().build()

        val updated = pullRequestService.setAssignee(pullRequest.id!!, null)
        return ResponseEntity.ok(updated.toResponse())
    }

    // 라벨 정의 자체는 만들지 않고 프로젝트에 이미 있는 IssueLabel(web/LabelRestApiController.kt로
    // CRUD)을 참조만 한다.
    @PostMapping("/{number}/labels")
    fun addLabel(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        @RequestBody request: AddPullRequestLabelRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!checkWritePermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val pullRequest = pullRequestService.getPullRequest(projectId, number)
            ?: return ResponseEntity.notFound().build()

        return try {
            val updated = pullRequestService.addLabel(pullRequest.id!!, request.labelId)
            ResponseEntity.ok(updated.toResponse())
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }

    @DeleteMapping("/{number}/labels/{labelId}")
    fun removeLabel(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        @PathVariable labelId: Long,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!checkWritePermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val pullRequest = pullRequestService.getPullRequest(projectId, number)
            ?: return ResponseEntity.notFound().build()

        val updated = pullRequestService.removeLabel(pullRequest.id!!, labelId)
        // raw 엔티티 반환 시의 순환 직렬화/비밀번호 노출 방지(getPullRequest() 참고).
        return ResponseEntity.ok(updated.toResponse())
    }

    data class UpdatePullRequestRequest(
        val title: String,
        val body: String?,
        // null이면 기존 브랜치를 유지한다.
        val fromBranch: String? = null,
        val toBranch: String? = null
    )
    data class SetAssigneeRequest(val userId: Long)

    data class AddPullRequestLabelRequest(val labelId: Long)
}
