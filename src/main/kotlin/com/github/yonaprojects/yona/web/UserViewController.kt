package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.watch.WatchRepository
import com.github.yonaprojects.yona.domain.notification.UserProjectNotificationRepository
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.RecentIssueService
import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.mention.MentionService
import com.github.yonaprojects.yona.domain.apitoken.ApiTokenPermission
import com.github.yonaprojects.yona.domain.apitoken.ApiTokenScopeGroup
import com.github.yonaprojects.yona.domain.apitoken.ApiTokenService
import com.github.yonaprojects.yona.domain.oauth2server.OAuthAuthorizedAppsService
import com.github.yonaprojects.yona.domain.oauth2server.OAuthAppRegistrationService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import com.github.yonaprojects.yona.domain.attachment.Attachment
import com.github.yonaprojects.yona.domain.attachment.AttachmentRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.user.FavoriteProjectRepository
import com.github.yonaprojects.yona.domain.user.FavoriteOrganizationRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.domain.Page

import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import org.springframework.http.ResponseEntity
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID

@Controller
class UserViewController(
    private val userRepository: UserRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val issueRepository: IssueRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val watchRepository: WatchRepository,
    private val projectRepository: ProjectRepository,
    private val userProjectNotificationRepository: UserProjectNotificationRepository,
    private val attachmentRepository: AttachmentRepository,
    private val postingRepository: PostingRepository,
    private val favoriteProjectRepository: FavoriteProjectRepository,
    private val favoriteOrganizationRepository: FavoriteOrganizationRepository,
    private val organizationUserRepository: OrganizationUserRepository,
    private val organizationRepository: OrganizationRepository,
    private val userService: UserService,
    private val accessControl: AccessControl,
    // yona Mention.getMentioningIssueIds() 대응 (P2-41).
    private val mentionService: MentionService,
    // yona User.getVisitedIssues() 대응.
    private val recentIssueService: RecentIssueService,
    // yona-wiki P3-02 Step6.6 — Fine-grained API 토큰 발급/관리 웹 UI.
    private val apiTokenService: ApiTokenService,
    // yona-wiki P3-07 Step6 — "Authorized OAuth Apps" 화면(사용자가 인가한 MCP OAuth 클라이언트
    // 조회/취소).
    private val oAuthAuthorizedAppsService: OAuthAuthorizedAppsService,
    // yona-wiki P3-17 — GitHub의 "Settings > Developer settings > OAuth Apps"에 대응하는 사용자
    // 셀프서비스 OAuth 앱 등록/조회/삭제 UI. 위 oAuthAuthorizedAppsService(내가 "인가"한 남의 앱)와는
    // 반대 방향(내가 "등록"한 앱)이라 완전히 별개 화면이다 — 혼동 금지.
    private val oAuthAppRegistrationService: OAuthAppRegistrationService,
    // yona-wiki P3-03 Step3 — GitHub의 "Settings > SSH and GPG keys" 화면과 동등한 SSH 키
    // 등록/조회/삭제 UI.
    private val sshKeyService: com.github.yonaprojects.yona.domain.sshkey.SshKeyService,
    // yona-wiki P3-03 Step7 — 커밋 서명 검증용 GPG 키 등록/조회/삭제 UI.
    private val gpgKeyService: com.github.yonaprojects.yona.domain.gpgkey.GpgKeyService,
    // yona controllers/Application.java:35 HIDE_PROJECT_LISTING 대응 (P0-23).
    @Value("\${yona.application.hide-project-listing:false}")
    private val hideProjectListing: Boolean = false
) {

    @GetMapping("/user/issues")
    fun userIssues(
        @RequestParam(required = false, defaultValue = "1") pageNum: Int,
        @RequestParam(required = false, defaultValue = "open") state: String,
        @RequestParam(required = false) filter: String?,
        @RequestParam(required = false, defaultValue = "updatedDate") orderBy: String,
        @RequestParam(required = false, defaultValue = "desc") orderDir: String,
        @RequestParam(required = false) authorId: Long?,
        @RequestParam(required = false) commenterId: Long?,
        @RequestParam(required = false) assigneeId: Long?,
        @RequestParam(required = false) mentionId: Long?,
        @RequestParam(required = false) sharerId: Long?,
        @RequestParam(required = false) favoriteId: Long?,
        authentication: Authentication?,
        model: Model,
        request: HttpServletRequest
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "redirect:/users/loginform"

        val page = if (pageNum < 1) 0 else pageNum - 1
        val sort = if (orderDir.equals("asc", ignoreCase = true)) {
            Sort.by(Sort.Direction.ASC, orderBy)
        } else {
            Sort.by(Sort.Direction.DESC, orderBy)
        }
        val pageable = PageRequest.of(page, 20, sort)

        val currentState = State.getValue(state.lowercase())
        val searchKeyword = if (!filter.isNullOrBlank()) "%$filter%" else null
        // yona Mention.getMentioningIssueIds() 대응 (P2-41) — LIKE 텍스트 검색 대신 실제 멘션 인덱스
        // 테이블 조회로 조직/프로젝트 그룹 멘션까지 포함해 계산한다.
        val mentionedIssueIds = mentionService.getMentioningIssueIds(loginUser.id!!)

        // 아무 필터도 주어지지 않은 상태라면 기본적으로 나에게 할당된(assigneeId) 이슈로 취급
        val isNoFilter = authorId == null && commenterId == null && assigneeId == null && 
                         mentionId == null && sharerId == null && favoriteId == null
        val effectiveAssigneeId = if (isNoFilter) {
            loginUser.id
        } else {
            assigneeId
        }

        val issuePage: Page<Issue> = when {
            effectiveAssigneeId != null -> issueRepository.findByAssigneeAndState(effectiveAssigneeId, currentState, searchKeyword, pageable)
            authorId != null -> issueRepository.findByAuthorIdAndState(authorId, currentState, searchKeyword, pageable)
            commenterId != null -> issueRepository.findCommentedByState(commenterId, currentState, searchKeyword, pageable)
            mentionId != null -> if (mentionedIssueIds.isEmpty()) {
                Page.empty(pageable)
            } else {
                issueRepository.findMentionedByState(mentionedIssueIds, currentState, searchKeyword, pageable)
            }
            favoriteId != null -> issueRepository.findFavoriteByState(favoriteId, currentState, searchKeyword, pageable)
            sharerId != null -> issueRepository.findSharedByState(sharerId, currentState, searchKeyword, pageable)
            else -> issueRepository.findByState(currentState, pageable)
        }

        // 탭 상태별 총 개수 카운트 (OPEN/CLOSED)
        val (openCount, closedCount) = when {
            effectiveAssigneeId != null -> {
                Pair(
                    issueRepository.countByAssigneeAndState(effectiveAssigneeId, State.OPEN),
                    issueRepository.countByAssigneeAndState(effectiveAssigneeId, State.CLOSED)
                )
            }
            authorId != null -> {
                Pair(
                    issueRepository.countByAuthorIdAndState(authorId, State.OPEN),
                    issueRepository.countByAuthorIdAndState(authorId, State.CLOSED)
                )
            }
            commenterId != null -> {
                Pair(
                    issueRepository.countCommentedByState(commenterId, State.OPEN),
                    issueRepository.countCommentedByState(commenterId, State.CLOSED)
                )
            }
            mentionId != null -> {
                Pair(
                    if (mentionedIssueIds.isEmpty()) 0L else issueRepository.countMentionedByState(mentionedIssueIds, State.OPEN),
                    if (mentionedIssueIds.isEmpty()) 0L else issueRepository.countMentionedByState(mentionedIssueIds, State.CLOSED)
                )
            }
            favoriteId != null -> {
                Pair(
                    issueRepository.countFavoriteByState(favoriteId, State.OPEN),
                    issueRepository.countFavoriteByState(favoriteId, State.CLOSED)
                )
            }
            sharerId != null -> {
                Pair(
                    issueRepository.countSharedByState(sharerId, State.OPEN),
                    issueRepository.countSharedByState(sharerId, State.CLOSED)
                )
            }
            else -> Pair(0L, 0L)
        }

        // 좌측 필터 카운트용 수치
        val mentionCount = if (mentionedIssueIds.isEmpty()) 0L else issueRepository.countMentionedByState(mentionedIssueIds, State.OPEN)
        val shareCount = issueRepository.countSharedByState(loginUser.id!!, State.OPEN)
        val favoriteCount = issueRepository.countFavoriteByState(loginUser.id!!, State.OPEN)

        model.addAttribute("currentUser", loginUser)
        model.addAttribute("issuePage", issuePage)
        model.addAttribute("state", state)
        model.addAttribute("filter", filter ?: "")
        model.addAttribute("orderBy", orderBy)
        model.addAttribute("orderDir", orderDir)
        
        model.addAttribute("authorId", authorId)
        model.addAttribute("commenterId", commenterId)
        model.addAttribute("assigneeId", effectiveAssigneeId)
        model.addAttribute("mentionId", mentionId)
        model.addAttribute("sharerId", sharerId)
        model.addAttribute("favoriteId", favoriteId)

        model.addAttribute("openCount", openCount)
        model.addAttribute("closedCount", closedCount)
        model.addAttribute("mentionCount", mentionCount)
        model.addAttribute("shareCount", shareCount)
        model.addAttribute("favoriteCount", favoriteCount)
        model.addAttribute("requestURI", request.requestURI)

        return "issue/my_list"
    }

    @GetMapping(value = ["/user/{loginId}", "/{loginId:^(?!projects|orgs|login|signup|user|site|admin|api|_notifications|notifications|files|favicon|assets|images|javascripts|stylesheets|webjars|bootstrap)[a-zA-Z0-9_.-]+}"])
    fun userProfile(
        @PathVariable loginId: String,
        @RequestParam(required = false, defaultValue = "14") daysAgo: Int,
        @RequestParam(required = false, defaultValue = "issues") selected: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val user = userRepository.findByLoginId(loginId).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }

        // yona UserApp.java:752 "!HIDE_PROJECT_LISTING || !currentUser().isAnonymous()" 대응
        // (P0-23) — HIDE_PROJECT_LISTING이 켜져 있으면 비로그인 방문자에게는 프로젝트/이슈/PR
        // 목록을 전혀 보여주지 않는다(로그인 사용자는 이 화면에서는 영향 없음).
        val hideFromThisViewer = hideProjectListing && loginUser == null

        // yona UserApp.java:811-846 getAclValidatedIssues()/getAclValidatedPullRequests()/ [GL-controllers_UserApp-064]
        // collectProjects()+addProjectNotDupped() 대응 (P0-25). 대상 사용자가 작성한 이슈/PR/
        // 소속 프로젝트를 방문자(loginUser)가 READ 가능한 것만 남긴다 — 필터링이 전혀 없어
        // 비공개 프로젝트의 이슈/PR 제목이 그 프로젝트 멤버가 아닌 누구에게나(익명 포함)
        // 프로필을 통해 유출되던 정보노출 취약점.
        val projects = if (hideFromThisViewer) {
            emptyList()
        } else {
            projectUserRepository.findByUserId(user.id!!).map { it.project }
                .filter { accessControl.isAllowedToReadProject(loginUser, it) }
        }

        // yona UserApp.java:740-743 "daysAgo < 0이면 1로 보정" 대응 (P2-38). [GL-controllers_UserApp-059]
        val effectiveDaysAgo = if (daysAgo < 0) 1 else daysAgo
        val since = Instant.now().minus(effectiveDaysAgo.toLong(), ChronoUnit.DAYS)

        val issues = if (hideFromThisViewer) {
            emptyList()
        } else {
            // yona UserApp.java:754-755 Issue.findRecentlyIssuesByDaysAgo(user, daysAgo) 대응
            // (P2-38) — 작성자 또는 담당자인 이슈 중 daysAgo일 이내에 갱신된 것만 노출한다.
            issueRepository.findRecentlyByUser(user.id!!, since)
                .filter { accessControl.isAllowedToReadProject(loginUser, it.project) }
        }
        val openIssuesCount = issues.count { it.state == State.OPEN }
        val closedIssuesCount = issues.count { it.state == State.CLOSED }

        val pullRequests = if (hideFromThisViewer) {
            emptyList()
        } else {
            // yona UserApp.java:757-759 PullRequest.findOpendPullRequestsByDaysAgo(user, daysAgo)
            // 대응 (P2-38). [GL-controllers_UserApp-060]
            pullRequestRepository.findByContributorAndUpdatedGreaterThanEqualOrderByUpdatedDescStateAsc(user, since)
                .filter { accessControl.isAllowedToReadProject(loginUser, it.toProject) }
        }

        model.addAttribute("user", user)
        model.addAttribute("projects", projects)
        model.addAttribute("issues", issues)
        model.addAttribute("openIssuesCount", openIssuesCount)
        model.addAttribute("closedIssuesCount", closedIssuesCount)
        model.addAttribute("pullRequests", pullRequests)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("daysAgo", effectiveDaysAgo)
        model.addAttribute("selected", selected)

        return "user/view"
    }

    // yona UserApp.verifyUser() 대응. legacy는 실패 시 notFound("Invalid verification")를 반환한다.
    @GetMapping("/verify/{loginId}/{verificationCode}")
    fun verifyUserLegacy(
        @PathVariable loginId: String,
        @PathVariable verificationCode: String,
        response: HttpServletResponse,
        model: Model
    ): String {
        return verifyUser(loginId, verificationCode, response, model)
    }

    @GetMapping("/user/verify")
    fun verifyUser(
        @RequestParam("loginId") loginId: String,
        @RequestParam("code") code: String,
        response: HttpServletResponse,
        model: Model
    ): String {
        val success = userService.verifyUser(loginId, code)
        if (!success) {
            response.status = HttpServletResponse.SC_NOT_FOUND
            return "error/404"
        }
        model.addAttribute("loginId", loginId)
        return "user/verified"
    }

    // yona UserApp.java:1101-1114 confirmEmail() 대응. 성공 시 editUserInfoForm으로 리다이렉트, [GL-controllers_UserApp-079;GL-controllers_UserApp-080]
    // 실패 시 ErrorViews.NotFound(404)를 반환한다. Play의 addUserInfoToSession(자동 세션 갱신)은
    // Spring Security 인증 모델과 근본적으로 다른 메커니즘이라 이식 범위에서 제외했다.
    @GetMapping("/user/email/confirm/{emailId}/{token}")
    fun confirmEmailLegacy(
        @PathVariable emailId: Long,
        @PathVariable token: String,
        response: HttpServletResponse
    ): String {
        return confirmEmail(emailId, token, response)
    }

    @GetMapping("/user/emails/{emailId}/confirm")
    fun confirmEmail(
        @PathVariable emailId: Long,
        @RequestParam("token") token: String,
        response: HttpServletResponse
    ): String {
        val success = userService.confirmEmail(emailId, token)
        if (!success) {
            response.status = HttpServletResponse.SC_NOT_FOUND
            return "error/404"
        }
        return "redirect:/user/editform"
    }

    @GetMapping("/user/editform")
    fun editUserProfileForm(
        authentication: Authentication?,
        model: Model
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "error/403"

        fillAvatarId(loginUser)
        model.addAttribute("user", loginUser)
        model.addAttribute("currentUser", loginUser)

        return "user/edit"
    }

    @GetMapping("/user/editform/emails")
    fun editUserEmailsForm(
        authentication: Authentication?,
        model: Model
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "error/403"

        fillAvatarId(loginUser)
        model.addAttribute("user", loginUser)
        model.addAttribute("emails", loginUser.emails)
        model.addAttribute("currentUser", loginUser)

        return "user/edit_emails"
    }

    @GetMapping("/user/editform/notifications")
    fun editUserNotificationsForm(
        authentication: Authentication?,
        model: Model
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "error/403"

        // 사용자가 감시 중인 프로젝트 목록 조회 (resourceType = PROJECT)
        val watches = watchRepository.findByUserAndResourceType(loginUser, ResourceType.PROJECT)
        val projects = watches.mapNotNull {
            projectRepository.findById(it.resourceId.toLongOrNull() ?: return@mapNotNull null).orElse(null)
        }

        // 전체 알림 타입 목록 (order 순으로 정렬)
        val notiTypes = EventType.values().sortedBy { it.order }

        // 알림 상태 맵 구성
        // Project ID -> Notification Type Name -> Allowed (Boolean)
        val notiMap = mutableMapOf<Long, Map<String, Boolean>>()
        for (project in projects) {
            val projectNotiMap = mutableMapOf<String, Boolean>()
            for (notiType in notiTypes) {
                val existing = userProjectNotificationRepository.findByUserAndProjectAndNotificationType(loginUser, project, notiType)
                val allowed = existing?.allowed ?: isNotifiedByDefault(notiType)
                projectNotiMap[notiType.name] = allowed
            }
            notiMap[project.id!!] = projectNotiMap
        }

        val notiTypeDescriptions = mapOf(
            EventType.NEW_ISSUE to "새 이슈 등록",
            EventType.NEW_POSTING to "새 게시물 등록",
            EventType.NEW_PULL_REQUEST to "새 코드보내기 등록",
            EventType.ISSUE_STATE_CHANGED to "이슈 상태 변경",
            EventType.ISSUE_ASSIGNEE_CHANGED to "이슈 담당자 변경",
            EventType.PULL_REQUEST_STATE_CHANGED to "코드보내기 상태 변경",
            EventType.NEW_COMMENT to "새 댓글 등록",
            EventType.NEW_REVIEW_COMMENT to "코드보내기에 새 댓글 등록",
            EventType.MEMBER_ENROLL_REQUEST to "멤버 등록 요청",
            EventType.PULL_REQUEST_MERGED to "코드보내기 반영됨(merged)",
            EventType.ISSUE_REFERRED_FROM_COMMIT to "커밋에서의 이슈 언급",
            EventType.PULL_REQUEST_COMMIT_CHANGED to "코드보내기 커밋 변경",
            EventType.NEW_COMMIT to "새 커밋",
            EventType.PULL_REQUEST_REVIEW_STATE_CHANGED to "코드보내기 리뷰 액션 변경",
            EventType.ISSUE_BODY_CHANGED to "이슈 본문 변경",
            EventType.ISSUE_REFERRED_FROM_PULL_REQUEST to "코드 주고받기에서의 이슈 언급",
            EventType.REVIEW_THREAD_STATE_CHANGED to "리뷰 스레드 상태 변경",
            EventType.ORGANIZATION_MEMBER_ENROLL_REQUEST to "조직 멤버 등록 요청",
            EventType.COMMENT_UPDATED to "댓글 수정",
            EventType.ISSUE_MOVED to "이슈 이동",
            EventType.ISSUE_SHARER_CHANGED to "이슈 공유 변경",
            EventType.ISSUE_LABEL_CHANGED to "이슈 라벨 변경",
            EventType.ISSUE_MILESTONE_CHANGED to "마일 스톤 변경",
            EventType.POSTING_BODY_CHANGED to "게시글 본문 변경",
            EventType.RESOURCE_DELETED to "이슈/게시글 삭제",
            EventType.MEMBER_ENROLL_ACCEPT to "멤버 가입 승인",
            EventType.ORGANIZATION_MEMBER_ENROLL_ACCEPT to "조직 멤버 가입 승인"
        )

        fillAvatarId(loginUser)
        model.addAttribute("user", loginUser)
        model.addAttribute("projects", projects)
        model.addAttribute("notiTypes", notiTypes)
        model.addAttribute("notiMap", notiMap)
        model.addAttribute("notiTypeDescriptions", notiTypeDescriptions)
        model.addAttribute("currentUser", loginUser)

        return "user/edit_notifications"
    }

    private fun isNotifiedByDefault(eventType: EventType): Boolean {
        return eventType != EventType.NEW_COMMENT
    }

    @GetMapping("/user/usermenuTabContentList")
    fun usermenuTabContentList(
        authentication: Authentication?,
        model: Model
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "common/usermenu_tab_content_list"

        // 1. 즐겨찾기 프로젝트 목록
        val favoriteProjects = favoriteProjectRepository.findByUserId(loginUser.id!!).map { it.project }

        // 2. 사용자가 가입한 조직 목록
        val organizations = organizationUserRepository.findByUserId(loginUser.id!!).map { it.organization }
        val favoriteOrganizations = favoriteOrganizationRepository.findByUserId(loginUser.id!!).map { it.organization }

        // 3. 참여한 프로젝트 목록
        val projectUsers = projectUserRepository.findByUserId(loginUser.id!!)
        val allUserProjects = projectUsers.map { it.project }

        // - 최근 방문 프로젝트 (최근 활동 순서로 정렬)
        val recentlyVisited = allUserProjects.sortedByDescending { it.createdDate }.take(10)

        // - 내가 생성한 프로젝트
        val createdByMe = projectRepository.findByOwner(loginUser.loginId!!)

        // - 지켜보기 프로젝트
        val watches = watchRepository.findByUserAndResourceType(loginUser, ResourceType.PROJECT)
        val watching = watches.mapNotNull { 
            projectRepository.findById(it.resourceId.toLongOrNull() ?: return@mapNotNull null).orElse(null) 
        }

        // - 참여함 (오너가 아니면서 멤버인 프로젝트)
        val joinmember = allUserProjects.filter { it.owner != loginUser.loginId }

        // 4. 전체 조직 목록 (All 탭)
        val allOrganizations = organizationRepository.findAll()

        // 5. 최근 방문한 이슈/게시글 (yona User.getVisitedIssues() 대응)
        val visitedIssues = recentIssueService.getRecentIssues(loginUser)

        model.addAttribute("currentUser", loginUser)
        model.addAttribute("favoriteProjects", favoriteProjects)
        model.addAttribute("favoriteOrganizations", favoriteOrganizations)
        model.addAttribute("organizations", organizations)
        model.addAttribute("recentlyVisited", recentlyVisited)
        model.addAttribute("createdByMe", createdByMe)
        model.addAttribute("watching", watching)
        model.addAttribute("joinmember", joinmember)
        model.addAttribute("allOrganizations", allOrganizations)
        model.addAttribute("visitedIssues", visitedIssues)

        return "common/usermenu_tab_content_list"
     }

    @GetMapping("/user/sidebar")
    fun userSidebar(
        @RequestParam(required = false, defaultValue = "/user/issues") path: String,
        @RequestParam(required = false, defaultValue = "") hash: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "redirect:/users/loginform"

        val iframePath = if (hash.isBlank()) path else "$path#$hash"

        // 즐겨찾기 프로젝트 목록
        val favoriteProjects = favoriteProjectRepository.findByUserId(loginUser.id!!).map { it.project }

        // 사용자가 가입한 조직 목록
        val organizations = organizationUserRepository.findByUserId(loginUser.id!!).map { it.organization }
        val favoriteOrganizations = favoriteOrganizationRepository.findByUserId(loginUser.id!!).map { it.organization }

        // 참여한 프로젝트 목록
        val projectUsers = projectUserRepository.findByUserId(loginUser.id!!)
        val allUserProjects = projectUsers.map { it.project }

        // 최근 방문 프로젝트 (최근 활동 순서로 정렬)
        val recentlyVisited = allUserProjects.sortedByDescending { it.createdDate }.take(10)

        // 내가 생성한 프로젝트
        val createdByMe = projectRepository.findByOwner(loginUser.loginId!!)

        // 지켜보기 프로젝트
        val watches = watchRepository.findByUserAndResourceType(loginUser, ResourceType.PROJECT)
        val watching = watches.mapNotNull { 
            projectRepository.findById(it.resourceId.toLongOrNull() ?: return@mapNotNull null).orElse(null) 
        }

        // 참여함 (오너가 아니면서 멤버인 프로젝트)
        val joinmember = allUserProjects.filter { it.owner != loginUser.loginId }

        // 전체 조직 목록 (All 탭)
        val allOrganizations = organizationRepository.findAll()

        // 최근 이슈 목록 (참여 프로젝트의 이슈 중 최근 업데이트된 10개)
        val recentIssues = if (allUserProjects.isNotEmpty()) {
            issueRepository.findByProjectIn(allUserProjects, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "updatedDate"))).content
        } else {
            emptyList()
        }

        model.addAttribute("currentUser", loginUser)
        model.addAttribute("iframePath", iframePath)
        model.addAttribute("favoriteProjects", favoriteProjects)
        model.addAttribute("favoriteOrganizations", favoriteOrganizations)
        model.addAttribute("organizations", organizations)
        model.addAttribute("recentlyVisited", recentlyVisited)
        model.addAttribute("createdByMe", createdByMe)
        model.addAttribute("watching", watching)
        model.addAttribute("joinmember", joinmember)
        model.addAttribute("allOrganizations", allOrganizations)
        model.addAttribute("recentIssues", recentIssues)

        return "site/layout_framed"
    }

    @GetMapping("/user/editform/password")
    fun editUserPasswordForm(
        authentication: Authentication?,
        model: Model
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "error/403"

        fillAvatarId(loginUser)
        model.addAttribute("user", loginUser)
        model.addAttribute("currentUser", loginUser)

        return "user/edit_password"
    }

    @GetMapping("/user/editform/token")
    fun editUserTokenForm(
        authentication: Authentication?,
        model: Model
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "error/403"

        fillAvatarId(loginUser)
        model.addAttribute("user", loginUser)
        model.addAttribute("currentUser", loginUser)

        return "user/edit_token"
    }

    // yona-wiki P3-02 Step6.6 — 레거시 전권 토큰 화면(edit_token.html, 위 editUserTokenForm)과는
    // 완전히 별개인 Fine-grained 토큰 발급/관리 화면. GitHub의 "Settings > Developer settings >
    // Personal access tokens" 컨벤션대로 목록(이 메서드)과 발급 폼(newApiTokenForm)을 별개
    // 페이지로 분리했다 — 발급 성공 시 이 목록으로 리다이렉트되고, 발급된 원문 토큰 값은
    // RedirectAttributes 플래시 속성으로 다음 GET 한 번만 노출된다(issueApiToken 참고).
    @GetMapping("/user/editform/tokens")
    fun editApiTokensForm(
        authentication: Authentication?,
        model: Model
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "error/403"

        fillAvatarId(loginUser)
        model.addAttribute("user", loginUser)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("tokens", apiTokenService.listByOwner(loginUser))

        return "user/edit_tokens"
    }

    @GetMapping("/user/editform/tokens/new")
    fun newApiTokenForm(
        authentication: Authentication?,
        model: Model
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "error/403"

        fillAvatarId(loginUser)
        fillTokenNewFormModel(loginUser, model)

        return "user/edit_tokens_new"
    }

    @PostMapping("/user/editform/tokens")
    fun issueApiToken(
        @RequestParam("name") name: String,
        @RequestParam(value = "allRepositories", defaultValue = "false") allRepositories: Boolean,
        @RequestParam(value = "scopedProjectIds", required = false) scopedProjectIds: List<Long>?,
        @RequestParam("expiresInDays") expiresInDays: Long,
        request: HttpServletRequest,
        authentication: Authentication?,
        model: Model,
        redirectAttributes: RedirectAttributes
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "error/403"

        // 권한 매트릭스(그룹 × 없음/읽기/쓰기)는 라디오 그룹이 그룹 수만큼 생기므로
        // scope_<GROUP_NAME> 파라미터 각각으로 넘어온다(예: scope_ISSUES=WRITE).
        val scopePermissions = ApiTokenScopeGroup.entries.associateWith { group ->
            val raw = request.getParameter("scope_${group.name}")
            ApiTokenPermission.entries.find { it.name == raw } ?: ApiTokenPermission.NONE
        }

        fillAvatarId(loginUser)
        try {
            val issued = apiTokenService.issue(
                owner = loginUser,
                name = name,
                allRepositories = allRepositories,
                scopedProjectIds = scopedProjectIds ?: emptyList(),
                scopePermissions = scopePermissions,
                expiresInDays = expiresInDays
            )
            // Post/Redirect/Get — 목록 화면으로 돌아가 원문 토큰 값을 플래시 속성으로 한 번만
            // 노출한다(DeployKeyController.newDeployKey()와 동일한 컨벤션).
            redirectAttributes.addFlashAttribute("issuedRawToken", issued.rawToken)
            redirectAttributes.addFlashAttribute("issuedTokenName", issued.apiToken.name)
            return "redirect:/user/editform/tokens"
        } catch (e: IllegalArgumentException) {
            // 검증 실패 시에는 목록이 아니라 방금 있던 발급 폼으로 되돌아가야 입력값을 잃지 않는다.
            model.addAttribute("tokenIssueError", e.message)
            fillTokenNewFormModel(
                loginUser, model,
                submittedName = name,
                submittedAllRepositories = allRepositories,
                submittedScopedProjectIds = scopedProjectIds ?: emptyList(),
                submittedExpiresInDays = expiresInDays,
                submittedScopePermissions = scopePermissions
            )
            return "user/edit_tokens_new"
        }
    }

    @PostMapping("/user/editform/tokens/{id}/revoke")
    fun revokeApiToken(
        @PathVariable id: Long,
        authentication: Authentication?
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "error/403"

        apiTokenService.revoke(loginUser, id)
        return "redirect:/user/editform/tokens"
    }

    private fun fillTokenNewFormModel(
        loginUser: User,
        model: Model,
        submittedName: String = "",
        submittedAllRepositories: Boolean = true,
        submittedScopedProjectIds: List<Long> = emptyList(),
        submittedExpiresInDays: Long = 30,
        submittedScopePermissions: Map<ApiTokenScopeGroup, ApiTokenPermission> = emptyMap()
    ) {
        model.addAttribute("user", loginUser)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("scopeGroups", ApiTokenScopeGroup.entries)
        // 토큰의 선택 저장소 범위로 고를 수 있는 후보 — 사용자가 멤버로 속한 프로젝트만
        // 노출한다(본인이 소속되지 않은 남의 저장소를 스코프에 담을 이유가 없다).
        val candidateProjects: List<com.github.yonaprojects.yona.domain.project.Project> =
            loginUser.id?.let { userId -> projectUserRepository.findByUserId(userId).map { it.project } } ?: emptyList()
        model.addAttribute("candidateProjects", candidateProjects)
        model.addAttribute("submittedName", submittedName)
        model.addAttribute("submittedAllRepositories", submittedAllRepositories)
        // [[submittedScopes]]와 동일한 이유(Kotlin emptyList()가 SpEL 리플렉션으로 contains()를
        // 못 찾는 kotlin.collections.EmptyList 싱글턴이라 500/503으로 이어짐)로 ArrayList로 감싼다
        // — candidateProjects가 있는 사용자가 발급 폼을 열면 재현된다.
        model.addAttribute("submittedScopedProjectIds", ArrayList(submittedScopedProjectIds))
        model.addAttribute("submittedExpiresInDays", submittedExpiresInDays)
        model.addAttribute("submittedScopePermissions", submittedScopePermissions)
    }

    // yona-wiki P3-07(MCP 서버) Step6 — GitHub의 "Settings > Applications > Authorized OAuth Apps"에
    // 대응하는 화면. 사용자가 자신이 인가한 OAuth 클라이언트(Claude Code 등 MCP 클라이언트)를
    // 조회하고 취소(revoke)할 수 있게 한다 — 백엔드(인가 서버)만 만들고 실제로 관리할 방법이 없는
    // 상태로 남기지 않기 위한 필수 UI.
    @GetMapping("/user/editform/oauth-apps")
    fun editOAuthAuthorizedAppsForm(
        authentication: Authentication?,
        model: Model
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "error/403"

        fillAvatarId(loginUser)
        model.addAttribute("user", loginUser)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("authorizedApps", oAuthAuthorizedAppsService.listAuthorizedApps(loginUser.loginId))

        return "user/edit_oauth_apps"
    }

    @PostMapping("/user/editform/oauth-apps/{clientId}/revoke")
    fun revokeOAuthAuthorizedApp(
        @PathVariable clientId: String,
        authentication: Authentication?
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "error/403"

        oAuthAuthorizedAppsService.revoke(loginUser.loginId, clientId)
        return "redirect:/user/editform/oauth-apps"
    }

    // yona-wiki P3-17 — GitHub의 "Settings > Developer settings > OAuth Apps"에 대응하는 사용자
    // 셀프서비스 OAuth 앱 등록 화면. [[p3-14]] 1라운드에서는 이 기능이 사이트 관리자 전용
    // (OAuthAppsAdminController)이었지만, GitHub/Forgejo 둘 다 OAuth 앱 등록은 사용자가 만드는
    // 자원으로서 계정에 종속된 셀프서비스 기능이라 이 저장소의 표준 방침(모호하면 GitHub 방식을
    // 따른다)에 맞춰 옮겼다(배경은 [[p3-17]] 참고). 위 editOAuthAuthorizedAppsForm(내가 "인가"한
    // 남의 앱, edit_oauth_apps.html)과는 반대 방향(내가 "등록"한 앱)이라 URL도 템플릿도 완전히
    // 별개다 — 혼동 금지. tokens/ssh-keys/gpg-keys와 동일한 컨벤션대로 목록(이 메서드)과 등록 폼
    // (newOwnedOAuthAppForm)을 별개 페이지로 분리했다 — 등록 성공 시 이 목록으로 리다이렉트되고,
    // confidential이면 발급된 client_secret 평문값이 플래시 속성으로 다음 GET 한 번만 노출된다
    // (registerOwnedOAuthApp 참고, URL에는 절대 담지 않는다).
    @GetMapping("/user/editform/oauth-apps-owned")
    fun editOwnedOAuthAppsForm(
        authentication: Authentication?,
        model: Model
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "error/403"

        fillAvatarId(loginUser)
        model.addAttribute("user", loginUser)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("ownedOAuthApps", oAuthAppRegistrationService.listByOwner(loginUser.id!!))

        return "user/edit_oauth_apps_owned"
    }

    @GetMapping("/user/editform/oauth-apps-owned/new")
    fun newOwnedOAuthAppForm(
        authentication: Authentication?,
        model: Model
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "error/403"

        fillAvatarId(loginUser)
        fillOwnedOAuthAppNewFormModel(loginUser, model)

        return "user/edit_oauth_apps_owned_new"
    }

    @PostMapping("/user/editform/oauth-apps-owned/register")
    fun registerOwnedOAuthApp(
        @RequestParam clientName: String,
        @RequestParam redirectUri: String,
        @RequestParam(defaultValue = "false") confidential: Boolean,
        @RequestParam(required = false) scopes: List<String>?,
        authentication: Authentication?,
        model: Model,
        redirectAttributes: RedirectAttributes
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "error/403"

        fillAvatarId(loginUser)
        try {
            val registered = oAuthAppRegistrationService.register(
                clientName = clientName,
                redirectUri = redirectUri,
                confidential = confidential,
                scopes = scopes,
                ownerId = loginUser.id
            )
            // Post/Redirect/Get — 목록 화면으로 돌아가 client_secret 평문값을 플래시 속성으로 한
            // 번만 노출한다(edit_tokens.html의 issuedRawToken과 동일한 컨벤션, URL에는 담지 않는다).
            redirectAttributes.addFlashAttribute("registeredClientId", registered.client.clientId)
            redirectAttributes.addFlashAttribute("registeredPlainSecret", registered.plainSecret)
            return "redirect:/user/editform/oauth-apps-owned"
        } catch (e: IllegalArgumentException) {
            // 검증 실패 시에는 목록이 아니라 방금 있던 등록 폼으로 되돌아가야 입력값을 잃지 않는다.
            model.addAttribute("oauthAppError", e.message)
            fillOwnedOAuthAppNewFormModel(
                loginUser, model,
                submittedClientName = clientName,
                submittedRedirectUri = redirectUri,
                submittedConfidential = confidential,
                submittedScopes = scopes ?: emptyList()
            )
            return "user/edit_oauth_apps_owned_new"
        }
    }

    @PostMapping("/user/editform/oauth-apps-owned/{id}/delete")
    fun deleteOwnedOAuthApp(
        @PathVariable id: String,
        authentication: Authentication?
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "error/403"

        // IDOR 방지 — 본인이 등록한 앱이 아니면(다른 사용자 소유거나 애초에 존재하지 않는 id)
        // 삭제를 거부한다. 어느 쪽인지는 구분해서 알려주지 않는다(존재 여부 자체도 노출하지 않음).
        val client = oAuthAppRegistrationService.findOwned(id, loginUser.id!!)
            ?: return "error/403"

        oAuthAppRegistrationService.deleteClientAndRelatedRecords(client)
        return "redirect:/user/editform/oauth-apps-owned"
    }

    private fun fillOwnedOAuthAppNewFormModel(
        loginUser: User,
        model: Model,
        submittedClientName: String = "",
        submittedRedirectUri: String = "",
        submittedConfidential: Boolean = false,
        submittedScopes: List<String> = emptyList()
    ) {
        model.addAttribute("user", loginUser)
        model.addAttribute("currentUser", loginUser)
        // yona-wiki P3-14 2라운드 — availableScopes()에 API 스코프와 identity 스코프(openid/profile/
        // email)가 함께 들어있다(register()의 화이트리스트 검증이 이 목록 하나만 기준으로 삼기
        // 때문). 템플릿은 콜론 포함 여부로 둘을 구분해 보여준다(edit_oauth_apps_owned_new.html
        // 참고) — identity 스코프 체크박스 자체는 각각 사람이 읽을 수 있는 설명이 필요해 템플릿에
        // 직접 하드코딩했으므로 별도 모델 속성은 두지 않는다.
        model.addAttribute("availableScopes", oAuthAppRegistrationService.availableScopes())
        model.addAttribute("submittedClientName", submittedClientName)
        model.addAttribute("submittedRedirectUri", submittedRedirectUri)
        model.addAttribute("submittedConfidential", submittedConfidential)
        // Kotlin의 emptyList()/listOf()는 리플렉션으로 접근 시 kotlin.collections.EmptyList
        // 싱글턴을 반환하는데, SpEL의 리플렉션 기반 메서드 탐색이 여기 선언된 contains(String)를
        // 못 찾아 "EL1004E: Method call: Method contains cannot be found on type
        // kotlin.collections.EmptyList" 예외를 던진다(템플릿의 submittedScopes.contains(scope)
        // 호출 시 500/503으로 이어짐 — 신규 등록 폼을 처음 열 때 기본값이 정확히 이 경우였다).
        // java.util.ArrayList로 감싸 일반 List 구현체로 노출해 회피한다.
        model.addAttribute("submittedScopes", ArrayList(submittedScopes))
    }

    // yona-wiki P3-03 Step3 — GitHub "Settings > SSH and GPG keys" 화면과 동일한 컨벤션
    // (edit_tokens.html/edit_oauth_apps.html과 같은 탭 메뉴/CSS 클래스/컨트롤러 패턴). 목록
    // (이 메서드)과 등록 폼(newSshKeyForm)을 별개 페이지로 분리했다 — 등록 성공 시 이 목록으로
    // 리다이렉트되고 플래시 속성으로 성공 메시지를 한 번만 노출한다(addSshKey 참고).
    @GetMapping("/user/editform/ssh-keys")
    fun editSshKeysForm(
        authentication: Authentication?,
        model: Model
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "error/403"

        fillAvatarId(loginUser)
        model.addAttribute("user", loginUser)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("sshKeys", sshKeyService.listByUser(loginUser))

        return "user/edit_ssh_keys"
    }

    @GetMapping("/user/editform/ssh-keys/new")
    fun newSshKeyForm(
        authentication: Authentication?,
        model: Model
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "error/403"

        fillAvatarId(loginUser)
        model.addAttribute("user", loginUser)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("submittedTitle", "")
        model.addAttribute("submittedPublicKey", "")

        return "user/edit_ssh_keys_new"
    }

    @PostMapping("/user/editform/ssh-keys")
    fun addSshKey(
        @RequestParam("title") title: String,
        @RequestParam("publicKey") publicKey: String,
        authentication: Authentication?,
        model: Model,
        redirectAttributes: RedirectAttributes
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "error/403"

        fillAvatarId(loginUser)
        try {
            sshKeyService.create(loginUser, title, publicKey)
            // Post/Redirect/Get — 목록 화면으로 돌아가 성공 메시지를 플래시 속성으로 한 번만
            // 노출한다(SSH 공개키는 사용자가 직접 입력한 값이라 토큰과 달리 재노출 위험이 없다).
            redirectAttributes.addFlashAttribute("sshKeyAdded", true)
            return "redirect:/user/editform/ssh-keys"
        } catch (e: IllegalArgumentException) {
            model.addAttribute("sshKeyError", e.message)
        } catch (e: com.github.yonaprojects.yona.domain.sshkey.SshPublicKeyFingerprint.InvalidPublicKeyException) {
            model.addAttribute("sshKeyError", e.message)
        }

        // 검증 실패 시에는 목록이 아니라 방금 있던 등록 폼으로 되돌아가야 입력값을 잃지 않는다.
        model.addAttribute("user", loginUser)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("submittedTitle", title)
        model.addAttribute("submittedPublicKey", publicKey)
        return "user/edit_ssh_keys_new"
    }

    @PostMapping("/user/editform/ssh-keys/{id}/delete")
    fun deleteSshKey(
        @PathVariable id: Long,
        authentication: Authentication?
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "error/403"

        sshKeyService.delete(loginUser, id)
        return "redirect:/user/editform/ssh-keys"
    }

    // yona-wiki P3-03 Step7 — GitHub "Settings > SSH and GPG keys" 화면의 GPG 키 섹션과 동일한
    // 컨벤션. 목록(이 메서드)과 등록 폼(newGpgKeyForm)을 별개 페이지로 분리했다 — 등록 성공 시 이
    // 목록으로 리다이렉트되고 플래시 속성으로 성공 메시지를 한 번만 노출한다(addGpgKey 참고).
    @GetMapping("/user/editform/gpg-keys")
    fun editGpgKeysForm(
        authentication: Authentication?,
        model: Model
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "error/403"

        fillAvatarId(loginUser)
        model.addAttribute("user", loginUser)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("gpgKeys", gpgKeyService.listByUser(loginUser))

        return "user/edit_gpg_keys"
    }

    @GetMapping("/user/editform/gpg-keys/new")
    fun newGpgKeyForm(
        authentication: Authentication?,
        model: Model
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "error/403"

        fillAvatarId(loginUser)
        model.addAttribute("user", loginUser)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("submittedArmoredPublicKey", "")

        return "user/edit_gpg_keys_new"
    }

    @PostMapping("/user/editform/gpg-keys")
    fun addGpgKey(
        @RequestParam("armoredPublicKey") armoredPublicKey: String,
        authentication: Authentication?,
        model: Model,
        redirectAttributes: RedirectAttributes
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "error/403"

        fillAvatarId(loginUser)
        try {
            gpgKeyService.create(loginUser, armoredPublicKey)
            // Post/Redirect/Get — 목록 화면으로 돌아가 성공 메시지를 플래시 속성으로 한 번만
            // 노출한다(GPG 공개키는 사용자가 직접 입력한 값이라 토큰과 달리 재노출 위험이 없다).
            redirectAttributes.addFlashAttribute("gpgKeyAdded", true)
            return "redirect:/user/editform/gpg-keys"
        } catch (e: IllegalArgumentException) {
            model.addAttribute("gpgKeyError", e.message)
        } catch (e: com.github.yonaprojects.yona.domain.gpgkey.GpgPublicKeyParser.InvalidGpgKeyException) {
            model.addAttribute("gpgKeyError", e.message)
        }

        // 검증 실패 시에는 목록이 아니라 방금 있던 등록 폼으로 되돌아가야 입력값을 잃지 않는다.
        model.addAttribute("user", loginUser)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("submittedArmoredPublicKey", armoredPublicKey)
        return "user/edit_gpg_keys_new"
    }

    @PostMapping("/user/editform/gpg-keys/{id}/delete")
    fun deleteGpgKey(
        @PathVariable id: Long,
        authentication: Authentication?
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "error/403"

        gpgKeyService.delete(loginUser, id)
        return "redirect:/user/editform/gpg-keys"
    }

    @GetMapping("/user/files")
    fun userFiles(
        @RequestParam(value = "filter", defaultValue = "") filter: String,
        @RequestParam(value = "pageNum", defaultValue = "1") pageNum: Int,
        authentication: Authentication?,
        model: Model
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "error/403"

        if (pageNum < 1) {
            return "error/404"
        }

        val pageable = PageRequest.of(pageNum - 1, 50, Sort.by("id").descending())
        val page = if (filter.isBlank()) {
            attachmentRepository.findByOwnerLoginId(loginUser.loginId!!, pageable)
        } else {
            attachmentRepository.findByOwnerLoginIdAndNameContainingIgnoreCase(loginUser.loginId!!, filter, pageable)
        }

        model.addAttribute("user", loginUser)
        model.addAttribute("page", page)
        model.addAttribute("filter", filter)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("controller", this)

        return "user/userFiles"
    }

    fun getAttachmentUrl(attachment: Attachment): String? {
        val containerIdLong = attachment.containerId.toLongOrNull() ?: return null
        return try {
            when (attachment.containerType) {
                ResourceType.ISSUE_POST -> {
                    val issue = issueRepository.findById(containerIdLong).orElse(null) ?: return null
                    "/${issue.project.owner}/${issue.project.name}/issue/${issue.number}"
                }
                ResourceType.BOARD_POST -> {
                    val posting = postingRepository.findById(containerIdLong).orElse(null) ?: return null
                    "/${posting.project.owner}/${posting.project.name}/post/${posting.number}"
                }
                ResourceType.PULL_REQUEST -> {
                    val pr = pullRequestRepository.findById(containerIdLong).orElse(null) ?: return null
                    "/${pr.toProject.owner}/${pr.toProject.name}/pull/${pr.number}"
                }
                ResourceType.PROJECT -> {
                    val project = projectRepository.findById(containerIdLong).orElse(null) ?: return null
                    "/${project.owner}/${project.name}"
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun fillAvatarId(user: User) {
        val attachments = attachmentRepository.findByContainerTypeAndContainerId(ResourceType.USER_AVATAR, user.id.toString())
        if (attachments.isNotEmpty()) {
            user.avatarId = attachments.last().id
        }
    }

    @PostMapping("/user/edit")
    fun editUserInfo(
        @RequestParam("name") name: String,
        @RequestParam("email") email: String,
        @RequestParam(value = "avatarId", required = false) avatarId: Long?,
        authentication: Authentication?
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "error/403"

        if (name.isBlank()) {
            return "redirect:/user/editform"
        }

        // 이메일 중복 체크
        if (loginUser.email != email && userRepository.findByEmail(email).isPresent) {
            return "redirect:/user/editform"
        }

        loginUser.name = name.trim()
        loginUser.email = email.trim()

        if (avatarId != null) {
            val attachment = attachmentRepository.findById(avatarId).orElse(null)
            if (attachment != null && attachment.mimeType?.startsWith("image") == true) {
                val oldAttachments = attachmentRepository.findByContainerTypeAndContainerId(ResourceType.USER_AVATAR, loginUser.id.toString())
                attachmentRepository.deleteAll(oldAttachments)

                attachment.containerType = ResourceType.USER_AVATAR
                attachment.containerId = loginUser.id.toString()
                attachmentRepository.save(attachment)
            }
        }

        userRepository.save(loginUser)
        return "redirect:/user/${loginUser.loginId}"
    }

    @PostMapping("/user/resetPassword")
    fun resetUserPassword(
        @RequestParam("oldPassword") oldPassword: String,
        @RequestParam("password") password: String,
        @RequestParam("retypedPassword") retypedPassword: String,
        authentication: Authentication?,
        request: HttpServletRequest
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "error/403"

        val hashedOld = hashPassword(oldPassword, loginUser.passwordSalt ?: "")
        if (loginUser.password != hashedOld) {
            return "redirect:/user/editform/password"
        }

        if (password != retypedPassword) {
            return "redirect:/user/editform/password"
        }

        if (password.length < 4) {
            return "redirect:/user/editform/password"
        }

        val newSalt = UUID.randomUUID().toString().substring(0, 8)
        val newHashed = hashPassword(password, newSalt)
        loginUser.passwordSalt = newSalt
        loginUser.password = newHashed
        userRepository.save(loginUser)

        request.logout()
        return "redirect:/users/loginform"
    }

    private fun hashPassword(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.reset()
        digest.update(salt.toByteArray(Charsets.UTF_8))
        var hashed = digest.digest(password.toByteArray(Charsets.UTF_8))
        for (i in 1 until 1024) {
            digest.reset()
            hashed = digest.digest(hashed)
        }
        return Base64.getEncoder().encodeToString(hashed)
    }

    @PostMapping("/user/resetVisitedList")
    fun resetVisitedList(): String {
        return "redirect:/user/editform"
    }

    @PostMapping("/user/email")
    fun addEmail(
        @RequestParam("email") email: String,
        authentication: Authentication?
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "error/403"

        try {
            userService.addEmail(loginUser.id!!, email)
        } catch (e: Exception) {
        }
        return "redirect:/user/editform/emails"
    }

    @RequestMapping(value = ["/user/email/delete/{emailId}"], method = [RequestMethod.DELETE, RequestMethod.GET, RequestMethod.POST])
    fun deleteEmail(
        @PathVariable emailId: Long,
        authentication: Authentication?
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "error/403"

        try {
            userService.deleteEmail(loginUser.id!!, emailId)
        } catch (e: Exception) {
        }
        return "redirect:/user/editform/emails"
    }

    @RequestMapping(value = ["/user/email/setAsMain/{emailId}"], method = [RequestMethod.PUT, RequestMethod.GET, RequestMethod.POST])
    fun setAsMainEmail(
        @PathVariable emailId: Long,
        authentication: Authentication?
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "error/403"

        try {
            userService.setAsMainEmail(loginUser.id!!, emailId)
        } catch (e: Exception) {
        }
        return "redirect:/user/editform/emails"
    }

    @RequestMapping(value = ["/user/email/sendValidationEmail/{emailId}"], method = [RequestMethod.POST, RequestMethod.GET])
    fun sendValidationEmail(
        @PathVariable emailId: Long,
        request: HttpServletRequest,
        authentication: Authentication?
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "error/403"

        try {
            val serverUrl = getServerUrl(request)
            userService.sendValidationEmail(loginUser.id!!, emailId, serverUrl)
        } catch (e: Exception) {
        }
        return "redirect:/user/editform/emails"
    }

    private fun getServerUrl(request: HttpServletRequest): String {
        val scheme = request.scheme
        val serverName = request.serverName
        val serverPort = request.serverPort
        return if (serverPort == 80 || serverPort == 443) {
            "$scheme://$serverName"
        } else {
            "$scheme://$serverName:$serverPort"
        }
    }

    @PostMapping("/user/editform/token_reset")
    fun resetToken(
        authentication: Authentication?
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "error/403"

        val rawToken = LocalDateTime.now().toString() + loginUser.loginId
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(rawToken.toByteArray(Charsets.UTF_8))
        val newToken = Base64.getEncoder().encodeToString(hash)
        
        loginUser.token = newToken
        userRepository.save(loginUser)

        return "redirect:/user/editform/token"
    }

    @PostMapping(value = ["/{loginId:^(?!projects|orgs|login|signup|user|site|admin|api|_notifications|notifications|files|favicon|assets|images|javascripts|stylesheets|webjars|bootstrap)[a-zA-Z0-9_.-]+}"])
    @ResponseBody
    fun resetUserPasswordBySiteManager(
        @PathVariable loginId: String,
        @RequestParam(value = "action", required = false) action: String?,
        authentication: Authentication?
    ): ResponseEntity<Map<String, Any>> {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (loginUser == null || !loginUser.isSiteManager) {
            return ResponseEntity.status(403).body(mapOf("isSuccess" to false, "reason" to "FORBIDDEN"))
        }

        if (action != "resetPassword") {
            return ResponseEntity.badRequest().body(mapOf("isSuccess" to false, "reason" to "BAD_REQUEST"))
        }

        val targetUser = userRepository.findByLoginId(loginId).orElse(null)
            ?: return ResponseEntity.status(404).body(mapOf("isSuccess" to false, "reason" to "USER_NOT_FOUND"))

        val newPassword = UUID.randomUUID().toString().substring(0, 6)
        val salt = UUID.randomUUID().toString().substring(0, 8)

        targetUser.passwordSalt = salt
        targetUser.password = hashPassword(newPassword, salt)
        userRepository.save(targetUser)

        return ResponseEntity.ok(mapOf(
            "loginId" to targetUser.loginId,
            "name" to targetUser.name,
            "newPassword" to newPassword,
            "isSuccess" to true
        ))
    }
}
