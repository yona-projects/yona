package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueCommentRepository
import com.github.yonaprojects.yona.domain.issue.IssueEventRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.issue.IssueLabelRepository
import com.github.yonaprojects.yona.domain.issue.IssueTimelineItem
import com.github.yonaprojects.yona.domain.milestone.MilestoneService
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.project.ProjectService
import com.github.yonaprojects.yona.domain.project.TitleHeadService
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.watch.WatchService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.ui.Model
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import com.github.yonaprojects.yona.domain.user.FavoriteIssueRepository
import com.github.yonaprojects.yona.domain.attachment.AttachmentRepository
import com.github.yonaprojects.yona.domain.attachment.AttachmentService
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import com.github.yonaprojects.yona.domain.project.RecentProjectRepository
import com.github.yonaprojects.yona.domain.user.User
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import com.github.yonaprojects.yona.domain.issue.IssueSpecification
import com.github.yonaprojects.yona.domain.issue.IssueService
import com.github.yonaprojects.yona.config.TemplateHelper
import com.github.yonaprojects.yona.domain.issue.IssueExcelService
import com.github.yonaprojects.yona.domain.issue.IssueComment
import com.github.yonaprojects.yona.domain.issue.RecentIssueService
import com.github.yonaprojects.yona.domain.role.RoleType
import org.springframework.http.HttpStatus

