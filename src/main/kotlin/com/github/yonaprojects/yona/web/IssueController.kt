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
import com.github.yonaprojects.yona.domain.project.TitleHeadService
import com.github.yonaprojects.yona.domain.support.isModifiedByOthers
import com.github.yonaprojects.yona.domain.support.sha1Hex

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
    private val titleHeadService: TitleHeadService
) {

    private fun getLoginUser(authentication: Authentication?): User? {
        if (authentication == null) return null
        return userRepository.findByLoginId(authentication.name).orElse(null)
    }

    private fun checkReadPermission(project: Project, user: User?): Boolean {
        return accessControl.isAllowed(user, project, Operation.READ)
    }

    // yona AccessControl.java:250-259,274-279 대응 (P1-82). 이슈 단건 READ는 프로젝트 수준
    // 권한(checkReadPermission)에 더해, 프로젝트 멤버가 아니어도 IssueSharer로 공유받은
    // 사용자에게 READ를 허용한다.
    private fun checkReadPermission(project: Project, issue: Issue, user: User?): Boolean {
        if (checkReadPermission(project, user)) return true
        if (user == null) return false
        return accessControl.isAllowedIfSharer(issue, user)
    }

    // yona-wiki P3-02 14라운드(TASK-0436) — legacy AccessControl.isProjectResourceCreatable()의
    // "PUBLIC 프로젝트면 멤버가 아닌 로그인 사용자도 이슈/게시글을 만들 수 있다" 분기
    // (app/utils/AccessControl.java:64-77)가 이 메서드엔 빠져 있었다. 프로젝트 직접멤버/그룹멤버
    // 여부만 확인해, PUBLIC 프로젝트라도 멤버가 아니면 이슈 생성이 항상 403으로 막혔다 — 세션
    // 기반 웹 UI(IssueViewController.newIssue() 등)는 이미 accessControl.isProjectResourceCreatable()을
    // 쓰고 있어 이 버그가 없었고, REST API(`POST /api/v1/projects/{owner}/{project}/issues`,
    // `yona issue create`가 호출하는 경로)만 이 손수 구현한 좁은 체크를 쓰고 있었다. 실서버(H2)로
    // 재현: PUBLIC 프로젝트의 비멤버 사용자가 `yona issue create`를 호출하면 403 Forbidden.
    private fun checkWritePermission(project: Project, user: User?): Boolean =
        accessControl.isProjectResourceCreatable(user, project, ResourceType.ISSUE_POST)

    // yona AccessControl.java:244-248의 "user.isManagerOf(project) || isAllowedIfAuthor(user, resource)
    // || isAllowedIfAssignee(user, resource)" 대응 (P2-12). 담당자(assignee)는 operation과 무관하게
    // author와 동급 쓰기 권한을 갖는다 — 프로젝트 멤버 여부와도 무관하다(:398-406 isAllowedIfAssignee()).
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
        // yona-wiki P3-02 4라운드(Step8.5 서버 보강) — `gh issue list --assignee/--label/--author`
        // 대응. 셋 다 없으면(기존 호출부 100% 유지) 기존 findByProject(AndState)로, 하나라도 있으면
        // JpaSpecificationExecutor 기반 동적 조건으로 좁힌다(IssueRepository는 이미
        // JpaSpecificationExecutor<Issue>를 구현하고 있어 신규 리포지토리 메서드가 필요 없다).
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
            // P3-30 — Issue 엔티티를 그대로 페이지네이션 응답에 담으면 project->projectUsers->user
            // 순환 직렬화로 비밀번호 해시까지 노출된다(IssueController.getIssue()와 동일한 근본원인).
            return ResponseEntity.ok(page.map { it.toResponse() })
        }

        val spec = buildIssueFilterSpecification(project, state, assignee, label, author)
        return ResponseEntity.ok(issueRepository.findAll(spec, clampedPageable).map { it.toResponse() })
    }

    // yona-wiki P3-02 4라운드(Step8.5 서버 보강) — 위 getIssues()의 assignee/label/author 필터
    // 조합을 위한 동적 Specification. author는 Issue.authorLoginId(비정규화 필드) 등가비교,
    // assignee는 Assignee.user.loginId 등가비교, label은 IssueLabel.name 등가비교(ManyToMany라
    // distinct 필요).
    private fun buildIssueFilterSpecification(
        project: Project,
        state: State?,
        assignee: String?,
        label: String?,
        author: String?
    ): org.springframework.data.jpa.domain.Specification<Issue> {
        return org.springframework.data.jpa.domain.Specification { root, query, cb ->
            val predicates = mutableListOf(cb.equal(root.get<Project>("project"), project))
            state?.let { predicates.add(cb.equal(root.get<State>("state"), it)) }
            author?.let { predicates.add(cb.equal(root.get<String>("authorLoginId"), it)) }
            assignee?.let {
                val assigneeJoin = root.join<Issue, com.github.yonaprojects.yona.domain.issue.Assignee>("assignee")
                val userJoin = assigneeJoin.join<com.github.yonaprojects.yona.domain.issue.Assignee, User>("user")
                predicates.add(cb.equal(userJoin.get<String>("loginId"), it))
            }
            label?.let {
                val labelJoin = root.join<Issue, com.github.yonaprojects.yona.domain.issue.IssueLabel>("labels")
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

        // yona IssueApp.java:267-269 issue()의 draft 전용 게이트 대응 (P1-84). AccessControl.isAllowed()
        // 호출보다 먼저 실행되는 별도 체크 — 프로젝트 멤버여도 작성자 본인이 아니면 초안은 못 본다.
        if (issue.isDraft && (user == null || issue.authorLoginId != user.loginId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        if (!checkReadPermission(project, issue, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        // P3-30(2026-09-09 코디네이터 발견/수정) — raw Issue 엔티티를 그대로 반환하면 project->
        // projectUsers->user 양방향 관계가 Jackson 직렬화 시 순환되며 User.password/passwordSalt까지
        // 노출된다(P3-26/P3-28과 동일한 근본원인). 실측: GET /-_-api/v1/.../issues/{number}(동일
        // 로직을 재사용하는 IssueApiController 경로)로 60690바이트 응답에서 password 값 확인.
        return ResponseEntity.ok(issue.toResponse())
    }

    // yona Issue.getTimeline() / conf/routes "issue/$number/timeline" 대응 (P1-07)
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

        // P3-30 — IssueEvent.issue(ManyToOne)를 그대로 반환하면 issue->project->projectUsers->user로
        // 이어지는 동일한 순환/비밀번호 노출 문제가 재발한다.
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

        // P3-30 — createIssue()도 getIssue()와 동일한 순환 직렬화/비밀번호 노출 위험이 있었다.
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

        // P3-30 — 동일한 순환 직렬화/비밀번호 노출 문제 대응.
        return ResponseEntity.ok(updated.toResponse())
    }

    // yona IssueApp.editIssue()의 hasTargetProject() 분기 대응 (P1-48). yona는 이 권한 확인을
    // editPosting() 안에서(즉 실제 이동이 이미 일어난 뒤에) 하지만, yona는 이동을 호출하기 전에
    // 원본 이슈 수정권한 + 대상 프로젝트 생성권한을 모두 먼저 확인한다(관찰 가능한 정상 동작은
    // legacy와 동일하되, legacy의 "권한 없어도 이동은 일부 반영되는" 인가 우회 허점은 들여오지 않는다).
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

        // P3-30 — 동일한 순환 직렬화/비밀번호 노출 문제 대응.
        return ResponseEntity.ok(moved.toResponse())
    }

    // yona IssueApp.editIssue()의 "if (issue.isPublish) { ... }" 발행 전환 대응 (P1-65).
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

        // P3-30 — 동일한 순환 직렬화/비밀번호 노출 문제 대응.
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
        // P3-30 — 동일한 순환 직렬화/비밀번호 노출 문제 대응.
        return ResponseEntity.ok(updated.toResponse())
    }


    // yona IssueApi.java:1176-1191 upvoteWeight() 대응 (P1-101). Issue.voters(공감 투표)와는 별개로 [GL-controllers_api_IssueApi-064;GL-controllers_api_IssueApi-065]
    // 이슈 자체에 +1 가중치를 매기는 정수 카운터. legacy는 AccessControl.isAllowed(user, issue.asResource(),
    // Operation.UPDATE)로 권한을 확인한다.
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

        // P3-30 — 동일한 순환 직렬화/비밀번호 노출 문제 대응.
        return ResponseEntity.ok(issueService.upvoteWeight(issue.id!!).toResponse())
    }

    // yona IssueApi.java:1194-1209 downvoteWeight() 대응 (P1-101).
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

        // P3-30 — 동일한 순환 직렬화/비밀번호 노출 문제 대응.
        return ResponseEntity.ok(issueService.downvoteWeight(issue.id!!).toResponse())
    }


    // yona IssueApi.java:551-584 detectChange() 대응 (P1-102). 클라이언트가 화면에 표시된 시점의 [GL-controllers_api_IssueApi-030;GL-controllers_api_IssueApi-031]
    // body 체크섬(클라이언트가 직접 SHA-1 계산)과 댓글 수를 보내면, 서버 현재 상태와 비교해 "다른
    // 사용자가 이미 수정했는지"를 폴링으로 감지한다. 저장 시점 충돌 차단(409)은 별도 — updateIssue의
    // 원본 대조 검사(아래) 참고.
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

    // yona IssueApi.java:319-349 updateIssueContent() 대응 (P1-102). 이슈 본문만 인라인 수정하는 [GL-controllers_api_IssueApi-020]
    // 경량 API — 클라이언트가 "저장 직전에 화면에 있던 원문 전체"를 그대로 보내면, 서버가 그 원문의
    // 체크섬과 현재 DB 값의 체크섬을 비교해 다르면(=그 사이에 다른 사람이 이미 수정) 409로 거부한다
    // (detectChange의 "클라이언트가 체크섬을 계산해 보냄"과 반대로, 여기는 서버가 두 원문을 각각 해시).
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
        // yona AbstractPosting.isPublish 대응 (P1-65). true면 초안(DRAFT)으로 생성한다.
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

    data class UpdateIssueContentRequest(
        val content: String,
        val original: String
    )

    companion object {
        // yona AbstractPostingApp.java:35 ITEMS_PER_PAGE / IssueApp.java:46 ITEMS_PER_PAGE_MAX 대응 (P1-105).
        const val ITEMS_PER_PAGE = 15
        const val ITEMS_PER_PAGE_MAX = 45
    }
}
