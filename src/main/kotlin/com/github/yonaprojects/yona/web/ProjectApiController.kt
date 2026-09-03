package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.attachment.Attachment
import com.github.yonaprojects.yona.domain.attachment.AttachmentRepository
import com.github.yonaprojects.yona.domain.enumeration.Operation
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.issue.Assignee
import com.github.yonaprojects.yona.domain.issue.AssigneeRepository
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueComment
import com.github.yonaprojects.yona.domain.issue.IssueCommentRepository
import com.github.yonaprojects.yona.domain.issue.IssueLabel
import com.github.yonaprojects.yona.domain.issue.IssueLabelRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingComment
import com.github.yonaprojects.yona.domain.board.PostingCommentRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.milestone.Milestone
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.notification.NotificationUrlResolver
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectNameValidator
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.role.RoleRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.support.Comment
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

// yona controllers/api/ProjectApi.java의 newProject() 대응 (P2-45). legacy 경로는
// `-_-api/v1/owners/:owner/projects`(별도의 외부연동용 API 네임스페이스, yona에는 아직 이식되지
// 않은 네임스페이스)이지만, yona는 이미 `/api/projects/...`를 이 REST API 네임스페이스로 통일해
// 써왔으므로(ProjectController.kt의 search/update/delete/transfer/fork 등) 같은 컨벤션을 따른다 —
// 프로젝트 생성 로직 자체는 legacy 그대로, URL 스킴만 yona 컨벤션으로 아키텍처 번역.
@RestController
class ProjectApiController(
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val userRepository: UserRepository,
    private val organizationRepository: OrganizationRepository,
    private val roleRepository: RoleRepository,
    private val repositoryService: RepositoryService,
    private val accessControl: AccessControl,
    // yona ProjectApi.java:46-72 exports() 대응 (P2-46)에 필요한 의존성. [GL-controllers_api_ProjectApi-002;GL-controllers_api_ProjectApi-003]
    private val issueRepository: IssueRepository,
    private val postingRepository: PostingRepository,
    private val issueCommentRepository: IssueCommentRepository,
    private val postingCommentRepository: PostingCommentRepository,
    private val milestoneRepository: MilestoneRepository,
    private val issueLabelRepository: IssueLabelRepository,
    private val assigneeRepository: AssigneeRepository,
    private val attachmentRepository: AttachmentRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val notificationUrlResolver: NotificationUrlResolver
) {
    private val logger = LoggerFactory.getLogger(ProjectApiController::class.java)

    private fun getLoginUser(authentication: Authentication?): User? {
        if (authentication == null) return null
        return userRepository.findByLoginId(authentication.name).orElse(null)
    }

    // yona ProjectApi.java:111-161 newProject() 대응 (P2-45). legacy Open API 경로 별칭은 P2-59 —
    // 두 경로 모두 동일한 owner 경로변수 + 요청 바디 구조라 핸들러 재작성 없이 매핑만 추가한다.
    @PostMapping(value = ["/api/projects/{owner}", "/-_-api/v1/owners/{owner}/projects"])
    fun newProject(
        @PathVariable owner: String,
        @RequestBody request: NewProjectApiRequest,
        authentication: Authentication?
    ): ResponseEntity<*> {
        val currentUser = getLoginUser(authentication)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build<Any>()

        // yona ProjectApi.java:120-123 "!currentUser.isSiteManager()" 대응.
        if (!currentUser.isSiteManager) {
            return ResponseEntity.badRequest()
                .body(mapOf("message" to "User creation with api is allowed by Site admin only."))
        }

        // yona models/Project.java:62 @ExConstraints.Restricted({".", "..", ".git"}) 대응 (P1-145).
        if (ProjectNameValidator.isRestricted(request.projectName)) {
            return ResponseEntity.badRequest()
                .body(mapOf("message" to "Project name is restricted: ${request.projectName}"))
        }

        // yona ProjectApi.java:125-131 대응 — 중복 시 legacy는 실제 HTTP 상태는 400(badRequest)이고
        // JSON 바디 안의 "status" 필드에만 409를 적어 넣는다(원문 그대로의 불일치, 의도적 "정정" 금지).
        val existed = projectRepository.findByOwnerAndName(owner, request.projectName).orElse(null)
        if (existed != null) {
            return ResponseEntity.badRequest().body(
                mapOf(
                    "status" to 409,
                    "reason" to "Conflict",
                    "project" to createdProjectNode(existed)
                )
            )
        }

        val organization = organizationRepository.findByName(owner).orElse(null)

        // yona ProjectApi.java:133-138 대응 — isGlobalResourceCreatable(P2-34에서 이식) + owner가
        // 기존 조직명이면 그 조직 admin만 허용.
        if (!accessControl.isGlobalResourceCreatable(currentUser) ||
            (organization != null && !accessControl.isOrganizationAdmin(organization, currentUser))
        ) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(mapOf("message" to "'${currentUser.name}' has no permission"))
        }

        val project = Project().apply {
            this.owner = owner
            this.name = request.projectName
            this.overview = request.projectDescription ?: ""
            this.vcs = request.projectVcs ?: "GIT"
            this.createdDate = parseProjectCreatedDate(request.projectCreatedDate)
            this.projectScope = parseProjectScope(request.projectScope)
            this.siteurl = "http://localhost:9000/${request.projectName}"
            // yona ProjectApi.java:148-150 "Organization.isNameExist(owner)면 project.organization
            // 연동" 대응.
            if (organization != null) {
                this.organization = organization
            }
            // yona ProjectApi.java:230-243 saveMenuSettingsToDefault() 대응 — 전체 메뉴 활성화가 [GL-controllers_api_ProjectApi-012]
            // Project 엔티티의 isCodeEnabled 등 기본값(전부 true)과 이미 동일해 별도 호출이 필요 없다.
        }
        val savedProject = projectRepository.save(project)

        // yona ProjectApi.java:152 "ProjectUser.assignRole(User.SITE_MANAGER_ID, ..., SITEMANAGER)"
        // 대응 — SITE_MANAGER_ID는 legacy가 "DB의 1번 유저가 사이트매니저"라고 가정하는 하드코딩된
        // 상수(User.java:64 `SITE_MANAGER_ID = 1L`)로, yona는 사이트매니저 여부를 User.isSiteManager
        // 상태값으로 판단해(id 하드코딩 관례 자체가 없음) 이 상수에 대응하는 값이 없다. 이 메서드에
        // 진입하려면 currentUser가 이미 사이트매니저임이 검증됐으므로(위 isSiteManager 체크), 그
        // 역할(SITEMANAGER)을 실제로 API를 호출한 사이트매니저 본인에게 부여하는 것이 legacy의 의도
        // (API로 만든 프로젝트에 사이트 차원의 소유권 role을 남긴다)를 그대로 보존하는 아키텍처 번역이다.
        roleRepository.findById(RoleType.SITEMANAGER.roleType).ifPresent { role ->
            val projectUser = ProjectUser(project = savedProject, user = currentUser, role = role)
            projectUserRepository.save(projectUser)
            savedProject.projectUsers.add(projectUser)
        }

        // yona ProjectApi.java:153 "RepositoryService.createRepository(project)" 대응.
        repositoryService.getRepository(savedProject).create()

        addProjectMembers(request.members, savedProject)

        return ResponseEntity.status(HttpStatus.CREATED).body(createdProjectNode(savedProject))
    }

    // yona ProjectApi.java:163-179 parseProjectScope() 대응. [GL-controllers_api_ProjectApi-008]
    private fun parseProjectScope(scope: String?): ProjectScope {
        return when (scope) {
            "PRIVATE" -> ProjectScope.PRIVATE
            "PUBLIC" -> ProjectScope.PUBLIC
            "PROTECTED" -> ProjectScope.PROTECTED
            else -> ProjectScope.PRIVATE
        }
    }

    // yona ProjectApi.java:322-325 getDateString()/IssueApi.java:723-735 parseDateString()의
    // 역함수 대응 — 두 메서드가 공유하는 포맷("yyyy-MM-dd a hh:mm:ss Z", Locale.ENGLISH)을 그대로
    // 재사용해 exports()가 만든 날짜 문자열을 다시 파싱할 수 있게 한다. 파싱 실패 시 legacy도 null을
    // 반환(에러 응답 없이 조용히 무시)한다.
    private fun parseProjectCreatedDate(dateString: String?): Instant? {
        if (dateString == null) return null
        return try {
            SimpleDateFormat("yyyy-MM-dd a hh:mm:ss Z", Locale.ENGLISH).parse(dateString).toInstant()
        } catch (e: Exception) {
            null
        }
    }

    // yona ProjectApi.java:189-210 addProjectMembers() 대응. [GL-controllers_api_ProjectApi-009;GL-controllers_api_ProjectApi-010]
    private fun addProjectMembers(members: List<NewProjectApiMember>?, project: Project) {
        if (members == null) return

        members.forEach { memberReq ->
            val member = userRepository.findByEmail(memberReq.email).orElse(null) ?: return@forEach

            val roleType = when (memberReq.role.lowercase()) {
                "member" -> RoleType.MEMBER
                "manager" -> RoleType.MANAGER
                else -> {
                    logger.warn("Unknown role type: ${memberReq.email}")
                    return@forEach
                }
            }
            val role = roleRepository.findById(roleType.roleType).orElse(null) ?: return@forEach

            val existing = projectUserRepository.findByProjectIdAndUserId(project.id!!, member.id!!).orElse(null)
            if (existing != null) {
                existing.role = role
                projectUserRepository.save(existing)
            } else {
                projectUserRepository.save(ProjectUser(project = project, user = member, role = role))
            }
        }

        // yona Project.java:637-655 cleanEnrolledUsers() 대응 — 이 프로젝트에 대해 이미 "가입 신청"을 [GL-models_Project-083;GL-models_Project-084]
        // 해둔 사용자가 여기서 멤버로 추가되면 그 신청을 자동 수락 처리하고 알림을 보낸다. newProject()는
        // 항상 새로 만드는 프로젝트라 이 시점엔 project.enrolledUsers가 구조적으로 항상 비어있어(방금
        // 막 생성돼 아직 아무도 가입 신청을 할 수 없었음) 실질적으로 항상 아무 일도 하지 않는 no-op이므로
        // 포팅하지 않는다(레거시에 없는 동작을 추가하는 게 아니라, 레거시에서도 이 호출 지점 한정으로는
        // 절대 발동할 수 없는 코드라 이식 대상에서 제외 — 기존 프로젝트에 멤버를 추가하는 다른 경로가
        // 있다면 그 경로에서는 별도로 검토가 필요할 수 있음).
    }

    // yona ProjectApi.java:220-228 createdProjectNode() 대응. [GL-controllers_api_ProjectApi-011]
    private fun createdProjectNode(project: Project): Map<String, Any?> {
        return mapOf(
            "id" to project.id,
            "owner" to project.owner,
            "name" to project.name,
            "overview" to project.overview,
            "vcs" to project.vcs
        )
    }

    // yona ProjectApi.java:46-72 exports() 대응 (P2-46) — 프로젝트를 이슈/게시글/댓글/마일스톤/라벨까지 [GL-controllers_api_ProjectApi-002;GL-controllers_api_ProjectApi-003]
    // 포함해 JSON으로 전체 직렬화한다. legacy `@IsAllowed(Operation.DELETE)`(프로젝트 매니저/조직관리자
    // 전용)와 동일하게 accessControl.isAllowed(user, project, Operation.DELETE)로 게이트한다.
    // legacy Open API 경로 별칭은 P2-59 — owner/projectName 경로변수 구조가 동일해 매핑만 추가한다.
    @GetMapping(value = ["/api/projects/{owner}/{projectName}/exports", "/-_-api/v1/owners/{owner}/projects/{projectName}/exports"])
    fun exports(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?
    ): ResponseEntity<*> {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build<Any>()

        val user = getLoginUser(authentication)
        if (!accessControl.isAllowed(user, project, Operation.DELETE)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build<Any>()
        }

        val issues = issueRepository.findByProject(project)
        val postings = postingRepository.findByProject(project)
        val milestones = milestoneRepository.findByProject(project)
        val labels = issueLabelRepository.findByProject(project)
        val members = projectUserRepository.findByProjectId(project.id!!)
        val assignees = assigneeRepository.findByProjectId(project.id!!)

        val result = linkedMapOf<String, Any?>(
            "owner" to project.owner,
            "projectName" to project.name,
            "projectDescription" to project.overview,
            "projectCreatedDate" to formatProjectApiDate(project.createdDate),
            "projectVcs" to project.vcs,
            "projectScope" to project.projectScope.name,
            "assignees" to assignees.map { composeUserJson(it.user) },
            "authors" to findAuthors(project, issues, postings).map { composeUserJson(it) },
            "memberCount" to members.size,
            "members" to members.map { composeMemberJson(it) },
            "issueCount" to issues.size,
            "postCount" to postings.size,
            "milestoneCount" to milestones.size,
            "labels" to labels.map { composeAllLabelsJson(it) },
            "issues" to issues.map { getIssueResult(it) },
            "posts" to postings.map { getPostingResult(it) },
            "milestones" to milestones.map { getMilestoneNode(it) }
        )

        return ResponseEntity.ok(result)
    }

    // yona Project.java:182-212 findAuthors()/getIssueUsers()/getPostingUsers()/getPullRequestUsers() [GL-models_Project-040;GL-models_Project-041]
    // 대응 — 이슈 작성자 + 게시글 작성자 + (이 프로젝트로 들어온) PR 기여자를 순서대로 합쳐 중복
    // 제거한다(legacy도 LinkedHashSet<User>로 등장 순서를 보존하며 중복 제거).
    private fun findAuthors(project: Project, issues: List<Issue>, postings: List<Posting>): List<User> {
        val authors = LinkedHashMap<Long, User>()
        fun addAuthor(authorId: Long?) {
            if (authorId == null || authors.containsKey(authorId)) return
            userRepository.findById(authorId).ifPresent { authors[authorId] = it }
        }
        issues.forEach { addAuthor(it.authorId) }
        postings.forEach { addAuthor(it.authorId) }
        pullRequestRepository.findByToProject(project).forEach { addAuthor(it.contributor?.id) }
        return authors.values.toList()
    }

    // yona ProjectApi.java:87-109 getAssginees()/getAuthors() + composeAuthorJson()/composeAssigneeJson() [GL-controllers_api_ProjectApi-005;GL-controllers_api_ProjectApi-006]
    // 대응 — 네 메서드 모두 {loginId,name,email} 동일한 형태를 만들어 하나로 합친다(출력 결과가
    // 완전히 동일해 legacy의 중복 코드 4벌을 그대로 옮기지 않고 공유 — 로직/출력 분기 자체가 없어
    // "행동을 단순화"하는 게 아니라 순수 중복 제거).
    private fun composeUserJson(user: User?): Map<String, Any?> {
        return mapOf("loginId" to user?.loginId, "name" to user?.name, "email" to user?.email)
    }

    // yona ProjectApi.java:350-364 composeMembersJson() 대응. [GL-controllers_api_ProjectApi-020]
    private fun composeMemberJson(projectUser: ProjectUser): Map<String, Any?> {
        return mapOf(
            "loginId" to projectUser.user.loginId,
            "name" to projectUser.user.name,
            "role" to projectUser.role.name,
            "email" to projectUser.user.email
        )
    }

    // yona ProjectApi.java:366-376 composeLabelJson() 대응 — 이슈 안에 포함되는 라벨 표현(isExclusive [GL-controllers_api_ProjectApi-021]
    // 없음). getAllLabels()(project 최상위 labels 필드용, isExclusive 포함)와는 필드가 달라 별도로 둔다.
    private fun composeLabelJson(label: IssueLabel): Map<String, Any?> {
        return mapOf("labelName" to label.name, "labelColor" to label.color, "category" to label.category.name)
    }

    // yona ProjectApi.java:378-389 getAllLabels() 대응. [GL-controllers_api_ProjectApi-022]
    private fun composeAllLabelsJson(label: IssueLabel): Map<String, Any?> {
        return mapOf(
            "labelName" to label.name,
            "labelColor" to label.color,
            "category" to label.category.name,
            "isExclusive" to label.category.isExclusive
        )
    }

    // yona ProjectApi.java:441-450 getMilestoneNode() 대응. [GL-controllers_api_ProjectApi-025]
    private fun getMilestoneNode(milestone: Milestone): Map<String, Any?> {
        val node = linkedMapOf<String, Any?>(
            "id" to milestone.id,
            "title" to milestone.title,
            "state" to milestone.state.state(),
            "description" to milestone.contents
        )
        milestone.dueDate?.let { node["dueDate"] = formatProjectApiDate(it) }
        return node
    }

    // yona ProjectApi.java:276-320 getResult(AbstractPosting)의 ISSUE_POST 케이스 대응. [GL-controllers_api_ProjectApi-016;GL-controllers_api_ProjectApi-017]
    private fun getIssueResult(issue: Issue): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>(
            "number" to issue.number,
            "id" to issue.id,
            "title" to issue.title,
            "type" to ResourceType.ISSUE_POST.name,
            "author" to composeUserJson(resolveUser(issue.authorId)),
            "createdAt" to formatIsoDate(issue.createdDate),
            "updatedAt" to formatIsoDate(issue.updatedDate),
            "body" to issue.body,
            "owner" to issue.project.owner,
            "projectName" to issue.project.name
        )

        issue.assignee?.let { result["assignees"] = listOf(composeUserJson(it.user)) }
        result["state"] = issue.state.name
        if (issue.labels.isNotEmpty()) {
            result["labels"] = issue.labels.map { composeLabelJson(it) }
        }
        issue.milestone?.let {
            result["milestoneId"] = it.id
            result["milestoneTitle"] = it.title
        }
        issue.dueDate?.let { result["dueDate"] = formatProjectApiDate(it) }
        result["refUrl"] = notificationUrlResolver.getUrl(ResourceType.ISSUE_POST, issue.id.toString())

        val attachments = attachmentRepository.findByContainerTypeAndContainerId(ResourceType.ISSUE_POST, issue.id.toString())
        if (attachments.isNotEmpty()) {
            result["attachments"] = attachments.map { composeAttachmentJson(it) }
        }

        val comments = composeIssueCommentsJson(issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(issue.id!!))
        if (comments.isNotEmpty()) {
            result["comments"] = comments
        }

        return result
    }

    // yona ProjectApi.java:276-320 getResult(AbstractPosting)의 그 외(BOARD_POST) 케이스 대응 — [GL-controllers_api_ProjectApi-016;GL-controllers_api_ProjectApi-017]
    // ISSUE_POST 전용 필드(assignees/state/labels/milestone*/dueDate/refUrl)는 legacy의
    // `if (type == ISSUE_POST)` 분기 밖이라 전혀 나오지 않는다.
    private fun getPostingResult(posting: Posting): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>(
            "number" to posting.number,
            "id" to posting.id,
            "title" to posting.title,
            "type" to ResourceType.BOARD_POST.name,
            "author" to composeUserJson(resolveUser(posting.authorId)),
            "createdAt" to formatIsoDate(posting.createdDate),
            "updatedAt" to formatIsoDate(posting.updatedDate),
            "body" to posting.body,
            "owner" to posting.project.owner,
            "projectName" to posting.project.name
        )

        val attachments = attachmentRepository.findByContainerTypeAndContainerId(ResourceType.BOARD_POST, posting.id.toString())
        if (attachments.isNotEmpty()) {
            result["attachments"] = attachments.map { composeAttachmentJson(it) }
        }

        val comments = composePostingCommentsJson(postingCommentRepository.findByPostingIdOrderByCreatedDateAsc(posting.id!!))
        if (comments.isNotEmpty()) {
            result["comments"] = comments
        }

        return result
    }

    // yona ProjectApi.java:391-421 composePlainCommentsJson()의 ISSUE_COMMENT 케이스 대응 — [GL-controllers_api_ProjectApi-023;GL-controllers_api_ProjectApi-024]
    // yona는 IssueComment/PostingComment가 parentComment 필드 타입이 서로 달라(자기 자신 타입만
    // 부모가 될 수 있음) 공통 상위타입으로 일반화할 수 없어 타입별로 나눠 이식한다(순수 아키텍처 차이,
    // 트리 조립 알고리즘 자체는 동일).
    private fun composeIssueCommentsJson(comments: List<IssueComment>): List<Map<String, Any?>> {
        val topLevel = LinkedHashMap<Long, MutableMap<String, Any?>>()
        val children = LinkedHashMap<Long, MutableList<Map<String, Any?>>>()
        for (comment in comments) {
            val parentId = comment.parentComment?.id
            if (parentId != null) {
                children.getOrPut(parentId) { mutableListOf() }.add(composeCommentNode(comment, ResourceType.ISSUE_COMMENT))
            } else {
                comment.id?.let { topLevel[it] = composeCommentNode(comment, ResourceType.ISSUE_COMMENT) }
            }
        }
        // yona 원본은 commentMap.get(key)가 null이면(부모가 top-level에 없는, 즉 2단계 이상 중첩된
        // 답글) NPE를 던진다 — exports() 전체를 500으로 죽이는 명백한 결함이라 그대로 재현하지 않고
        // 조용히 건너뛴다(의도적 축약이 아니라 legacy 버그 재현 거부, 근거 명시).
        children.forEach { (parentId, childList) -> topLevel[parentId]?.set("childComments", childList) }
        return topLevel.values.toList()
    }

    // yona ProjectApi.java:391-421 composePlainCommentsJson()의 NONISSUE_COMMENT 케이스 대응. [GL-controllers_api_ProjectApi-023;GL-controllers_api_ProjectApi-024]
    private fun composePostingCommentsJson(comments: List<PostingComment>): List<Map<String, Any?>> {
        val topLevel = LinkedHashMap<Long, MutableMap<String, Any?>>()
        val children = LinkedHashMap<Long, MutableList<Map<String, Any?>>>()
        for (comment in comments) {
            val parentId = comment.parentComment?.id
            if (parentId != null) {
                children.getOrPut(parentId) { mutableListOf() }.add(composeCommentNode(comment, ResourceType.NONISSUE_COMMENT))
            } else {
                comment.id?.let { topLevel[it] = composeCommentNode(comment, ResourceType.NONISSUE_COMMENT) }
            }
        }
        children.forEach { (parentId, childList) -> topLevel[parentId]?.set("childComments", childList) }
        return topLevel.values.toList()
    }

    // yona ProjectApi.java:423-439 getCommentNode() 대응 — IssueComment/PostingComment가 공유하는
    // support.Comment 기반클래스(id/contents/createdDate/authorId)만으로 충분해 타입별로 나누지 않는다.
    private fun composeCommentNode(comment: Comment, resourceType: ResourceType): MutableMap<String, Any?> {
        val node = linkedMapOf<String, Any?>(
            "id" to comment.id,
            "type" to resourceType.name,
            "author" to composeUserJson(resolveUser(comment.authorId)),
            "createdAt" to formatIsoDate(comment.createdDate),
            "body" to comment.contents
        )
        val attachments = attachmentRepository.findByContainerTypeAndContainerId(resourceType, comment.id.toString())
        if (attachments.isNotEmpty()) {
            node["attachments"] = attachments.map { composeAttachmentJson(it) }
        }
        return node
    }

    // yona ProjectApi.java 46-72 exports()의 toJson(attachments)(Attachment 엔티티 기본 Jackson
    // 직렬화) 대응 — yona Attachment 엔티티 필드가 legacy와 1:1로 대응해 같은 필드명을 그대로 쓴다.
    private fun composeAttachmentJson(attachment: Attachment): Map<String, Any?> {
        return mapOf(
            "id" to attachment.id,
            "name" to attachment.name,
            "hash" to attachment.hash,
            "containerType" to attachment.containerType.name,
            "mimeType" to attachment.mimeType,
            "size" to attachment.size,
            "containerId" to attachment.containerId,
            "createdDate" to attachment.createdDate?.let { formatIsoDate(it) },
            "ownerLoginId" to attachment.ownerLoginId
        )
    }

    private fun resolveUser(userId: Long?): User? {
        if (userId == null) return null
        return userRepository.findById(userId).orElse(null)
    }

    // yona utils/JodaDateUtil.java:16 ISO_FORMAT("yyyy-MM-dd'T'HH:mm:ssZ") 대응 — issue/posting/comment의
    // createdAt/updatedAt에 쓰인다. null이면 legacy도 빈 문자열을 반환한다(JodaDateUtil.getDateString).
    private fun formatIsoDate(instant: Instant?): String {
        if (instant == null) return ""
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(Date.from(instant))
    }

    // yona ProjectApi.java:322-325 getDateString()("yyyy-MM-dd a hh:mm:ss Z", Locale.ENGLISH) 대응 —
    // projectCreatedDate/dueDate(이슈+마일스톤)에 쓰인다. parseProjectCreatedDate()의 역함수와 동일 포맷.
    private fun formatProjectApiDate(instant: Instant?): String? {
        if (instant == null) return null
        return SimpleDateFormat("yyyy-MM-dd a hh:mm:ss Z", Locale.ENGLISH).format(Date.from(instant))
    }
}

// yona ProjectApi.java newProject()가 소비하는 JSON 스키마(exports()가 생산하는 것과 동일한 필드명)
// 대응 — projectName만 필수이고 나머지는 legacy와 동일하게 전부 선택값이다.
data class NewProjectApiRequest(
    val projectName: String,
    val projectDescription: String? = null,
    val projectVcs: String? = null,
    val projectCreatedDate: String? = null,
    val projectScope: String? = null,
    val members: List<NewProjectApiMember>? = null
)

data class NewProjectApiMember(
    val email: String,
    val role: String
)
