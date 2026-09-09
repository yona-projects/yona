package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.board.PostingService
import com.github.yonaprojects.yona.domain.enumeration.Operation
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.issue.IssueLabelRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.support.isModifiedByOthers
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/projects/{projectId}/posts")
class BoardController(
    private val postingService: PostingService,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val userRepository: UserRepository,
    private val postingRepository: PostingRepository,
    private val issueLabelRepository: IssueLabelRepository,
    private val accessControl: AccessControl
) {

    private fun getLoginUser(authentication: Authentication?): User? {
        if (authentication == null) return null
        return userRepository.findByLoginId(authentication.name).orElse(null)
    }

    private fun checkReadPermission(project: Project, user: User?): Boolean {
        return accessControl.isAllowed(user, project, Operation.READ)
    }

    // yona BoardApp.java:211 @IsCreatable(ResourceType.BOARD_POST) 대응 (P1-113). 공개 프로젝트의 [GL-controllers_BoardApp-009]
    // 비멤버 로그인 사용자도 게시글을 쓸 수 있는데, 여기서는 프로젝트 멤버/그룹멤버로만 좁게 검사해
    // yona보다 과도하게 제한하고 있었다. 이 함수는 createPosting()에서만 쓰이므로(UPDATE/DELETE는
    // 별도의 더 엄격한 규칙을 씀) 안전하게 생성 권한 규칙으로 교체한다.
    private fun checkWritePermission(project: Project, user: User?): Boolean {
        return accessControl.isProjectResourceCreatable(user, project, ResourceType.BOARD_POST)
    }

    // yona AbstractPostingApp.java:35 ITEMS_PER_PAGE(15) 대응 (P1-105). 게시글 목록은 이슈와 달리
    // 클라이언트가 페이지 크기를 바꿀 수 없는 고정값이라, 요청에 담긴 size는 무시하고 항상 15로 고정한다.
    @GetMapping
    fun getPostings(
        @PathVariable projectId: Long,
        @PageableDefault(size = ITEMS_PER_PAGE) pageable: Pageable,
        authentication: Authentication?
    ): ResponseEntity<Page<PostingResponse>> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication)
        if (!checkReadPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val fixedPageable = PageRequest.of(
            pageable.pageNumber,
            ITEMS_PER_PAGE,
            pageable.sort
        )
        val page = postingService.getPostings(projectId, fixedPageable)
        // P3-30(2026-09-09 코디네이터 발견/수정) — raw Posting 엔티티를 그대로 페이지네이션 응답에
        // 담으면 project->projectUsers->user 양방향 관계가 순환 직렬화되며 User.password/
        // passwordSalt까지 노출된다(P3-26/P3-28/IssueController와 동일한 근본원인).
        return ResponseEntity.ok(page.map { it.toResponse() })
    }

    @GetMapping("/{postId}")
    fun getPosting(
        @PathVariable projectId: Long,
        @PathVariable postId: Long,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val posting = postingService.getPosting(projectId, postId)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication)
        if (!checkReadPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        // P3-30 — 동일한 순환 직렬화/비밀번호 노출 문제 대응.
        return ResponseEntity.ok(posting.toResponse())
    }

    @PostMapping
    fun createPosting(
        @PathVariable projectId: Long,
        @RequestBody request: CreatePostingRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!checkWritePermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val posting = Posting(
            title = request.title,
            body = request.body,
            notice = request.notice ?: false,
            readme = request.readme ?: false,
            project = project
        )

        val saved = postingService.createPosting(projectId, posting, user.id!!)
        // P3-30 — 동일한 순환 직렬화/비밀번호 노출 문제 대응.
        return ResponseEntity.status(HttpStatus.CREATED).body(saved.toResponse())
    }

    @PutMapping("/{postId}")
    fun updatePosting(
        @PathVariable projectId: Long,
        @PathVariable postId: Long,
        @RequestBody request: UpdatePostingRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val posting = postingService.getPosting(projectId, postId)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!accessControl.isAllowed(user, project, posting, Operation.UPDATE)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val updated = postingService.updatePosting(
            projectId = projectId,
            number = postId,
            title = request.title,
            body = request.body,
            notice = request.notice ?: false,
            readme = request.readme ?: false,
            authorId = user.id!!,
            sendNotificationMail = request.sendNotificationMail ?: false
        )

        // P3-30 — 동일한 순환 직렬화/비밀번호 노출 문제 대응.
        return ResponseEntity.ok(updated.toResponse())
    }


    // yona BoardApi.java:128-159 updatePostingContent() 대응 (P1-107). 게시글 본문만 인라인 수정하는 [GL-controllers_api_BoardApi-006]
    // 경량 API — updateIssueContent(이슈, P1-102)와 동일하게 클라이언트가 저장 직전 화면 원문
    // (request.original)을 그대로 보내면, 서버가 그 원문과 현재 DB 값을 각각 해시해 비교해 다르면
    // (=그 사이 다른 사람이 이미 수정) 409로 거부한다. legacy와 동일하게 권한 확인이 충돌 검사보다 먼저다.
    @PatchMapping("/{postId}/content")
    fun updatePostingContent(
        @PathVariable projectId: Long,
        @PathVariable postId: Long,
        @RequestBody request: UpdatePostingContentRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val posting = postingService.getPosting(projectId, postId)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!accessControl.isAllowed(user, project, posting, Operation.UPDATE)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        if (isModifiedByOthers(posting.body ?: "", request.original)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "Already modified by someone.", "storedContent" to posting.body))
        }

        posting.body = request.content
        postingRepository.save(posting)

        return ResponseEntity.ok(mapOf("body" to posting.body))
    }

    // yona api.BoardApi.updatePostLabel 대응 — 게시글에 붙은 라벨 집합을 통째로 교체한다.
    @PutMapping("/{postId}/labels")
    fun updatePostLabels(
        @PathVariable projectId: Long,
        @PathVariable postId: Long,
        @RequestBody labelIds: List<Long>,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val posting = postingService.getPosting(projectId, postId)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!accessControl.isAllowed(user, project, posting, Operation.UPDATE)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        posting.labels = issueLabelRepository.findAllById(labelIds).toMutableSet()
        val saved = postingRepository.save(posting)

        // P3-30 — 동일한 순환 직렬화/비밀번호 노출 문제 대응.
        return ResponseEntity.ok(saved.toResponse())
    }

    @DeleteMapping("/{postId}")
    fun deletePosting(
        @PathVariable projectId: Long,
        @PathVariable postId: Long,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val posting = postingService.getPosting(projectId, postId)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!accessControl.isAllowed(user, project, posting, Operation.DELETE)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        postingService.deletePosting(projectId, postId, user.id!!)
        return ResponseEntity.ok(mapOf("status" to "success"))
    }

    data class CreatePostingRequest(
        val title: String,
        val body: String,
        val notice: Boolean?,
        val readme: Boolean?
    )

    data class UpdatePostingRequest(
        val title: String,
        val body: String,
        val notice: Boolean?,
        val readme: Boolean?,
        val sendNotificationMail: Boolean? = null
    )


    data class UpdatePostingContentRequest(
        val content: String,
        val original: String
    )

    companion object {
        // yona AbstractPostingApp.java:35 ITEMS_PER_PAGE 대응 (P1-105).
        const val ITEMS_PER_PAGE = 15
    }
}
