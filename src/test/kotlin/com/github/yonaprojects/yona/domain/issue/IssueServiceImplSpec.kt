package com.github.yonaprojects.yona.domain.issue

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.milestone.Milestone
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.notification.NotificationEventRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.watch.Watch
import com.github.yonaprojects.yona.domain.watch.WatchRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * IssueServiceImpl JaCoCo 라인/분기/메서드 커버리지 보강용 테스트.
 *
 * IssueServiceSpec.kt가 이미 IssueServiceImpl의 주요 시나리오(초안/발행, 라벨 배타성,
 * moveIssue의 라벨 이전/알림, 투표/댓글 투표, 캐스케이드 삭제, 멘션/TitleHead 연동 등)를
 * 폭넓게 다루고 있으므로, 여기서는 그 스펙에서 다루지 않고 남아 있던 분기만 보강한다:
 * - createIssue/updateIssue의 담당자·마일스톤·라벨 "지정" 분기(기존 스펙은 전부 미지정만 호출)
 * - changeState/changeAssignee/changeMilestone의 조기 반환(동일 값 재지정), 담당자/마일스톤
 *   해제(값→null), 작성자 정보 없음(authorId null), 요청자 미존재(updaterLoginId 불일치) 분기
 * - moveIssue의 담당자 존재, "비공개지만 이동 실행자 소유가 아님", 초안 이동, 감시자가 전혀
 *   없어 알림이 저장되지 않는 경우, updateIssueToOtherProject의 "멤버이지만 라벨 없음" 분기
 * - publishNewIssueNotification의 본문 null(issue.body ?: "") 분기
 * - unvoteIssue/unvoteComment의 다중 투표자 중 미매칭 순회 분기
 * - 각 공개 메서드의 "리소스 없음" 예외 분기(지금까지 어떤 테스트도 호출하지 않아
 *   JaCoCo METHOD 커버리지에 잡히지 않던 orElseThrow 람다들)
 */
