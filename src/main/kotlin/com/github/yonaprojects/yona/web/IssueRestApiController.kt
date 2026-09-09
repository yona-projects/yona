package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// Go CLI 등 외부 클라이언트를 위한 신규 범용 REST API
// (`/api/v1/projects/{owner}/{project}/issues`). ApiTokenAuthenticationFilter가 이미 이
// 네임스페이스로 들어오는 요청의 스코프(ISSUES 그룹) 인가를 필터 단계에서 처리하므로(필터의
// resourceSegmentToResourceType 매핑에 "issues"가 이미 존재) 이 컨트롤러는 스코프 판정을 다시
// 구현하지 않는다.
//
// 클래스명이 IssueApiController가 아니라 IssueRestApiController인 이유: 그 이름은 이미
// IssueApiController.kt(legacy Open API 네임스페이스 `-_-api/v1/owners/...` 전용)가 쓰고 있다.
//
// 비즈니스 로직/권한 체크(AccessControl 기반) 자체는 기존 IssueController.kt
// (`/api/projects/{projectId}/issues`, 숫자 projectId 기반, 웹 프런트엔드용)에 이미 완비돼 있어
// 새로 만들지 않고, owner/project 이름으로 프로젝트를 찾아 그 컨트롤러의 공개 메서드에 위임하는
// 얇은 어댑터로만 구현한다(신규 서비스 로직 없음). 댓글 작성은 같은 방식으로 CommentController에
// 위임한다.
@RestController
@RequestMapping("/api/v1/projects/{owner}/{project}/issues")
class IssueRestApiController(
    private val projectRepository: ProjectRepository,
    private val issueController: IssueController,
    private val commentController: CommentController
) {

    // 예전엔 여기서 IssueController가 반환한 값을 다시 .toResponse()로 감쌌으나, 그건
    // IssueController가 raw Issue 엔티티를 반환하던 시절의 방어였다. IssueController(getIssues/
    // createIssue/getIssue/updateIssue/changeState/moveIssue)가 전부 자체적으로 IssueResponse
    // DTO를 반환하도록 고쳐지면서, 이미 DTO로 변환된 값에 존재하지 않는 확장 함수를 호출하려다
    // 컴파일 에러가 났다 — mapBody 자체를 제거하고 그대로 위임한다(PullRequestApiController가
    // 겪은 것과 동일한 정리).

    // `gh issue list --assignee/--label/--author` 대응. IssueController.getIssues()에 이미 추가한
    // 동일한 이름의 선택 파라미터를 그대로 전달만 한다(신규 서비스 로직 없음, 얇은 어댑터 원칙 유지).
    @GetMapping
    fun list(
        @PathVariable owner: String,
        @PathVariable project: String,
        @RequestParam(required = false) state: State?,
        @RequestParam(required = false) assignee: String?,
        @RequestParam(required = false) label: String?,
        @RequestParam(required = false) author: String?,
        @PageableDefault(size = IssueController.ITEMS_PER_PAGE) pageable: Pageable,
        authentication: Authentication?
    ): ResponseEntity<Page<IssueResponse>> {
        val found = projectRepository.findByOwnerAndName(owner, project).orElse(null)
            ?: return ResponseEntity.notFound().build()
        return issueController.getIssues(found.id!!, state, assignee, label, author, pageable, authentication)
    }

    @PostMapping
    fun create(
        @PathVariable owner: String,
        @PathVariable project: String,
        @RequestBody request: IssueController.CreateIssueRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val found = projectRepository.findByOwnerAndName(owner, project).orElse(null)
            ?: return ResponseEntity.notFound().build()
        return issueController.createIssue(found.id!!, request, authentication)
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
        return issueController.getIssue(found.id!!, number, authentication)
    }

    // "개별 조회/수정/코멘트/클로즈"의 "수정" 대응. 부분 수정 의미가 강한
    // PATCH를 쓴다(IssueController의 웹용 대응 메서드는 PUT이지만, 그건 폼 전체 재제출을 전제로
    // 한 웹 프런트엔드 컨벤션이고 이 신규 API는 CLI/서드파티 연동 대상이라 REST 관례상 PATCH가
    // 더 적절하다 — 필드는 동일한 UpdateIssueRequest를 그대로 재사용).
    @PatchMapping("/{number}")
    fun update(
        @PathVariable owner: String,
        @PathVariable project: String,
        @PathVariable number: Long,
        @RequestBody request: IssueController.UpdateIssueRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val found = projectRepository.findByOwnerAndName(owner, project).orElse(null)
            ?: return ResponseEntity.notFound().build()
        return issueController.updateIssue(found.id!!, number, request, authentication)
    }

    @PostMapping("/{number}/comments")
    fun addComment(
        @PathVariable owner: String,
        @PathVariable project: String,
        @PathVariable number: Long,
        @RequestBody request: CommentController.CommentRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val found = projectRepository.findByOwnerAndName(owner, project).orElse(null)
            ?: return ResponseEntity.notFound().build()
        return commentController.createIssueComment(found.id!!, number, request, authentication)
    }

    @PostMapping("/{number}/close")
    fun close(
        @PathVariable owner: String,
        @PathVariable project: String,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val found = projectRepository.findByOwnerAndName(owner, project).orElse(null)
            ?: return ResponseEntity.notFound().build()
        return issueController.changeState(found.id!!, number, State.CLOSED, authentication)
    }

    // `gh issue reopen`. IssueController.changeState()가
    // close와 동일한 범용 상태변경 API라 값만 OPEN으로 고정해 위임한다.
    @PostMapping("/{number}/reopen")
    fun reopen(
        @PathVariable owner: String,
        @PathVariable project: String,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val found = projectRepository.findByOwnerAndName(owner, project).orElse(null)
            ?: return ResponseEntity.notFound().build()
        return issueController.changeState(found.id!!, number, State.OPEN, authentication)
    }

    // `gh issue transfer`. 서버 기능(IssueController.
    // moveIssue(), MoveIssueRequest(targetProjectId: Long))은 이미 있지만 숫자 ID를 요구한다 - CLI가
    // 매번 대상 프로젝트의 숫자 ID를 미리 조회하지 않아도 되도록, 이 어댑터가 owner/project 이름
    // 쌍을 받아 내부에서 ID로 resolve한 뒤 위임한다(신규 서비스 로직 없음).
    @PostMapping("/{number}/transfer")
    fun transfer(
        @PathVariable owner: String,
        @PathVariable project: String,
        @PathVariable number: Long,
        @RequestBody request: TransferIssueRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val found = projectRepository.findByOwnerAndName(owner, project).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val targetProject = projectRepository.findByOwnerAndName(request.targetOwner, request.targetProject).orElse(null)
            ?: return ResponseEntity.badRequest().build()

        return issueController.moveIssue(
            found.id!!, number, IssueController.MoveIssueRequest(targetProject.id!!), authentication
        )
    }

    data class TransferIssueRequest(
        val targetOwner: String,
        val targetProject: String
    )
}
