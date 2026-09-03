package com.github.yonaprojects.yona.domain.watch

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueRepository
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
import com.github.yonaprojects.yona.domain.user.UserState
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import com.github.yonaprojects.yona.domain.issue.IssueService
import com.github.yonaprojects.yona.domain.comment.CommentService
import com.github.yonaprojects.yona.domain.notification.NotificationEventRepository
import com.github.yonaprojects.yona.domain.notification.UserProjectNotification
import com.github.yonaprojects.yona.domain.notification.UserProjectNotificationRepository
import com.github.yonaprojects.yona.domain.enumeration.EventType
import org.springframework.transaction.annotation.Transactional

@Transactional
class WatchServiceSpec @Autowired constructor(
    private val watchService: WatchService,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val roleRepository: RoleRepository,
    private val issueRepository: IssueRepository,
    private val watchRepository: WatchRepository,
    private val unwatchRepository: UnwatchRepository,
    private val issueService: IssueService,
    private val commentService: CommentService,
    private val notificationEventRepository: NotificationEventRepository,
    private val userProjectNotificationRepository: UserProjectNotificationRepository
) : AbstractIntegrationTest() {

    init {
        describe("WatchService 통합 테스트") {
            // 테스트용 데이터 준비
            lateinit var user1: User
            lateinit var user2: User
            lateinit var user3: User
            lateinit var project: Project
            lateinit var issue: Issue

            beforeEach {
                notificationEventRepository.deleteAll()
                userProjectNotificationRepository.deleteAll()
                watchRepository.deleteAll()
                unwatchRepository.deleteAll()
                issueRepository.deleteAll()
                projectUserRepository.deleteAll()
                projectRepository.deleteAll()
                userRepository.deleteAll()

                user1 = userRepository.save(User(loginId = "user1", name = "사용자1", email = "user1@example.com"))
                user2 = userRepository.save(User(loginId = "user2", name = "사용자2", email = "user2@example.com"))
                user3 = userRepository.save(User(loginId = "user3", name = "사용자3", email = "user3@example.com"))
                // 이 스펙의 기존 시나리오들은 프로젝트 멤버십과 무관한 "감시 매커니즘" 자체를 검증하므로
                // PUBLIC 프로젝트를 사용해 P1-21의 allowedWatchersOnly 권한 필터에 영향받지 않게 한다.
                // 권한 필터 자체는 아래 별도 describe 블록에서 PRIVATE 프로젝트로 명시적으로 검증한다.
                project = projectRepository.save(Project(name = "test-project", owner = "user1", projectScope = ProjectScope.PUBLIC))
                issue = issueRepository.save(
                    Issue(
                        title = "테스트 이슈",
                        body = "내용",
                        project = project,
                        authorId = user1.id,
                        authorLoginId = user1.loginId,
                        authorName = user1.name
                    )
                )
            }

            it("기본 감시 및 감시 취소 기능 동작 검증") {
                // Given
                val resourceType = ResourceType.ISSUE_POST
                val resourceId = issue.id.toString()

                // When & Then - 초기에는 감시하지 않음
                watchService.isWatching(user2, resourceType, resourceId) shouldBe false

                // When - 감시 시작
                watchService.watch(user2, resourceType, resourceId)
                // Then
                watchService.isWatching(user2, resourceType, resourceId) shouldBe true

                // When - 감시 해제
                watchService.unwatch(user2, resourceType, resourceId)
                // Then
                watchService.isWatching(user2, resourceType, resourceId) shouldBe false
            }

            it("프로젝트 감시자와 이슈 감시자를 조합한 실제 감시자(findActualWatchers) 산출 검증") {
                // Given
                val pResource = ResourceType.PROJECT
                val pResourceId = project.id.toString()
                val iResource = ResourceType.ISSUE_POST
                val iResourceId = issue.id.toString()

                // user2 가 프로젝트를 감시함
                watchService.watch(user2, pResource, pResourceId)

                // When & Then - 프로젝트 감시자는 이슈 감시자에 자동으로 포함되어야 함
                val watchers1 = watchService.findActualWatchers(
                    baseWatchers = setOf(user1), // 작성자 user1
                    resourceType = iResource,
                    resourceId = iResourceId,
                    projectId = project.id
                )
                watchers1.map { it.loginId }.toSet() shouldBe setOf("user1", "user2")

                // user2 가 이슈에 대해서는 감시를 해제(unwatch)함
                watchService.unwatch(user2, iResource, iResourceId)

                // Then - 이슈 감시에서 user2 가 제외되어야 함
                val watchers2 = watchService.findActualWatchers(
                    baseWatchers = setOf(user1),
                    resourceType = iResource,
                    resourceId = iResourceId,
                    projectId = project.id
                )
                watchers2.map { it.loginId }.toSet() shouldBe setOf("user1")
            }

            it("이슈 및 댓글 생성 시 알림 대상자(receivers) 계산 통합 연동 검증") {
                // Given
                val pResource = ResourceType.PROJECT
                val pResourceId = project.id.toString()

                // user2 가 프로젝트를 감시함
                watchService.watch(user2, pResource, pResourceId)

                // When - user1 이 신규 이슈 등록
                val newIssue = issueService.createIssue(
                    Issue(
                        title = "신규 TDD 이슈",
                        body = "TDD 내용",
                        project = project
                    ),
                    author = user1,
                    assigneeUser = null,
                    milestoneId = null,
                    labelIds = null
                )

                // Then - 신규 이슈 등록 알림 이벤트의 수신자에 user2(프로젝트 감시자)가 포함되어야 함 (user1 은 작성자이자 발송자이므로 제외)
                val events = notificationEventRepository.findAll()
                val issueEvent = events.find { ev -> ev.eventType == EventType.NEW_ISSUE }
                issueEvent shouldNotBe null
                issueEvent!!.receivers.map { u -> u.loginId }.toSet() shouldBe setOf("user2")

                // Given - user3 이 해당 이슈 감시, user2 는 이슈 감시 해제(unwatch)
                watchService.watch(user3, ResourceType.ISSUE_POST, newIssue.id.toString())
                watchService.unwatch(user2, ResourceType.ISSUE_POST, newIssue.id.toString())

                // When - user1 이 댓글을 작성함
                commentService.createIssueComment(
                    issueId = newIssue.id!!,
                    contents = "새로운 댓글 등록",
                    author = user1,
                    parentCommentId = null
                )

                // Then - 댓글 등록 알림 이벤트의 수신자에 user3만 포함되어야 함 (user2는 unwatch 했고, user1은 발송자이므로 제외)
                val updatedEvents = notificationEventRepository.findAll()
                val commentEvent = updatedEvents.find { ev -> ev.eventType == EventType.NEW_COMMENT }
                commentEvent shouldNotBe null
                commentEvent!!.receivers.map { u -> u.loginId }.toSet() shouldBe setOf("user3")
            }

            describe("allowedWatchersOnly 권한 필터링 (P1-21, yona Watch.findActualWatchers 대응)") {
                it("allowedWatchersOnly=true이면 비공개 프로젝트에 접근 권한이 없는 감시자는 실제 감시자에서 제외되어야 한다") {
                    val managerRole = roleRepository.findById(RoleType.MANAGER.roleType)
                        .orElseGet { roleRepository.save(Role(id = RoleType.MANAGER.roleType, name = "MANAGER")) }

                    val privateProject = projectRepository.save(
                        Project(name = "private-project", owner = "user1", projectScope = ProjectScope.PRIVATE)
                    )
                    projectUserRepository.save(ProjectUser(project = privateProject, user = user1, role = managerRole))

                    val privateIssue = issueRepository.save(
                        Issue(
                            title = "비공개 이슈",
                            body = "내용",
                            project = privateProject,
                            authorId = user1.id,
                            authorLoginId = user1.loginId,
                            authorName = user1.name
                        )
                    )

                    // user2(프로젝트 멤버 아님)와 user3(프로젝트 멤버)이 둘 다 이슈를 감시
                    projectUserRepository.save(ProjectUser(project = privateProject, user = user3, role = managerRole))
                    watchService.watch(user2, ResourceType.ISSUE_POST, privateIssue.id.toString())
                    watchService.watch(user3, ResourceType.ISSUE_POST, privateIssue.id.toString())

                    val watchers = watchService.findActualWatchers(
                        baseWatchers = emptySet(),
                        resourceType = ResourceType.ISSUE_POST,
                        resourceId = privateIssue.id.toString(),
                        projectId = privateProject.id,
                        allowedWatchersOnly = true
                    )

                    watchers.map { it.loginId }.toSet() shouldBe setOf("user3")
                }

                it("allowedWatchersOnly=false이면 권한 필터를 건너뛰고 감시자 전원을 반환해야 한다") {
                    val managerRole = roleRepository.findById(RoleType.MANAGER.roleType)
                        .orElseGet { roleRepository.save(Role(id = RoleType.MANAGER.roleType, name = "MANAGER")) }

                    val privateProject = projectRepository.save(
                        Project(name = "private-project-2", owner = "user1", projectScope = ProjectScope.PRIVATE)
                    )
                    projectUserRepository.save(ProjectUser(project = privateProject, user = user1, role = managerRole))

                    val privateIssue = issueRepository.save(
                        Issue(
                            title = "비공개 이슈2",
                            body = "내용",
                            project = privateProject,
                            authorId = user1.id,
                            authorLoginId = user1.loginId,
                            authorName = user1.name
                        )
                    )

                    watchService.watch(user2, ResourceType.ISSUE_POST, privateIssue.id.toString())

                    val watchers = watchService.findActualWatchers(
                        baseWatchers = emptySet(),
                        resourceType = ResourceType.ISSUE_POST,
                        resourceId = privateIssue.id.toString(),
                        projectId = privateProject.id,
                        allowedWatchersOnly = false
                    )

                    watchers.map { it.loginId }.toSet() shouldBe setOf("user2")
                }
            }

            describe("프로젝트별 알림 뮤트 토글 (P1-22, yona NotificationEvent.filterReceivers()의 UserProjectNotification 대응)") {
                it("프로젝트 감시자가 특정 이벤트타입을 뮤트했으면 그 이벤트에서는 실제 감시자에서 제외되어야 한다") {
                    watchService.watch(user2, ResourceType.PROJECT, project.id.toString())
                    userProjectNotificationRepository.save(
                        UserProjectNotification(
                            user = user2,
                            project = project,
                            notificationType = EventType.ISSUE_STATE_CHANGED,
                            allowed = false
                        )
                    )

                    val watchers = watchService.findActualWatchers(
                        baseWatchers = setOf(user1),
                        resourceType = ResourceType.ISSUE_POST,
                        resourceId = issue.id.toString(),
                        projectId = project.id,
                        eventType = EventType.ISSUE_STATE_CHANGED
                    )

                    watchers.map { it.loginId }.toSet() shouldBe setOf("user1")
                }

                it("뮤트 설정이 없으면(기본값) 프로젝트 감시자는 계속 알림을 받아야 한다") {
                    watchService.watch(user2, ResourceType.PROJECT, project.id.toString())

                    val watchers = watchService.findActualWatchers(
                        baseWatchers = setOf(user1),
                        resourceType = ResourceType.ISSUE_POST,
                        resourceId = issue.id.toString(),
                        projectId = project.id,
                        eventType = EventType.ISSUE_STATE_CHANGED
                    )

                    watchers.map { it.loginId }.toSet() shouldBe setOf("user1", "user2")
                }

                it("NEW_COMMENT는 프로젝트 감시만으로는 기본적으로 알림을 받지 않아야 한다(yona isNotifiedByDefault)") {
                    watchService.watch(user2, ResourceType.PROJECT, project.id.toString())

                    val watchers = watchService.findActualWatchers(
                        baseWatchers = setOf(user1),
                        resourceType = ResourceType.ISSUE_POST,
                        resourceId = issue.id.toString(),
                        projectId = project.id,
                        eventType = EventType.NEW_COMMENT
                    )

                    watchers.map { it.loginId }.toSet() shouldBe setOf("user1")
                }

                it("이슈에 직접 명시적으로 감시를 건 사용자는 프로젝트 뮤트와 무관하게 항상 포함되어야 한다") {
                    watchService.watch(user2, ResourceType.PROJECT, project.id.toString())
                    userProjectNotificationRepository.save(
                        UserProjectNotification(
                            user = user2,
                            project = project,
                            notificationType = EventType.NEW_COMMENT,
                            allowed = false
                        )
                    )
                    // user2가 이슈 자체도 명시적으로 감시
                    watchService.watch(user2, ResourceType.ISSUE_POST, issue.id.toString())

                    val watchers = watchService.findActualWatchers(
                        baseWatchers = setOf(user1),
                        resourceType = ResourceType.ISSUE_POST,
                        resourceId = issue.id.toString(),
                        projectId = project.id,
                        eventType = EventType.NEW_COMMENT
                    )

                    watchers.map { it.loginId }.toSet() shouldBe setOf("user1", "user2")
                }
            }
            describe("추가 커버리지 테스트") {
                it("unwatch 후 다시 watch 시 unwatch 데이터가 삭제되어야 한다") {
                    watchService.unwatch(user2, ResourceType.ISSUE_POST, issue.id.toString())
                    watchService.watch(user2, ResourceType.ISSUE_POST, issue.id.toString())
                    watchService.isWatching(user2, ResourceType.ISSUE_POST, issue.id.toString()) shouldBe true
                }
                it("watch 안 한 상태에서 unwatch 시 watch null 분기 커버리지") {
                    watchService.unwatch(user3, ResourceType.ISSUE_POST, issue.id.toString())
                    watchService.isWatching(user3, ResourceType.ISSUE_POST, issue.id.toString()) shouldBe false
                }
                it("siteManager는 allowedWatchersOnly 필터에서 무조건 포함된다") {
                    val managerRole = roleRepository.findById(RoleType.MANAGER.roleType).orElseGet { roleRepository.save(Role(id = RoleType.MANAGER.roleType, name = "MANAGER")) }
                    val privateProject = projectRepository.save(Project(name = "private-project-3", owner = "user1", projectScope = ProjectScope.PRIVATE))
                    val siteManagerUser = userRepository.save(User(loginId = "sitemanager", name = "관리자", email = "site@example.com").apply { state = UserState.SITE_ADMIN })
                    
                    watchService.watch(siteManagerUser, ResourceType.ISSUE_POST, "999")
                    val watchers = watchService.findActualWatchers(emptySet(), ResourceType.ISSUE_POST, "999", privateProject.id, true)
                    watchers.map { it.loginId }.contains("sitemanager") shouldBe true
                }
                it("projectId가 null인 전역 리소스는 누구나 읽을 수 있다 (hasReadPermission)") {
                    watchService.watch(user2, ResourceType.ISSUE_POST, "999")
                    val watchers = watchService.findActualWatchers(emptySet(), ResourceType.ISSUE_POST, "999", null, true)
                    watchers.map { it.loginId }.contains("user2") shouldBe true
                }

                it("이미 감시 중인 리소스에 다시 watch()를 호출하면 중복 저장 없이 그대로 유지되어야 한다") {
                    watchService.watch(user2, ResourceType.ISSUE_POST, issue.id.toString())
                    watchService.watch(user2, ResourceType.ISSUE_POST, issue.id.toString())

                    watchService.isWatching(user2, ResourceType.ISSUE_POST, issue.id.toString()) shouldBe true
                    watchRepository.findByResourceTypeAndResourceId(ResourceType.ISSUE_POST, issue.id.toString()).size shouldBe 1
                }

                it("이미 감시 해제된 리소스에 다시 unwatch()를 호출하면 중복 저장 없이 그대로 유지되어야 한다") {
                    watchService.unwatch(user3, ResourceType.ISSUE_POST, issue.id.toString())
                    watchService.unwatch(user3, ResourceType.ISSUE_POST, issue.id.toString())

                    watchService.isWatching(user3, ResourceType.ISSUE_POST, issue.id.toString()) shouldBe false
                    unwatchRepository.findByResourceTypeAndResourceId(ResourceType.ISSUE_POST, issue.id.toString()).size shouldBe 1
                }

                it("Watch와 Unwatch 레코드가 동시에 존재하면(비정상 데이터) isWatching은 false여야 한다") {
                    watchRepository.save(Watch(user = user2, resourceType = ResourceType.ISSUE_POST, resourceId = issue.id.toString()))
                    unwatchRepository.save(Unwatch(user = user2, resourceType = ResourceType.ISSUE_POST, resourceId = issue.id.toString()))

                    watchService.isWatching(user2, ResourceType.ISSUE_POST, issue.id.toString()) shouldBe false
                }

                it("PUBLIC 프로젝트는 allowedWatchersOnly=true여도 비멤버 감시자를 포함해야 한다 (hasReadPermission)") {
                    watchService.watch(user2, ResourceType.ISSUE_POST, issue.id.toString())

                    val watchers = watchService.findActualWatchers(
                        baseWatchers = emptySet(),
                        resourceType = ResourceType.ISSUE_POST,
                        resourceId = issue.id.toString(),
                        projectId = project.id,
                        allowedWatchersOnly = true
                    )

                    watchers.map { it.loginId }.contains("user2") shouldBe true
                }

                it("존재하지 않는 projectId면 allowedWatchersOnly 필터에서 읽기 권한이 없다고 처리해야 한다 (hasReadPermission)") {
                    watchService.watch(user2, ResourceType.ISSUE_POST, "888")

                    val watchers = watchService.findActualWatchers(
                        baseWatchers = emptySet(),
                        resourceType = ResourceType.ISSUE_POST,
                        resourceId = "888",
                        projectId = 999999L,
                        allowedWatchersOnly = true
                    )

                    watchers.map { it.loginId }.contains("user2") shouldBe false
                }

                it("baseWatcher가 프로젝트 감시자이기도 하면(리소스는 직접 감시 안 함) 뮤트 설정을 따라야 한다") {
                    // isOnlyProjectWatcher = user in projectWatchers && user !in baseWatchers && user !in resourceWatchers
                    // 기존 테스트들은 baseWatcher가 projectWatchers에 속하지 않는 경우만 다뤄, "baseWatcher이면서
                    // projectWatcher이기도 한" 조합(세 조건 중 두 번째 조건으로 false가 되는 경로)이 비어 있었다.
                    watchService.watch(user1, ResourceType.PROJECT, project.id.toString())
                    userProjectNotificationRepository.save(
                        UserProjectNotification(
                            user = user1,
                            project = project,
                            notificationType = EventType.ISSUE_STATE_CHANGED,
                            allowed = false
                        )
                    )

                    val watchers = watchService.findActualWatchers(
                        baseWatchers = setOf(user1),
                        resourceType = ResourceType.ISSUE_POST,
                        resourceId = issue.id.toString(),
                        projectId = project.id,
                        eventType = EventType.ISSUE_STATE_CHANGED
                    )

                    // user1은 baseWatcher이므로 isOnlyProjectWatcher=false가 되어 뮤트와 무관하게 포함되어야 한다.
                    watchers.map { it.loginId }.contains("user1") shouldBe true
                }

                // yona models/resource/ResourcePersistAdapter.java의 postDelete() 대응 (P1-147).
                it("deleteAll 호출 시 해당 리소스의 Watch/Unwatch가 모두 삭제되어야 한다") {
                    watchService.watch(user1, ResourceType.ISSUE_POST, "12345")
                    watchService.watch(user2, ResourceType.ISSUE_POST, "12345")
                    watchService.unwatch(user3, ResourceType.ISSUE_POST, "12345")
                    // 다른 리소스는 영향받지 않아야 한다.
                    watchService.watch(user1, ResourceType.ISSUE_POST, "99999")

                    watchService.deleteAll(ResourceType.ISSUE_POST, "12345")

                    watchRepository.findByResourceTypeAndResourceId(ResourceType.ISSUE_POST, "12345") shouldBe emptyList()
                    unwatchRepository.findByResourceTypeAndResourceId(ResourceType.ISSUE_POST, "12345") shouldBe emptyList()
                    watchRepository.findByResourceTypeAndResourceId(ResourceType.ISSUE_POST, "99999").size shouldBe 1
                }
            }
        }
    }
}