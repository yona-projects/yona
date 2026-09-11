package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.issue.Assignee
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.issue.IssueService
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.RoleType
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
import com.github.yonaprojects.yona.domain.attachment.AttachmentService
import com.github.yonaprojects.yona.domain.enumeration.Operation
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.issue.IssueCommentRepository
import com.github.yonaprojects.yona.domain.issue.IssueEvent
import com.github.yonaprojects.yona.domain.issue.IssueEventRepository
import com.github.yonaprojects.yona.domain.issue.IssueLabel
import com.github.yonaprojects.yona.domain.project.TitleHeadService
import com.github.yonaprojects.yona.domain.support.isModifiedByOthers
import com.github.yonaprojects.yona.domain.support.sha1Hex
import com.github.yonaprojects.yona.domain.comment.CommentService
import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.watch.WatchService
import org.springframework.data.jpa.domain.Specification

@RestController
@RequestMapping("/api/projects/{projectId}/issues")
class IssueController(
    private val issueService: IssueService,
    private val issueRepository: IssueRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val userRepository: UserRepository,
    private val attachmentService: AttachmentService,
    private val issueCommentRepository: IssueCommentRepository,
    private val issueEventRepository: IssueEventRepository,
    private val accessControl: AccessControl,
    private val titleHeadService: TitleHeadService,
    private val watchService: WatchService,
    private val commentService: CommentService
) {

    private fun getLoginUser(authentication: Authentication?): User? {
        if (authentication == null) return null
        return userRepository.findByLoginId(authentication.name).orElse(null)
    }

    private fun checkReadPermission(project: Project, user: User?): Boolean {
        return accessControl.isAllowed(user, project, Operation.READ)
    }

    // 이슈 단건 READ는 프로젝트 수준 권한(checkReadPermission)에 더해, 프로젝트 멤버가 아니어도
    // IssueSharer로 공유받은 사용자에게 READ를 허용한다.
    private fun checkReadPermission(project: Project, issue: Issue, user: User?): Boolean {
        if (checkReadPermission(project, user)) return true
        if (user == null) return false
        return accessControl.isAllowedIfSharer(issue, user)
    }

    // accessControl.isProjectResourceCreatable()을 반드시 써야 한다 — 프로젝트 직접멤버/그룹멤버
    // 여부만 확인하는 체크로 바꾸면 PUBLIC 프로젝트에서도 비멤버의 이슈 생성이 항상 403으로 막힌다.
    private fun checkWritePermission(project: Project, user: User?): Boolean =
        accessControl.isProjectResourceCreatable(user, project, ResourceType.ISSUE_POST)

    // 담당자(assignee)는 operation과 무관하게 author와 동급 쓰기 권한을 갖는다 — 프로젝트 멤버
    // 여부와도 무관하다.
    private fun isManagerOrAuthorOrAssignee(project: Project, issue: Issue, user: User?): Boolean {
        if (user == null) return false
        if (issue.authorId == user.id) return true
        if (issue.assignee?.user?.id == user.id) return true
        return projectUserRepository.findByProjectIdAndUserId(project.id!!, user.id!!)
            .map { it.role.id == RoleType.MANAGER.roleType }
            .orElse(false)
    }

    @GetMapping
    fun getIssues(
        @PathVariable projectId: Long,
        @RequestParam(required = false) state: State?,
        // 셋 다 없으면 기존 findByProject(AndState)로, 하나라도 있으면 JpaSpecificationExecutor
        // 기반 동적 조건으로 좁힌다.
        @RequestParam(required = false) assignee: String?,
        @RequestParam(required = false) label: String?,
        @RequestParam(required = false) author: String?,
        @PageableDefault(size = ITEMS_PER_PAGE) pageable: Pageable,
        authentication: Authentication?
    ): ResponseEntity<Page<IssueResponse>> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication)
        if (!checkReadPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val clampedPageable = PageRequest.of(
            pageable.pageNumber,
            minOf(pageable.pageSize, ITEMS_PER_PAGE_MAX),
            pageable.sort
        )

        if (assignee == null && label == null && author == null) {
            val page = if (state != null) {
                issueRepository.findByProjectAndState(project, state, clampedPageable)
            } else {
                issueRepository.findByProject(project, clampedPageable)
            }
            // Issue 엔티티를 그대로 담으면 project->projectUsers->user 순환 직렬화로 비밀번호
            // 해시까지 노출된다 — toResponse()로 변환해서 반환한다.
            return ResponseEntity.ok(page.map { it.toResponse() })
        }

        val spec = buildIssueFilterSpecification(project, state, assignee, label, author)
        return ResponseEntity.ok(issueRepository.findAll(spec, clampedPageable).map { it.toResponse() })
    }

    // getIssues()의 assignee/label/author 필터 조합을 위한 동적 Specification. author는
    // Issue.authorLoginId(비정규화 필드) 등가비교, assignee는 Assignee.user.loginId 등가비교,
    // label은 IssueLabel.name 등가비교(ManyToMany라 distinct 필요).
    private fun buildIssueFilterSpecification(
        project: Project,
        state: State?,
        assignee: String?,
        label: String?,
        author: String?
    ): Specification<Issue> {
        return Specification { root, query, cb ->
            val predicates = mutableListOf(cb.equal(root.get<Project>("project"), project))
            state?.let { predicates.add(cb.equal(root.get<State>("state"), it)) }
            author?.let { predicates.add(cb.equal(root.get<String>("authorLoginId"), it)) }
            assignee?.let {
                val assigneeJoin = root.join<Issue, Assignee>("assignee")
                val userJoin = assigneeJoin.join<Assignee, User>("user")
                predicates.add(cb.equal(userJoin.get<String>("loginId"), it))
            }
            label?.let {
                val labelJoin = root.join<Issue, IssueLabel>("labels")
                predicates.add(cb.equal(labelJoin.get<String>("name"), it))
                query?.distinct(true)
            }
            cb.and(*predicates.toTypedArray())
        }
    }

    @GetMapping("/{number}")
    fun getIssue(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val issue = issueRepository.findByProjectAndNumber(project, number)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication)

        // AccessControl.isAllowed() 호출보다 먼저 실행되는 별도 체크 — 프로젝트 멤버여도 작성자
        // 본인이 아니면 초안은 못 본다.
        if (issue.isDraft && (user == null || issue.authorLoginId != user.loginId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        if (!checkReadPermission(project, issue, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        // raw Issue 엔티티를 그대로 반환하면 project->projectUsers->user 양방향 관계가 Jackson
        // 직렬화 시 순환되며 User.password/passwordSalt까지 노출된다.
        return ResponseEntity.ok(issue.toResponse())
    }

    @GetMapping("/{number}/timeline")
    fun getTimeline(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<List<IssueEventResponse>> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val issue = issueRepository.findByProjectAndNumber(project, number)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication)
        if (!checkReadPermission(project, issue, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        // IssueEvent.issue(ManyToOne)를 그대로 반환하면 issue->project->projectUsers->user로
        // 이어지는 순환/비밀번호 노출 문제가 재발한다.
        return ResponseEntity.ok(issueEventRepository.findByIssueOrderByCreatedAsc(issue).map { it.toResponse() })
    }

    @PostMapping
    fun createIssue(
        @PathVariable projectId: Long,
        @RequestBody request: CreateIssueRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!checkWritePermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val assigneeUser = request.assigneeId?.let { userRepository.findById(it).orElse(null) }

        val issue = Issue(
            title = request.title,
            body = request.body ?: "",
            project = project
        )

        val saved = issueService.createIssue(
            issue = issue,
            author = user,
            assigneeUser = assigneeUser,
            milestoneId = request.milestoneId,
            labelIds = request.labelIds,
            isDraft = request.isDraft
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(saved.toResponse())
    }

    @PutMapping("/{number}")
    fun updateIssue(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        @RequestBody request: UpdateIssueRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val issue = issueRepository.findByProjectAndNumber(project, number)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!isManagerOrAuthorOrAssignee(project, issue, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val assigneeUser = request.assigneeId?.let { userRepository.findById(it).orElse(null) }

        val updated = issueService.updateIssue(
            issueId = issue.id!!,
            title = request.title,
            body = request.body,
            updater = user,
            assigneeUser = assigneeUser,
            milestoneId = request.milestoneId,
            labelIds = request.labelIds
        )

        // raw 엔티티 반환 시의 순환 직렬화/비밀번호 노출 방지(getIssue() 참고).
        return ResponseEntity.ok(updated.toResponse())
    }

    // 실제 이동을 호출하기 전에 원본 이슈 수정권한 + 대상 프로젝트 생성권한을 모두 먼저 확인한다
    // — 권한 없이도 이동이 일부 반영되는 인가 우회를 막기 위함.
    @PostMapping("/{number}/move")
    fun moveIssue(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        @RequestBody request: MoveIssueRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val issue = issueRepository.findByProjectAndNumber(project, number)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!isManagerOrAuthorOrAssignee(project, issue, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val targetProject = projectRepository.findById(request.targetProjectId).orElse(null)
            ?: return ResponseEntity.badRequest().build()

        if (!accessControl.isProjectResourceCreatable(user, targetProject, ResourceType.ISSUE_POST)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val moved = issueService.moveIssue(issue.id!!, request.targetProjectId, user)

        // raw 엔티티 반환 시의 순환 직렬화/비밀번호 노출 방지(getIssue() 참고).
        return ResponseEntity.ok(moved.toResponse())
    }

    @PostMapping("/{number}/publish")
    fun publishIssue(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val issue = issueRepository.findByProjectAndNumber(project, number)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!isManagerOrAuthorOrAssignee(project, issue, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val published = issueService.publishIssue(issue.id!!, user)

        // raw 엔티티 반환 시의 순환 직렬화/비밀번호 노출 방지(getIssue() 참고).
        return ResponseEntity.ok(published.toResponse())
    }

    @DeleteMapping("/{number}")
    fun deleteIssue(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val issue = issueRepository.findByProjectAndNumber(project, number)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!isManagerOrAuthorOrAssignee(project, issue, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        issueService.deleteIssueCascade(issue)
        return ResponseEntity.ok(mapOf("status" to "success"))
    }

    @PostMapping("/{number}/state")
    fun changeState(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        @RequestParam state: State,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val issue = issueRepository.findByProjectAndNumber(project, number)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        if (!isManagerOrAuthorOrAssignee(project, issue, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val updated = issueService.changeState(issue.id!!, state, user.loginId)
        // raw 엔티티 반환 시의 순환 직렬화/비밀번호 노출 방지(getIssue() 참고).
        return ResponseEntity.ok(updated.toResponse())
    }


    // Issue.voters(공감 투표)와는 별개로 이슈 자체에 +1 가중치를 매기는 정수 카운터.
    @PostMapping("/{number}/upvoteWeight")
    fun upvoteWeight(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val issue = issueRepository.findByProjectAndNumber(project, number)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication)
        if (!accessControl.isAllowed(user, project, issue, Operation.UPDATE)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        return ResponseEntity.ok(issueService.upvoteWeight(issue.id!!).toResponse())
    }

    @PostMapping("/{number}/downvoteWeight")
    fun downvoteWeight(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val issue = issueRepository.findByProjectAndNumber(project, number)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication)
        if (!accessControl.isAllowed(user, project, issue, Operation.UPDATE)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        return ResponseEntity.ok(issueService.downvoteWeight(issue.id!!).toResponse())
    }


    // 클라이언트가 화면에 표시된 시점의 body 체크섬(클라이언트가 직접 SHA-1 계산)과 댓글 수를
    // 보내면, 서버 현재 상태와 비교해 "다른 사용자가 이미 수정했는지"를 폴링으로 감지한다. 저장
    // 시점 충돌 차단(409)은 별도 — updateIssueContent()의 원본 대조 검사 참고.
    @PostMapping("/{number}/detectChange")
    fun detectChange(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        @RequestBody request: DetectChangeRequest,
        authentication: Authentication?
    ): ResponseEntity<Map<String, Any?>> {
        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val issue = issueRepository.findByProjectAndNumber(project, number)
            ?: return ResponseEntity.notFound().build()

        val comments = issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(issue.id!!)
        val currentNumOfComments = comments.size

        val result = mutableMapOf<String, Any?>()
        if (request.numOfComments < currentNumOfComments) {
            val lastComment = comments.last()
            val commentAuthor = lastComment.authorLoginId?.let { userRepository.findByLoginId(it).orElse(null) }
            result["commentAuthorName"] = commentAuthor?.getDisplayName() ?: lastComment.authorLoginId
        }

        val currentChecksum = sha1Hex(issue.body ?: "")
        result["issueBodyChanged"] = currentChecksum != request.issueBodyChecksum
        result["numOfComments"] = currentNumOfComments
        result["issueBodyChecksum"] = currentChecksum
        result["issueUpdateDate"] = (issue.updatedDate ?: issue.createdDate)?.toEpochMilli()
        result["result"] = "ok"

        return ResponseEntity.ok(result)
    }

    // P3-52 항목1 — legacy IssueApi.commentNotiRecivers() 대응. 댓글 작성 중(디바운스) "지금 이
    // 내용으로 등록하면 누구에게 알림이 갈지" 미리보기. legacy는 임시 IssueComment를 만들어
    // NotificationEvent.getMandatoryReceivers(comment, NEW_COMMENT)를 호출하지만, yona
    // CommentServiceImpl.createIssueComment()는 이미 더 단순화된 대응 로직(baseWatchers=이슈
    // 작성자 + watchService.findActualWatchers(ISSUE_POST, NEW_COMMENT) + 멘션 - 본인)으로
    // 실제 알림을 발행한다 — 미리보기가 실제 발행 결과와 어긋나지 않도록 그 로직을 그대로
    // 재사용한다(범위는 legacy와 동일하게 이슈 댓글 한정, 게시글/PR 댓글은 대상 아님).
    @PostMapping("/{number}/commentNotiReceivers")
    fun commentNotiReceivers(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        @RequestBody request: CommentNotiReceiversRequest,
        authentication: Authentication?
    ): ResponseEntity<Map<String, Any?>> {
        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val issue = issueRepository.findByProjectAndNumber(project, number)
            ?: return ResponseEntity.notFound().build()

        val authorUser = issue.authorId?.let { userRepository.findById(it).orElse(null) }
        val baseWatchers = if (authorUser != null) setOf(authorUser) else emptySet()
        val receivers = watchService.findActualWatchers(
            baseWatchers = baseWatchers,
            resourceType = ResourceType.ISSUE_POST,
            resourceId = issue.id.toString(),
            projectId = issue.project.id,
            eventType = EventType.NEW_COMMENT
        ).toMutableSet()
        receivers.removeIf { it.id == user.id }
        receivers.addAll(commentService.extractMentionedUsers(request.comment))

        val users = receivers.sortedBy { it.name }.map { r ->
            mapOf(
                "loginId" to r.loginId,
                "name" to r.getDisplayName(),
                "pureNameOnly" to r.getPureNameOnly(),
                "avatarUrl" to (r.avatarUrl ?: "")
            )
        }

        return ResponseEntity.ok(mapOf("receivers" to users))
    }

    // 이슈 본문만 인라인 수정하는 경량 API — 클라이언트가 "저장 직전에 화면에 있던 원문 전체"를
    // 그대로 보내면, 서버가 그 원문의 체크섬과 현재 DB 값의 체크섬을 비교해 다르면(=그 사이에 다른
    // 사람이 이미 수정) 409로 거부한다.
    @PatchMapping("/{number}/content")
    fun updateIssueContent(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        @RequestBody request: UpdateIssueContentRequest,
        authentication: Authentication?
    ): ResponseEntity<Map<String, Any?>> {
        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val issue = issueRepository.findByProjectAndNumber(project, number)
            ?: return ResponseEntity.notFound().build()

        if (isModifiedByOthers(issue.body ?: "", request.original)) {
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

    data class CreateIssueRequest(
        val title: String,
        val body: String?,
        val milestoneId: Long?,
        val assigneeId: Long?,
        val labelIds: List<Long>?,
        // true면 초안(DRAFT)으로 생성한다.
        val isDraft: Boolean = false
    )

    data class UpdateIssueRequest(
        val title: String,
        val body: String,
        val milestoneId: Long?,
        val assigneeId: Long?,
        val labelIds: List<Long>?
    )

    data class MoveIssueRequest(
        val targetProjectId: Long
    )


    data class DetectChangeRequest(
        val issueBodyChecksum: String,
        val numOfComments: Int
    )

    data class CommentNotiReceiversRequest(
        val comment: String = "",
        val parentCommentId: String? = null
    )

    data class UpdateIssueContentRequest(
        val content: String,
        val original: String
    )

    companion object {
        const val ITEMS_PER_PAGE = 15
        const val ITEMS_PER_PAGE_MAX = 45
    }
}
