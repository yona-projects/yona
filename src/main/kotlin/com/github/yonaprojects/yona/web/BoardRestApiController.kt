package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.project.ProjectRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// yonaco 등 외부 클라이언트를 위한 신규 범용 REST API(`/api/v1/projects/{owner}/{project}/board`).
// ApiTokenAuthenticationFilter의 resourceSegmentToResourceType 매핑에 "board"가 이미 있어(->
// ApiTokenScopeGroup.BOARD) 이 네임스페이스로 오는 요청은 필터 단계에서 이미 인가된다 —
// 컨트롤러에서 다시 구현하지 않는다.
//
// 이 어댑터가 생기기 전에는 fine-grained(스코프) 토큰으로 게시판을 읽을 방법이 전혀 없었다 —
// 유일한 JSON 경로(`/api/projects/{id}/posts`, BoardController)가
// ApiTokenAuthenticationFilter의 레거시 숫자ID 패턴에 걸려 무조건 ADMINISTRATION 스코프를
// 요구했기 때문이다(ProjectMemberController 전용으로 의도된 패턴이 너무 넓게 잡혀 있던 버그 —
// 그 패턴 자체는 ApiTokenAuthenticationFilter.legacyProjectIdPattern에서 좁혔다). 비즈니스
// 로직/권한 체크는 기존 BoardController.kt(`/api/projects/{projectId}/posts`, 숫자 projectId
// 기반, 웹 프런트엔드용)에 이미 완비돼 있어, owner/project 이름으로 프로젝트를 찾아 그 컨트롤러의
// 공개 메서드에 위임하는 얇은 어댑터로만 구현한다(신규 서비스 로직 없음, PullRequestApiController와
// 동일 패턴). 인라인 본문 수정(`/content`)과 라벨 일괄 교체(`/labels`)는 아직 다루지 않는다 —
// 필요해지면 동일 패턴으로 추가한다.
@RestController
@RequestMapping("/api/v1/projects/{owner}/{project}/board")
class BoardRestApiController(
    private val projectRepository: ProjectRepository,
    private val boardController: BoardController
) {

    @GetMapping
    fun list(
        @PathVariable owner: String,
        @PathVariable project: String,
        pageable: Pageable,
        authentication: Authentication?
    ): ResponseEntity<Page<PostingResponse>> {
        val found = projectRepository.findByOwnerAndName(owner, project).orElse(null)
            ?: return ResponseEntity.notFound().build()
        return boardController.getPostings(found.id!!, pageable, authentication)
    }

    @GetMapping("/{postId}")
    fun get(
        @PathVariable owner: String,
        @PathVariable project: String,
        @PathVariable postId: Long,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val found = projectRepository.findByOwnerAndName(owner, project).orElse(null)
            ?: return ResponseEntity.notFound().build()
        return boardController.getPosting(found.id!!, postId, authentication)
    }

    @PostMapping
    fun create(
        @PathVariable owner: String,
        @PathVariable project: String,
        @RequestBody request: BoardController.CreatePostingRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val found = projectRepository.findByOwnerAndName(owner, project).orElse(null)
            ?: return ResponseEntity.notFound().build()
        return boardController.createPosting(found.id!!, request, authentication)
    }

    @PutMapping("/{postId}")
    fun update(
        @PathVariable owner: String,
        @PathVariable project: String,
        @PathVariable postId: Long,
        @RequestBody request: BoardController.UpdatePostingRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val found = projectRepository.findByOwnerAndName(owner, project).orElse(null)
            ?: return ResponseEntity.notFound().build()
        return boardController.updatePosting(found.id!!, postId, request, authentication)
    }

    @DeleteMapping("/{postId}")
    fun delete(
        @PathVariable owner: String,
        @PathVariable project: String,
        @PathVariable postId: Long,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        val found = projectRepository.findByOwnerAndName(owner, project).orElse(null)
            ?: return ResponseEntity.notFound().build()
        return boardController.deletePosting(found.id!!, postId, authentication)
    }
}
