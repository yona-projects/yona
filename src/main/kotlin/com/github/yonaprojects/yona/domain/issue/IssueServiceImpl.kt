package com.github.yonaprojects.yona.domain.issue

import com.github.yonaprojects.yona.domain.attachment.AttachmentService
import com.github.yonaprojects.yona.domain.comment.CommentService
import com.github.yonaprojects.yona.domain.mention.MentionService
import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.notification.NotificationEvent
import com.github.yonaprojects.yona.domain.notification.NotificationEventRecorder
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.TitleHeadService
import com.github.yonaprojects.yona.domain.support.HistoryUtil
import com.github.yonaprojects.yona.domain.user.FavoriteIssueRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.watch.WatchService
import io.micrometer.core.instrument.MeterRegistry
import jakarta.persistence.EntityManager
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional
class IssueServiceImpl(
    private val issueRepository: IssueRepository,
    private val userRepository: UserRepository,
    private val milestoneRepository: MilestoneRepository,
    private val projectRepository: ProjectRepository,
    private val issueLabelRepository: IssueLabelRepository,
    private val notificationEventRecorder: NotificationEventRecorder,
    private val eventPublisher: ApplicationEventPublisher,
    private val issueCommentRepository: IssueCommentRepository,
    private val watchService: WatchService,
    private val issueEventRepository: IssueEventRepository,
    private val issueLabelService: IssueLabelService,
    private val titleHeadService: TitleHeadService,
    private val attachmentService: AttachmentService,
    private val favoriteIssueRepository: FavoriteIssueRepository,
    private val commentService: CommentService,
    private val mentionService: MentionService,
    // recordIssueEvent()가 위임하는 IssueEventRepository.recordWithDraftMerge()에 그대로 전달한다.
    private val meterRegistry: MeterRegistry,
    // nextIssueNumber()의 원자적 채번 UPDATE 이후, 이미 영속성 컨텍스트에 관리 중인 project
    // 엔티티의 lastIssueNumber 필드를 DB의 최신 값으로 다시 동기화하는 데 쓴다(JPQL 벌크 UPDATE는
    // 1차 캐시를 자동으로 갱신하지 않는다).
    private val entityManager: EntityManager
) : IssueService {

    override fun getIssuesByFilter(filter: IssueFilterType, user: User): List<Issue> {
        val userId = user.id!!
        return when (filter) {
            IssueFilterType.ASSIGNED -> issueRepository.findByAssignee_UserId(userId)
                .sortedByDescending { it.updatedDate }
            IssueFilterType.CREATED -> issueRepository.findByAuthorId(userId)
                .sortedByDescending { it.updatedDate }
            IssueFilterType.MENTIONED -> issueRepository.findAllById(mentionService.getMentioningIssueIds(userId))
                .sortedByDescending { it.updatedDate }
            IssueFilterType.FAVORITE -> favoriteIssueRepository.findByUserId(userId).map { it.issue }
                .sortedByDescending { it.updatedDate }
            IssueFilterType.ALL -> {
                val assigned = issueRepository.findByAssignee_UserId(userId)
                val created = issueRepository.findByAuthorId(userId)
                val mentioned = issueRepository.findAllById(mentionService.getMentioningIssueIds(userId))
                val favorite = favoriteIssueRepository.findByUserId(userId).map { it.issue }
                (assigned + created + mentioned + favorite)
                    .distinctBy { it.id }
                    .sortedByDescending { it.updatedDate }
            }
        }
    }

    // project.lastIssueNumber = project.lastIssueNumber + 1; projectRepository.save(project) 방식은
    // 잠금이 전혀 없어, 동시에 같은 프로젝트에 이슈를 만들면 두 트랜잭션이 같은 번호를 읽고 각각
    // 저장하려다 issue(project_id, number) UNIQUE 제약을 위반해 500 에러가 난다.
    //
    // @Lock(PESSIMISTIC_WRITE)로 프로젝트 행을 잠그는 방식은 H2(AUTO_SERVER=TRUE 파일 모드)에서
    // "select ... for update"가 실제로는 블로킹하지 않아(두 트랜잭션의 "for update" SELECT가
    // 상대방의 커밋을 기다리지 않은 채 같은 옛 값을 읽어감) 여전히 같은 버그가 재현됐다 — MariaDB
    // (InnoDB)에서는 정상 직렬화되지만, H2도 이 저장소가 공식 지원하는 DB 중 하나라 안전해야 한다.
    // UPDATE 문 자체의 행 잠금(모든 RDBMS가 갱신 시점에 즉시 배타 잠금을 거는 기본 동작)은 MVCC
    // 엔진에서도 흔들리지 않으므로, "증가 UPDATE 실행 → 그 결과값을 다시 SELECT"로 바꿔 Project
    // 엔티티의 Java 필드는 아예 건드리지 않는다(건드리면 트랜잭션 커밋 시점의 dirty checking이 이
    // 스테일한 값으로 되돌려 쓸 위험이 있다).
    private fun nextIssueNumber(project: Project): Long {
        projectRepository.incrementLastIssueNumber(project.id!!)
        val newNumber = projectRepository.findLastIssueNumber(project.id!!)
        // JPQL 벌크 UPDATE는 영속성 컨텍스트의 1차 캐시를 건드리지 않는다 — project가 이미 관리
        // 중인 엔티티라면(대부분 그렇다) 그 Java 필드는 여전히 증가 전 값을 들고 있어, 이후 같은
        // 세션 안에서 project를 다시 조회하는 코드가 스테일한 값을 볼 수 있다. refresh()로
        // DB의 최신 값을 즉시 동기화한다.
        if (entityManager.contains(project)) {
            entityManager.refresh(project)
        }
        return newNumber
    }

    override fun createIssue(
        issue: Issue,
        author: User,
        assigneeUser: User?,
        milestoneId: Long?,
        labelIds: List<Long>?,
        isDraft: Boolean,
        explicitNumber: Long?,
        sendNotification: Boolean
    ): Issue {
        val project = issue.project
        if (explicitNumber != null && explicitNumber > 0) {
            // yona AbstractPosting.saveWithNumber() 대응 — project.lastIssueNumber 카운터는
            // 건드리지 않고 지정된 번호를 그대로 쓴다.
            issue.number = explicitNumber
        } else {
            issue.number = nextIssueNumber(project)
        }
        issue.createdDate = Instant.now()
        issue.updatedDate = Instant.now()
        issue.authorId = author.id
        issue.authorLoginId = author.loginId
        issue.authorName = author.name

        issue.isDraft = isDraft
        issue.state = if (isDraft) State.DRAFT else State.OPEN

        if (assigneeUser != null) {
            issue.assignee = Assignee(user = assigneeUser, project = project)
        }

        if (milestoneId != null) {
            // id로만 조회하고 그 마일스톤이 이 이슈의 project 소속인지 검증하지 않으면(IDOR), REST
            // API로 labelIds/milestoneId를 직접 받는 이 경로가 다른(심지어 멤버가 아닌 PRIVATE)
            // 프로젝트의 마일스톤을 노출·연결하는 데 악용될 수 있다. project 소속이 아니면 조용히
            // 무시한다(웹 폼은 항상 자기 프로젝트 마일스톤만 보내므로 정상 사용에는 영향 없음).
            val milestone = milestoneRepository.findById(milestoneId).orElse(null)
            if (milestone != null && milestone.project.id == project.id) {
                issue.milestone = milestone
            }
        }

        if (!labelIds.isNullOrEmpty()) {
            // 위와 동일한 근본원인의 라벨 버전(IDOR 방지).
            val labels = issueLabelRepository.findAllById(labelIds).filter { it.project.id == project.id }
            issue.labels = labels.toMutableSet()
        }

        val savedIssue = issueRepository.save(issue)

        // 초안 여부와 무관하게 저장할 때마다 항상 멘션 인덱스를 동기화한다(알림 발송 여부와는 별개).
        mentionService.update(ResourceType.ISSUE_POST, savedIssue.id.toString(), commentService.extractMentionedUsers(savedIssue.body ?: ""))

        titleHeadService.saveTitleHeadKeyword(project, savedIssue.title)

        // 초안은 발행(publishIssue) 시점에야 처음 알림이 발행된다. sendNotification=false는
        // 마이그레이션으로 과거 이슈를 대량 삽입할 때 알림 폭주를 막는 용도.
        if (!isDraft && sendNotification) {
            publishNewIssueNotification(savedIssue, author)
        }

        return savedIssue
    }

    // 생성 시에도 이미 번호를 매기므로, 발행 시의 재채번은 초안이 예약해간 번호를 "발행 시점의 최신
    // 번호"로 대체하는 것이다(그 사이 다른 이슈가 먼저 발행됐다면 그만큼 밀림).
    override fun publishIssue(issueId: Long, publisher: User): Issue {
        val issue = issueRepository.findById(issueId).orElseThrow { IllegalArgumentException("Issue not found: $issueId") }
        val project = issue.project

        issue.createdDate = Instant.now()
        if (issue.state == State.DRAFT) {
            issue.state = State.OPEN
        }
        issue.isDraft = false

        issue.number = nextIssueNumber(project)

        issue.history = ""

        val savedIssue = issueRepository.save(issue)

        publishNewIssueNotification(savedIssue, publisher)

        return savedIssue
    }

    // 이슈를 새로 만들 때뿐 아니라, 다른 프로젝트로 이동했을 때도 "그 프로젝트에 새로 생긴 이슈"로서
    // 동일한 형식의 알림을 다시 발행한다(moveIssue()) — sender만 다르다(생성 시=작성자, 이동
    // 시=이동을 실행한 사용자).
    private fun publishNewIssueNotification(issue: Issue, sender: User) {
        val title = "[${issue.project.name}] 신규 이슈 등록: #${issue.number} ${issue.title}"
        val notificationEvent = NotificationEvent(
            title = title,
            senderId = sender.id,
            created = Instant.now(),
            resourceType = ResourceType.ISSUE_POST,
            resourceId = issue.id.toString(),
            eventType = EventType.NEW_ISSUE,
            newValue = title
        )

        val receivers = watchService.findActualWatchers(
            baseWatchers = setOf(sender),
            resourceType = ResourceType.ISSUE_POST,
            resourceId = issue.id.toString(),
            projectId = issue.project.id,
            eventType = notificationEvent.eventType
        ).toMutableSet()
        // 신규 이슈 본문의 @멘션도 수신자에 포함한다.
        receivers.addAll(commentService.extractMentionedUsers(issue.body ?: ""))
        receivers.removeIf { it.id == sender.id }
        notificationEvent.receivers = receivers

        notificationEventRecorder.record(notificationEvent)?.let { eventPublisher.publishEvent(it) }
    }

    override fun updateIssue(
        issueId: Long,
        title: String,
        body: String,
        updater: User,
        assigneeUser: User?,
        milestoneId: Long?,
        labelIds: List<Long>?
    ): Issue {
        val issue = issueRepository.findById(issueId).orElseThrow { IllegalArgumentException("Issue not found") }
        val oldBody = issue.body
        val oldLabelNames = issue.labels.map { it.name }.sorted()
        val oldTitle = issue.title

        issue.title = title
        issue.body = (body)
        issue.updatedDate = Instant.now()

        // history 유무와 무관하게 편집이 있을 때마다 항상 갱신된다.
        issue.updatedByAuthorId = updater.id
        issue.updatedByAuthorLoginId = updater.loginId
        issue.updatedByAuthorName = updater.name

        if ((oldBody ?: "") != body) {
            issue.history = HistoryUtil.appendHistory(
                originalBody = oldBody,
                newBody = body,
                updaterName = updater.name,
                updaterLoginId = updater.loginId ?: "",
                updatedDate = issue.updatedDate,
                existingHistory = issue.history
            )
        }

        if (assigneeUser != null) {
            issue.assignee = Assignee(user = assigneeUser, project = issue.project)
        } else {
            issue.assignee = null
        }

        if (milestoneId != null) {
            // createIssue와 동일하게 다른 프로젝트 소속 마일스톤은 조용히 무시한다(IDOR 방지).
            val milestone = milestoneRepository.findById(milestoneId).orElse(null)
            issue.milestone = if (milestone != null && milestone.project.id == issue.project.id) milestone else null
        } else {
            issue.milestone = null
        }

        if (labelIds != null) {
            // 위와 동일한 근본원인의 라벨 버전(IDOR 방지).
            val labels = issueLabelRepository.findAllById(labelIds).filter { it.project.id == issue.project.id }
            issue.labels = labels.toMutableSet()
        } else {
            issue.labels.clear()
        }

        // 이슈 수정마다 호출하는 검증(생성 시점에는 호출 안 함). 같은 배타(exclusive) 카테고리의
        // 라벨을 두 개 이상 붙일 수 없다.
        checkExclusiveLabelCategories(issue.labels)

        val savedIssue = issueRepository.save(issue)

        // 본문이 안 바뀌었어도 매 수정마다 무조건 재동기화한다(변화 없으면 diff-sync가 no-op).
        mentionService.update(ResourceType.ISSUE_POST, savedIssue.id.toString(), commentService.extractMentionedUsers(savedIssue.body ?: ""))

        // 제목이 안 바뀌었어도 매 수정마다 무조건 두 호출을 모두 실행한다(그런 경우 새 키워드
        // +1/-1이 상쇄돼 관찰 가능한 순변화는 없다).
        titleHeadService.saveTitleHeadKeyword(savedIssue.project, savedIssue.title)
        titleHeadService.deleteTitleHeadKeyword(savedIssue.project, oldTitle)

        if (oldBody != body) {
            recordIssueEvent(savedIssue, EventType.ISSUE_BODY_CHANGED, updater.loginId!!, oldBody, body)

            // yona NotificationEvent.afterIssueBodyChanged() 대응 — 지금까지 yona는 ISSUE_BODY_CHANGED를
            // IssueEvent(타임라인)에만 기록하고 알림 메일은 보내지 않고 있었다(PostingServiceImpl의
            // POSTING_BODY_CHANGED와 달리 누락돼 있던 부분). getMandatoryReceivers()의 "본문에서 새로
            // @멘션된 사용자 추가" 세부 로직은 이 파일의 다른 이슈 이벤트들과 마찬가지로 watcher 기반
            // 통지로 단순화한다(멘션 감지는 댓글 쪽(CommentServiceImpl)에만 있고 이슈 본문 수정에는
            // 아직 없다 — 별도 항목).
            val bodyChangedTitle = "[${savedIssue.project.name}] 이슈 #${savedIssue.number} 본문 수정: ${savedIssue.title}"
            val bodyChangedEvent = NotificationEvent(
                title = bodyChangedTitle,
                senderId = updater.id,
                created = Instant.now(),
                resourceType = ResourceType.ISSUE_POST,
                resourceId = savedIssue.id.toString(),
                eventType = EventType.ISSUE_BODY_CHANGED,
                oldValue = oldBody,
                newValue = body
            )
            val bodyChangedReceivers = watchService.findActualWatchers(
                baseWatchers = setOf(updater),
                resourceType = ResourceType.ISSUE_POST,
                resourceId = savedIssue.id.toString(),
                projectId = savedIssue.project.id,
                eventType = bodyChangedEvent.eventType
            ).toMutableSet()
            bodyChangedReceivers.removeIf { it.id == updater.id }
            bodyChangedEvent.receivers = bodyChangedReceivers

            notificationEventRecorder.record(bodyChangedEvent)?.let { eventPublisher.publishEvent(it) }
        }

        val newLabelNames = savedIssue.labels.map { it.name }.sorted()
        if (oldLabelNames != newLabelNames) {
            recordIssueEvent(
                savedIssue,
                EventType.ISSUE_LABEL_CHANGED,
                updater.loginId!!,
                oldLabelNames.joinToString(", "),
                newLabelNames.joinToString(", "),
                skipWaypoint = false
            )
        }

        return savedIssue
    }

    override fun changeState(issueId: Long, newState: State, updaterLoginId: String): Issue {
        val issue = issueRepository.findById(issueId).orElseThrow { IllegalArgumentException("Issue not found") }
        val oldState = issue.state
        if (oldState == newState) {
            return issue
        }

        issue.state = newState
        issue.updatedDate = Instant.now()
        val savedIssue = issueRepository.save(issue)

        val updater = userRepository.findByLoginId(updaterLoginId).orElse(null)
        val title = "[${issue.project.name}] 이슈 #${issue.number} 상태 변경: $oldState -> $newState"
        val notificationEvent = NotificationEvent(
            title = title,
            senderId = updater?.id,
            created = Instant.now(),
            resourceType = ResourceType.ISSUE_STATE,
            resourceId = savedIssue.id.toString(),
            eventType = EventType.ISSUE_STATE_CHANGED,
            oldValue = oldState.toString(),
            newValue = newState.toString()
        )

        // 감시자(Watch) 추가
        val authorUser = issue.authorId?.let { userRepository.findById(it).orElse(null) }
        val baseWatchers = if (authorUser != null) setOf(authorUser) else emptySet()
        val receivers = watchService.findActualWatchers(
            baseWatchers = baseWatchers,
            resourceType = ResourceType.ISSUE_POST,
            resourceId = savedIssue.id.toString(),
            projectId = issue.project.id,
            eventType = notificationEvent.eventType
        ).toMutableSet()
        if (updater != null) {
            receivers.removeIf { it.id == updater.id }
        }
        notificationEvent.receivers = receivers

        notificationEventRecorder.record(notificationEvent)?.let { eventPublisher.publishEvent(it) }

        recordIssueEvent(savedIssue, EventType.ISSUE_STATE_CHANGED, updaterLoginId, oldState.toString(), newState.toString())

        return savedIssue
    }

    override fun changeAssignee(issueId: Long, newAssigneeUser: User?, updaterLoginId: String): Issue {
        val issue = issueRepository.findById(issueId).orElseThrow { IllegalArgumentException("Issue not found") }
        val oldAssignee = issue.assignee?.user
        if (oldAssignee?.id == newAssigneeUser?.id) {
            return issue
        }

        if (newAssigneeUser == null) {
            issue.assignee = null
        } else {
            issue.assignee = Assignee(user = newAssigneeUser, project = issue.project)
        }
        issue.updatedDate = Instant.now()
        val savedIssue = issueRepository.save(issue)

        val updater = userRepository.findByLoginId(updaterLoginId).orElse(null)
        val title = "[${issue.project.name}] 이슈 #${issue.number} 담당자 변경"
        val notificationEvent = NotificationEvent(
            title = title,
            senderId = updater?.id,
            created = Instant.now(),
            resourceType = ResourceType.ISSUE_ASSIGNEE,
            resourceId = savedIssue.id.toString(),
            eventType = EventType.ISSUE_ASSIGNEE_CHANGED,
            oldValue = oldAssignee?.name,
            newValue = newAssigneeUser?.name
        )

        // 감시자(Watch) 추가
        val authorUser = issue.authorId?.let { userRepository.findById(it).orElse(null) }
        val baseWatchers = if (authorUser != null) setOf(authorUser) else emptySet()
        val receivers = watchService.findActualWatchers(
            baseWatchers = baseWatchers,
            resourceType = ResourceType.ISSUE_POST,
            resourceId = savedIssue.id.toString(),
            projectId = issue.project.id,
            eventType = notificationEvent.eventType
        ).toMutableSet()
        if (updater != null) {
            receivers.removeIf { it.id == updater.id }
        }
        notificationEvent.receivers = receivers

        notificationEventRecorder.record(notificationEvent)?.let { eventPublisher.publishEvent(it) }

        recordIssueEvent(savedIssue, EventType.ISSUE_ASSIGNEE_CHANGED, updaterLoginId, oldAssignee?.name, newAssigneeUser?.name)

        return savedIssue
    }

    override fun changeMilestone(issueId: Long, newMilestoneId: Long?, updaterLoginId: String): Issue {
        val issue = issueRepository.findById(issueId).orElseThrow { IllegalArgumentException("Issue not found") }
        val oldMilestone = issue.milestone
        if (oldMilestone?.id == newMilestoneId) {
            return issue
        }

        if (newMilestoneId == null) {
            issue.milestone = null
        } else {
            val milestone = milestoneRepository.findById(newMilestoneId).orElse(null)
            issue.milestone = milestone
        }
        issue.updatedDate = Instant.now()
        val savedIssue = issueRepository.save(issue)

        val updater = userRepository.findByLoginId(updaterLoginId).orElse(null)
        val title = "[${issue.project.name}] 이슈 #${issue.number} 마일스톤 변경"
        val notificationEvent = NotificationEvent(
            title = title,
            senderId = updater?.id,
            created = Instant.now(),
            resourceType = ResourceType.ISSUE_MILESTONE,
            resourceId = savedIssue.id.toString(),
            eventType = EventType.ISSUE_MILESTONE_CHANGED,
            oldValue = oldMilestone?.title,
            newValue = issue.milestone?.title
        )

        // 감시자(Watch) 추가
        val authorUser = issue.authorId?.let { userRepository.findById(it).orElse(null) }
        val baseWatchers = if (authorUser != null) setOf(authorUser) else emptySet()
        val receivers = watchService.findActualWatchers(
            baseWatchers = baseWatchers,
            resourceType = ResourceType.ISSUE_POST,
            resourceId = savedIssue.id.toString(),
            projectId = issue.project.id,
            eventType = notificationEvent.eventType
        ).toMutableSet()
        if (updater != null) {
            receivers.removeIf { it.id == updater.id }
        }
        notificationEvent.receivers = receivers

        notificationEventRecorder.record(notificationEvent)?.let { eventPublisher.publishEvent(it) }

        recordIssueEvent(savedIssue, EventType.ISSUE_MILESTONE_CHANGED, updaterLoginId, oldMilestone?.title, issue.milestone?.title)

        return savedIssue
    }

    // 권한 확인(대상 프로젝트 생성권한/원본 이슈 수정권한)은 이 저장소의 다른 서비스 메서드들과
    // 동일하게 컨트롤러 쪽 책임이라 여기서는 하지 않는다 — 컨트롤러는 이동을 호출하기 전에 두
    // 권한을 모두 먼저 확인해야 한다(순서가 반대면 권한 없이도 이동이 일부 반영되는 인가 우회
    // 허점이 생긴다).
    override fun moveIssue(issueId: Long, targetProjectId: Long, mover: User): Issue {
        val issue = issueRepository.findById(issueId).orElseThrow { IllegalArgumentException("Issue not found: $issueId") }
        val previous = issue.project

        // yona isRequestedToOtherProject() 대응 — 같은 프로젝트면 아무 것도 하지 않는다.
        if (previous.id == targetProjectId) {
            return issue
        }

        val targetProject = projectRepository.findById(targetProjectId)
            .orElseThrow { IllegalArgumentException("Project not found: $targetProjectId") }

        // yona editIssue()의 "Set<User> fromWatchers = originalIssue.getWatchers()" 대응 — 이동
        // 시점(=기존 프로젝트 기준)의 감시자를 미리 캡처해둔다. 이동 후에 계산하면 새 프로젝트의
        // 뮤트/권한 설정을 기준으로 잘못 계산되므로 반드시 이동 직전에 캡처해야 한다.
        val baseWatchers = mutableSetOf<User>()
        issue.authorId?.let { authorId -> userRepository.findById(authorId).ifPresent { baseWatchers.add(it) } }
        issue.assignee?.user?.let { baseWatchers.add(it) }
        baseWatchers.addAll(issue.voters)
        val fromWatchers = watchService.findActualWatchers(
            baseWatchers = baseWatchers,
            resourceType = ResourceType.ISSUE_POST,
            resourceId = issue.id.toString(),
            projectId = previous.id,
            eventType = EventType.ISSUE_MOVED
        )

        // yona isFromMyOwnPrivateProject() 대응.
        val fromOwnPrivateProject = previous.isPrivate && previous.owner.equals(mover.loginId, ignoreCase = true)

        moveIssueAndSubtasksToProject(issue, targetProject, mover)

        if (fromOwnPrivateProject) {
            // yona editIssue() preUpdateHook의 isFromMyOwnPrivateProject() 분기 대응 — 알림 없이
            // 이력만 비운다(자신의 비공개 프로젝트에서 옮겨질 때 그 안의 편집 이력이 노출되지 않도록).
            issue.history = ""
        } else if (!issue.isDraft) {
            // yona addIssueMovedNotification()의 "!issue.isDraft"면 ISSUE_MOVED+NEW_ISSUE를 모두
            // 발행하는 것과 동일 — 초안(draft)이면 어느 쪽도 발행하지 않는다.
            publishIssueMovedNotification(previous, issue, fromWatchers, mover)
            publishNewIssueNotification(issue, mover)
        }

        return issueRepository.save(issue)
    }

    // yona moveIssueToOtherProject()/moveSubtaskToOtherProject() 대응 — 이슈 자신과 직계 서브태스크를
    // 모두 대상 프로젝트로 옮긴다. 서브태스크는(legacy와 동일하게) 별도 알림을 받지 않는다.
    private fun moveIssueAndSubtasksToProject(issue: Issue, targetProject: Project, mover: User) {
        updateIssueToOtherProject(issue, targetProject, mover)
        for (subtask in issueRepository.findByParentId(issue.id!!)) {
            updateIssueToOtherProject(subtask, targetProject, mover)
            issueRepository.save(subtask)
        }
    }

    // yona updateIssueToOtherProject() 대응.
    private fun updateIssueToOtherProject(issue: Issue, targetProject: Project, mover: User) {
        issue.project = targetProject
        issue.number = nextIssueNumber(targetProject)
        issue.createdDate = Instant.now()
        issue.updatedDate = Instant.now()
        issue.milestone = null

        for (comment in issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(issue.id!!)) {
            comment.projectId = targetProject.id
            issueCommentRepository.save(comment)
        }

        // yona "UserApp.currentUser().isMemberOf(toOtherProject) && issue.labels.size() > 0"의
        // transferLabels()/그 외에는 라벨을 비우는 분기 대응.
        val originalLabels = issue.labels.toSet()
        issue.labels = if (mover.isMemberOf(targetProject) && originalLabels.isNotEmpty()) {
            issueLabelService.transferLabelsForIssue(originalLabels, targetProject).toMutableSet()
        } else {
            mutableSetOf()
        }

        issueRepository.save(issue)
    }

    // yona NotificationEvent.afterIssueMoved(previous, issue, fromWatchers) 대응. title은
    // formatReplyTitle(issue)와 동일하게 이동 "이후"(새 프로젝트로 옮겨진 뒤) 시점의 값을 쓴다.
    // legacy와 마찬가지로 mover 자신을 수신자에서 제외하지 않는다 — 본인이 watcher/author/assignee/
    // voter였다면 자신이 실행한 이동에 대한 알림도 함께 받는다(다른 알림들과 달리 self-제외가 없음).
    private fun publishIssueMovedNotification(previous: Project, issue: Issue, receivers: Set<User>, mover: User) {
        val oldValue = "${previous.owner}/${previous.name}"
        val newValue = "${issue.project.owner}/${issue.project.name}"

        val notificationEvent = NotificationEvent(
            title = "Re: [${issue.project.name}] ${issue.title} (#${issue.number})",
            senderId = mover.id,
            created = Instant.now(),
            resourceType = ResourceType.ISSUE_POST,
            resourceId = issue.id.toString(),
            eventType = EventType.ISSUE_MOVED,
            oldValue = oldValue,
            newValue = newValue
        )
        notificationEvent.receivers = receivers.toMutableSet()

        notificationEventRecorder.record(notificationEvent)?.let { eventPublisher.publishEvent(it) }

        // 알림과 함께 이슈 타임라인에도 이동 이력을 남긴다.
        recordIssueEvent(issue, EventType.ISSUE_MOVED, mover.loginId!!, oldValue, newValue)
    }

    // 같은 배타(exclusive) 카테고리의 라벨이 두 개 이상이면 거부한다. Set 순회 순서에 의존하지 않고
    // "이미 본 배타 카테고리"를 누적하며 검사하므로, 라벨이 몇 개든 어떤 순서로 순회되든 동일한
    // 결과가 나온다.
    private fun checkExclusiveLabelCategories(labels: Set<IssueLabel>) {
        val seenExclusiveCategories = mutableSetOf<Long>()
        for (label in labels) {
            val categoryId = label.category.id ?: continue
            if (categoryId in seenExclusiveCategories) {
                throw IssueLabelExclusiveCategoryException(
                    "This category does not allow an issue to have two or more labels of the category"
                )
            }
            if (label.category.isExclusive) {
                seenExclusiveCategories.add(categoryId)
            }
        }
    }

    private fun recordIssueEvent(
        issue: Issue,
        eventType: EventType,
        senderLoginId: String,
        oldValue: String?,
        newValue: String?,
        skipWaypoint: Boolean = true
    ) {
        val issueEvent = IssueEvent(
            issue = issue,
            senderLoginId = senderLoginId,
            senderEmail = userRepository.findByLoginId(senderLoginId).map { it.email }.orElse(null),
            oldValue = oldValue,
            newValue = newValue,
            created = Instant.now(),
            eventType = eventType
        )
        issueEventRepository.recordWithDraftMerge(issueEvent, skipWaypoint, meterRegistry)
    }

    override fun voteIssue(issueId: Long, user: User) {
        val issue = issueRepository.findById(issueId)
            .orElseThrow { IllegalArgumentException("Issue not found: $issueId") }
        val dbUser = userRepository.findById(user.id!!)
            .orElseThrow { IllegalArgumentException("User not found: ${user.id}") }

        if (issue.voters.any { it.id == dbUser.id }) {
            throw IllegalStateException("이미 투표하였습니다.")
        }
        issue.voters.add(dbUser)
        issueRepository.save(issue)
    }


    override fun upvoteWeight(issueId: Long): Issue {
        val issue = issueRepository.findById(issueId)
            .orElseThrow { IllegalArgumentException("Issue not found: $issueId") }
        issue.weight = issue.weight + 1
        return issueRepository.save(issue)
    }

    override fun downvoteWeight(issueId: Long): Issue {
        val issue = issueRepository.findById(issueId)
            .orElseThrow { IllegalArgumentException("Issue not found: $issueId") }
        issue.weight = issue.weight - 1
        return issueRepository.save(issue)
    }


    // IssueComment/IssueEvent/FavoriteIssue는 issue FK가 nullable=false라 반드시 먼저 삭제해야
    // issueRepository.delete(issue)가 FK 제약 위반 없이 성공한다(assignee/sharers/labels/voters는
    // Issue 엔티티 자체의 cascade로 처리됨).
    override fun deleteIssueCascade(issue: Issue) {
        val comments = issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(issue.id!!)
        for (comment in comments) {
            attachmentService.deleteAll(ResourceType.ISSUE_COMMENT, comment.id.toString())
        }
        attachmentService.deleteAll(ResourceType.ISSUE_POST, issue.id.toString())
        watchService.deleteAll(ResourceType.ISSUE_POST, issue.id.toString())
        titleHeadService.deleteTitleHeadKeyword(issue.project, issue.title)

        favoriteIssueRepository.deleteAll(favoriteIssueRepository.findByIssueId(issue.id!!))
        issueEventRepository.deleteAll(issueEventRepository.findByIssueOrderByCreatedAsc(issue))
        // 답글(parentComment)이 원 댓글보다 항상 나중에 생성되므로, 생성일 역순으로 지우면
        // 답글이 부모보다 먼저 삭제돼 자기참조 FK(parent_comment_id) 위반을 피할 수 있다.
        issueCommentRepository.deleteAll(comments.asReversed())
        issueRepository.delete(issue)
    }

    override fun unvoteIssue(issueId: Long, user: User) {
        val issue = issueRepository.findById(issueId)
            .orElseThrow { IllegalArgumentException("Issue not found: $issueId") }
        val dbUser = userRepository.findById(user.id!!)
            .orElseThrow { IllegalArgumentException("User not found: ${user.id}") }

        val voter = issue.voters.find { it.id == dbUser.id }
            ?: throw IllegalStateException("투표하지 않은 이슈입니다.")
        issue.voters.remove(voter)
        issueRepository.save(issue)
    }

    override fun voteComment(commentId: Long, user: User) {
        val comment = issueCommentRepository.findById(commentId)
            .orElseThrow { IllegalArgumentException("IssueComment not found: $commentId") }
        val dbUser = userRepository.findById(user.id!!)
            .orElseThrow { IllegalArgumentException("User not found: ${user.id}") }

        if (comment.voters.any { it.id == dbUser.id }) {
            throw IllegalStateException("이미 투표하였습니다.")
        }
        comment.voters.add(dbUser)
        issueCommentRepository.save(comment)
    }

    override fun unvoteComment(commentId: Long, user: User) {
        val comment = issueCommentRepository.findById(commentId)
            .orElseThrow { IllegalArgumentException("IssueComment not found: $commentId") }
        val dbUser = userRepository.findById(user.id!!)
            .orElseThrow { IllegalArgumentException("User not found: ${user.id}") }

        val voter = comment.voters.find { it.id == dbUser.id }
            ?: throw IllegalStateException("투표하지 않은 댓글입니다.")
        comment.voters.remove(voter)
        issueCommentRepository.save(comment)
    }
}