@Transactional
class IssueServiceImplSpec @Autowired constructor(
    private val issueService: IssueService,
    private val issueRepository: IssueRepository,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val notificationEventRepository: NotificationEventRepository,
    private val issueEventRepository: IssueEventRepository,
    private val milestoneRepository: MilestoneRepository,
    private val issueLabelRepository: IssueLabelRepository,
    private val issueLabelCategoryRepository: IssueLabelCategoryRepository,
    private val watchRepository: WatchRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val issueCommentRepository: IssueCommentRepository,
    private val roleRepository: RoleRepository
) : AbstractIntegrationTest() {

    private fun mkUser(loginId: String, name: String = loginId): User =
        userRepository.save(User(loginId = loginId, name = name, email = "$loginId@yona.io"))

    private fun mkProject(name: String, owner: String, scope: ProjectScope = ProjectScope.PUBLIC): Project =
        projectRepository.save(Project(name = name, owner = owner, projectScope = scope))

    private fun mkIssue(
        title: String,
        project: Project,
        author: User? = null,
        body: String? = "본문",
        isDraft: Boolean = false,
        history: String? = null
    ): Issue = issueRepository.save(
        Issue(
            title = title, body = body, project = project,
            authorId = author?.id, authorLoginId = author?.loginId, authorName = author?.name,
            createdDate = Instant.now(), isDraft = isDraft, history = history
        )
    )

    init {
        describe("IssueServiceImpl 커버리지 보강") {
            beforeEach {
                watchRepository.deleteAll()
                issueEventRepository.deleteAll()
                notificationEventRepository.deleteAll()
                issueCommentRepository.deleteAll()
                projectUserRepository.deleteAll()
                issueRepository.deleteAll()
                issueLabelRepository.deleteAll()
                issueLabelCategoryRepository.deleteAll()
                milestoneRepository.deleteAll()
                projectRepository.deleteAll()
                userRepository.deleteAll()
            }

            describe("createIssue의 담당자/마일스톤/라벨 지정 및 본문 null 분기") {
                it("담당자를 지정해 이슈를 생성하면 Assignee가 설정되어야 한다") {
                    val author = mkUser("ci-a1")
                    val assignee = mkUser("ci-a1-assignee")
                    val project = mkProject("ci-a1-project", author.loginId)

                    val saved = issueService.createIssue(
                        issue = Issue(title = "담당자 지정 생성", body = "본문", project = project),
                        author = author, assigneeUser = assignee, isDraft = true
                    )

                    saved.assignee?.user?.id shouldBe assignee.id
                }

                it("마일스톤을 지정해 이슈를 생성하면 마일스톤이 설정되어야 한다") {
                    val author = mkUser("ci-m1")
                    val project = mkProject("ci-m1-project", author.loginId)
                    val milestone = milestoneRepository.save(Milestone(title = "1.0", project = project))

                    val saved = issueService.createIssue(
                        issue = Issue(title = "마일스톤 지정 생성", body = "본문", project = project),
                        author = author, milestoneId = milestone.id, isDraft = true
                    )

                    saved.milestone?.id shouldBe milestone.id
                }

                it("라벨을 지정해 이슈를 생성하면 라벨이 매핑되어야 한다") {
                    val author = mkUser("ci-l1")
                    val project = mkProject("ci-l1-project", author.loginId)
                    val category = issueLabelCategoryRepository.save(
                        IssueLabelCategory(name = "종류", isExclusive = false, project = project)
                    )
                    val label = issueLabelRepository.save(
                        IssueLabel(category = category, color = "red", name = "버그", project = project)
                    )

                    val saved = issueService.createIssue(
                        issue = Issue(title = "라벨 지정 생성", body = "본문", project = project),
                        author = author, labelIds = listOf(label.id!!), isDraft = true
                    )

                    saved.labels.map { it.id } shouldBe listOf(label.id)
                }

                // yona-wiki P3-02 14라운드(실서버 REST API로 재현한 실제 IDOR) — labelIds를
                // issueLabelRepository.findAllById()로만 조회하고 그 라벨이 실제로 issue.project
                // 소속인지 전혀 검증하지 않았다. `POST /api/v1/projects/{owner}/{project}/issues`가
                // labelIds를 그대로 받는 REST API라, 자기 프로젝트에 이슈를 만들 권한만 있으면
                // labelIds에 완전히 무관한 다른(심지어 자신이 멤버도 아닌 PRIVATE) 프로젝트의 라벨
                // id를 넣어 그 라벨(이름/색상/카테고리)을 자기 이슈에 노출시킬 수 있었다 — TASK-0426
                // (label edit/delete IDOR)과 같은 근본원인이 issue 생성/수정 경로에는 남아 있었다.
                it("다른 프로젝트 소속 라벨 id를 지정해도 그 라벨은 매핑되지 않아야 한다") {
                    val author = mkUser("ci-l2")
                    val project = mkProject("ci-l2-project", author.loginId)
                    val otherProject = mkProject("ci-l2-other-project", "someone-else", ProjectScope.PRIVATE)
                    val otherCategory = issueLabelCategoryRepository.save(
                        IssueLabelCategory(name = "다른프로젝트카테고리", isExclusive = false, project = otherProject)
                    )
                    val otherLabel = issueLabelRepository.save(
                        IssueLabel(category = otherCategory, color = "black", name = "남의라벨", project = otherProject)
                    )

                    val saved = issueService.createIssue(
                        issue = Issue(title = "교차 프로젝트 라벨 시도", body = "본문", project = project),
                        author = author, labelIds = listOf(otherLabel.id!!), isDraft = true
                    )

                    saved.labels.shouldBeEmpty()
                }

                // yona-wiki P3-02 14라운드 — 위와 같은 근본원인의 마일스톤 버전. milestoneId도
                // project 소속 검증 없이 findById()로만 조회했다.
                it("다른 프로젝트 소속 마일스톤 id를 지정해도 그 마일스톤은 설정되지 않아야 한다") {
                    val author = mkUser("ci-m2")
                    val project = mkProject("ci-m2-project", author.loginId)
                    val otherProject = mkProject("ci-m2-other-project", "someone-else", ProjectScope.PRIVATE)
                    val otherMilestone = milestoneRepository.save(Milestone(title = "남의마일스톤", project = otherProject))

                    val saved = issueService.createIssue(
                        issue = Issue(title = "교차 프로젝트 마일스톤 시도", body = "본문", project = project),
                        author = author, milestoneId = otherMilestone.id, isDraft = true
                    )

                    saved.milestone shouldBe null
                }

                // yona NotificationEvent.forNewIssue()의 issue.body ?: "" 대응 — 본문이 없는(null)
                // 이슈를 정식 발행해도 신규 이슈 알림 생성 중 NPE 없이 처리되어야 한다.
                it("본문이 없는 이슈를 정식 생성해도 신규 이슈 알림이 발행되어야 한다") {
                    val author = mkUser("ci-nb1")
                    val project = mkProject("ci-nb1-project", author.loginId)
                    val watcher = mkUser("ci-nb1-watcher")
                    watchRepository.save(Watch(user = watcher, resourceType = ResourceType.PROJECT, resourceId = project.id.toString()))

                    val saved = issueService.createIssue(
                        issue = Issue(title = "본문 없는 이슈", body = null, project = project),
                        author = author
                    )

                    val events = notificationEventRepository.findAll()
                    events.size shouldBe 1
                    events.first().eventType shouldBe EventType.NEW_ISSUE
                    saved.body shouldBe null
                }
            }

            describe("updateIssue의 담당자/마일스톤 지정, 원본 본문 null, 알림 저장 분기") {
                it("담당자를 지정해 updateIssue를 호출하면 Assignee가 설정되어야 한다") {
                    val author = mkUser("ui-a1")
                    val assignee = mkUser("ui-a1-assignee")
                    val project = mkProject("ui-a1-project", author.loginId)
                    val issue = mkIssue("담당자 없는 이슈", project, author)

                    val updated = issueService.updateIssue(
                        issueId = issue.id!!, title = issue.title, body = issue.body ?: "",
                        updater = author, assigneeUser = assignee
                    )

                    updated.assignee?.user?.id shouldBe assignee.id
                }

                it("마일스톤을 지정해 updateIssue를 호출하면 마일스톤이 설정되어야 한다") {
                    val author = mkUser("ui-m1")
                    val project = mkProject("ui-m1-project", author.loginId)
                    val milestone = milestoneRepository.save(Milestone(title = "2.0", project = project))
                    val issue = mkIssue("마일스톤 없는 이슈", project, author)

                    val updated = issueService.updateIssue(
                        issueId = issue.id!!, title = issue.title, body = issue.body ?: "",
                        updater = author, milestoneId = milestone.id
                    )

                    updated.milestone?.id shouldBe milestone.id
                }

                // yona-wiki P3-02 14라운드 — createIssue와 동일한 근본원인의 updateIssue 버전.
                // `PUT /api/v1/projects/{owner}/{project}/issues/{number}`도 labelIds를 그대로
                // 받으므로, 이미 존재하는 이슈를 수정할 권한만 있으면 다른 프로젝트의 라벨을 매핑해
                // 조용히 붙일 수 있었다.
                it("다른 프로젝트 소속 라벨 id로 updateIssue를 호출해도 그 라벨은 매핑되지 않아야 한다") {
                    val author = mkUser("ui-l2")
                    val project = mkProject("ui-l2-project", author.loginId)
                    val issue = mkIssue("라벨 없는 이슈", project, author)
                    val otherProject = mkProject("ui-l2-other-project", "someone-else", ProjectScope.PRIVATE)
                    val otherCategory = issueLabelCategoryRepository.save(
                        IssueLabelCategory(name = "다른프로젝트카테고리", isExclusive = false, project = otherProject)
                    )
                    val otherLabel = issueLabelRepository.save(
                        IssueLabel(category = otherCategory, color = "black", name = "남의라벨", project = otherProject)
                    )

                    val updated = issueService.updateIssue(
                        issueId = issue.id!!, title = issue.title, body = issue.body ?: "",
                        updater = author, labelIds = listOf(otherLabel.id!!)
                    )

                    updated.labels.shouldBeEmpty()
                }

                // yona-wiki P3-02 14라운드 — 마일스톤 버전.
                it("다른 프로젝트 소속 마일스톤 id로 updateIssue를 호출해도 그 마일스톤은 설정되지 않아야 한다") {
                    val author = mkUser("ui-m2")
                    val project = mkProject("ui-m2-project", author.loginId)
                    val issue = mkIssue("마일스톤 없는 이슈", project, author)
                    val otherProject = mkProject("ui-m2-other-project", "someone-else", ProjectScope.PRIVATE)
                    val otherMilestone = milestoneRepository.save(Milestone(title = "남의마일스톤", project = otherProject))

                    val updated = issueService.updateIssue(
                        issueId = issue.id!!, title = issue.title, body = issue.body ?: "",
                        updater = author, milestoneId = otherMilestone.id
                    )

                    updated.milestone shouldBe null
                }

                // "(oldBody ?: \"\") != body" 분기의 oldBody==null 쪽 — 원본 본문이 없던 이슈를 수정.
                it("원본 본문이 없던 이슈를 수정하면 history가 정상적으로 생성되어야 한다") {
                    val author = mkUser("ui-nb1")
                    val project = mkProject("ui-nb1-project", author.loginId)
                    val issue = mkIssue("본문 없던 이슈", project, author, body = null)

                    val updated = issueService.updateIssue(
                        issueId = issue.id!!, title = issue.title, body = "새로 채운 본문", updater = author
                    )

                    updated.body shouldBe "새로 채운 본문"
                    updated.history shouldNotBe null
                }

                it("감시자가 있는 상태에서 본문을 수정하면 ISSUE_BODY_CHANGED 알림이 저장되어야 한다") {
                    val author = mkUser("ui-w1")
                    val updater = mkUser("ui-w1-updater")
                    val project = mkProject("ui-w1-project", author.loginId)
                    val watcher = mkUser("ui-w1-watcher")
                    watchRepository.save(Watch(user = watcher, resourceType = ResourceType.PROJECT, resourceId = project.id.toString()))
                    val issue = mkIssue("본문 수정 알림 이슈", project, author, body = "원래 본문")

                    issueService.updateIssue(
                        issueId = issue.id!!, title = issue.title, body = "바뀐 본문", updater = updater
                    )

                    notificationEventRepository.findAll().any { it.eventType == EventType.ISSUE_BODY_CHANGED } shouldBe true
                }

                it("존재하지 않는 이슈를 수정하려 하면 IllegalArgumentException을 던져야 한다") {
                    val author = mkUser("ui-nf1")
                    shouldThrow<IllegalArgumentException> {
                        issueService.updateIssue(issueId = 999999L, title = "x", body = "x", updater = author)
                    }
                }
            }

            describe("changeState의 조기 반환/작성자 없음/요청자 미존재/존재하지 않는 이슈 분기") {
                it("동일한 상태로 변경을 시도하면 조기 반환하고 아무 것도 바뀌지 않아야 한다") {
                    val author = mkUser("cs-e1")
                    val project = mkProject("cs-e1-project", author.loginId)
                    val issue = mkIssue("동일 상태 이슈", project, author)

                    val result = issueService.changeState(issue.id!!, State.OPEN, author.loginId)

                    result.state shouldBe State.OPEN
                    issueEventRepository.findByIssueOrderByCreatedAsc(issue).shouldBeEmpty()
                    notificationEventRepository.findAll().shouldBeEmpty()
                }

                it("작성자 정보가 없고 요청자도 존재하지 않는 이슈의 상태를 변경해도 정상 처리되어야 한다") {
                    val project = mkProject("cs-n1-project", "owner-x")
                    val issue = mkIssue("작성자 없는 이슈", project, author = null)

                    val result = issueService.changeState(issue.id!!, State.CLOSED, "no-such-user")

                    result.state shouldBe State.CLOSED
                    val issueEvents = issueEventRepository.findByIssueOrderByCreatedAsc(issue)
                    issueEvents.size shouldBe 1
                    issueEvents.first().senderEmail shouldBe null
                }

                it("존재하지 않는 이슈의 상태를 변경하려 하면 IllegalArgumentException을 던져야 한다") {
                    shouldThrow<IllegalArgumentException> {
                        issueService.changeState(999999L, State.CLOSED, "someone")
                    }
                }
            }

            describe("changeAssignee의 조기 반환/해제/작성자 없음/요청자 미존재/존재하지 않는 이슈 분기") {
                it("동일한 담당자로 재지정을 시도하면 조기 반환해야 한다") {
                    val author = mkUser("ca-e1")
                    val assignee = mkUser("ca-e1-assignee")
                    val project = mkProject("ca-e1-project", author.loginId)
                    val issue = issueRepository.save(
                        Issue(
                            title = "동일 담당자 이슈", body = "본문", project = project,
                            authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                            createdDate = Instant.now(), assignee = Assignee(user = assignee, project = project)
                        )
                    )

                    val result = issueService.changeAssignee(issue.id!!, assignee, author.loginId)

                    result.assignee?.user?.id shouldBe assignee.id
                    issueEventRepository.findByIssueOrderByCreatedAsc(issue).shouldBeEmpty()
                }

                it("담당자가 있는 이슈에서 담당자를 해제하면 assignee가 null이 되고 알림이 저장되어야 한다") {
                    val author = mkUser("ca-c1")
                    val oldAssignee = mkUser("ca-c1-old")
                    val updater = mkUser("ca-c1-updater")
                    val project = mkProject("ca-c1-project", author.loginId)
                    val watcher = mkUser("ca-c1-watcher")
                    watchRepository.save(Watch(user = watcher, resourceType = ResourceType.PROJECT, resourceId = project.id.toString()))
                    val issue = issueRepository.save(
                        Issue(
                            title = "담당자 해제 이슈", body = "본문", project = project,
                            authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                            createdDate = Instant.now(), assignee = Assignee(user = oldAssignee, project = project)
                        )
                    )

                    val result = issueService.changeAssignee(issue.id!!, null, updater.loginId)

                    result.assignee shouldBe null
                    notificationEventRepository.findAll().any { it.eventType == EventType.ISSUE_ASSIGNEE_CHANGED } shouldBe true
                }

                it("작성자 정보가 없고 요청자도 존재하지 않는 이슈의 담당자를 변경해도 정상 처리되어야 한다") {
                    val newAssignee = mkUser("ca-n1-new")
                    val project = mkProject("ca-n1-project", "owner-x")
                    val issue = mkIssue("작성자 없는 이슈", project, author = null)

                    val result = issueService.changeAssignee(issue.id!!, newAssignee, "no-such-user")

                    result.assignee?.user?.id shouldBe newAssignee.id
                }

                it("존재하지 않는 이슈의 담당자를 변경하려 하면 IllegalArgumentException을 던져야 한다") {
                    val assignee = mkUser("ca-nf1")
                    shouldThrow<IllegalArgumentException> {
                        issueService.changeAssignee(999999L, assignee, "someone")
                    }
                }
            }

            describe("changeMilestone의 조기 반환/해제/작성자 없음/요청자 미존재/존재하지 않는 이슈 분기") {
                it("동일한 마일스톤으로 재지정을 시도하면 조기 반환해야 한다") {
                    val author = mkUser("cm-e1")
                    val project = mkProject("cm-e1-project", author.loginId)
                    val milestone = milestoneRepository.save(Milestone(title = "동일 마일스톤", project = project))
                    val issue = issueRepository.save(
                        Issue(
                            title = "동일 마일스톤 이슈", body = "본문", project = project,
                            authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                            createdDate = Instant.now(), milestone = milestone
                        )
                    )

                    val result = issueService.changeMilestone(issue.id!!, milestone.id, author.loginId)

                    result.milestone?.id shouldBe milestone.id
                    issueEventRepository.findByIssueOrderByCreatedAsc(issue).shouldBeEmpty()
                }

                it("마일스톤이 있는 이슈에서 마일스톤을 해제하면 milestone이 null이 되고 알림이 저장되어야 한다") {
                    val author = mkUser("cm-c1")
                    val updater = mkUser("cm-c1-updater")
                    val project = mkProject("cm-c1-project", author.loginId)
                    val watcher = mkUser("cm-c1-watcher")
                    watchRepository.save(Watch(user = watcher, resourceType = ResourceType.PROJECT, resourceId = project.id.toString()))
                    val milestone = milestoneRepository.save(Milestone(title = "해제될 마일스톤", project = project))
                    val issue = issueRepository.save(
                        Issue(
                            title = "마일스톤 해제 이슈", body = "본문", project = project,
                            authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                            createdDate = Instant.now(), milestone = milestone
                        )
                    )

                    val result = issueService.changeMilestone(issue.id!!, null, updater.loginId)

                    result.milestone shouldBe null
                    notificationEventRepository.findAll().any { it.eventType == EventType.ISSUE_MILESTONE_CHANGED } shouldBe true
                }

                it("작성자 정보가 없고 요청자도 존재하지 않는 이슈의 마일스톤을 변경해도 정상 처리되어야 한다") {
                    val project = mkProject("cm-n1-project", "owner-x")
                    val milestone = milestoneRepository.save(Milestone(title = "새 마일스톤", project = project))
                    val issue = mkIssue("작성자 없는 이슈", project, author = null)

                    val result = issueService.changeMilestone(issue.id!!, milestone.id, "no-such-user")

                    result.milestone?.id shouldBe milestone.id
                }

                it("존재하지 않는 이슈의 마일스톤을 변경하려 하면 IllegalArgumentException을 던져야 한다") {
                    shouldThrow<IllegalArgumentException> {
                        issueService.changeMilestone(999999L, null, "someone")
                    }
                }
            }

            describe("moveIssue의 담당자/비공개 소유자 불일치/초안/감시자 0명/존재하지 않는 이슈 분기") {
                it("담당자가 있는 이슈를 이동해도 담당자 정보가 유지되어야 한다") {
                    val mover = mkUser("mv-a1")
                    val assignee = mkUser("mv-a1-assignee")
                    val fromProject = mkProject("mv-a1-from", "owner-a")
                    val toProject = mkProject("mv-a1-to", "owner-b")
                    val issue = issueRepository.save(
                        Issue(
                            title = "담당자 있는 이슈", body = "본문", project = fromProject,
                            authorId = mover.id, authorLoginId = mover.loginId, authorName = mover.name,
                            createdDate = Instant.now(), assignee = Assignee(user = assignee, project = fromProject)
                        )
                    )

                    val moved = issueService.moveIssue(issue.id!!, toProject.id!!, mover)

                    moved.assignee?.user?.id shouldBe assignee.id
                    moved.project.id shouldBe toProject.id
                }

                // fromOwnPrivateProject = isPrivate && owner.equals(mover.loginId) 중 owner 불일치로
                // false가 되는 경우 — history가 비워지지 않고 알림도 정상 발행되어야 한다.
                it("비공개 프로젝트라도 이동 실행자의 소유가 아니면 history가 비워지지 않아야 한다") {
                    val mover = mkUser("mv-p1")
                    val fromProject = mkProject("mv-p1-from", "other-owner", ProjectScope.PRIVATE)
                    val toProject = mkProject("mv-p1-to", "owner-b")
                    // mover가 비공개 프로젝트의 읽기 권한을 가져야(WatchServiceImpl.hasReadPermission)
                    // findActualWatchers()의 allowedWatchersOnly 필터에서 걸러지지 않고 알림 수신자로 남는다.
                    val moverProjectUser = ProjectUser(user = mover, project = fromProject, role = roleRepository.save(Role(id = RoleType.MEMBER.roleType)))
                    mover.projectUsers.add(moverProjectUser)
                    projectUserRepository.save(moverProjectUser)
                    val watcher = mkUser("mv-p1-watcher")
                    watchRepository.save(Watch(user = watcher, resourceType = ResourceType.PROJECT, resourceId = fromProject.id.toString()))
                    val issue = issueRepository.save(
                        Issue(
                            title = "타인 비공개 프로젝트 이슈", body = "본문", project = fromProject,
                            authorId = mover.id, authorLoginId = mover.loginId, authorName = mover.name,
                            createdDate = Instant.now(), history = "유지되어야 할 이력"
                        )
                    )

                    val moved = issueService.moveIssue(issue.id!!, toProject.id!!, mover)

                    moved.history shouldBe "유지되어야 할 이력"
                    notificationEventRepository.findAll().isNotEmpty() shouldBe true
                }

                it("초안 이슈를 공개 프로젝트에서 이동하면 알림이 발행되지 않고 history도 유지되어야 한다") {
                    val mover = mkUser("mv-d1")
                    val fromProject = mkProject("mv-d1-from", "owner-a")
                    val toProject = mkProject("mv-d1-to", "owner-b")
                    val watcher = mkUser("mv-d1-watcher")
                    watchRepository.save(Watch(user = watcher, resourceType = ResourceType.PROJECT, resourceId = fromProject.id.toString()))
                    val issue = issueRepository.save(
                        Issue(
                            title = "초안 이슈", body = "본문", project = fromProject,
                            authorId = mover.id, authorLoginId = mover.loginId, authorName = mover.name,
                            createdDate = Instant.now(), isDraft = true, history = "그대로 유지"
                        )
                    )

                    val moved = issueService.moveIssue(issue.id!!, toProject.id!!, mover)

                    moved.history shouldBe "그대로 유지"
                    moved.project.id shouldBe toProject.id
                    notificationEventRepository.findAll().shouldBeEmpty()
                }

                // publishIssueMovedNotification의 record()?.let 분기 중 record()==null(수신자 0명) 쪽.
                it("작성자/담당자/감시자가 전혀 없는 이슈를 이동하면 ISSUE_MOVED 알림이 저장되지 않아야 한다") {
                    val mover = mkUser("mv-z1")
                    val fromProject = mkProject("mv-z1-from", "owner-a")
                    val toProject = mkProject("mv-z1-to", "owner-b")
                    val issue = issueRepository.save(
                        Issue(title = "고아 이슈", body = "본문", project = fromProject, createdDate = Instant.now())
                    )

                    issueService.moveIssue(issue.id!!, toProject.id!!, mover)

                    notificationEventRepository.findAll().shouldBeEmpty()
                }

                // updateIssueToOtherProject의 "mover.isMemberOf(targetProject) && labels.isNotEmpty()"
                // 중 멤버이지만 라벨이 없어 false가 되는 조합.
                it("이동하는 사용자가 대상 프로젝트 멤버이지만 라벨이 없으면 라벨 없이 이동되어야 한다") {
                    val mover = mkUser("mv-mem1")
                    val fromProject = mkProject("mv-mem1-from", "owner-a")
                    val toProject = mkProject("mv-mem1-to", "owner-b")
                    val projectUser = ProjectUser(user = mover, project = toProject, role = roleRepository.save(Role(id = RoleType.MEMBER.roleType)))
                    mover.projectUsers.add(projectUser)
                    projectUserRepository.save(projectUser)
                    val issue = issueRepository.save(
                        Issue(
                            title = "라벨 없는 이슈", body = "본문", project = fromProject,
                            authorId = mover.id, authorLoginId = mover.loginId, authorName = mover.name,
                            createdDate = Instant.now()
                        )
                    )

                    val moved = issueService.moveIssue(issue.id!!, toProject.id!!, mover)

                    moved.labels.shouldBeEmpty()
                }

                it("존재하지 않는 이슈를 이동하려 하면 IllegalArgumentException을 던져야 한다") {
                    val mover = mkUser("mv-nf1")
                    val toProject = mkProject("mv-nf1-to", "owner-b")
                    shouldThrow<IllegalArgumentException> {
                        issueService.moveIssue(999999L, toProject.id!!, mover)
                    }
                }
            }

            describe("투표/댓글 투표의 존재하지 않는 리소스 예외 및 다중 투표자 미매칭 분기") {
                it("존재하지 않는 이슈에 투표하려 하면 IllegalArgumentException을 던져야 한다") {
                    val user = mkUser("v-nf1")
                    shouldThrow<IllegalArgumentException> { issueService.voteIssue(999999L, user) }
                }

                it("존재하지 않는 사용자가 이슈에 투표하려 하면 IllegalArgumentException을 던져야 한다") {
                    val author = mkUser("v-nf2-author")
                    val project = mkProject("v-nf2-project", author.loginId)
                    val issue = mkIssue("투표 대상 이슈", project, author)
                    val ghost = User(id = 999999L, loginId = "ghost-v", name = "유령", email = "ghost-v@yona.io")

                    shouldThrow<IllegalArgumentException> { issueService.voteIssue(issue.id!!, ghost) }
                }

                it("존재하지 않는 이슈의 투표를 취소하려 하면 IllegalArgumentException을 던져야 한다") {
                    val user = mkUser("uv-nf1")
                    shouldThrow<IllegalArgumentException> { issueService.unvoteIssue(999999L, user) }
                }

                it("존재하지 않는 사용자가 이슈 투표 취소를 시도하면 IllegalArgumentException을 던져야 한다") {
                    val author = mkUser("uv-nf2-author")
                    val project = mkProject("uv-nf2-project", author.loginId)
                    val issue = mkIssue("투표취소 대상 이슈", project, author)
                    val ghost = User(id = 999999L, loginId = "ghost-uv", name = "유령", email = "ghost-uv@yona.io")

                    shouldThrow<IllegalArgumentException> { issueService.unvoteIssue(issue.id!!, ghost) }
                }

                // voters.find{}가 매칭 없이 여러 원소를 모두 순회하는 경로(중간에 매칭되지 않는
                // 원소가 하나 이상 있는 경우) 보강.
                it("여러 명이 투표한 이슈에서 투표하지 않은 사용자가 취소를 시도하면 예외가 발생해야 한다") {
                    val author = mkUser("uv-many-author")
                    val voterA = mkUser("uv-many-a")
                    val voterB = mkUser("uv-many-b")
                    val nonVoter = mkUser("uv-many-c")
                    val project = mkProject("uv-many-project", author.loginId)
                    val issue = issueRepository.save(
                        Issue(
                            title = "여러 투표자 이슈", body = "본문", project = project,
                            authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                            createdDate = Instant.now(), voters = mutableSetOf(voterA, voterB)
                        )
                    )

                    shouldThrow<IllegalStateException> { issueService.unvoteIssue(issue.id!!, nonVoter) }
                }

                it("존재하지 않는 댓글에 투표하려 하면 IllegalArgumentException을 던져야 한다") {
                    val user = mkUser("vc-nf1")
                    shouldThrow<IllegalArgumentException> { issueService.voteComment(999999L, user) }
                }

                it("존재하지 않는 사용자가 댓글에 투표하려 하면 IllegalArgumentException을 던져야 한다") {
                    val author = mkUser("vc-nf2-author")
                    val project = mkProject("vc-nf2-project", author.loginId)
                    val issue = mkIssue("댓글 있는 이슈", project, author)
                    val comment = issueCommentRepository.save(
                        IssueComment(
                            contents = "댓글", issue = issue, createdDate = Instant.now(),
                            authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                            projectId = project.id
                        )
                    )
                    val ghost = User(id = 999999L, loginId = "ghost-vc", name = "유령", email = "ghost-vc@yona.io")

                    shouldThrow<IllegalArgumentException> { issueService.voteComment(comment.id!!, ghost) }
                }

                it("존재하지 않는 댓글의 투표를 취소하려 하면 IllegalArgumentException을 던져야 한다") {
                    val user = mkUser("uvc-nf1")
                    shouldThrow<IllegalArgumentException> { issueService.unvoteComment(999999L, user) }
                }

                it("존재하지 않는 사용자가 댓글 투표 취소를 시도하면 IllegalArgumentException을 던져야 한다") {
                    val author = mkUser("uvc-nf2-author")
                    val project = mkProject("uvc-nf2-project", author.loginId)
                    val issue = mkIssue("댓글 있는 이슈2", project, author)
                    val comment = issueCommentRepository.save(
                        IssueComment(
                            contents = "댓글", issue = issue, createdDate = Instant.now(),
                            authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                            projectId = project.id
                        )
                    )
                    val ghost = User(id = 999999L, loginId = "ghost-uvc", name = "유령", email = "ghost-uvc@yona.io")

                    shouldThrow<IllegalArgumentException> { issueService.unvoteComment(comment.id!!, ghost) }
                }

                it("여러 명이 투표한 댓글에서 투표하지 않은 사용자가 취소를 시도하면 예외가 발생해야 한다") {
                    val author = mkUser("uvc-many-author")
                    val voterA = mkUser("uvc-many-a")
                    val voterB = mkUser("uvc-many-b")
                    val nonVoter = mkUser("uvc-many-c")
                    val project = mkProject("uvc-many-project", author.loginId)
                    val issue = mkIssue("댓글 투표 이슈", project, author)
                    val comment = issueCommentRepository.save(
                        IssueComment(
                            contents = "댓글", issue = issue, createdDate = Instant.now(),
                            authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                            projectId = project.id, voters = mutableSetOf(voterA, voterB)
                        )
                    )

                    shouldThrow<IllegalStateException> { issueService.unvoteComment(comment.id!!, nonVoter) }
                }
            }
        }
    }
}
