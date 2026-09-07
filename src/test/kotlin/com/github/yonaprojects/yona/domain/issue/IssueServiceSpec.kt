package com.github.yonaprojects.yona.domain.issue

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.TitleHeadRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.notification.NotificationEventRepository
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.milestone.Milestone
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.FavoriteIssue
import com.github.yonaprojects.yona.domain.user.FavoriteIssueRepository
import com.github.yonaprojects.yona.domain.watch.Watch
import com.github.yonaprojects.yona.domain.watch.WatchRepository
import com.github.yonaprojects.yona.domain.mention.MentionService
import com.github.yonaprojects.yona.domain.mention.MentionRepository
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationUser
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.assertions.throwables.shouldThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Transactional
class IssueServiceSpec @Autowired constructor(
    private val issueService: IssueService,
    private val issueRepository: IssueRepository,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val notificationEventRepository: NotificationEventRepository,
    private val issueCommentRepository: IssueCommentRepository,
    private val issueEventRepository: IssueEventRepository,
    private val milestoneRepository: MilestoneRepository,
    private val issueLabelRepository: IssueLabelRepository,
    private val issueLabelCategoryRepository: IssueLabelCategoryRepository,
    private val watchRepository: WatchRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val titleHeadRepository: TitleHeadRepository,
    private val favoriteIssueRepository: FavoriteIssueRepository,
    private val mentionService: MentionService,
    private val mentionRepository: MentionRepository,
    private val organizationRepository: OrganizationRepository,
    private val organizationUserRepository: OrganizationUserRepository,
    private val roleRepository: RoleRepository
) : AbstractIntegrationTest() {

    init {
        describe("IssueService 비즈니스 테스트") {
            beforeEach {
                mentionRepository.deleteAll()
                organizationUserRepository.deleteAll()
                organizationRepository.deleteAll()
                favoriteIssueRepository.deleteAll()
                watchRepository.deleteAll()
                issueEventRepository.deleteAll()
                notificationEventRepository.deleteAll()
                issueCommentRepository.deleteAll()
                projectUserRepository.deleteAll()
                issueRepository.deleteAll()
                issueLabelRepository.deleteAll()
                issueLabelCategoryRepository.deleteAll()
                milestoneRepository.deleteAll()
                titleHeadRepository.deleteAll()
                projectRepository.deleteAll()
                userRepository.deleteAll()
            }

            it("이슈 상태를 변경하면 상태가 DB에 갱신되고 알림 이벤트가 생성되어야 한다") {
                // Given
                val author = userRepository.save(
                    User(loginId = "tester", name = "테스터", email = "tester@yona.io")
                )
                val project = projectRepository.save(
                    Project(name = "test-project", owner = "tester", projectScope = ProjectScope.PUBLIC)
                )
                // NotificationEventRecorder(P1-27)는 legacy와 동일하게 수신자가 없으면 저장하지 않으므로
                // (본인이 본인 이슈 상태를 바꾸면 본인은 수신자에서 제외된다), 실제 수신자가 될 프로젝트
                // 감시자를 한 명 둔다.
                val watcher = userRepository.save(User(loginId = "watcher", name = "감시자", email = "watcher@yona.io"))
                watchRepository.save(Watch(user = watcher, resourceType = ResourceType.PROJECT, resourceId = project.id.toString()))

                val issue = Issue(
                    title = "수정할 버그",
                    body = "버그 설명입니다.",
                    project = project,
                    authorId = author.id,
                    authorLoginId = author.loginId,
                    authorName = author.name,
                    createdDate = Instant.now(),
                    state = State.OPEN
                )
                val savedIssue = issueRepository.save(issue)

                // When
                val updatedIssue = issueService.changeState(savedIssue.id!!, State.CLOSED, "tester")

                // Then
                updatedIssue.state shouldBe State.CLOSED

                // 알림 이벤트 생성 검증
                val events = notificationEventRepository.findAll()
                events.size shouldBe 1
                val event = events.first()
                event.eventType shouldBe EventType.ISSUE_STATE_CHANGED
                event.oldValue shouldBe State.OPEN.toString()
                event.newValue shouldBe State.CLOSED.toString()
                event.senderId shouldBe author.id

                // 이슈 타임라인(IssueEvent) 생성 검증 (P1-07)
                val issueEvents = issueEventRepository.findByIssueOrderByCreatedAsc(savedIssue)
                issueEvents.size shouldBe 1
                issueEvents.first().eventType shouldBe EventType.ISSUE_STATE_CHANGED
                issueEvents.first().oldValue shouldBe State.OPEN.toString()
                issueEvents.first().newValue shouldBe State.CLOSED.toString()
                issueEvents.first().senderLoginId shouldBe "tester"
            }

            it("담당자를 변경하면 IssueEvent 타임라인 항목이 생성되어야 한다") {
                val author = userRepository.save(User(loginId = "tester2", name = "테스터2", email = "tester2@yona.io"))
                val assignee = userRepository.save(User(loginId = "assignee1", name = "담당자", email = "assignee1@yona.io"))
                val project = projectRepository.save(Project(name = "assignee-test-project", owner = "tester2"))
                val issue = Issue(
                    title = "담당자 배정 테스트", body = "...", project = project,
                    authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                    createdDate = Instant.now(), state = State.OPEN
                )
                val savedIssue = issueRepository.save(issue)

                issueService.changeAssignee(savedIssue.id!!, assignee, "tester2")

                val issueEvents = issueEventRepository.findByIssueOrderByCreatedAsc(savedIssue)
                issueEvents.size shouldBe 1
                issueEvents.first().eventType shouldBe EventType.ISSUE_ASSIGNEE_CHANGED
                issueEvents.first().newValue shouldBe assignee.name
            }

            it("마일스톤을 변경하면 IssueEvent 타임라인 항목이 생성되어야 한다") {
                val author = userRepository.save(User(loginId = "tester3", name = "테스터3", email = "tester3@yona.io"))
                val project = projectRepository.save(Project(name = "milestone-test-project", owner = "tester3"))
                val milestone = milestoneRepository.save(
                    Milestone(title = "1.0 출시", project = project)
                )
                val issue = Issue(
                    title = "마일스톤 배정 테스트", body = "...", project = project,
                    authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                    createdDate = Instant.now(), state = State.OPEN
                )
                val savedIssue = issueRepository.save(issue)

                val updated = issueService.changeMilestone(savedIssue.id!!, milestone.id, "tester3")
                updated.milestone?.id shouldBe milestone.id

                val issueEvents = issueEventRepository.findByIssueOrderByCreatedAsc(savedIssue)
                issueEvents.size shouldBe 1
                issueEvents.first().eventType shouldBe EventType.ISSUE_MILESTONE_CHANGED
                issueEvents.first().newValue shouldBe "1.0 출시"
            }

            it("updateIssue로 본문을 변경하면 IssueEvent 타임라인 항목(ISSUE_BODY_CHANGED)이 생성되어야 한다") {
                val author = userRepository.save(User(loginId = "tester4", name = "테스터4", email = "tester4@yona.io"))
                val project = projectRepository.save(Project(name = "body-test-project", owner = "tester4"))
                val issue = Issue(
                    title = "본문 변경 테스트", body = "원래 본문", project = project,
                    authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                    createdDate = Instant.now(), state = State.OPEN
                )
                val savedIssue = issueRepository.save(issue)

                issueService.updateIssue(
                    issueId = savedIssue.id!!,
                    title = savedIssue.title,
                    body = "변경된 본문",
                    updater = author,
                    assigneeUser = null,
                    milestoneId = null,
                    labelIds = null
                )

                val issueEvents = issueEventRepository.findByIssueOrderByCreatedAsc(savedIssue)
                issueEvents.size shouldBe 1
                issueEvents.first().eventType shouldBe EventType.ISSUE_BODY_CHANGED
                issueEvents.first().oldValue shouldBe "원래 본문"
                issueEvents.first().newValue shouldBe "변경된 본문"
                issueEvents.first().senderLoginId shouldBe "tester4"
            }

            it("updateIssue로 본문을 변경하면 변경 이력(history)이 기록되어야 한다(P2-02)") {
                val author = userRepository.save(User(loginId = "tester4b", name = "테스터4B", email = "tester4b@yona.io"))
                val project = projectRepository.save(Project(name = "body-history-project", owner = "tester4b"))
                val issue = Issue(
                    title = "본문 이력 테스트", body = "원래 본문", project = project,
                    authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                    createdDate = Instant.now(), state = State.OPEN
                )
                val savedIssue = issueRepository.save(issue)

                val updated = issueService.updateIssue(
                    issueId = savedIssue.id!!,
                    title = savedIssue.title,
                    body = "변경된 본문",
                    updater = author,
                    assigneeUser = null,
                    milestoneId = null,
                    labelIds = null
                )

                updated.history shouldNotBe null
                updated.history!!.contains("history-made-by") shouldBe true
                updated.history!!.contains("테스터4B") shouldBe true
            }

            it("updateIssue로 본문이 바뀌지 않으면 ISSUE_BODY_CHANGED 이벤트가 생성되지 않아야 한다") {
                val author = userRepository.save(User(loginId = "tester5", name = "테스터5", email = "tester5@yona.io"))
                val project = projectRepository.save(Project(name = "body-nochange-project", owner = "tester5"))
                val issue = Issue(
                    title = "본문 유지 테스트", body = "동일 본문", project = project,
                    authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                    createdDate = Instant.now(), state = State.OPEN
                )
                val savedIssue = issueRepository.save(issue)

                issueService.updateIssue(
                    issueId = savedIssue.id!!,
                    title = savedIssue.title,
                    body = "동일 본문",
                    updater = author,
                    assigneeUser = null,
                    milestoneId = null,
                    labelIds = null
                )

                issueEventRepository.findByIssueOrderByCreatedAsc(savedIssue).size shouldBe 0
            }

            it("updateIssue로 라벨을 변경하면 IssueEvent 타임라인 항목(ISSUE_LABEL_CHANGED)이 생성되어야 한다") {
                val author = userRepository.save(User(loginId = "tester6", name = "테스터6", email = "tester6@yona.io"))
                val project = projectRepository.save(Project(name = "label-test-project", owner = "tester6"))
                val category = issueLabelCategoryRepository.save(
                    IssueLabelCategory(name = "종류", isExclusive = false, project = project)
                )
                val bugLabel = issueLabelRepository.save(
                    IssueLabel(category = category, color = "red", name = "버그", project = project)
                )
                val issue = Issue(
                    title = "라벨 변경 테스트", body = "본문", project = project,
                    authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                    createdDate = Instant.now(), state = State.OPEN
                )
                val savedIssue = issueRepository.save(issue)

                issueService.updateIssue(
                    issueId = savedIssue.id!!,
                    title = savedIssue.title,
                    body = savedIssue.body ?: "",
                    updater = author,
                    assigneeUser = null,
                    milestoneId = null,
                    labelIds = listOf(bugLabel.id!!)
                )

                val issueEvents = issueEventRepository.findByIssueOrderByCreatedAsc(savedIssue)
                issueEvents.size shouldBe 1
                issueEvents.first().eventType shouldBe EventType.ISSUE_LABEL_CHANGED
                issueEvents.first().newValue shouldBe "버그"
            }

            // yona Issue.checkLabels() 대응 (P1-80).
            it("updateIssue로 같은 배타(exclusive) 카테고리의 라벨을 두 개 이상 붙이면 거부되어야 한다") {
                val author = userRepository.save(User(loginId = "tester6b", name = "테스터6b", email = "tester6b@yona.io"))
                val project = projectRepository.save(Project(name = "exclusive-label-test-project", owner = "tester6b"))
                val exclusiveCategory = issueLabelCategoryRepository.save(
                    IssueLabelCategory(name = "우선순위", isExclusive = true, project = project)
                )
                val highLabel = issueLabelRepository.save(
                    IssueLabel(category = exclusiveCategory, color = "red", name = "높음", project = project)
                )
                val lowLabel = issueLabelRepository.save(
                    IssueLabel(category = exclusiveCategory, color = "blue", name = "낮음", project = project)
                )
                val issue = Issue(
                    title = "배타 라벨 검증 테스트", body = "본문", project = project,
                    authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                    createdDate = Instant.now(), state = State.OPEN
                )
                val savedIssue = issueRepository.save(issue)

                io.kotest.assertions.throwables.shouldThrow<IssueLabelExclusiveCategoryException> {
                    issueService.updateIssue(
                        issueId = savedIssue.id!!,
                        title = savedIssue.title,
                        body = savedIssue.body ?: "",
                        updater = author,
                        assigneeUser = null,
                        milestoneId = null,
                        labelIds = listOf(highLabel.id!!, lowLabel.id!!)
                    )
                }
            }

            it("updateIssue로 서로 다른 배타 카테고리의 라벨은 각각 하나씩 함께 붙일 수 있어야 한다") {
                val author = userRepository.save(User(loginId = "tester6c", name = "테스터6c", email = "tester6c@yona.io"))
                val project = projectRepository.save(Project(name = "exclusive-label-ok-project", owner = "tester6c"))
                val priorityCategory = issueLabelCategoryRepository.save(
                    IssueLabelCategory(name = "우선순위", isExclusive = true, project = project)
                )
                val typeCategory = issueLabelCategoryRepository.save(
                    IssueLabelCategory(name = "종류", isExclusive = true, project = project)
                )
                val highLabel = issueLabelRepository.save(
                    IssueLabel(category = priorityCategory, color = "red", name = "높음", project = project)
                )
                val bugLabel = issueLabelRepository.save(
                    IssueLabel(category = typeCategory, color = "orange", name = "버그", project = project)
                )
                val issue = Issue(
                    title = "배타 라벨 정상 케이스", body = "본문", project = project,
                    authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                    createdDate = Instant.now(), state = State.OPEN
                )
                val savedIssue = issueRepository.save(issue)

                val updated = issueService.updateIssue(
                    issueId = savedIssue.id!!,
                    title = savedIssue.title,
                    body = savedIssue.body ?: "",
                    updater = author,
                    assigneeUser = null,
                    milestoneId = null,
                    labelIds = listOf(highLabel.id!!, bugLabel.id!!)
                )

                updated.labels.map { it.name }.toSet() shouldBe setOf("높음", "버그")
            }

            it("같은 사용자가 30초 내에 상태를 연속 변경(A->B->C)하면 IssueEvent가 A->C 하나로 병합돼야 한다(P1-38)") {
                val author = userRepository.save(User(loginId = "tester7", name = "테스터7", email = "tester7@yona.io"))
                val project = projectRepository.save(Project(name = "merge-test-project", owner = "tester7"))
                val issue = Issue(
                    title = "상태 병합 테스트", body = "본문", project = project,
                    authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                    createdDate = Instant.now(), state = State.OPEN
                )
                val savedIssue = issueRepository.save(issue)

                issueService.changeState(savedIssue.id!!, State.CLOSED, "tester7")
                issueService.changeState(savedIssue.id!!, State.REJECTED, "tester7")

                val issueEvents = issueEventRepository.findByIssueOrderByCreatedAsc(savedIssue)
                issueEvents.size shouldBe 1
                issueEvents.first().eventType shouldBe EventType.ISSUE_STATE_CHANGED
                issueEvents.first().oldValue shouldBe State.OPEN.toString()
                issueEvents.first().newValue shouldBe State.REJECTED.toString()
            }

            it("같은 사용자가 30초 내에 상태를 원래대로 되돌리면(A->B->A) IssueEvent가 모두 상쇄돼야 한다(P1-38)") {
                val author = userRepository.save(User(loginId = "tester8", name = "테스터8", email = "tester8@yona.io"))
                val project = projectRepository.save(Project(name = "cancel-test-project", owner = "tester8"))
                val issue = Issue(
                    title = "상태 상쇄 테스트", body = "본문", project = project,
                    authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                    createdDate = Instant.now(), state = State.OPEN
                )
                val savedIssue = issueRepository.save(issue)

                issueService.changeState(savedIssue.id!!, State.CLOSED, "tester8")
                issueService.changeState(savedIssue.id!!, State.OPEN, "tester8")

                issueEventRepository.findByIssueOrderByCreatedAsc(savedIssue).size shouldBe 0
            }

            it("updateIssue로 라벨을 30초 내에 연속 변경하면 중간 지점은 남기되 정확히 되돌리는 경우만 상쇄돼야 한다(P1-38)") {
                val author = userRepository.save(User(loginId = "tester9", name = "테스터9", email = "tester9@yona.io"))
                val project = projectRepository.save(Project(name = "label-merge-project", owner = "tester9"))
                val category = issueLabelCategoryRepository.save(
                    IssueLabelCategory(name = "종류", isExclusive = false, project = project)
                )
                val bugLabel = issueLabelRepository.save(
                    IssueLabel(category = category, color = "red", name = "버그", project = project)
                )
                val featureLabel = issueLabelRepository.save(
                    IssueLabel(category = category, color = "blue", name = "기능", project = project)
                )
                val issue = Issue(
                    title = "라벨 연속 변경 테스트", body = "본문", project = project,
                    authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                    createdDate = Instant.now(), state = State.OPEN
                )
                val savedIssue = issueRepository.save(issue)

                // 1) 라벨 없음 -> [버그]
                issueService.updateIssue(
                    issueId = savedIssue.id!!, title = savedIssue.title, body = savedIssue.body ?: "",
                    updater = author, assigneeUser = null, milestoneId = null,
                    labelIds = listOf(bugLabel.id!!)
                )
                // 2) [버그] -> [버그, 기능] (중간 지점, 되돌리는 게 아니므로 남아야 함)
                issueService.updateIssue(
                    issueId = savedIssue.id!!, title = savedIssue.title, body = savedIssue.body ?: "",
                    updater = author, assigneeUser = null, milestoneId = null,
                    labelIds = listOf(bugLabel.id!!, featureLabel.id!!)
                )
                // 3) [버그, 기능] -> [버그] (2번을 정확히 되돌림 -> 2, 3번 모두 상쇄)
                issueService.updateIssue(
                    issueId = savedIssue.id!!, title = savedIssue.title, body = savedIssue.body ?: "",
                    updater = author, assigneeUser = null, milestoneId = null,
                    labelIds = listOf(bugLabel.id!!)
                )

                val issueEvents = issueEventRepository.findByIssueOrderByCreatedAsc(savedIssue)
                issueEvents.size shouldBe 1
                issueEvents.first().newValue shouldBe "버그"
            }

            it("사용자가 이슈에 투표를 던지거나 취소할 수 있어야 한다") {
                // Given
                val author = userRepository.save(
                    User(loginId = "tester", name = "테스터", email = "tester@yona.io")
                )
                val project = projectRepository.save(
                    Project(name = "test-project", owner = "tester")
                )
                val issue = issueRepository.save(
                    Issue(
                        title = "투표할 이슈",
                        body = "이슈 본문",
                        project = project,
                        authorId = author.id,
                        authorLoginId = author.loginId,
                        authorName = author.name,
                        createdDate = Instant.now()
                    )
                )

                // 1) 투표 수행 검증
                issueService.voteIssue(issue.id!!, author)

                val issueAfterVote = issueRepository.findById(issue.id!!).get()
                issueAfterVote.voters.size shouldBe 1
                issueAfterVote.voters.first().id shouldBe author.id

                // 2) 동일 사용자 중복 투표 시도 시 예외 발생 검증
                shouldThrow<IllegalStateException> {
                    issueService.voteIssue(issue.id!!, author)
                }

                // 3) 투표 취소 수행 검증
                issueService.unvoteIssue(issue.id!!, author)

                val issueAfterUnvote = issueRepository.findById(issue.id!!).get()
                issueAfterUnvote.voters.size shouldBe 0

                // 4) 투표하지 않은 사용자가 취소 시도 시 예외 발생 검증
                shouldThrow<IllegalStateException> {
                    issueService.unvoteIssue(issue.id!!, author)
                }
            }

            // yona IssueApi.java:1176-1210 upvoteWeight()/downvoteWeight() 대응 (P1-101). 이슈에 [GL-controllers_api_IssueApi-064;GL-controllers_api_IssueApi-065]
            // +1/-1 가중치를 매기는 기능 — Issue.voters(공감 투표)와는 별개의 정수 카운터.
            it("이슈 가중치(weight)를 증감시킬 수 있어야 한다") {
                val author = userRepository.save(
                    User(loginId = "weight-tester", name = "가중치테스터", email = "weight-tester@yona.io")
                )
                val project = projectRepository.save(
                    Project(name = "weight-project", owner = "weight-tester")
                )
                val issue = issueRepository.save(
                    Issue(
                        title = "가중치 테스트 이슈", body = "본문", project = project,
                        authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                        createdDate = Instant.now()
                    )
                )
                issue.weight shouldBe 0

                val upvoted = issueService.upvoteWeight(issue.id!!)
                upvoted.weight shouldBe 1
                issueRepository.findById(issue.id!!).get().weight shouldBe 1

                val upvotedAgain = issueService.upvoteWeight(issue.id!!)
                upvotedAgain.weight shouldBe 2

                val downvoted = issueService.downvoteWeight(issue.id!!)
                downvoted.weight shouldBe 1
                issueRepository.findById(issue.id!!).get().weight shouldBe 1
            }

            it("사용자가 이슈 댓글에 투표를 던지거나 취소할 수 있어야 한다") {
                // Given
                val author = userRepository.save(
                    User(loginId = "tester", name = "테스터", email = "tester@yona.io")
                )
                val project = projectRepository.save(
                    Project(name = "test-project", owner = "tester")
                )
                val issue = issueRepository.save(
                    Issue(
                        title = "댓글 투표할 이슈",
                        body = "이슈 본문",
                        project = project,
                        authorId = author.id,
                        authorLoginId = author.loginId,
                        authorName = author.name,
                        createdDate = Instant.now()
                    )
                )
                
                val comment = issueCommentRepository.save(
                    IssueComment(
                        contents = "댓글 내용",
                        issue = issue,
                        createdDate = Instant.now(),
                        authorId = author.id,
                        authorLoginId = author.loginId,
                        authorName = author.name,
                        projectId = project.id
                    )
                )

                // 1) 댓글 투표 수행 검증
                issueService.voteComment(comment.id!!, author)

                val commentAfterVote = issueCommentRepository.findById(comment.id!!).get()
                commentAfterVote.voters.size shouldBe 1
                commentAfterVote.voters.first().id shouldBe author.id

                // 2) 동일 사용자 중복 투표 시도 시 예외 발생 검증
                shouldThrow<IllegalStateException> {
                    issueService.voteComment(comment.id!!, author)
                }

                // 3) 댓글 투표 취소 수행 검증
                issueService.unvoteComment(comment.id!!, author)

                val commentAfterUnvote = issueCommentRepository.findById(comment.id!!).get()
                commentAfterUnvote.voters.size shouldBe 0

                // 4) 투표하지 않은 사용자가 취소 시도 시 예외 발생 검증
                shouldThrow<IllegalStateException> {
                    issueService.unvoteComment(comment.id!!, author)
                }
            }

            // yona IssueApp.editIssue()의 hasTargetProject()/moveIssueToOtherProject()/
            // addIssueMovedNotification() 대응 (P1-48).
            describe("moveIssue (P1-48, 이슈를 다른 프로젝트로 이동)") {
                it("다른 프로젝트로 이동하면 project/번호/생성일이 갱신되고 마일스톤이 초기화되어야 한다") {
                    val mover = userRepository.save(User(loginId = "mover", name = "이동실행자", email = "mover@yona.io"))
                    val fromProject = projectRepository.save(Project(name = "from-proj", owner = "owner-a", projectScope = ProjectScope.PUBLIC))
                    val toProject = projectRepository.save(Project(name = "to-proj", owner = "owner-b", projectScope = ProjectScope.PUBLIC))
                    val milestone = milestoneRepository.save(Milestone(title = "v1.0", project = fromProject))

                    val originalCreatedDate = Instant.now().minusSeconds(3600)
                    val issue = issueRepository.save(
                        Issue(
                            title = "이동할 이슈", body = "본문", project = fromProject,
                            authorId = mover.id, authorLoginId = mover.loginId, authorName = mover.name,
                            createdDate = originalCreatedDate, milestone = milestone
                        )
                    )

                    val moved = issueService.moveIssue(issue.id!!, toProject.id!!, mover)

                    moved.project.id shouldBe toProject.id
                    moved.number shouldBe 1L
                    moved.milestone shouldBe null
                    moved.createdDate shouldNotBe originalCreatedDate

                    val reloadedToProject = projectRepository.findById(toProject.id!!).get()
                    reloadedToProject.lastIssueNumber shouldBe 1L
                }

                it("같은 프로젝트로 이동을 시도하면 아무 변화 없이 그대로 반환하고 알림도 발행하지 않아야 한다") {
                    val mover = userRepository.save(User(loginId = "mover2", name = "이동실행자2", email = "mover2@yona.io"))
                    val project = projectRepository.save(Project(name = "same-proj", owner = "owner-a", projectScope = ProjectScope.PUBLIC))
                    val issue = issueRepository.save(
                        Issue(
                            title = "제자리 이슈", body = "본문", project = project,
                            authorId = mover.id, authorLoginId = mover.loginId, authorName = mover.name,
                            createdDate = Instant.now(), number = 1L
                        )
                    )
                    project.lastIssueNumber = 1L
                    projectRepository.save(project)

                    val result = issueService.moveIssue(issue.id!!, project.id!!, mover)

                    result.number shouldBe 1L
                    notificationEventRepository.findAll().size shouldBe 0
                }

                it("이슈의 댓글들도 대상 프로젝트로 projectId가 갱신되어야 한다") {
                    val mover = userRepository.save(User(loginId = "mover3", name = "이동실행자3", email = "mover3@yona.io"))
                    val fromProject = projectRepository.save(Project(name = "from-proj3", owner = "owner-a", projectScope = ProjectScope.PUBLIC))
                    val toProject = projectRepository.save(Project(name = "to-proj3", owner = "owner-b", projectScope = ProjectScope.PUBLIC))
                    val issue = issueRepository.save(
                        Issue(
                            title = "댓글 있는 이슈", body = "본문", project = fromProject,
                            authorId = mover.id, authorLoginId = mover.loginId, authorName = mover.name,
                            createdDate = Instant.now()
                        )
                    )
                    val comment = issueCommentRepository.save(
                        IssueComment(
                            contents = "댓글", issue = issue, createdDate = Instant.now(),
                            authorId = mover.id, authorLoginId = mover.loginId, authorName = mover.name,
                            projectId = fromProject.id
                        )
                    )

                    issueService.moveIssue(issue.id!!, toProject.id!!, mover)

                    val reloadedComment = issueCommentRepository.findById(comment.id!!).get()
                    reloadedComment.projectId shouldBe toProject.id
                }

                it("이동하는 사용자가 대상 프로젝트 멤버이고 라벨이 있으면 라벨이 대상 프로젝트로 이전되어야 한다") {
                    val mover = userRepository.save(User(loginId = "mover4", name = "이동실행자4", email = "mover4@yona.io"))
                    val fromProject = projectRepository.save(Project(name = "from-proj4", owner = "owner-a", projectScope = ProjectScope.PUBLIC))
                    val toProject = projectRepository.save(Project(name = "to-proj4", owner = "owner-b", projectScope = ProjectScope.PUBLIC))
                    // ProjectUser를 리포지토리로만 저장하면 mover 객체의 in-memory projectUsers
                    // 컬렉션이 갱신되지 않는다(같은 영속성 컨텍스트의 식별자 맵 캐시 때문에 findById로
                    // 다시 조회해도 동일 인스턴스가 반환되고, User(...) 생성자로 직접 만든 엔티티는
                    // Hibernate가 지연로딩 프록시로 바꿔치기하지 않는다) — isMemberOf()가 읽는 바로 그
                    // 컬렉션에 직접 추가해 실제 멤버십 상태를 반영한다.
                    val projectUser = ProjectUser(user = mover, project = toProject, role = roleRepository.save(Role(id = RoleType.MEMBER.roleType)))
                    mover.projectUsers.add(projectUser)
                    projectUserRepository.save(projectUser)

                    val category = issueLabelCategoryRepository.save(IssueLabelCategory(name = "버그", isExclusive = false, project = fromProject))
                    val label = issueLabelRepository.save(IssueLabel(category = category, color = "#ff0000", name = "critical", project = fromProject))
                    val issue = issueRepository.save(
                        Issue(
                            title = "라벨 있는 이슈", body = "본문", project = fromProject,
                            authorId = mover.id, authorLoginId = mover.loginId, authorName = mover.name,
                            createdDate = Instant.now(), labels = mutableSetOf(label)
                        )
                    )

                    val moved = issueService.moveIssue(issue.id!!, toProject.id!!, mover)

                    moved.labels.size shouldBe 1
                    val transferredLabel = moved.labels.first()
                    transferredLabel.name shouldBe "critical"
                    transferredLabel.project.id shouldBe toProject.id
                    transferredLabel.category.name shouldBe "버그"
                }

                it("이동하는 사용자가 대상 프로젝트 멤버가 아니면 라벨이 비워져야 한다") {
                    val mover = userRepository.save(User(loginId = "mover5", name = "이동실행자5", email = "mover5@yona.io"))
                    val fromProject = projectRepository.save(Project(name = "from-proj5", owner = "owner-a", projectScope = ProjectScope.PUBLIC))
                    val toProject = projectRepository.save(Project(name = "to-proj5", owner = "owner-b", projectScope = ProjectScope.PUBLIC))

                    val category = issueLabelCategoryRepository.save(IssueLabelCategory(name = "버그", isExclusive = false, project = fromProject))
                    val label = issueLabelRepository.save(IssueLabel(category = category, color = "#ff0000", name = "critical", project = fromProject))
                    val issue = issueRepository.save(
                        Issue(
                            title = "라벨 있는 이슈2", body = "본문", project = fromProject,
                            authorId = mover.id, authorLoginId = mover.loginId, authorName = mover.name,
                            createdDate = Instant.now(), labels = mutableSetOf(label)
                        )
                    )

                    val moved = issueService.moveIssue(issue.id!!, toProject.id!!, mover)

                    moved.labels.shouldBeEmpty()
                }

                it("서브태스크(하위 이슈)도 함께 대상 프로젝트로 이동하되 별도 알림은 발행하지 않아야 한다") {
                    val mover = userRepository.save(User(loginId = "mover6", name = "이동실행자6", email = "mover6@yona.io"))
                    val fromProject = projectRepository.save(Project(name = "from-proj6", owner = "owner-a", projectScope = ProjectScope.PUBLIC))
                    val toProject = projectRepository.save(Project(name = "to-proj6", owner = "owner-b", projectScope = ProjectScope.PUBLIC))

                    // number를 명시적으로 다르게 준다 — 실제 생성 흐름(IssueServiceImpl)은 항상
                    // project.lastIssueNumber로 채우지만, 이 테스트는 그 흐름을 우회해 리포지토리에
                    // 직접 save하므로 둘 다 기본값(null)로 두면 CUBRID의 (project_id, number) 유니크
                    // 제약이 "NULL과 NULL은 같다"고 판단해(다른 DB는 NULL을 서로 다르다고 취급) 두
                    // 번째 insert가 제약 위반으로 실패한다.
                    val parent = issueRepository.save(
                        Issue(
                            title = "부모 이슈", body = "본문", project = fromProject,
                            authorId = mover.id, authorLoginId = mover.loginId, authorName = mover.name,
                            createdDate = Instant.now(), number = 1L
                        )
                    )
                    val child = issueRepository.save(
                        Issue(
                            title = "자식 이슈", body = "본문", project = fromProject,
                            authorId = mover.id, authorLoginId = mover.loginId, authorName = mover.name,
                            createdDate = Instant.now(), parent = parent, number = 2L
                        )
                    )

                    issueService.moveIssue(parent.id!!, toProject.id!!, mover)

                    val reloadedChild = issueRepository.findById(child.id!!).get()
                    reloadedChild.project.id shouldBe toProject.id
                    reloadedChild.number shouldNotBe null

                    // NEW_ISSUE(부모 자신에 대한 재알림)만 있을 뿐, 자식 이슈에 대한 별도 알림은 없어야 한다
                    // (수신자가 없어 저장되지 않을 수 있으므로 이벤트 수 자체가 이 케이스의 핵심 단언은 아니지만,
                    // resourceId가 child를 가리키는 이벤트가 없어야 한다).
                    notificationEventRepository.findAll().none { it.resourceId == child.id.toString() } shouldBe true
                }

                it("이동하면 ISSUE_MOVED(이전 프로젝트 감시자 수신)와 NEW_ISSUE(새 프로젝트 감시자 수신) 알림이 모두 발행되어야 한다") {
                    val mover = userRepository.save(User(loginId = "mover7", name = "이동실행자7", email = "mover7@yona.io"))
                    val fromProject = projectRepository.save(Project(name = "from-proj7", owner = "owner-a", projectScope = ProjectScope.PUBLIC))
                    val toProject = projectRepository.save(Project(name = "to-proj7", owner = "owner-b", projectScope = ProjectScope.PUBLIC))

                    val fromWatcherUser = userRepository.save(User(loginId = "fromwatcher7", name = "이전감시자", email = "fw7@yona.io"))
                    watchRepository.save(Watch(user = fromWatcherUser, resourceType = ResourceType.PROJECT, resourceId = fromProject.id.toString()))
                    val toWatcherUser = userRepository.save(User(loginId = "towatcher7", name = "새감시자", email = "tw7@yona.io"))
                    watchRepository.save(Watch(user = toWatcherUser, resourceType = ResourceType.PROJECT, resourceId = toProject.id.toString()))

                    val issue = issueRepository.save(
                        Issue(
                            title = "알림 검증용 이슈", body = "본문", project = fromProject,
                            authorId = mover.id, authorLoginId = mover.loginId, authorName = mover.name,
                            createdDate = Instant.now()
                        )
                    )

                    issueService.moveIssue(issue.id!!, toProject.id!!, mover)

                    val events = notificationEventRepository.findAll()
                    val movedEvent = events.first { it.eventType == EventType.ISSUE_MOVED }
                    movedEvent.oldValue shouldBe "owner-a/from-proj7"
                    movedEvent.newValue shouldBe "owner-b/to-proj7"
                    // yona afterIssueMoved()는 다른 알림들과 달리 mover 자신을 수신자에서 빼지 않는다 —
                    // mover가 이슈 작성자(author)이기도 하므로 baseWatchers에 포함돼 함께 수신해야 한다.
                    movedEvent.receivers.map { it.id }.toSet() shouldBe setOf(mover.id, fromWatcherUser.id)

                    val newIssueEvent = events.first { it.eventType == EventType.NEW_ISSUE }
                    newIssueEvent.receivers.map { it.id } shouldBe listOf(toWatcherUser.id)
                }

                // yona IssueApp.addIssueMovedNotification()의 IssueEvent.addFromNotificationEvent(notiEvent,
                // originalIssue, loginId) 대응 (P1-70).
                it("이동하면 이슈 타임라인(IssueEvent)에도 ISSUE_MOVED 이력이 남아야 한다") {
                    val mover = userRepository.save(User(loginId = "mover8", name = "이동실행자8", email = "mover8@yona.io"))
                    val fromProject = projectRepository.save(Project(name = "from-proj8", owner = "owner-a", projectScope = ProjectScope.PUBLIC))
                    val toProject = projectRepository.save(Project(name = "to-proj8", owner = "owner-b", projectScope = ProjectScope.PUBLIC))

                    val issue = issueRepository.save(
                        Issue(
                            title = "타임라인 검증용 이슈", body = "본문", project = fromProject,
                            authorId = mover.id, authorLoginId = mover.loginId, authorName = mover.name,
                            createdDate = Instant.now()
                        )
                    )

                    val moved = issueService.moveIssue(issue.id!!, toProject.id!!, mover)

                    val issueEvents = issueEventRepository.findByIssueOrderByCreatedAsc(moved)
                    val movedEvent = issueEvents.first { it.eventType == EventType.ISSUE_MOVED }
                    movedEvent.oldValue shouldBe "owner-a/from-proj8"
                    movedEvent.newValue shouldBe "owner-b/to-proj8"
                    movedEvent.senderLoginId shouldBe mover.loginId
                }

                it("자신의 비공개 프로젝트에서 이동하면 history가 비워지고 알림이 발행되지 않아야 한다") {
                    val mover = userRepository.save(User(loginId = "mover8", name = "이동실행자8", email = "mover8@yona.io"))
                    val fromProject = projectRepository.save(
                        Project(name = "private-proj8", owner = "mover8", projectScope = ProjectScope.PRIVATE)
                    )
                    val toProject = projectRepository.save(Project(name = "to-proj8", owner = "owner-b", projectScope = ProjectScope.PUBLIC))

                    val issue = issueRepository.save(
                        Issue(
                            title = "비공개 프로젝트 이슈", body = "본문", project = fromProject,
                            authorId = mover.id, authorLoginId = mover.loginId, authorName = mover.name,
                            createdDate = Instant.now(), history = "이전 편집 이력"
                        )
                    )

                    val moved = issueService.moveIssue(issue.id!!, toProject.id!!, mover)

                    moved.history shouldBe ""
                    notificationEventRepository.findAll().size shouldBe 0
                }

                it("대상 프로젝트가 존재하지 않으면 IllegalArgumentException을 던져야 한다") {
                    val mover = userRepository.save(User(loginId = "mover9", name = "이동실행자9", email = "mover9@yona.io"))
                    val fromProject = projectRepository.save(Project(name = "from-proj9", owner = "owner-a", projectScope = ProjectScope.PUBLIC))
                    val issue = issueRepository.save(
                        Issue(
                            title = "이슈9", body = "본문", project = fromProject,
                            authorId = mover.id, authorLoginId = mover.loginId, authorName = mover.name,
                            createdDate = Instant.now()
                        )
                    )

                    shouldThrow<IllegalArgumentException> {
                        issueService.moveIssue(issue.id!!, 999999L, mover)
                    }
                }
            }

            // yona AbstractPosting.isPublish(transient 폼 플래그)/Issue.isDraft(영속 필드)/
            // IssueApp.editIssue()의 "if (issue.isPublish) {...}" 발행 전환 대응 (P1-65).
            describe("createIssue/publishIssue (초안 생성 및 발행 전환)") {
                it("isDraft=true로 생성하면 State.DRAFT로 저장되고 신규 이슈 알림이 발행되지 않아야 한다") {
                    val author = userRepository.save(User(loginId = "drafter", name = "초안작성자", email = "drafter@yona.io"))
                    val project = projectRepository.save(Project(name = "draft-proj", owner = "owner-a", projectScope = ProjectScope.PUBLIC))
                    val issue = Issue(title = "초안 이슈", body = "아직 다듬는 중", project = project)

                    val saved = issueService.createIssue(issue = issue, author = author, isDraft = true)

                    saved.state shouldBe State.DRAFT
                    saved.isDraft shouldBe true
                    saved.number shouldNotBe null
                    notificationEventRepository.findAll().size shouldBe 0
                }

                it("isDraft=false(기본값)로 생성하면 기존과 동일하게 State.OPEN+신규 이슈 알림이 발행되어야 한다") {
                    val author = userRepository.save(User(loginId = "author2", name = "작성자2", email = "author2@yona.io"))
                    val project = projectRepository.save(Project(name = "normal-proj", owner = "owner-a", projectScope = ProjectScope.PUBLIC))
                    val watcher = userRepository.save(User(loginId = "watcher2", name = "감시자2", email = "watcher2@yona.io"))
                    watchRepository.save(Watch(user = watcher, resourceType = ResourceType.PROJECT, resourceId = project.id.toString()))
                    val issue = Issue(title = "정식 이슈", body = "본문", project = project)

                    val saved = issueService.createIssue(issue = issue, author = author)

                    saved.state shouldBe State.OPEN
                    saved.isDraft shouldBe false
                    val events = notificationEventRepository.findAll()
                    events.size shouldBe 1
                    events.first().eventType shouldBe EventType.NEW_ISSUE
                }

                // yona NotificationEvent.forNewIssue()의 getReceivers(issue, author)(watchers +
                // getMentionedUsers(body)) 대응 (P1-127). 신규 이슈 본문에 있는 @멘션도 알림 수신자에
                // 포함되어야 한다 — 지금까지는 댓글에서만 멘션이 처리되고 신규 게시물 생성 시점에는
                // 전혀 처리되지 않았다.
                it("이슈 본문에 멘션이 포함되어 있으면 멘션된 사용자도 신규 이슈 알림 수신자에 포함되어야 한다") {
                    val author = userRepository.save(User(loginId = "mention-author", name = "작성자", email = "mention-author@yona.io"))
                    val mentioned = userRepository.save(User(loginId = "mentioned-user", name = "멘션대상", email = "mentioned-user@yona.io"))
                    val project = projectRepository.save(Project(name = "mention-proj", owner = "owner-a", projectScope = ProjectScope.PUBLIC))
                    val issue = Issue(title = "멘션 포함 이슈", body = "@mentioned-user 님 확인 부탁드립니다.", project = project)

                    issueService.createIssue(issue = issue, author = author)

                    val events = notificationEventRepository.findAll()
                    events.size shouldBe 1
                    events.first().receivers.map { it.loginId } shouldBe listOf("mentioned-user")
                }

                it("초안을 발행하면 createdDate가 갱신되고 state가 DRAFT->OPEN으로 바뀌고 번호가 재채번되며 신규 이슈 알림이 발행되어야 한다") {
                    val author = userRepository.save(User(loginId = "drafter2", name = "초안작성자2", email = "drafter2@yona.io"))
                    val project = projectRepository.save(Project(name = "publish-proj", owner = "owner-a", projectScope = ProjectScope.PUBLIC))
                    val watcher = userRepository.save(User(loginId = "watcher3", name = "감시자3", email = "watcher3@yona.io"))
                    watchRepository.save(Watch(user = watcher, resourceType = ResourceType.PROJECT, resourceId = project.id.toString()))

                    val draft = issueService.createIssue(
                        issue = Issue(title = "발행할 초안", body = "본문", project = project),
                        author = author,
                        isDraft = true
                    )
                    val draftNumber = draft.number
                    val draftCreatedDate = draft.createdDate

                    // 초안이 예약해간 번호 이후로 다른 이슈가 먼저 발행되어 시퀀스가 진행된 상태를 재현 —
                    // 발행 시 재채번이 "생성 시점 번호"가 아니라 "발행 시점의 최신 lastIssueNumber
                    // 기준"임을 검증하기 위함.
                    issueService.createIssue(
                        issue = Issue(title = "그 사이에 만들어진 이슈", body = "본문", project = project),
                        author = author
                    )

                    val published = issueService.publishIssue(draft.id!!, author)

                    published.state shouldBe State.OPEN
                    published.isDraft shouldBe false
                    published.number shouldNotBe draftNumber
                    published.createdDate shouldNotBe draftCreatedDate
                    published.history shouldBe ""

                    val events = notificationEventRepository.findAll()
                    // 초안 생성 시에는 알림이 없었고(위 테스트로 이미 확인), 중간에 만든 이슈 1건 +
                    // 발행 시 신규 이슈 알림 1건 = 총 2건이어야 한다.
                    events.size shouldBe 2
                    events.count { it.eventType == EventType.NEW_ISSUE && it.resourceId == published.id.toString() } shouldBe 1
                }

                it("이미 DRAFT가 아닌 이슈를 발행해도(엣지 케이스) createdDate/번호/이력은 그대로 갱신되어야 한다(legacy와 동일)") {
                    val author = userRepository.save(User(loginId = "author3", name = "작성자3", email = "author3@yona.io"))
                    val project = projectRepository.save(Project(name = "already-open-proj", owner = "owner-a", projectScope = ProjectScope.PUBLIC))

                    val issue = issueService.createIssue(
                        issue = Issue(title = "이미 열린 이슈", body = "본문", project = project),
                        author = author
                    )
                    val originalNumber = issue.number

                    val republished = issueService.publishIssue(issue.id!!, author)

                    republished.state shouldBe State.OPEN
                    republished.number shouldNotBe originalNumber
                }

                it("존재하지 않는 이슈를 발행하려 하면 IllegalArgumentException을 던져야 한다") {
                    val author = userRepository.save(User(loginId = "author4", name = "작성자4", email = "author4@yona.io"))

                    shouldThrow<IllegalArgumentException> {
                        issueService.publishIssue(999999L, author)
                    }
                }

                // yona controllers/api/IssueApi.java createIssuesNode()의 "number 필드가 있으면
                // saveWithNumber(number), 없으면 save()" 대응 (P2-56 복원, legacy Open API 마이그레이션
                // 전용 옵션).
                it("explicitNumber가 주어지면 project.lastIssueNumber 카운터를 건드리지 않고 그 번호를 그대로 쓴다") {
                    val author = userRepository.save(User(loginId = "import-author", name = "임포트작성자", email = "import-author@yona.io"))
                    val project = projectRepository.save(Project(name = "import-proj", owner = "owner-a", projectScope = ProjectScope.PUBLIC, lastIssueNumber = 5))

                    val imported = issueService.createIssue(
                        issue = Issue(title = "과거 이슈 임포트", body = "본문", project = project),
                        author = author,
                        explicitNumber = 42L
                    )

                    imported.number shouldBe 42L
                    projectRepository.findById(project.id!!).get().lastIssueNumber shouldBe 5L

                    // explicitNumber 없이 다음 이슈를 만들면 여전히 카운터 기준(6)으로 정상 채번돼야 한다.
                    val next = issueService.createIssue(
                        issue = Issue(title = "다음 정상 이슈", body = "본문", project = project),
                        author = author
                    )
                    next.number shouldBe 6L
                }

                it("sendNotification=false로 생성하면 정식 이슈(state=OPEN)여도 신규 이슈 알림이 발행되지 않아야 한다") {
                    val author = userRepository.save(User(loginId = "silent-author", name = "무음작성자", email = "silent-author@yona.io"))
                    val project = projectRepository.save(Project(name = "silent-proj", owner = "owner-a", projectScope = ProjectScope.PUBLIC))

                    val saved = issueService.createIssue(
                        issue = Issue(title = "알림 없는 이슈", body = "본문", project = project),
                        author = author,
                        sendNotification = false
                    )

                    saved.state shouldBe State.OPEN
                    notificationEventRepository.findAll().size shouldBe 0
                }
            }

            // yona AbstractPosting.save()/AbstractPostingApp.editPosting()의 TitleHead 연동 대응 (P1-103).
            describe("createIssue/updateIssue의 TitleHead(제목 머리말 자동완성) 연동") {
                it("createIssue로 대괄호 머리말이 있는 제목을 만들면 TitleHead가 빈도 1로 저장되어야 한다") {
                    val author = userRepository.save(User(loginId = "th-author1", name = "작성자", email = "th1@yona.io"))
                    val project = projectRepository.save(Project(name = "th-proj1", owner = "owner-a", projectScope = ProjectScope.PUBLIC))

                    issueService.createIssue(issue = Issue(title = "[Bug] 로그인 오류", body = "본문", project = project), author = author)

                    val found = titleHeadRepository.findByProjectIdAndHeadKeyword(project.id!!, "Bug")
                    found shouldNotBe null
                    found!!.frequency shouldBe 1
                }

                it("updateIssue로 제목을 바꾸면 새 머리말은 생기고 예전 머리말은 사라져야 한다") {
                    val author = userRepository.save(User(loginId = "th-author2", name = "작성자2", email = "th2@yona.io"))
                    val project = projectRepository.save(Project(name = "th-proj2", owner = "owner-a", projectScope = ProjectScope.PUBLIC))
                    val issue = issueService.createIssue(issue = Issue(title = "[Bug] 로그인 오류", body = "본문", project = project), author = author)

                    issueService.updateIssue(
                        issueId = issue.id!!,
                        title = "[Feature] 새 기능",
                        body = "본문",
                        updater = author
                    )

                    titleHeadRepository.findByProjectIdAndHeadKeyword(project.id!!, "Bug") shouldBe null
                    titleHeadRepository.findByProjectIdAndHeadKeyword(project.id!!, "Feature")!!.frequency shouldBe 1
                }

                it("updateIssue를 호출해도 제목의 머리말이 그대로면 빈도가 순변화 없이 유지되어야 한다") {
                    val author = userRepository.save(User(loginId = "th-author3", name = "작성자3", email = "th3@yona.io"))
                    val project = projectRepository.save(Project(name = "th-proj3", owner = "owner-a", projectScope = ProjectScope.PUBLIC))
                    val issue = issueService.createIssue(issue = Issue(title = "[Bug] 로그인 오류", body = "본문", project = project), author = author)

                    issueService.updateIssue(
                        issueId = issue.id!!,
                        title = "[Bug] 로그인 오류(내용만 수정)",
                        body = "수정된 본문",
                        updater = author
                    )

                    titleHeadRepository.findByProjectIdAndHeadKeyword(project.id!!, "Bug")!!.frequency shouldBe 1
                }
            }

            describe("deleteIssueCascade (P0-19, yona Project.delete()의 이슈 삭제 대응)") {
                it("댓글/이벤트/즐겨찾기가 있는 이슈도 FK 위반 없이 삭제되고 연관 행이 모두 정리되어야 한다") {
                    val author = userRepository.save(User(loginId = "cascade-author", name = "캐스케이드", email = "cascade@yona.io"))
                    val project = projectRepository.save(Project(name = "cascade-project", owner = "cascade-author"))
                    val issue = issueService.createIssue(
                        issue = Issue(title = "[Bug] 삭제될 이슈", body = "본문", project = project),
                        author = author
                    )
                    titleHeadRepository.findByProjectIdAndHeadKeyword(project.id!!, "Bug") shouldNotBe null
                    issueCommentRepository.save(
                        IssueComment(
                            contents = "댓글", authorId = author.id, authorLoginId = author.loginId,
                            authorName = author.name, projectId = project.id, issue = issue
                        )
                    )
                    issueEventRepository.save(
                        IssueEvent(
                            issue = issue, senderLoginId = author.loginId,
                            eventType = EventType.ISSUE_STATE_CHANGED, oldValue = "OPEN", newValue = "CLOSED"
                        )
                    )
                    favoriteIssueRepository.save(FavoriteIssue(user = author, issue = issue))

                    issueService.deleteIssueCascade(issue)

                    issueRepository.findById(issue.id!!).isPresent shouldBe false
                    issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(issue.id!!).shouldBeEmpty()
                    issueEventRepository.findByIssueOrderByCreatedAsc(issue).shouldBeEmpty()
                    favoriteIssueRepository.findByIssueId(issue.id!!).shouldBeEmpty()
                    titleHeadRepository.findByProjectIdAndHeadKeyword(project.id!!, "Bug") shouldBe null
                }
            }

            // yona AbstractPosting.save()의 updateMention() 대응 (P2-41, 사용자 지시로 yona의 로직·구조·
            // 한계를 그대로 포팅). 조직 이름으로 그룹 멘션하면 직접 이름이 언급되지 않은 조직원도
            // "나를 멘션한 이슈" 검색에 잡혀야 한다(LIKE 텍스트 검색으로는 놓치던 것).
            describe("createIssue의 멘션 인덱스 동기화 (P2-41)") {
                it("조직 이름으로 그룹 멘션한 이슈는 직접 언급되지 않은 조직원도 멘션 인덱스에서 찾을 수 있어야 한다") {
                    val author = userRepository.save(User(loginId = "mention-author", name = "작성자"))
                    val orgMember = userRepository.save(User(loginId = "mention-org-member", name = "조직원"))
                    val project = projectRepository.save(Project(name = "mention-org-project", owner = "mention-author"))
                    val org = organizationRepository.save(Organization(name = "mention-target-org"))
                    val memberRole = roleRepository.save(Role(id = RoleType.ORG_MEMBER.roleType))
                    organizationUserRepository.save(OrganizationUser(user = orgMember, organization = org, role = memberRole))

                    val issue = Issue(
                        title = "그룹 멘션 이슈",
                        body = "@mention-target-org 확인 부탁드립니다.",
                        project = project,
                        authorId = author.id,
                        authorLoginId = author.loginId,
                        authorName = author.name
                    )

                    val savedIssue = issueService.createIssue(issue, author)

                    mentionService.getMentioningIssueIds(orgMember.id!!) shouldBe listOf(savedIssue.id)
                }

                it("이슈 본문을 수정하면 멘션 인덱스도 새 본문 기준으로 갱신돼야 한다") {
                    val author = userRepository.save(User(loginId = "mention-editor", name = "수정자"))
                    val mentioned = userRepository.save(User(loginId = "mention-edited-target", name = "새로멘션됨"))
                    val project = projectRepository.save(Project(name = "mention-edit-project", owner = "mention-editor"))

                    val issue = Issue(
                        title = "멘션 없는 이슈",
                        body = "아무도 멘션하지 않음",
                        project = project,
                        authorId = author.id,
                        authorLoginId = author.loginId,
                        authorName = author.name
                    )
                    val savedIssue = issueService.createIssue(issue, author)
                    mentionService.getMentioningIssueIds(mentioned.id!!) shouldBe emptyList()

                    issueService.updateIssue(
                        issueId = savedIssue.id!!,
                        title = savedIssue.title,
                        body = "@mention-edited-target 이제 멘션합니다.",
                        updater = author
                    )

                    mentionService.getMentioningIssueIds(mentioned.id!!) shouldBe listOf(savedIssue.id)
                }
            }

            // yona models/support/IssueSearchCondition.java 대응 (P2-52).
            describe("getIssuesByFilter") {
                it("ASSIGNED - 담당자로 지정된 이슈만 반환해야 한다") {
                    val user = userRepository.save(User(loginId = "filter-assigned-user", name = "필터담당자"))
                    val otherUser = userRepository.save(User(loginId = "filter-assigned-other", name = "다른유저"))
                    val project = projectRepository.save(Project(name = "filter-assigned-project", owner = "filter-assigned-user"))

                    val assignedIssue = issueService.createIssue(
                        Issue(title = "담당 이슈", project = project, authorId = otherUser.id, authorLoginId = otherUser.loginId),
                        otherUser, assigneeUser = user
                    )
                    issueService.createIssue(
                        Issue(title = "담당 아닌 이슈", project = project, authorId = otherUser.id, authorLoginId = otherUser.loginId),
                        otherUser
                    )

                    val result = issueService.getIssuesByFilter(IssueFilterType.ASSIGNED, user)

                    result.map { it.id } shouldBe listOf(assignedIssue.id)
                }

                it("CREATED - 본인이 작성한 이슈만 반환해야 한다") {
                    val author = userRepository.save(User(loginId = "filter-created-author", name = "작성자"))
                    val other = userRepository.save(User(loginId = "filter-created-other", name = "다른유저2"))
                    val project = projectRepository.save(Project(name = "filter-created-project", owner = "filter-created-author"))

                    val createdIssue = issueService.createIssue(
                        Issue(title = "내가 쓴 이슈", project = project, authorId = author.id, authorLoginId = author.loginId), author
                    )
                    issueService.createIssue(
                        Issue(title = "남이 쓴 이슈", project = project, authorId = other.id, authorLoginId = other.loginId), other
                    )

                    val result = issueService.getIssuesByFilter(IssueFilterType.CREATED, author)

                    result.map { it.id } shouldBe listOf(createdIssue.id)
                }

                it("MENTIONED - 나를 멘션한 이슈만 반환해야 한다") {
                    val author = userRepository.save(User(loginId = "filter-mention-author", name = "멘션작성자"))
                    val mentioned = userRepository.save(User(loginId = "filter-mention-target", name = "멘션대상"))
                    val project = projectRepository.save(Project(name = "filter-mention-project", owner = "filter-mention-author"))

                    val mentioningIssue = issueService.createIssue(
                        Issue(
                            title = "멘션 이슈", body = "@filter-mention-target 확인해주세요", project = project,
                            authorId = author.id, authorLoginId = author.loginId
                        ),
                        author
                    )
                    issueService.createIssue(
                        Issue(title = "멘션 없는 이슈", body = "그냥 이슈", project = project, authorId = author.id, authorLoginId = author.loginId),
                        author
                    )

                    val result = issueService.getIssuesByFilter(IssueFilterType.MENTIONED, mentioned)

                    result.map { it.id } shouldBe listOf(mentioningIssue.id)
                }

                it("FAVORITE - 즐겨찾기한 이슈만 반환해야 한다") {
                    val author = userRepository.save(User(loginId = "filter-favorite-author", name = "즐찾작성자"))
                    val project = projectRepository.save(Project(name = "filter-favorite-project", owner = "filter-favorite-author"))
                    val favoritedIssue = issueService.createIssue(
                        Issue(title = "즐겨찾는 이슈", project = project, authorId = author.id, authorLoginId = author.loginId), author
                    )
                    issueService.createIssue(
                        Issue(title = "즐겨찾지 않는 이슈", project = project, authorId = author.id, authorLoginId = author.loginId), author
                    )
                    favoriteIssueRepository.save(FavoriteIssue(user = author, issue = favoritedIssue))

                    val result = issueService.getIssuesByFilter(IssueFilterType.FAVORITE, author)

                    result.map { it.id } shouldBe listOf(favoritedIssue.id)
                }

                it("ALL - 담당/작성/멘션/즐겨찾기 이슈를 중복 없이 합쳐 반환해야 한다") {
                    val user = userRepository.save(User(loginId = "filter-all-user", name = "올필터유저"))
                    val other = userRepository.save(User(loginId = "filter-all-other", name = "타인"))
                    val project = projectRepository.save(Project(name = "filter-all-project", owner = "filter-all-user"))

                    val createdIssue = issueService.createIssue(
                        Issue(title = "내가 쓴 이슈", project = project, authorId = user.id, authorLoginId = user.loginId), user
                    )
                    val assignedIssue = issueService.createIssue(
                        Issue(title = "담당 이슈", project = project, authorId = other.id, authorLoginId = other.loginId),
                        other, assigneeUser = user
                    )
                    val mentionedIssue = issueService.createIssue(
                        Issue(title = "멘션 이슈", body = "@filter-all-user 확인", project = project, authorId = other.id, authorLoginId = other.loginId),
                        other
                    )
                    val favoritedIssue = issueService.createIssue(
                        Issue(title = "즐겨찾는 이슈", project = project, authorId = other.id, authorLoginId = other.loginId), other
                    )
                    favoriteIssueRepository.save(FavoriteIssue(user = user, issue = favoritedIssue))
                    // 작성자이면서 동시에 담당자이기도 한 이슈 — ALL 결과에 한 번만 나와야 한다(중복 제거).
                    val bothCreatedAndAssigned = issueService.createIssue(
                        Issue(title = "작성+담당 겹침 이슈", project = project, authorId = user.id, authorLoginId = user.loginId),
                        user, assigneeUser = user
                    )
                    issueService.createIssue(
                        Issue(title = "무관한 이슈", project = project, authorId = other.id, authorLoginId = other.loginId), other
                    )

                    val result = issueService.getIssuesByFilter(IssueFilterType.ALL, user)

                    result.map { it.id }.toSet() shouldBe setOf(
                        createdIssue.id, assignedIssue.id, mentionedIssue.id, favoritedIssue.id, bothCreatedAndAssigned.id
                    )
                    result.size shouldBe 5
                }
            }
        }
    }
}
