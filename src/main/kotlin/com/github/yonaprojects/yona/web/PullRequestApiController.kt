package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// Go CLI 등 외부 클라이언트를 위한 신규 범용 REST API(`/api/v1/projects/{owner}/{project}/pull-requests`).
// ApiTokenAuthenticationFilter의 resourceSegmentToResourceType 매핑에 "pull-requests"가 이미 있어
// 이 네임스페이스로 오는 요청은 필터 단계에서 이미 PULL_REQUESTS 스코프 그룹으로 인가된다 —
// 컨트롤러에서 다시 구현하지 않는다.
//
// 비즈니스 로직/권한 체크는 기존 PullRequestController.kt(`/api/projects/{projectId}/pullrequests`,
// 숫자 projectId 기반, 웹 프런트엔드용)에 이미 완비돼 있어, owner/project 이름으로 프로젝트를 찾아
// 그 컨트롤러의 공개 메서드에 위임하는 얇은 어댑터로만 구현한다(신규 서비스 로직 없음).
@RestController
@RequestMapping("/api/v1/projects/{owner}/{project}/pull-requests")
class PullRequestApiController(
    private val projectRepository: ProjectRepository,
    private val pullRequestController: PullRequestController
) {

    // pullRequestController.*()가 반환하는 ResponseEntity<PullRequest>는 엔티티를 그대로 담고
    // 있어(toProject/fromProject/contributor/
    // receiver 등이 다시 Project<->User 양방향 연관관계를 끌고 들어와 순환 직렬화 유발,
    // RestApiResponseDto.kt 참고) 이 얇은 어댑터 경계에서 항상 응답 DTO로 변환한 뒤 상태코드만
    // 유지해 반환한다.
    private fun <T : Any> ResponseEntity<T>.mapBody(transform: (T) -> Any): ResponseEntity<Any> =
        ResponseEntity.status(statusCode).body(body?.let(transform))

    @GetMapping
    fun list(
        @PathVariable owner: String,
        @PathVariable project: String,
        @RequestParam(required = false) state: State?,
        @RequestParam(required = false) author: String?,
        @RequestParam(required = false) assignee: String?,
        @RequestParam(required = false) label: String?,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val found = projectRepository.findByOwnerAndName(owner, project).orElse(null)
            ?: return ResponseEntity.notFound().build()
        return pullRequestController.getPullRequests(found.id!!, state, author, assignee, label, authentication)
            .mapBody { list -> list.map { it.toResponse() } }
    }

    @PostMapping
    fun create(
        @PathVariable owner: String,
        @PathVariable project: String,
        @RequestBody request: PullRequestController.CreatePullRequestRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val found = projectRepository.findByOwnerAndName(owner, project).orElse(null)
            ?: return ResponseEntity.notFound().build()
        // createPullRequest()가 이제 순환 직렬화/비밀번호 노출
        // 수정으로 이미 변환된 본문을 담은 ResponseEntity<Any>를 돌려준다(위 merge()와 동일한
        // 이유로 여기서 다시 .mapBody{it.toResponse()}를 호출할 정적 타입 정보가 없다).
        return pullRequestController.createPullRequest(found.id!!, request, authentication)
    }

    @GetMapping("/{number}")
    fun get(
        @PathVariable owner: String,
        @PathVariable project: String,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val found = projectRepository.findByOwnerAndName(owner, project).orElse(null)
            ?: return ResponseEntity.notFound().build()
        // getPullRequest()가 이제 이미 변환된 본문을 담은
        // ResponseEntity<Any>를 돌려준다(위 merge()와 동일한 이유).
        return pullRequestController.getPullRequest(found.id!!, number, authentication)
    }

    @PostMapping("/{number}/merge")
    fun merge(
        @PathVariable owner: String,
        @PathVariable project: String,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val found = projectRepository.findByOwnerAndName(owner, project).orElse(null)
            ?: return ResponseEntity.notFound().build()
        // mergePullRequest()가 성공 시 이미 PullRequestMergeResultResponse로 변환해서 돌려준다 —
        // 그대로 전달만 한다.
        return pullRequestController.mergePullRequest(found.id!!, number, authentication)
    }

    // PullRequestService가 제공하는 리뷰 단위는 리뷰어 등록/해제(addReviewer/removeReviewer)이며,
    // 코드 라인 단위 리뷰 코멘트(ReviewComment/CommentThread)는 이 범용 REST API의 범위가 아니다
    // (기존 ReviewApiController가 별도로 다룸).
    @PostMapping("/{number}/reviewers")
    fun addReviewer(
        @PathVariable owner: String,
        @PathVariable project: String,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<Unit> {
        val found = projectRepository.findByOwnerAndName(owner, project).orElse(null)
            ?: return ResponseEntity.notFound().build()
        return pullRequestController.addReviewer(found.id!!, number, authentication)
    }

    // addReviewer만 어댑터가 있고 removeReviewer 어댑터가 없으면, Go CLI 등 외부 클라이언트의
    // 유일한 경로인 이 v1 REST API에서 "리뷰어 등록은 되는데 등록 취소는 불가능"한 기능 갭이 된다.
    @DeleteMapping("/{number}/reviewers")
    fun removeReviewer(
        @PathVariable owner: String,
        @PathVariable project: String,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<Unit> {
        val found = projectRepository.findByOwnerAndName(owner, project).orElse(null)
            ?: return ResponseEntity.notFound().build()
        return pullRequestController.removeReviewer(found.id!!, number, authentication)
    }


    @PutMapping("/{number}/assignee")
    fun setAssignee(
        @PathVariable owner: String,
        @PathVariable project: String,
        @PathVariable number: Long,
        @RequestBody request: PullRequestController.SetAssigneeRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val found = projectRepository.findByOwnerAndName(owner, project).orElse(null)
            ?: return ResponseEntity.notFound().build()
        // setAssignee()가 이제 이미 변환된 본문을 담은
        // ResponseEntity<Any>를 돌려준다(위 merge()와 동일한 이유).
        return pullRequestController.setAssignee(found.id!!, number, request, authentication)
    }

    @DeleteMapping("/{number}/assignee")
    fun removeAssignee(
        @PathVariable owner: String,
        @PathVariable project: String,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val found = projectRepository.findByOwnerAndName(owner, project).orElse(null)
            ?: return ResponseEntity.notFound().build()
        // removeAssignee()가 이제 이미 변환된 본문을 담은
        // ResponseEntity<Any>를 돌려준다.
        return pullRequestController.removeAssignee(found.id!!, number, authentication)
    }

    @PostMapping("/{number}/labels")
    fun addLabel(
        @PathVariable owner: String,
        @PathVariable project: String,
        @PathVariable number: Long,
        @RequestBody request: PullRequestController.AddPullRequestLabelRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val found = projectRepository.findByOwnerAndName(owner, project).orElse(null)
            ?: return ResponseEntity.notFound().build()
        // addLabel()이 이제 이미 변환된 본문을 담은
        // ResponseEntity<Any>를 돌려준다.
        return pullRequestController.addLabel(found.id!!, number, request, authentication)
    }

    @DeleteMapping("/{number}/labels/{labelId}")
    fun removeLabel(
        @PathVariable owner: String,
        @PathVariable project: String,
        @PathVariable number: Long,
        @PathVariable labelId: Long,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val found = projectRepository.findByOwnerAndName(owner, project).orElse(null)
            ?: return ResponseEntity.notFound().build()
        // removeLabel()이 이제 이미 변환된 본문을 담은
        // ResponseEntity<Any>를 돌려준다.
        return pullRequestController.removeLabel(found.id!!, number, labelId, authentication)
    }

    // PullRequestController.updatePullRequest()가 이미 PUT으로 존재해 그대로 위임한다.
    @PatchMapping("/{number}")
    fun update(
        @PathVariable owner: String,
        @PathVariable project: String,
        @PathVariable number: Long,
        @RequestBody request: PullRequestController.UpdatePullRequestRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val found = projectRepository.findByOwnerAndName(owner, project).orElse(null)
            ?: return ResponseEntity.notFound().build()
        // updatePullRequest()가 이미 변환된 본문을 담은 ResponseEntity<Any>를 돌려준다.
        return pullRequestController.updatePullRequest(found.id!!, number, request, authentication)
    }

    // PullRequestController.changeState()(범용 상태변경 POST)가 양방향 지원하고 있어 값만 고정해
    // 위임하는 어댑터만 추가한다.
    @PostMapping("/{number}/close")
    fun close(
        @PathVariable owner: String,
        @PathVariable project: String,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val found = projectRepository.findByOwnerAndName(owner, project).orElse(null)
            ?: return ResponseEntity.notFound().build()
        // changeState()가 성공/실패 모두 이미 변환된 본문(PullRequestResponse 또는 error map)을
        // 담은 ResponseEntity<Any>를 돌려준다.
        return pullRequestController.changeState(found.id!!, number, State.CLOSED, authentication)
    }

    @PostMapping("/{number}/reopen")
    fun reopen(
        @PathVariable owner: String,
        @PathVariable project: String,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val found = projectRepository.findByOwnerAndName(owner, project).orElse(null)
            ?: return ResponseEntity.notFound().build()
        return pullRequestController.changeState(found.id!!, number, State.OPEN, authentication)
    }

    // FileDiff는 순환 참조는 없지만, FileDiff.a/b(RawText)/editList(EditList)/oldMode/newMode(FileMode)가
    // Jackson 빈 컨벤션에 안 맞는 JGit 내부 타입이라 그대로 직렬화하면 base64 rawContent 등 JGit
    // 내부 표현이 그대로 노출돼 CLI 응답이 깨진다. PullRequestController.getDiff()가
    // FileDiffResponse(RestApiResponseDto.kt)로 변환해 내려주므로 그대로 위임한다.
    @GetMapping("/{number}/diff")
    fun diff(
        @PathVariable owner: String,
        @PathVariable project: String,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<List<FileDiffResponse>> {
        val found = projectRepository.findByOwnerAndName(owner, project).orElse(null)
            ?: return ResponseEntity.notFound().build()
        return pullRequestController.getDiff(found.id!!, number, authentication)
    }

    // submitReview()가 성공/실패(자기 승인 시도) 모두 이미 변환된 본문을 담은 ResponseEntity<Any>를
    // 돌려주므로 그대로 위임한다.
    @PostMapping("/{number}/reviews")
    fun submitReview(
        @PathVariable owner: String,
        @PathVariable project: String,
        @PathVariable number: Long,
        @RequestBody request: PullRequestController.SubmitPullRequestReviewRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val found = projectRepository.findByOwnerAndName(owner, project).orElse(null)
            ?: return ResponseEntity.notFound().build()
        return pullRequestController.submitReview(found.id!!, number, request, authentication)
    }

    @GetMapping("/{number}/reviews")
    fun getReviews(
        @PathVariable owner: String,
        @PathVariable project: String,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val found = projectRepository.findByOwnerAndName(owner, project).orElse(null)
            ?: return ResponseEntity.notFound().build()
        return pullRequestController.getReviews(found.id!!, number, authentication)
    }

    @PostMapping("/{number}/comments")
    fun addComment(
        @PathVariable owner: String,
        @PathVariable project: String,
        @PathVariable number: Long,
        @RequestBody request: PullRequestController.PullRequestCommentRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val found = projectRepository.findByOwnerAndName(owner, project).orElse(null)
            ?: return ResponseEntity.notFound().build()
        return pullRequestController.addComment(found.id!!, number, request, authentication).mapBody { it.toResponse() }
    }
}
