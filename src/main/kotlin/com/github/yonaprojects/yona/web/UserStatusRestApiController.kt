package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.mention.MentionService
import com.github.yonaprojects.yona.domain.notification.NotificationEventRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.user.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

// yona-wiki P3-02 16라운드(TASK-0440) — `gh status`("내가 구독 중인 모든 저장소에 걸친 작업 현황"
// 대시보드) 대응. 이미 구현된 UserIssueStatusRestApiController(GET /api/v1/user/issues/status,
// `gh issue status` 대응)는 이슈만 다뤄 gh status의 부분집합이다. 이 컨트롤러는 gh status의 5개
// 구성요소를 재감사한 결과에 따라 범위를 정했다:
// - Assigned Issues: 기존 UserIssueStatusRestApiController와 동일한 IssueRepository 메서드 재사용.
// - Assigned Pull Requests / Review Requests: PullRequestServiceImpl.setAssignee/addReviewer(각각
//   13/12라운드)로 데이터 모델은 이미 있었지만 "로그인 사용자 전체"를 대상으로 한 조회가 없어
//   PullRequestRepository에 신규 쿼리 2쌍만 추가했다(신규 서비스 로직 없음).
// - Mentions: MentionService.getMentioningIssueIds()가 이미 있으나 ISSUE_POST/ISSUE_COMMENT만
//   다룬다 — PullRequest 본문·리뷰 코멘트에는 멘션 감지가 아예 연결돼 있지 않다
//   (PullRequestServiceImpl/ReviewComment 어디에도 mentionService.update() 호출이 없음, 전수
//   확인). 따라서 이 섹션은 "이슈 멘션만" 노출하고(gh status처럼 이슈+PR 멘션 통합은 불가),
//   PR 멘션 감지 자체를 새로 설계하지는 않는다(범위 밖 — 있는 기능을 노출하는 게 원칙).
// - Repository Activity: 신규 기능처럼 보이지만 실제로는 NotificationEvent + WatchService의
//   findActualWatchers()가 이미 "내가 watch하는 프로젝트에 새 이슈/PR/댓글이 생기면 나를
//   receiver로 알림 이벤트를 남긴다"를 구현해뒀다(IssueServiceImpl.createIssue() 등). 이미
//   `/api/notifications`(레거시, 세션 인증 전용)로 노출돼 있던 것과 같은 데이터를 이 엔드포인트
//   에도 얇게 포함시켰다 — 신규 서비스 로직 없이 NotificationEventRepository.findByReceiver()만
//   재사용.
@RestController
@RequestMapping("/api/v1/user")
class UserStatusRestApiController(
    private val issueRepository: IssueRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val mentionService: MentionService,
    private val notificationEventRepository: NotificationEventRepository,
    private val userRepository: UserRepository
) {

    data class StatusSectionResponse<T>(
        val openCount: Long,
        val closedCount: Long,
        val items: List<T>
    )

    // NotificationController.NotificationResponse와 동일한 최소 필드 구성 — NotificationEvent는
    // resourceType/eventType만으로도 CLI가 사람이 읽을 문구를 만들 수 있고(예: "NEW_ISSUE" ->
    // "새 이슈"), 엔티티를 중첩하지 않으니 순환 직렬화 위험도 없다.
    data class RepositoryActivityItemResponse(
        val id: Long,
        val title: String,
        val eventType: String,
        val resourceType: String,
        val resourceId: String,
        val senderId: Long?,
        val created: Instant?
    )

    data class UserStatusResponse(
        val assignedIssues: StatusSectionResponse<IssueResponse>,
        val assignedPullRequests: StatusSectionResponse<PullRequestResponse>,
        val reviewRequests: StatusSectionResponse<PullRequestResponse>,
        val mentionedIssues: StatusSectionResponse<IssueResponse>,
        val repositoryActivity: List<RepositoryActivityItemResponse>
    )

    // gh status 자체가 --state 플래그 없이 항상 "현재 열려있는 것"만 보여주는 대시보드라
    // (`gh status --help` 실측 확인) state 파라미터를 두지 않고 OPEN으로 고정한다. 각 섹션의
    // openCount/closedCount는 배지 용도로 그대로 계산해 함께 내려준다.
    @GetMapping("/status")
    fun status(authentication: Authentication?): ResponseEntity<UserStatusResponse> {
        val user = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val userId = user.id!!
        val pageable = PageRequest.of(0, 20)

        val assignedIssuesPage = issueRepository.findByAssigneeAndState(userId, State.OPEN, null, pageable)
        val assignedPrPage = pullRequestRepository.findByAssigneeUserIdAndState(userId, State.OPEN, pageable)
        val reviewRequestPage = pullRequestRepository.findByReviewerIdAndState(userId, State.OPEN, pageable)

        val mentionedIssueIds = mentionService.getMentioningIssueIds(userId)
        val mentionedIssuesPage = if (mentionedIssueIds.isEmpty()) {
            Page.empty(pageable)
        } else {
            issueRepository.findMentionedByState(mentionedIssueIds, State.OPEN, null, pageable)
        }

        val activity = notificationEventRepository.findByReceiver(user, PageRequest.of(0, 20)).content.map { event ->
            RepositoryActivityItemResponse(
                id = event.id ?: 0L,
                title = event.title,
                eventType = event.eventType.name,
                resourceType = event.resourceType.name,
                resourceId = event.resourceId,
                senderId = event.senderId,
                created = event.created
            )
        }

        return ResponseEntity.ok(
            UserStatusResponse(
                assignedIssues = StatusSectionResponse(
                    openCount = issueRepository.countByAssigneeAndState(userId, State.OPEN),
                    closedCount = issueRepository.countByAssigneeAndState(userId, State.CLOSED),
                    items = assignedIssuesPage.content.map { it.toResponse() }
                ),
                assignedPullRequests = StatusSectionResponse(
                    openCount = pullRequestRepository.countByAssigneeUserIdAndState(userId, State.OPEN),
                    closedCount = pullRequestRepository.countByAssigneeUserIdAndState(userId, State.CLOSED),
                    items = assignedPrPage.content.map { it.toResponse() }
                ),
                reviewRequests = StatusSectionResponse(
                    openCount = pullRequestRepository.countByReviewerIdAndState(userId, State.OPEN),
                    closedCount = pullRequestRepository.countByReviewerIdAndState(userId, State.CLOSED),
                    items = reviewRequestPage.content.map { it.toResponse() }
                ),
                mentionedIssues = StatusSectionResponse(
                    openCount = if (mentionedIssueIds.isEmpty()) 0L else issueRepository.countMentionedByState(mentionedIssueIds, State.OPEN),
                    closedCount = if (mentionedIssueIds.isEmpty()) 0L else issueRepository.countMentionedByState(mentionedIssueIds, State.CLOSED),
                    items = mentionedIssuesPage.content.map { it.toResponse() }
                ),
                repositoryActivity = activity
            )
        )
    }
}
