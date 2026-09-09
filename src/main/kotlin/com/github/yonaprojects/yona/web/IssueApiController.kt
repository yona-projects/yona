package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.attachment.AttachmentService
import com.github.yonaprojects.yona.domain.board.PostingCommentRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.enumeration.Operation
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueComment
import com.github.yonaprojects.yona.domain.issue.IssueCommentRepository
import com.github.yonaprojects.yona.domain.issue.IssueLabelCategoryRepository
import com.github.yonaprojects.yona.domain.issue.IssueLabelRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.issue.IssueService
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.support.isModifiedByOthers
import com.github.yonaprojects.yona.domain.support.isModifiedByOthersLegacyChecksum
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// yona controllers/api/IssueApi.java 대응 (P2-55, P2-56). legacy Open API 네임스페이스
// (`-_-api/v1/owners/{owner}/projects/{projectName}/...`)를 그대로 유지하는 컨트롤러 — 비즈니스
// 로직은 IssueController.kt와 동일한 IssueService/리포지토리를 재사용하고, 요청/응답 필드명만
// legacy JSON 계약(title/body/milestoneTitle/assignees[].loginId/labels[].labelName 등)에 맞춘다.
@RestController
class IssueApiController(
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val postingRepository: PostingRepository,
    private val issueRepository: IssueRepository,
    private val postingCommentRepository: PostingCommentRepository,
    private val issueCommentRepository: IssueCommentRepository,
    private val attachmentService: AttachmentService,
    private val userRepository: UserRepository,
    private val issueService: IssueService,
    private val accessControl: AccessControl,
    private val issueLabelRepository: IssueLabelRepository,
    private val issueLabelCategoryRepository: IssueLabelCategoryRepository,
    private val milestoneRepository: MilestoneRepository
) {

    private fun getLoginUser(authentication: Authentication?): User? {
        if (authentication == null) return null
        return userRepository.findByLoginId(authentication.name).orElse(null)
    }

    private fun checkWritePermission(projectId: Long, user: User?): Boolean {
        if (user == null) return false
        return projectUserRepository.existsByProjectIdAndUserId(projectId, user.id!!)
    }

    private fun checkReadPermission(project: Project, issue: Issue, user: User?): Boolean {
        return accessControl.isAllowed(user, project, Operation.READ) || (user != null && accessControl.isAllowedIfSharer(issue, user))
    }

    private fun isManagerOrAuthorOrAssignee(project: Project, issue: Issue, user: User?): Boolean {
        if (user == null) return false
        if (issue.authorId == user.id) return true
        if (issue.assignee?.user?.id == user.id) return true
        return projectUserRepository.findByProjectIdAndUserId(project.id!!, user.id!!)
            .map { it.role.id == RoleType.MANAGER.roleType }
            .orElse(false)
    }

    private fun resolveIssueState(state: String?): State =
        if (state?.equals("OPEN", ignoreCase = true) != false) State.OPEN else State.CLOSED

    // yona controllers/api/IssueApi.java getIssue() 대응 (P2-56).
    @GetMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}")
    fun getIssueLegacyPath(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.badRequest().build()
        val issue = issueRepository.findByProjectAndNumber(project, number)
            ?: return ResponseEntity.badRequest().build()

        val user = getLoginUser(authentication)
        if (!checkReadPermission(project, issue, user)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        // P3-30(2026-09-09 코디네이터 발견/수정) — raw Issue 엔티티를 그대로 반환하면 project->
        // projectUsers->user 순환 직렬화로 User.password/passwordSalt까지 노출된다. 실측: 이
        // 엔드포인트를 실제 앱으로 호출해 60690바이트 응답에 password 값이 그대로 있는 것을 확인.
        // 응답 필드명은 기존 IssueResponse(id/number/title/body/state/...)를 그대로 재사용한다 —
        // 실제 legacy 응답은 이미 {"result": {...}} 래핑이나 milestoneTitle/assignees[].loginId 같은
        // 필드 매핑을 하지 않은 채(가공 없이 그대로 엔티티만 직렬화) 서비스 중이었으므로, "진짜" legacy
        // Java 계약과는 이미 어긋나 있었다 — 여기서는 그 기존 관찰가능한 동작(감싸지 않은 평탄한 JSON,
        // title/body/state/weight 등 필드명)만 유지하며 비밀번호 노출만 제거한다.
        return ResponseEntity.ok(issue.toResponse())
    }

    // yona controllers/api/IssueApi.java:1176-1191 upvoteWeight() 대응 (P2-56).
    @PostMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}/upvoteWeight")
    fun upvoteWeightLegacyPath(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val issue = issueRepository.findByProjectAndNumber(project, number)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication)
        if (!accessControl.isAllowed(user, project, issue, Operation.UPDATE)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        // P3-30 — 동일한 순환 직렬화/비밀번호 노출 문제 대응.
        return ResponseEntity.ok(issueService.upvoteWeight(issue.id!!).toResponse())
    }

    // yona controllers/api/IssueApi.java:1194-1209 downvoteWeight() 대응 (P2-56).
    @PostMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}/downvoteWeight")
    fun downvoteWeightLegacyPath(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val issue = issueRepository.findByProjectAndNumber(project, number)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication)
        if (!accessControl.isAllowed(user, project, issue, Operation.UPDATE)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        // P3-30 — 동일한 순환 직렬화/비밀번호 노출 문제 대응.
        return ResponseEntity.ok(issueService.downvoteWeight(issue.id!!).toResponse())
    }

    // yona controllers/api/IssueApi.java:319-349 updateIssueContent() 대응 (P2-56). legacy 필드명은
    // `content`/`sha1`(원문 체크섬) — IssueController.updateIssueContent()와 동일한 충돌감지 로직.
    @PatchMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}/content")
    fun updateIssueContentLegacyPath(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable number: Long,
        @RequestBody request: LegacyUpdateIssueContentRequest,
        authentication: Authentication?
    ): ResponseEntity<Map<String, Any?>> {
        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val issue = issueRepository.findByProjectAndNumber(project, number)
            ?: return ResponseEntity.notFound().build()

        if (isModifiedByOthersLegacyChecksum(issue.body ?: "", request.sha1)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "Already modified by someone.", "storedContent" to issue.body))
        }

        if (!accessControl.isAllowed(user, project, issue, Operation.UPDATE)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        issue.body = request.content
        issueRepository.save(issue)

        return ResponseEntity.ok(mapOf("body" to issue.body))
    }

    // yona controllers/api/IssueApi.java:292-317 updateIssueState() 대응 (P2-56). legacy 필드명은
    // `state`("open"/그 외는 모두 closed 취급) — IssueController.changeState()와 동일한 서비스 재사용.
    @PatchMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}")
    fun updateIssueStateLegacyPath(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable number: Long,
        @RequestBody request: LegacyUpdateIssueStateRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val issue = issueRepository.findByProjectAndNumber(project, number)
            ?: return ResponseEntity.notFound().build()

        if (!isManagerOrAuthorOrAssignee(project, issue, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val updated = issueService.changeState(issue.id!!, resolveIssueState(request.state), user.loginId!!)
        // P3-30 — 동일한 순환 직렬화/비밀번호 노출 문제 대응.
        return ResponseEntity.ok(updated.toResponse())
    }

    // yona controllers/api/IssueApi.java:271-289,352-379 updateIssue()/updateIssueNode() 대응
    // (P2-56). legacy 필드명은 title/body/milestoneTitle(제목으로 마일스톤 조회)/state/
    // assignees[0].loginId — IssueController.updateIssue()/changeState()와 동일한 서비스 재사용.
    @PutMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}")
    fun updateIssueLegacyPath(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable number: Long,
        @RequestBody request: LegacyUpdateIssueRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val issue = issueRepository.findByProjectAndNumber(project, number)
            ?: return ResponseEntity.notFound().build()

        if (!isManagerOrAuthorOrAssignee(project, issue, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val assigneeUser = request.assignees?.firstOrNull()?.loginId?.let { userRepository.findByLoginId(it).orElse(null) }
        val milestoneId = request.milestoneTitle?.let { milestoneRepository.findByProjectAndTitle(project, it)?.id }

        var updated = issueService.updateIssue(
            issueId = issue.id!!,
            title = request.title,
            body = request.body,
            updater = user,
            assigneeUser = assigneeUser,
            milestoneId = milestoneId,
            labelIds = null
        )

        if (request.state != null) {
            updated = issueService.changeState(issue.id!!, resolveIssueState(request.state), user.loginId!!)
        }

        // P3-30 — 동일한 순환 직렬화/비밀번호 노출 문제 대응.
        return ResponseEntity.ok(updated.toResponse())
    }

    // yona controllers/api/IssueApi.java:427-443 updateLabels()/IssueApi.java:163-184
    // updateIssueLabel() 대응 (P2-56). legacy는 요청 바디 전체가 라벨 ID 문자열 배열이다
    // (`for(JsonNode node: json){ Long labelId = Long.parseLong(node.asText()); ... }`).
    @PostMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issuelabel/{number}")
    fun updateIssueLabelLegacyPath(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable number: Long,
        @RequestBody labelIds: List<String>,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val issue = issueRepository.findByProjectAndNumber(project, number)
            ?: return ResponseEntity.notFound().build()

        if (!isManagerOrAuthorOrAssignee(project, issue, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val labels = labelIds.mapNotNull { it.toLongOrNull() }
            .let { issueLabelRepository.findAllById(it) }
            .toMutableSet()
        issue.labels = labels
        val saved = issueRepository.save(issue)

        return ResponseEntity.ok(mapOf("id" to project.owner, "labels" to saved.labels.size))
    }

    // yona controllers/api/IssueApi.java:246-268,392-425 newIssues()/createIssuesNode() 대응
    // (P2-56, 2026-08-28 number/sendNotification 복원). legacy는 `{issues:[...], sendNotification}`
    // 배열 배치 생성 — 각 항목은 title/body/state/milestoneTitle(제목 조회)/assignees[0].loginId/
    // labels[](labelName+category 조회)/number(명시적 이슈번호, migration 전용)로 구성된다.
    // `IssueService.createIssue()`에 `explicitNumber`/`sendNotification` 파라미터를 추가해
    // legacy `saveWithNumber()`(카운터 미증가, 번호 그대로 사용)와 "sendNotification=false면 알림
    // 미발행" 동작을 그대로 재현한다.
    @PostMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues")
    fun newIssuesLegacyPath(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestBody request: LegacyNewIssuesRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.badRequest().build()

        val currentUser = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val created = request.issues.map { item ->
            val author = item.author?.loginId?.let { userRepository.findByLoginId(it).orElse(null) } ?: currentUser
            val assigneeUser = item.assignees?.firstOrNull()?.loginId?.let { userRepository.findByLoginId(it).orElse(null) }
            val milestoneId = item.milestoneTitle?.let { milestoneRepository.findByProjectAndTitle(project, it)?.id }
            val labelIds = item.labels?.mapNotNull { labelRef ->
                val category = labelRef.category?.let { issueLabelCategoryRepository.findByProjectAndName(project, it) }
                if (category == null || labelRef.labelName == null) null
                else issueLabelRepository.findByProjectAndCategoryAndName(project, category, labelRef.labelName)?.id
            }

            var savedIssue = issueService.createIssue(
                issue = Issue(title = item.title, body = item.body, project = project),
                author = author,
                assigneeUser = assigneeUser,
                milestoneId = milestoneId,
                labelIds = labelIds,
                isDraft = false,
                explicitNumber = item.number,
                sendNotification = request.sendNotification
            )

            if (item.state?.equals("CLOSED", ignoreCase = true) == true) {
                savedIssue = issueService.changeState(savedIssue.id!!, State.CLOSED, author.loginId!!)
            }

            mapOf("status" to 201, "location" to "/${project.owner}/${project.name}/issue/${savedIssue.number}")
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(created)
    }

    data class LegacyUpdateIssueContentRequest(val content: String = "", val sha1: String = "")
    data class LegacyUpdateIssueStateRequest(val state: String? = null)
    data class LegacyAssigneeRef(val loginId: String? = null)
    data class LegacyLabelRef(val labelName: String? = null, val category: String? = null)
    data class LegacyAuthorRef(val loginId: String? = null)
    data class LegacyUpdateIssueRequest(
        val title: String = "",
        val body: String = "",
        val milestoneTitle: String? = null,
        val state: String? = null,
        val assignees: List<LegacyAssigneeRef>? = null
    )
    data class LegacyNewIssueItem(
        val title: String = "",
        val body: String = "",
        val state: String? = null,
        val milestoneTitle: String? = null,
        val assignees: List<LegacyAssigneeRef>? = null,
        val labels: List<LegacyLabelRef>? = null,
        val author: LegacyAuthorRef? = null,
        // yona controllers/api/IssueApi.java createIssuesNode()의 "number" 필드 대응 (P2-56 복원) —
        // 마이그레이션 시 과거 이슈 번호를 그대로 보존하기 위해 지정. 0 이하면 무시하고 자동 채번.
        val number: Long? = null
    )
    data class LegacyNewIssuesRequest(
        val issues: List<LegacyNewIssueItem> = emptyList(),
        val sendNotification: Boolean = false
    )

    // yona controllers/api/IssueApi.java imports() 대응 (P2-55). 게시글(Posting) 하나를 이슈로
    // 전환한다 — legacy Issue.from(posting)/IssueComment.from(postingComment, issue)와 동일하게
    // 필드를 그대로 복사하고, 댓글은 최상위→답글 순으로 옮긴다(legacy와 동일하게 2단계 depth만
    // 지원). 첨부파일은 AttachmentService.moveAll()로 캐시까지 함께 갱신하며 이관한다. 원본
    // 게시글과 옮겨진 게시글댓글은 이관 후 삭제한다(legacy removePosting()의 deleteOnly()에 대응 —
    // 복제가 아닌 이동이므로 원본을 남기지 않는다).
    @PostMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues/imports")
    fun imports(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam(required = false) postNumber: String?,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.badRequest().build()

        val user = getLoginUser(authentication)
        if (!checkWritePermission(project.id!!, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val number = postNumber?.toLongOrNull()
            ?: return ResponseEntity.badRequest().build()
        val posting = postingRepository.findByProjectAndNumber(project, number)
            ?: return ResponseEntity.badRequest().build()

        project.lastIssueNumber = project.lastIssueNumber + 1
        projectRepository.save(project)

        val savedIssue = issueRepository.save(
            Issue(
                title = posting.title,
                body = posting.body,
                history = posting.history,
                createdDate = posting.createdDate,
                updatedDate = posting.updatedDate,
                authorId = posting.authorId,
                authorLoginId = posting.authorLoginId,
                authorName = posting.authorName,
                project = project,
                number = project.lastIssueNumber
            )
        )

        val postingComments = postingCommentRepository.findByPostingIdOrderByCreatedDateAsc(posting.id!!)
        val (topLevel, replies) = postingComments.partition { it.parentComment == null }
        val postingCommentIdToIssueComment = mutableMapOf<Long, IssueComment>()

        topLevel.forEach { postingComment ->
            val issueComment = issueCommentRepository.save(
                IssueComment(
                    issue = savedIssue,
                    contents = postingComment.contents,
                    createdDate = postingComment.createdDate,
                    authorId = postingComment.authorId,
                    authorLoginId = postingComment.authorLoginId,
                    authorName = postingComment.authorName,
                    projectId = postingComment.projectId
                )
            )
            postingCommentIdToIssueComment[postingComment.id!!] = issueComment
        }
        replies.forEach { postingComment ->
            val parentIssueComment = postingComment.parentComment?.id?.let { postingCommentIdToIssueComment[it] }
            val issueComment = issueCommentRepository.save(
                IssueComment(
                    issue = savedIssue,
                    contents = postingComment.contents,
                    createdDate = postingComment.createdDate,
                    authorId = postingComment.authorId,
                    authorLoginId = postingComment.authorLoginId,
                    authorName = postingComment.authorName,
                    projectId = postingComment.projectId,
                    parentComment = parentIssueComment
                )
            )
            postingCommentIdToIssueComment[postingComment.id!!] = issueComment
        }

        attachmentService.moveAll(ResourceType.BOARD_POST, posting.id.toString(), ResourceType.ISSUE_POST, savedIssue.id.toString())
        postingCommentIdToIssueComment.forEach { (postingCommentId, issueComment) ->
            attachmentService.moveAll(
                ResourceType.NONISSUE_COMMENT, postingCommentId.toString(),
                ResourceType.ISSUE_COMMENT, issueComment.id.toString()
            )
        }

        postingCommentRepository.deleteAll(postingComments)
        postingRepository.delete(posting)

        return ResponseEntity.ok(mapOf("number" to savedIssue.number))
    }
}
