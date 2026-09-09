package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.board.PostingService
import com.github.yonaprojects.yona.domain.enumeration.Operation
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.issue.IssueLabelRepository
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.support.isModifiedByOthers
import com.github.yonaprojects.yona.domain.support.isModifiedByOthersLegacyChecksum
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

// yona controllers/api/BoardApi.java 대응. legacy Open API 네임스페이스
// (`-_-api/v1/owners/{owner}/projects/{projectName}/...`)를 그대로 유지하는 컨트롤러 — 비즈니스
// 로직은 BoardController.kt와 동일한 PostingService/리포지토리를 재사용하고, 요청/응답 필드명만
// legacy JSON 계약에 맞춘다.
@RestController
class BoardApiController(
    private val projectRepository: ProjectRepository,
    private val postingRepository: PostingRepository,
    private val postingService: PostingService,
    private val userRepository: UserRepository,
    private val issueLabelRepository: IssueLabelRepository,
    private val accessControl: AccessControl
) {

    private fun getLoginUser(authentication: Authentication?): User? {
        if (authentication == null) return null
        return userRepository.findByLoginId(authentication.name).orElse(null)
    }

    // yona controllers/api/BoardApi.java updatePostLabel() 대응. legacy는 요청 바디
    // 전체가 라벨 ID 문자열 배열이다.
    @PostMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/postlabel/{number}")
    fun updatePostLabelLegacyPath(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable number: Long,
        @RequestBody labelIds: List<String>,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val posting = postingRepository.findByProjectAndNumber(project, number)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication)
        if (!accessControl.isAllowed(user, project, posting, Operation.UPDATE)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val labels = labelIds.mapNotNull { it.toLongOrNull() }
            .let { issueLabelRepository.findAllById(it) }
            .toMutableSet()
        posting.labels = labels
        val saved = postingRepository.save(posting)

        return ResponseEntity.ok(mapOf("id" to project.owner, "labels" to saved.labels.size))
    }

    // yona controllers/api/BoardApi.java updatePostingContent() 대응. legacy
    // 필드명은 `content`/`sha1`(원문 체크섬).
    @PatchMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/posts/{number}/content")
    fun updatePostingContentLegacyPath(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable number: Long,
        @RequestBody request: LegacyUpdatePostingContentRequest,
        authentication: Authentication?
    ): ResponseEntity<Map<String, Any?>> {
        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val posting = postingRepository.findByProjectAndNumber(project, number)
            ?: return ResponseEntity.notFound().build()

        if (!accessControl.isAllowed(user, project, posting, Operation.UPDATE)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        if (isModifiedByOthersLegacyChecksum(posting.body ?: "", request.sha1)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "Already modified by someone.", "storedContent" to posting.body))
        }

        posting.body = request.content
        postingRepository.save(posting)

        return ResponseEntity.ok(mapOf("body" to posting.body))
    }

    // yona controllers/api/BoardApi.java newPostings()/createPostingNode() 대응.
    // legacy는 `{posts:[...]}` 배열 배치 생성
    // (title/body/author/createdAt/updatedAt/number) — `PostingService.createPosting()`에
    // `explicitNumber` 파라미터를 추가해 legacy `saveWithNumber()`(카운터 미증가, 번호 그대로
    // 사용) 동작을 그대로 재현한다.
    @PostMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/posts")
    fun newPostingsLegacyPath(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestBody request: LegacyNewPostingsRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.badRequest().build()

        val currentUser = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!accessControl.isProjectResourceCreatable(currentUser, project, ResourceType.BOARD_POST)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val created = request.posts.map { item ->
            val author = item.author?.loginId?.let { userRepository.findByLoginId(it).orElse(null) } ?: currentUser
            val saved = postingService.createPosting(
                project.id!!,
                Posting(title = item.title, body = item.body, project = project),
                author.id!!,
                explicitNumber = item.number
            )
            mapOf("status" to 201, "location" to "/${project.owner}/${project.name}/post/${saved.number}")
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(created)
    }

    data class LegacyUpdatePostingContentRequest(val content: String = "", val sha1: String = "")
    data class LegacyPostingAuthorRef(val loginId: String? = null)
    data class LegacyNewPostingItem(
        val title: String = "",
        val body: String = "",
        val author: LegacyPostingAuthorRef? = null,
        // yona controllers/api/BoardApi.java createPostingNode()의 "number" 필드 대응 —
        // 마이그레이션 시 과거 게시글 번호를 그대로 보존하기 위해 지정. 0 이하면 무시하고 자동 채번.
        val number: Long? = null
    )
    data class LegacyNewPostingsRequest(val posts: List<LegacyNewPostingItem> = emptyList())
}