@Controller
class IssueViewController(
    private val projectRepository: ProjectRepository,
    private val projectService: ProjectService,
    private val issueRepository: IssueRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val userRepository: UserRepository,
    private val issueCommentRepository: IssueCommentRepository,
    private val watchService: WatchService,
    private val milestoneService: MilestoneService,
    private val issueLabelRepository: IssueLabelRepository,
    private val favoriteIssueRepository: FavoriteIssueRepository,
    private val attachmentRepository: AttachmentRepository,
    private val messageSource: MessageSource,
    private val recentProjectRepository: RecentProjectRepository,
    private val issueService: IssueService,
    private val templateHelper: TemplateHelper,
    private val issueExcelService: IssueExcelService,
    private val repositoryService: RepositoryService,
    private val recentIssueService: RecentIssueService,
    private val accessControl: AccessControl,
    private val titleHeadService: TitleHeadService,
    private val issueEventRepository: IssueEventRepository,
    private val attachmentService: AttachmentService
) {

    @GetMapping("/{owner}/{projectName}/issues")
    fun listIssues(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam(required = false, defaultValue = "OPEN") state: State,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false) pageNum: Int?,
        @RequestParam(required = false) filter: String?,
        @RequestParam(required = false) authorId: Long?,
        @RequestParam(required = false) assigneeId: Long?,
        @RequestParam(required = false) milestoneId: Long?,
        @RequestParam(required = false) commenterId: Long?,
        @RequestParam(required = false) labelIds: List<Long>?,
        @RequestParam(required = false) dueDate: String?,
        @RequestParam(required = false) format: String?,
        @RequestParam(required = false, defaultValue = "createdDate") orderBy: String,
        @RequestParam(required = false, defaultValue = "desc") orderDir: String,
        @RequestParam(required = false, defaultValue = "15") itemsPerPage: Int,
        authentication: Authentication?,
        model: Model
    ): Any {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: run {
                model.addAttribute("messageKey", "error.forbidden.or.notfound")
                return "error/404"
            }

        // 권한 체크
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (!accessControl.isAllowedToReadProject(loginUser, project)) {
            // yona error/forbidden.scala.html 대응 — 프로젝트는 이미 찾았으므로
            // 프로젝트 헤더/메뉴가 붙는 컨텍스트 인지형 403으로 교체.
            model.addAttribute("project", project)
            model.addAttribute("messageKey", "error.forbidden.or.notfound")
            return "error/forbidden"
        }

        val actualPage = if (pageNum != null) {
            if (pageNum > 0) pageNum - 1 else 0
        } else {
            page
        }

        val sort = if (orderDir.equals("asc", ignoreCase = true)) {
            Sort.by(Sort.Direction.ASC, orderBy)
        } else {
            Sort.by(Sort.Direction.DESC, orderBy)
        }
        // yona IssueApp.getItemsPerPage() 대응 — 요청값이 45를 넘으면 clamp.
        val pageable = PageRequest.of(actualPage, minOf(itemsPerPage, ITEMS_PER_PAGE_MAX), sort)

        // Specification 생성 및 필터 적용
        val spec = IssueSpecification.filterIssues(
            project = project,
            state = state,
            filter = filter,
            authorId = authorId,
            assigneeId = assigneeId,
            milestoneId = milestoneId,
            commenterId = commenterId,
            labelIds = labelIds,
            dueDate = dueDate
        )

        if (format == "xls") {
            val allIssues = issueRepository.findAll(spec)
            val excelData = issueExcelService.excelFrom(allIssues)

            val zoneId = ZoneId.systemDefault()
            val dateStr = DateTimeFormatter.ofPattern("yyyyMMddHHmm")
                .withZone(zoneId)
                .format(Instant.now())
            val filename = "${project.name}_issues_${dateStr}.xls"
            val encodedFilename = URLEncoder.encode(filename, "UTF-8").replace("+", "%20")

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/vnd.ms-excel")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''$encodedFilename")
                .body(excelData)
        }

        val issuePage = issueRepository.findAll(spec, pageable)

        val openIssuesCount = issueRepository.countByProjectAndState(project, State.OPEN)
        val closedIssuesCount = issueRepository.countByProjectAndState(project, State.CLOSED)

        // 퀵링크 카운트 조회
        var assignedToMeCount = 0L
        var authoredByMeCount = 0L
        var commentedByMeCount = 0L

        if (loginUser != null) {
            assignedToMeCount = issueRepository.count(IssueSpecification.filterIssues(
                project = project,
                state = state,
                filter = null,
                authorId = null,
                assigneeId = loginUser.id,
                milestoneId = null,
                commenterId = null,
                labelIds = null,
                dueDate = null
            ))
            authoredByMeCount = issueRepository.count(IssueSpecification.filterIssues(
                project = project,
                state = state,
                filter = null,
                authorId = loginUser.id,
                assigneeId = null,
                milestoneId = null,
                commenterId = null,
                labelIds = null,
                dueDate = null
            ))
            commentedByMeCount = issueRepository.count(IssueSpecification.filterIssues(
                project = project,
                state = state,
                filter = null,
                authorId = null,
                assigneeId = null,
                milestoneId = null,
                commenterId = loginUser.id,
                labelIds = null,
                dueDate = null
            ))
        }

        // 마일스톤 및 멤버 목록 (어드밴스드 검색 폼용)
        val milestones = milestoneService.getMilestones(project.id!!, State.OPEN)
        val closedMilestones = milestoneService.getMilestones(project.id!!, State.CLOSED)
        val projectUsers = projectUserRepository.findByProjectId(project.id!!)
        val members = projectUsers.map { it.user }
        val labels = issueLabelRepository.findByProject(project)

        // yona partial_list_wrap.scala.html "currentPage.getPageIndex==0 && !param.hasCondition
        // && !param.state.equals(CLOSED)" 대응. 검색/필터 조건이 전혀 없는 목록 첫 페이지
        // (닫힌 이슈 탭 제외)에서만, 로그인한 작성자 본인의 초안(State.DRAFT) 이슈를 최상단에 노출한다.
        // legacy SearchCondition.hasCondition()은 assigneeId/authorId/mentionId/commenterId/sharerId/
        // favoriteId만 검사한다 — filter(텍스트 검색)/milestoneId/labelIds/dueDate는 포함되지 않는다
        // (예: 마일스톤이나 라벨로만 필터링해도 초안 영역은 그대로 보인다). yona는 mentionId/sharerId/
        // favoriteId 파라미터를 아직 지원하지 않아 그 부분은 항상 false로 취급한다.
        val hasCondition = authorId != null || assigneeId != null || commenterId != null
        val draftIssues = if (loginUser != null && actualPage == 0 && !hasCondition && state != State.CLOSED) {
            issueRepository.findByProjectAndAuthorLoginIdAndIsDraftTrueOrderByNumberDesc(project, loginUser.loginId!!)
        } else {
            emptyList()
        }
        model.addAttribute("draftIssues", draftIssues)

        model.addAttribute("project", project)
        model.addAttribute("issuePage", issuePage)
        model.addAttribute("state", state)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("filter", filter)
        model.addAttribute("orderBy", orderBy)
        model.addAttribute("orderDir", orderDir)
        model.addAttribute("openIssuesCount", openIssuesCount)
        model.addAttribute("closedIssuesCount", closedIssuesCount)

        model.addAttribute("authorId", authorId)
        model.addAttribute("assigneeId", assigneeId)
        model.addAttribute("milestoneId", milestoneId)
        model.addAttribute("commenterId", commenterId)
        model.addAttribute("labelIds", labelIds)
        model.addAttribute("dueDate", dueDate)

        model.addAttribute("assignedToMeCount", assignedToMeCount)
        model.addAttribute("authoredByMeCount", authoredByMeCount)
        model.addAttribute("commentedByMeCount", commentedByMeCount)

        model.addAttribute("milestones", milestones)
        model.addAttribute("closedMilestones", closedMilestones)
        model.addAttribute("members", members)
        model.addAttribute("labels", labels)
        model.addAttribute("templateHelper", templateHelper)

        return "issue/list"
    }

    @GetMapping("/{owner}/{projectName}/issue/{number}")
    fun viewIssue(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable number: Long,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: run {
                model.addAttribute("messageKey", "error.forbidden.or.notfound")
                return "error/404"
            }

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (!accessControl.isAllowedToReadProject(loginUser, project)) {
            // yona error/forbidden.scala.html 대응.
            model.addAttribute("project", project)
            model.addAttribute("messageKey", "error.forbidden.or.notfound")
            return "error/forbidden"
        }

        val issue = issueRepository.findByProjectAndNumber(project, number) ?: run {
            // yona error/notfound.scala.html 대응 — 프로젝트는 찾았지만 그 안의
            // 이슈를 찾지 못한 경우이므로 컨텍스트 인지형 404(targetType="issue_post")로 교체.
            model.addAttribute("project", project)
            model.addAttribute("targetType", "issue_post")
            return "error/notfound"
        }
        val comments = issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(issue.id!!)

        if (loginUser != null) {
            try {
                recentIssueService.recordIssueVisit(loginUser, issue)
            } catch (e: Exception) {
                // NOOP: 방문 이력 기록 실패가 이슈 조회 자체를 막지 않아야 한다
            }
        }

        val isWatching = loginUser?.let {
            watchService.isWatching(it, ResourceType.ISSUE_POST, issue.id.toString())
        } ?: false

        val isWatchingProject = loginUser?.let {
            watchService.isWatching(it, ResourceType.PROJECT, project.id.toString())
        } ?: false

        val isFavoriteIssue = loginUser?.let {
            favoriteIssueRepository.findByUserIdAndIssueId(it.id!!, issue.id!!).isPresent
        } ?: false

        val isAllowedUpdate = accessControl.isAllowedToUpdateIssue(loginUser, project, issue.authorLoginId)

        val attachments = attachmentRepository.findByContainerTypeAndContainerId(ResourceType.ISSUE_POST, issue.id.toString())
        val attachmentsJson = attachments.joinToString(prefix = "{\"attachments\":[", postfix = "]}", separator = ",") { attach ->
            val id = attach.id?.toString() ?: ""
            val mimeType = attach.mimeType ?: ""
            val name = attach.name.replace("\"", "\\\"").replace("\n", "\\n")
            val url = "/files/${attach.id}"
            val size = attach.size?.toString() ?: "0"
            """{"id":"$id","mimeType":"$mimeType","name":"$name","url":"$url","size":$size}"""
        }

        buildTimelineModel(project, issue, comments, loginUser, model)

        // yona issue/view.scala.html milestone dl 대응 — 인라인 마일스톤 수정
        // select2 위젯의 open/closed optgroup용.
        val openMilestones = milestoneService.getMilestones(project.id!!, State.OPEN)
        val closedMilestonesForIssue = milestoneService.getMilestones(project.id!!, State.CLOSED)

        model.addAttribute("project", project)
        model.addAttribute("issue", issue)
        model.addAttribute("comments", comments)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("isWatching", isWatching)
        model.addAttribute("isWatchingProject", isWatchingProject)
        model.addAttribute("isFavoriteIssue", isFavoriteIssue)
        model.addAttribute("isAllowedUpdate", isAllowedUpdate)
        model.addAttribute("attachmentsJson", attachmentsJson)
        model.addAttribute("openMilestones", openMilestones)
        model.addAttribute("closedMilestones", closedMilestonesForIssue)

        return "issue/view"
    }

    // yona IssueApp.timeline() 대응 — massUpdate로 담당자/마일스톤/마감일을 저장한 뒤
    // yobi.issue.View.js의 _updateTimeline()이 AJAX로 다시 불러오는 타임라인 조각. issue/view.html의
    // th:fragment="timelineItems"(.timeline-list) 한 곳만 다시 렌더링해 돌려준다 — viewIssue()와
    // 동일한 모델 조립 로직(buildTimelineModel)을 공유해 두 진입점이 어긋나지 않게 한다.
    @GetMapping("/{owner}/{projectName}/issue/{number}/timeline")
    fun timeline(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable number: Long,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"
        val issue = issueRepository.findByProjectAndNumber(project, number) ?: return "error/404"
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (!accessControl.isAllowedToReadProject(loginUser, project)) {
            return "error/403"
        }

        val comments = issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(issue.id!!)
        buildTimelineModel(project, issue, comments, loginUser, model)
        model.addAttribute("project", project)
        model.addAttribute("issue", issue)
        model.addAttribute("currentUser", loginUser)

        return "issue/view :: timelineItems"
    }

    // viewIssue()/timeline() 공유 모델 조립 — yona Issue.getTimeline() 대응.
    private fun buildTimelineModel(
        project: Project,
        issue: Issue,
        comments: List<IssueComment>,
        loginUser: User?,
        model: Model
    ) {
        val events = issueEventRepository.findByIssueOrderByCreatedAsc(issue)
            .filter { it.eventType != EventType.ISSUE_BODY_CHANGED }
        // legacy issue/partial_comment.scala.html/common.childComments() 대응 — 대댓글(parentComment != null)은
        // 최상위 타임라인에 별도 항목으로 나타나지 않고
        // 부모 댓글 아래 common/childComments 조각에서만 렌더링된다.
        val topLevelComments = comments.filter { it.parentComment == null }
        val childCommentsByParentId: Map<Long, List<IssueComment>> =
            comments.filter { it.parentComment != null }
                .groupBy { it.parentComment!!.id!! }
        val timeline = (
            topLevelComments.map { IssueTimelineItem(kind = "COMMENT", date = it.createdDate ?: Instant.EPOCH, comment = it) } +
                events.map { IssueTimelineItem(kind = "EVENT", date = it.created, event = it) }
            ).sortedBy { it.date }

        // legacy issue/partial_comment.scala.html의 isAllowed(..., Operation.DELETE) 대응 —
        // 매니저는 남의 댓글도 삭제할 수 있다(CommentController의 실제 권한 체크와 동일 기준).
        val isProjectManager = loginUser != null && projectUserRepository.findByProjectIdAndUserId(project.id!!, loginUser.id!!)
            .map { it.role.id == RoleType.MANAGER.roleType }
            .orElse(false)

        model.addAttribute("childCommentsByParentId", childCommentsByParentId)
        model.addAttribute("isProjectManager", isProjectManager)
        model.addAttribute("commentApiBase", "/api/projects/${project.id}/issues/${issue.number}/comments")
        model.addAttribute("timeline", timeline)
        // P3-50: 댓글 수정 폼(common/commentUpdateForm)이 "이미 첨부된 파일" 목록을 보여주려면
        // 댓글별 첨부파일 목록이 필요하다 — findByContainerTypeAndContainerId 자체가
        // @Cacheable(컨테이너 단위)이라 댓글마다 개별 호출해도 ProjectApiController의
        // composeCommentNode()와 동일한 패턴(N+1이지만 캐시로 상쇄)을 재사용한다.
        model.addAttribute(
            "commentAttachmentsByCommentId",
            comments.associate { comment ->
                comment.id!! to attachmentRepository.findByContainerTypeAndContainerId(
                    ResourceType.ISSUE_COMMENT, comment.id.toString()
                )
            }
        )
    }

    @GetMapping("/{owner}/{projectName}/issueform")
    fun createIssueForm(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam(required = false) parentIssueId: Long?,
        @RequestParam(required = false, defaultValue = "false") isFromGlobalMenuNew: Boolean,
        @RequestParam(required = false) bodyText: String? = null,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: run {
                model.addAttribute("messageKey", "error.forbidden.or.notfound")
                return "error/404"
            }

        val currentAuth = authentication ?: SecurityContextHolder.getContext().authentication
        val loginUser = currentAuth?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (!accessControl.isProjectResourceCreatable(loginUser, project, ResourceType.ISSUE_POST)) {
            // yona error/forbidden.scala.html 대응.
            model.addAttribute("project", project)
            model.addAttribute("messageKey", "error.forbidden.or.notfound")
            return "error/forbidden"
        }

        // 마일스톤 목록 가져오기
        val milestones = milestoneService.getMilestones(project.id!!, State.OPEN)

        // 프로젝트의 멤버 목록 가져오기
        val projectUsers = projectUserRepository.findByProjectId(project.id!!)
        val members = projectUsers.map { it.user }

        // 라벨 목록 가져오기
        val labels = issueLabelRepository.findByProject(project)
        val labelMap = labels.groupBy { it.category }

        // 1. 하위 태스크용 프로젝트 목록
        val movableProjects = projectUserRepository.findByUserId(loginUser!!.id!!).map { it.project }

        // 2. 부모 이슈 후보군 — yona Issue.findParentIssueByProject(project, "", 300) 대응.
        // 상태 무관(오픈/클로즈 모두 포함) 부모 없는 이슈를 최신순 최대 300건까지 후보로 노출한다.
        val parentCandidates = issueRepository.findByProjectAndParentIsNullOrderByCreatedDateDesc(
            project, PageRequest.of(0, PARENT_CANDIDATE_LIMIT)
        )

        // 3. 기존의 부모 이슈 정보
        val parentIssue = parentIssueId?.let { id ->
            issueRepository.findById(id).orElse(null)
        }

        val issueTemplate = bodyText ?: getIssueTemplate(project)
        model.addAttribute("issueTemplate", issueTemplate)

        model.addAttribute("project", project)
        model.addAttribute("milestones", milestones)
        model.addAttribute("members", members)
        model.addAttribute("labels", labels)
        model.addAttribute("labelMap", labelMap)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("movableProjects", movableProjects)
        model.addAttribute("parentCandidates", parentCandidates)
        model.addAttribute("parentIssue", parentIssue)
        model.addAttribute("isFromGlobalMenuNew", isFromGlobalMenuNew)

        return "issue/create"
    }

    @GetMapping("/{owner}/{projectName}/issue/{number}/editform")
    fun editIssueForm(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable number: Long,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: run {
                model.addAttribute("messageKey", "error.forbidden.or.notfound")
                return "error/404"
            }

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        val issue = issueRepository.findByProjectAndNumber(project, number) ?: run {
            model.addAttribute("project", project)
            model.addAttribute("targetType", "issue_post")
            return "error/notfound"
        }
        if (!accessControl.isAllowedToUpdateIssue(loginUser, project, issue.authorLoginId)) {
            model.addAttribute("project", project)
            model.addAttribute("messageKey", "error.forbidden.or.notfound")
            return "error/forbidden"
        }

        // 마일스톤 목록 가져오기
        val milestones = milestoneService.getMilestones(project.id!!, State.OPEN)

        // 프로젝트의 멤버 목록 가져오기
        val projectUsers = projectUserRepository.findByProjectId(project.id!!)
        val members = projectUsers.map { it.user }

        // 라벨 목록 가져오기
        val labels = issueLabelRepository.findByProject(project)
        val labelMap = labels.groupBy { it.category }

        // 1. 하위 태스크용 프로젝트 목록
        val movableProjects = projectUserRepository.findByUserId(loginUser!!.id!!).map { it.project }

        // 2. 부모 이슈 후보군 — yona Issue.findParentIssueByProject(project, "", 300) 대응.
        // 상태 무관 부모 없는 이슈를 최신순 최대 300건까지, 자기 자신은 제외하고 후보로 노출한다.
        val parentCandidates = issueRepository.findByProjectAndParentIsNullOrderByCreatedDateDesc(
            project, PageRequest.of(0, PARENT_CANDIDATE_LIMIT)
        ).filter { it.id != issue.id }

        // yona partial_select_subtask.scala.html hasChildIssue 대응 — 이 이슈가 이미
        // 하위이슈를 갖고 있으면(=이미 부모 이슈) 다른 이슈의 하위이슈로 만들 수 없다.
        val hasChildIssue = issueRepository.countByParentId(issue.id!!) > 0

        val attachments = attachmentRepository.findByContainerTypeAndContainerId(ResourceType.ISSUE_POST, issue.id.toString())
        val attachmentsJson = attachments.joinToString(prefix = "{\"attachments\":[", postfix = "]}", separator = ",") { attach ->
            val id = attach.id?.toString() ?: ""
            val mimeType = attach.mimeType ?: ""
            val name = attach.name.replace("\"", "\\\"").replace("\n", "\\n")
            val url = "/files/${attach.id}"
            val size = attach.size?.toString() ?: "0"
            """{"id":"$id","mimeType":"$mimeType","name":"$name","url":"$url","size":$size}"""
        }

        model.addAttribute("project", project)
        model.addAttribute("issue", issue)
        model.addAttribute("milestones", milestones)
        model.addAttribute("members", members)
        model.addAttribute("labels", labels)
        model.addAttribute("labelMap", labelMap)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("movableProjects", movableProjects)
        model.addAttribute("parentCandidates", parentCandidates)
        model.addAttribute("parentIssue", issue.parent)
        model.addAttribute("hasChildIssue", hasChildIssue)
        model.addAttribute("attachmentsJson", attachmentsJson)

        return "issue/edit"
    }

    @PostMapping("/{owner}/{projectName}/issues")
    fun createIssue(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam title: String,
        @RequestParam body: String,
        @RequestParam(required = false) parentIssueId: Long?,
        @RequestParam(required = false) targetProjectId: Long?,
        @RequestParam(required = false) assigneeLoginId: String?,
        @RequestParam(required = false) milestoneId: Long?,
        @RequestParam(required = false) dueDate: String?,
        @RequestParam(required = false) labelIds: List<Long>?,
        @RequestParam(required = false, defaultValue = "false") isDraft: Boolean,
        @RequestParam(required = false) temporaryUploadFiles: String?,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "redirect:/users/loginform"

        if (!accessControl.isProjectResourceCreatable(loginUser, project, ResourceType.ISSUE_POST)) {
            // yona error/forbidden.scala.html 대응.
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        val issue = Issue(
            title = title,
            body = body,
            project = project
        )
        issue.isDraft = isDraft
        issue.state = if (isDraft) State.DRAFT else State.OPEN

        if (parentIssueId != null) {
            val parentIssue = issueRepository.findById(parentIssueId).orElse(null)
            issue.parent = parentIssue
        }

        if (!dueDate.isNullOrEmpty()) {
            try {
                val localDate = LocalDate.parse(dueDate)
                val zone = ZoneId.systemDefault()
                val instant = localDate.atTime(23, 59, 59).atZone(zone).toInstant()
                issue.dueDate = instant
            } catch (e: Exception) {}
        }

        val assigneeUser = assigneeLoginId?.let { userRepository.findByLoginId(it).orElse(null) }

        val saved = issueService.createIssue(
            issue = issue,
            author = loginUser,
            assigneeUser = assigneeUser,
            milestoneId = milestoneId,
            labelIds = labelIds
        )

        if (!temporaryUploadFiles.isNullOrBlank()) {
            val fileIds = temporaryUploadFiles.split(",").mapNotNull { it.trim().toLongOrNull() }
            // yona Attachment.moveOnlySelected() 대응 — 소유권 검증 없이 요청받은 ID를
            // 그대로 재배선하지 않고, 실제로 이 로그인 사용자가 업로드한 임시 첨부만 옮긴다.
            attachmentService.moveOnlySelected(
                fromType = ResourceType.NOT_A_RESOURCE,
                fromId = "",
                toType = ResourceType.ISSUE_POST,
                toId = saved.id.toString(),
                selectedIds = fileIds,
                moverLoginId = loginUser.loginId ?: ""
            )
        }

        return "redirect:/${owner.encodePathSegment()}/${projectName.encodePathSegment()}/issue/${saved.number}"
    }

    @GetMapping("/user/issues/new")
    fun newDirectIssueForm(
        @RequestParam(required = false, defaultValue = "-1") commentId: Long,
        authentication: Authentication?,
        model: Model
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "redirect:/users/loginform"

        val recentList = recentProjectRepository.findByUserIdOrderByVisitedDateDesc(loginUser.id!!)
        var project = recentList.firstOrNull()?.let {
            projectRepository.findById(it.projectId).orElse(null)
        }

        if (project == null) {
            val projectUsers = projectUserRepository.findByUserId(loginUser.id!!)
            val allUserProjects = projectUsers.map { it.project }
            project = allUserProjects.sortedByDescending { it.createdDate }.firstOrNull()
        }

        var bodyText: String? = null
        if (project != null && commentId != -1L) {
            val comment = issueCommentRepository.findById(commentId).orElse(null)
            if (comment != null) {
                bodyText = comment.contents + "\n\n_Originally posted by @" + comment.authorLoginId + " in " +
                        "/${project.owner}/${project.name}/issue/${comment.issue.number}#comment-${comment.id}_"
            }
        }

        return if (project != null) {
            createIssueForm(
                owner = project.owner!!,
                projectName = project.name!!,
                parentIssueId = null,
                isFromGlobalMenuNew = true,
                bodyText = bodyText,
                authentication = authentication,
                model = model
            )
        } else {
            val locale = LocaleContextHolder.getLocale()
            val msg = messageSource.getMessage("project.is.empty", null, "프로젝트가 존재하지 않습니다.", locale)
            model.addAttribute("warning", msg)
            "redirect:/"
        }
    }

    @GetMapping("/user/issues/new/mine")
    fun newDirectMyIssueForm(
        authentication: Authentication?,
        model: Model
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "redirect:/users/loginform"

        var project = projectRepository.findByOwnerAndName(loginUser.loginId!!, "inbox").orElse(null)
        if (project == null) {
            project = projectRepository.findByOwnerAndName(loginUser.loginId!!, "_private").orElse(null)
        }

        if (project == null) {
            val myProjects = projectRepository.findByOwner(loginUser.loginId!!)
            val privateProjects = myProjects.filter { it.projectScope == ProjectScope.PRIVATE }
            project = privateProjects.sortedByDescending { it.createdDate }.firstOrNull()
        }

        if (project == null) {
            val myProjects = projectRepository.findByOwner(loginUser.loginId!!)
            val publicProjects = myProjects.filter { it.projectScope == ProjectScope.PUBLIC }
            project = publicProjects.sortedByDescending { it.createdDate }.firstOrNull()
        }

        return if (project != null) {
            createIssueForm(
                owner = project.owner!!,
                projectName = project.name!!,
                parentIssueId = null,
                isFromGlobalMenuNew = true,
                authentication = authentication,
                model = model
            )
        } else {
            "redirect:/"
        }
    }

    // yona IssueApp.massUpdate()의 Accept 헤더 콘텐츠 협상 대응. 이슈 목록의 체크박스
    // 일괄수정(폼 submit, text/html)과 issue/view.html 상세화면의 인라인 담당자/마일스톤/마감일 위젯
    // (yobi.issue.View.js의 $.ajax(dataType:"json"))이 같은 엔드포인트를 공유한다 — legacy와 동일하게
    // JSON을 원하는 요청에는 redirect 대신 JSON 바디로 응답한다.
    @PostMapping("/{owner}/{projectName}/issues/massupdate")
    @Transactional
    fun massUpdate(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @ModelAttribute form: IssueMassUpdateForm,
        authentication: Authentication?,
        @RequestParam(required = false, defaultValue = "false") delete: Boolean,
        @RequestParam(required = false, defaultValue = "false") isDueDateChanged: Boolean,
        @RequestParam(required = false) dueDate: String?,
        @RequestHeader(value = "Accept", required = false) accept: String? = null,
        model: Model
    ): Any {
        val wantsJson = accept?.contains("application/json") == true
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            // "redirect:/error/404"·"redirect:/error/403"는 실제로 매핑된 라우트가 없어 Spring의
            // 기본 404/403으로 빠지던 버그였다 — 다른 메서드들과 동일하게 뷰 이름을 직접 리턴(비JSON
            // 경로)하도록 정정. JSON을 원하는 요청(wantsJson)은 계속 상태코드만 반환한다.
            ?: return if (wantsJson) ResponseEntity.notFound().build<Any>() else "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (loginUser == null) {
            if (wantsJson) return ResponseEntity.status(HttpStatus.FORBIDDEN).build<Any>()
            // yona error/forbidden.scala.html 대응 — 프로젝트는 이미 찾았으므로
            // 컨텍스트 인지형 403. 멤버십 게이트(isMemberOf)는 legacy massUpdate()와
            // 동일하게 이슈 단위 권한 체크로 대체돼 여기서는 로그인 여부만 확인한다(주석 아래 참고).
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        // yona IssueApp.massUpdate() 대응 — legacy는 프로젝트 멤버십을 통째로 게이트하지 않고
        // 이슈 1건씩 AccessControl.isAllowed(user, issue, Operation.UPDATE)로 권한을 확인해
        // updatedItems/rejectedByPermission을 집계한 뒤, 아무것도 갱신하지 못하고 권한 거부만
        // 있었을 때에만 403을 반환한다(updatedItems==0 && rejectedByPermission>0). 이전 구현은
        // 여기서 loginUser.isMemberOf(project)로 선제 차단했는데, User.isMemberOf()는 User
        // 엔티티에 매핑된(mappedBy="user") 지연 컬렉션 projectUsers를 참조해 같은 트랜잭션 안에서
        // User가 먼저 로드된 뒤 ProjectUser가 별도로 저장되면 스냅샷이 갱신되지 않아 실제로는
        // 멤버인데도 false가 되는 문제도 있었다(massUpdate가 403을 반환하는 원인이었다). 이제는
        // legacy처럼 이슈 단위 권한 체크만 쓴다.
        var firstUpdatedIssue: Issue? = null
        var updatedItems = 0
        var rejectedByPermission = 0
        val issueIds = form.issues.mapNotNull { it.id }
        if (issueIds.isNotEmpty()) {
            val issuesToUpdate = issueRepository.findAllById(issueIds)
            for (issue in issuesToUpdate) {
                if (issue.project.id != project.id) continue
                if (issue.isDraft) continue

                // 1. 삭제
                if (delete) {
                    if (accessControl.isAllowedToUpdateIssue(loginUser, project, issue.authorLoginId)) {
                        // yona Project.delete() 이슈 삭제 대응 — 댓글/이벤트/즐겨찾기/첨부파일/
                        // 타이틀헤드까지 함께 정리하는 IssueServiceImpl.deleteIssueCascade() 재사용.
                        issueService.deleteIssueCascade(issue)
                        updatedItems++
                    } else {
                        rejectedByPermission++
                    }
                    continue
                }

                if (!accessControl.isAllowedToUpdateIssue(loginUser, project, issue.authorLoginId)) {
                    rejectedByPermission++
                    continue
                }
                updatedItems++

                // 2. 상태 변경
                if (!form.state.isNullOrEmpty()) {
                    try {
                        val newState = State.valueOf(form.state!!.uppercase())
                        issueService.changeState(issue.id!!, newState, loginUser.loginId)
                    } catch (e: Exception) {}
                }

                // 3. 담당자 변경
                if (form.assignee != null) {
                    val assigneeUserId = form.assignee?.id
                    if (assigneeUserId == null || assigneeUserId == -1L) {
                        issueService.changeAssignee(issue.id!!, null, loginUser.loginId)
                    } else {
                        val assigneeUser = userRepository.findById(assigneeUserId).orElse(null)
                        if (assigneeUser != null) {
                            issueService.changeAssignee(issue.id!!, assigneeUser, loginUser.loginId)
                        }
                    }
                }

                // 4. 마일스톤 변경
                if (form.milestone != null) {
                    val milestoneId = form.milestone?.id
                    if (milestoneId == null || milestoneId == -1L) {
                        issueService.changeMilestone(issue.id!!, null, loginUser.loginId)
                    } else {
                        issueService.changeMilestone(issue.id!!, milestoneId, loginUser.loginId)
                    }
                }

                // 5. 라벨 추가/삭제
                var labelsChanged = false
                if (form.attachingLabelIds.isNotEmpty()) {
                    val labelsToAdd = issueLabelRepository.findAllById(form.attachingLabelIds)
                    issue.labels.addAll(labelsToAdd)
                    labelsChanged = true
                }
                if (form.detachingLabelIds.isNotEmpty()) {
                    val labelsToRemove = issueLabelRepository.findAllById(form.detachingLabelIds)
                    issue.labels.removeAll(labelsToRemove.toSet())
                    labelsChanged = true
                }

                // 6. 마감일 변경
                if (isDueDateChanged) {
                    if (dueDate.isNullOrEmpty()) {
                        issue.dueDate = null
                    } else {
                        try {
                            val localDate = LocalDate.parse(dueDate)
                            val zone = ZoneId.systemDefault()
                            val instant = localDate.atTime(23, 59, 59).atZone(zone).toInstant()
                            issue.dueDate = instant
                        } catch (e: Exception) {}
                    }
                    labelsChanged = true // 혹은 마감일 저장용 트리거
                }

                if (labelsChanged) {
                    issue.updatedDate = Instant.now()
                    issueRepository.save(issue)
                }
            }
            // yona IssueApp.massUpdate() "issueMassUpdate.issues.get(0)" 대응 — JSON 응답의 마감일
            // 메시지는 폼에 담긴 첫 번째 이슈 기준으로 계산한다(우리 위젯은 항상 이슈 1건만 보낸다).
            firstUpdatedIssue = issuesToUpdate.firstOrNull { it.project.id == project.id }
        }

        // yona IssueApp.massUpdate() "if (updatedItems == 0 && rejectedByPermission > 0) return forbidden(...)" 대응.
        if (updatedItems == 0 && rejectedByPermission > 0) {
            return if (wantsJson) ResponseEntity.status(HttpStatus.FORBIDDEN).build<Any>() else "redirect:/error/403"
        }

        if (wantsJson) {
            if (isDueDateChanged && firstUpdatedIssue != null) {
                val fresh = issueRepository.findById(firstUpdatedIssue!!.id!!).orElse(firstUpdatedIssue!!)
                val isOverDue = templateHelper.isOverDueDate(fresh)
                val dueDateMsg = if (isOverDue) {
                    messageSource.getMessage("issue.dueDate.overdue", null, LocaleContextHolder.getLocale())
                } else {
                    templateHelper.until(fresh)
                }
                return ResponseEntity.ok(mapOf("isOverDue" to isOverDue, "dueDateMsg" to dueDateMsg))
            }
            return ResponseEntity.ok(emptyMap<String, Any>())
        }

        return "redirect:/${owner.encodePathSegment()}/${projectName.encodePathSegment()}/issues"
    }

    private fun getIssueTemplate(project: Project): String {
        return try {
            val bytes = repositoryService.getRepository(project).getRawFile("HEAD", "ISSUE_TEMPLATE.md")
            if (bytes != null) String(bytes, StandardCharsets.UTF_8) else ""
        } catch (e: Exception) {
            ""
        }
    }

    @PostMapping(value = ["/{owner}/{projectName}/issue/{number}/editform", "/{owner}/{projectName}/issue/{number}/edit"])
    fun editIssue(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable number: Long,
        @ModelAttribute request: IssueForm,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: run {
                // yona error/forbidden.scala.html 대응.
                model.addAttribute("project", project)
                return "error/forbidden"
            }

        val issue = issueRepository.findByProjectAndNumber(project, number)
            ?: run {
                model.addAttribute("project", project)
                model.addAttribute("targetType", "issue_post")
                return "error/notfound"
            }

        if (issue.authorLoginId != loginUser.loginId &&
            !projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) &&
            !accessControl.isAllowedIfGroupMember(project, loginUser)
        ) {
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        // yona editIssue()의 hasTargetProject()/isRequestedToOtherProject()/moveIssueToOtherProject()
        // 대응 — issue/edit.html의 targetProjectId select(다른 프로젝트로 이동)가 이 필드가 없어 실제로는
        // 죽은 UI였다. moveIssue()는 이미 legacy와 동일하게 구현돼 있었으나
        // 아무 데서도 호출되지 않고 있었음 — 여기서 배선한다.
        var redirectProject = project
        val requestedTargetProjectId = request.targetProjectId
        if (requestedTargetProjectId != null && requestedTargetProjectId != project.id) {
            val targetProject = projectRepository.findById(requestedTargetProjectId).orElse(null)
                ?: run {
                    model.addAttribute("project", project)
                    return "error/notfound"
                }
            if (!accessControl.isProjectResourceCreatable(loginUser, targetProject, ResourceType.ISSUE_POST)) {
                model.addAttribute("project", targetProject)
                return "error/forbidden"
            }
            issueService.moveIssue(issue.id!!, targetProject.id!!, loginUser)
            redirectProject = targetProject
        }

        val assigneeUser = request.assigneeLoginId?.let {
            if (it.isNotBlank()) userRepository.findByLoginId(it).orElse(null) else null
        }

        issue.title = request.title
        issue.body = request.body ?: ""

        if (!request.dueDate.isNullOrBlank()) {
            try {
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                val localDate = LocalDate.parse(request.dueDate, formatter)
                issue.dueDate = ZonedDateTime.of(localDate, LocalTime.MIDNIGHT, ZoneId.systemDefault()).toInstant()
            } catch (e: Exception) {
                // ignore
            }
        } else {
            issue.dueDate = null
        }

        val parentIssue = request.parentIssueId?.let { issueRepository.findById(it).orElse(null) }
        issue.parent = parentIssue

        val updated = issueService.updateIssue(
            issueId = issue.id!!,
            title = request.title,
            body = request.body ?: "",
            updater = loginUser,
            assigneeUser = assigneeUser,
            milestoneId = request.milestoneId,
            labelIds = request.labelIds
        )

        return "redirect:/${redirectProject.owner!!.encodePathSegment()}/${redirectProject.name.encodePathSegment()}/issue/${updated.number}"
    }

    companion object {
        // yona IssueApp.ITEMS_PER_PAGE_MAX 대응.
        private const val ITEMS_PER_PAGE_MAX = 45
        // yona Issue.findParentIssueByProject(project, "", 300) 대응.
        private const val PARENT_CANDIDATE_LIMIT = 300
    }
}

class IssueMassUpdateForm {
    var issues: List<IssueIdForm> = mutableListOf()
    var state: String? = null
    var assignee: AssigneeIdForm? = null
    var milestone: MilestoneIdForm? = null
    var attachingLabelIds: List<Long> = mutableListOf()
    var detachingLabelIds: List<Long> = mutableListOf()
}

class IssueIdForm {
    var id: Long? = null
}

class AssigneeIdForm {
    var id: Long? = null
}

class MilestoneIdForm {
    var id: Long? = null
}

data class IssueForm(
    var title: String = "",
    var body: String? = "",
    var assigneeLoginId: String? = null,
    var milestoneId: Long? = null,
    var dueDate: String? = null,
    var labelIds: List<Long>? = null,
    var parentIssueId: Long? = null,
    // yona Issue.targetProjectId(transient 폼 필드) 대응.
    var targetProjectId: Long? = null
)