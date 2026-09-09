package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.enumeration.SearchType
import com.github.yonaprojects.yona.domain.support.SearchService
import com.github.yonaprojects.yona.domain.user.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// `yona search issues/projects`. web/SearchController.kt
// (`/search`, `/{owner}/{projectName}/search` 등)는 Thymeleaf 뷰(`search/list`)를 렌더링하는
// 세션 기반 컨트롤러라 위임 대상으로 쓸 수 없어(응답이 뷰 이름 String), 그 컨트롤러가 이미 쓰는
// SearchService(searchInAll)를 직접 호출해 JSON으로 노출하는 신규 얇은 컨트롤러를 뒀다(신규
// 서비스 로직 없음).
//
// `yona search prs` 지원: SearchType.PULL_REQUEST 신설 + SearchServiceImpl/PullRequestRepository에
// 기존 ISSUE 타입과 동일한 패턴(전역 검색은 toProject가 허용 프로젝트에 있거나 내가 contributor인
// PR까지 포함)의 인덱싱/검색 쿼리를 추가해 아래 searchPullRequests()로 노출한다.
//
// **스코프 인가 갭**: 이 엔드포인트는 여러 프로젝트를 가로지르는 전역
// 검색이라 `/api/v1/projects/{owner}/{project}/{resource}` 3세그먼트 모델(저장소 단위 스코프)에
// 자연스럽게 맞지 않는다. `/api/v1/search/**`는 ApiTokenAuthenticationFilter의 어떤 스코프 패턴과도
// 매칭되지 않아 세션 로그인/레거시 전권 토큰으로만 인증되고, Fine-grained 스코프 토큰은 이 경로에서
// 인증되지 않는다(레거시 findByToken 조회가 스코프 토큰의 원문값을 모르므로 자연히 비로그인 취급 -
// 구멍이 아니라 기능 제한, ProjectRestApiController 목록/조회 API의 갭과 동일한 성격).
@RestController
@RequestMapping("/api/v1/search")
class SearchRestApiController(
    private val searchService: SearchService,
    private val userRepository: UserRepository
) {

    private fun getLoginUser(authentication: Authentication?) =
        authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }

    // searchInAll()이 반환하는 Page<Issue>/Page<Project>/
    // Page<PullRequest>는 JPA 엔티티를 그대로 담고 있어(Project<->User 양방향 연관관계로 순환
    // 직렬화 유발, RestApiResponseDto.kt 참고) IssueRestApiController/PullRequestApiController와
    // 동일한 응답 DTO로 변환해 반환한다.
    @GetMapping("/issues")
    fun searchIssues(
        @RequestParam q: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        authentication: Authentication?
    ): ResponseEntity<Page<IssueResponse>> {
        if (q.isBlank()) return ResponseEntity.badRequest().build()
        val user = getLoginUser(authentication)
        val result = searchService.searchInAll(q, SearchType.ISSUE, user, PageRequest.of(page, size))
        return ResponseEntity.ok(result.issues.map { it.toResponse() })
    }

    // yona ProjectApi.createdProjectNode() 대응 — ProjectRestApiController.
    // toProjectNode()와 동일한 필드 구성이라 그 패턴을 그대로 재사용하는
    // RestApiResponseDto.kt의 ProjectRefResponse로 통일한다.
    @GetMapping("/projects")
    fun searchProjects(
        @RequestParam q: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        authentication: Authentication?
    ): ResponseEntity<Page<ProjectRefResponse>> {
        if (q.isBlank()) return ResponseEntity.badRequest().build()
        val user = getLoginUser(authentication)
        val result = searchService.searchInAll(q, SearchType.PROJECT, user, PageRequest.of(page, size))
        return ResponseEntity.ok(result.projects.map { it.toRefResponse() })
    }


    // `yona search prs`. SearchType.PULL_REQUEST 신설 + PullRequestRepository.searchPullRequests()
    // 인덱싱 쿼리 신설 이후, issues/projects와 동일한 패턴으로 노출한다.
    @GetMapping("/prs")
    fun searchPullRequests(
        @RequestParam q: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        authentication: Authentication?
    ): ResponseEntity<Page<PullRequestResponse>> {
        if (q.isBlank()) return ResponseEntity.badRequest().build()
        val user = getLoginUser(authentication)
        val result = searchService.searchInAll(q, SearchType.PULL_REQUEST, user, PageRequest.of(page, size))
        return ResponseEntity.ok(result.pullRequests.map { it.toResponse() })
    }
}
