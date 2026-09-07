package com.github.yonaprojects.yona.domain.project

import com.github.yonaprojects.yona.domain.attachment.AttachmentService
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.watch.WatchService
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.board.PostingService
import com.github.yonaprojects.yona.domain.issue.Assignee
import com.github.yonaprojects.yona.domain.issue.AssigneeRepository
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueLabelCategory
import com.github.yonaprojects.yona.domain.issue.IssueLabelCategoryRepository
import com.github.yonaprojects.yona.domain.issue.IssueLabelService
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.issue.IssueService
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationUser
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommentThread
import com.github.yonaprojects.yona.domain.pullrequest.CommentThreadRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequest
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestCommit
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestCommitRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestEvent
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestEventRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.UserState
import com.github.yonaprojects.yona.domain.user.FavoriteProject
import com.github.yonaprojects.yona.domain.user.FavoriteProjectRepository
import com.github.yonaprojects.yona.domain.vcs.PlayRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import com.github.yonaprojects.yona.domain.webhook.Webhook
import com.github.yonaprojects.yona.domain.webhook.WebhookRepository
import com.github.yonaprojects.yona.domain.webhook.WebhookThread
import com.github.yonaprojects.yona.domain.webhook.WebhookThreadRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.Optional

class ProjectServiceImplSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val repositoryService = mockk<RepositoryService>()
    val userRepository = mockk<UserRepository>()
    val projectTransferRepository = mockk<ProjectTransferRepository>()
    val roleRepository = mockk<RoleRepository>()
    val organizationRepository = mockk<OrganizationRepository>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    val labelRepository = mockk<LabelRepository>()
    val issueRepository = mockk<IssueRepository>()
    val issueService = mockk<IssueService>()
    val issueLabelCategoryRepository = mockk<IssueLabelCategoryRepository>()
    val issueLabelService = mockk<IssueLabelService>()
    val assigneeRepository = mockk<AssigneeRepository>()
    val webhookRepository = mockk<WebhookRepository>()
    val webhookThreadRepository = mockk<WebhookThreadRepository>()
    val postingRepository = mockk<PostingRepository>()
    val postingService = mockk<PostingService>()
    val commentThreadRepository = mockk<CommentThreadRepository>()
    val pullRequestRepository = mockk<PullRequestRepository>()
    val pullRequestEventRepository = mockk<PullRequestEventRepository>()
    val pullRequestCommitRepository = mockk<PullRequestCommitRepository>()
    val favoriteProjectRepository = mockk<FavoriteProjectRepository>(relaxed = true)
    val watchService = mockk<WatchService>(relaxed = true)

    val projectService = ProjectServiceImpl(
        projectRepository,
        projectUserRepository,
        repositoryService,
        userRepository,
        projectTransferRepository,
        roleRepository,
        organizationRepository,
        organizationUserRepository,
        labelRepository,
        issueRepository,
        issueService,
        issueLabelCategoryRepository,
        issueLabelService,
        assigneeRepository,
        webhookRepository,
        webhookThreadRepository,
        postingRepository,
        postingService,
        commentThreadRepository,
        pullRequestRepository,
        pullRequestEventRepository,
        pullRequestCommitRepository,
        favoriteProjectRepository,
        watchService,
        "/tmp/yona/git",
        "/tmp/yona/svn",
        "/tmp/yona/hg"
    )

    describe("ProjectServiceImpl.acceptTransfer") {
        val sender = User(id = 1L, loginId = "sender", name = "보내는사람")
        val project = Project(id = 10L, name = "yona-project", owner = "sender", vcs = "GIT")

        fun pendingTransfer(destination: String) = ProjectTransfer(
            id = 50L,
            project = project,
            sender = sender,
            destination = destination,
            confirmKey = "correct-key",
            newProjectName = "yona-project",
            requested = Instant.now()
        )

        it("이관 목적지(destination) 로그인ID와 일치하지 않는 사용자가 수락하면 예외가 발생해야 한다") {
            // Given
            val pt = pendingTransfer("intended-owner")
            val stranger = User(id = 99L, loginId = "stranger", name = "제3자")

            every {
                projectTransferRepository.findByIdAndAcceptedAndRequestedAfter(50L, false, any())
            } returns Optional.of(pt)
            every { userRepository.findById(99L) } returns Optional.of(stranger)
            every { organizationRepository.findByName("intended-owner") } returns Optional.empty()

            // When & Then
            shouldThrow<IllegalArgumentException> {
                projectService.acceptTransfer(50L, "correct-key", 99L)
            }
        }

        it("이관 목적지 로그인ID와 정확히 일치하는 사용자는 수락할 수 있어야 한다") {
            // Given
            val pt = pendingTransfer("intended-owner")
            val destUser = User(id = 2L, loginId = "intended-owner", name = "받는사람")
            val managerRole = Role(id = RoleType.MANAGER.roleType)

            every {
                projectTransferRepository.findByIdAndAcceptedAndRequestedAfter(50L, false, any())
            } returns Optional.of(pt)
            every { userRepository.findById(2L) } returns Optional.of(destUser)
            every { projectRepository.save(any()) } returns project
            every { projectUserRepository.findByProjectIdAndUserId(10L, 1L) } returns Optional.empty()
            every { userRepository.findByLoginId("intended-owner") } returns Optional.of(destUser)
            every { organizationRepository.findByName("intended-owner") } returns Optional.empty()
            every { projectUserRepository.findByProjectIdAndUserId(10L, 2L) } returns Optional.empty()
            every { roleRepository.findById(RoleType.MANAGER.roleType) } returns Optional.of(managerRole)
            every { projectUserRepository.save(any()) } returns mockk()
            every { projectTransferRepository.delete(any()) } returns Unit

            // When & Then (예외가 발생하지 않아야 함)
            projectService.acceptTransfer(50L, "correct-key", 2L)

            pt.accepted shouldBe true
            // yona acceptTransfer()의 "project.organization = null"(개인에게 이관) 대응 (P1-73).
            project.organization shouldBe null
            // yona disableProjectTransferLink()가 실제로는 ProjectTransfer 행을 삭제하는 것 대응
            // (P1-74) — accepted=true로 남겨두지 않는다.
            verify(exactly = 1) { projectTransferRepository.delete(pt) }
        }

        // yona FavoriteProject.java:41-50 updateFavoriteProject() 대응 (P2-27). yona 원본은 이 [GL-models_FavoriteProject-008]
        // 동기화를 개명(ProjectApp.settingProject())에서만 호출하지만, yona는 개명 전용 경로가
        // 없어 이름/소유자 변경이 실제로 일어나는 유일한 지점인 acceptTransfer에서 수행한다.
        it("이관이 완료되면 이 프로젝트를 즐겨찾기한 사용자들의 owner/projectName도 갱신해야 한다 (P2-27)") {
            val pt = pendingTransfer("intended-owner")
            val destUser = User(id = 2L, loginId = "intended-owner", name = "받는사람")
            val managerRole = Role(id = RoleType.MANAGER.roleType)
            val favoriter = User(id = 3L, loginId = "favoriter", name = "즐겨찾기유저")
            val favorite = FavoriteProject(id = 900L, user = favoriter, project = project, owner = "sender", projectName = "yona-project")

            every {
                projectTransferRepository.findByIdAndAcceptedAndRequestedAfter(50L, false, any())
            } returns Optional.of(pt)
            every { userRepository.findById(2L) } returns Optional.of(destUser)
            every { projectRepository.save(any()) } returns project
            every { projectUserRepository.findByProjectIdAndUserId(10L, 1L) } returns Optional.empty()
            every { userRepository.findByLoginId("intended-owner") } returns Optional.of(destUser)
            every { organizationRepository.findByName("intended-owner") } returns Optional.empty()
            every { projectUserRepository.findByProjectIdAndUserId(10L, 2L) } returns Optional.empty()
            every { roleRepository.findById(RoleType.MANAGER.roleType) } returns Optional.of(managerRole)
            every { projectUserRepository.save(any()) } returns mockk()
            every { projectTransferRepository.delete(any()) } returns Unit
            every { favoriteProjectRepository.findByProjectId(10L) } returns listOf(favorite)
            every { favoriteProjectRepository.save(any()) } returns favorite

            projectService.acceptTransfer(50L, "correct-key", 2L)

            verify(exactly = 1) {
                favoriteProjectRepository.save(match { it.owner == "intended-owner" && it.projectName == "yona-project" })
            }
        }

        it("이관 목적지가 조직(Organization) 이름이면 해당 조직의 ORG_ADMIN만 수락할 수 있어야 한다") {
            // Given: 조직의 일반 멤버(ORG_MEMBER)가 수락 시도 -> 거부
            val pt = pendingTransfer("some-org")
            val org = Organization(id = 5L, name = "some-org")
            val orgMemberUser = User(id = 3L, loginId = "org-member", name = "조직멤버")
            val memberRole = Role(id = RoleType.ORG_MEMBER.roleType)
            val orgUser = OrganizationUser(id = 500L, user = orgMemberUser, organization = org, role = memberRole)

            every {
                projectTransferRepository.findByIdAndAcceptedAndRequestedAfter(50L, false, any())
            } returns Optional.of(pt)
            every { userRepository.findById(3L) } returns Optional.of(orgMemberUser)
            every { organizationRepository.findByName("some-org") } returns Optional.of(org)
            every { organizationUserRepository.findByOrganizationIdAndUserId(5L, 3L) } returns Optional.of(orgUser)

            // When & Then
            shouldThrow<IllegalArgumentException> {
                projectService.acceptTransfer(50L, "correct-key", 3L)
            }
        }

        it("이관 목적지가 조직 이름이고 수락자가 해당 조직의 ORG_ADMIN이면 수락할 수 있어야 한다") {
            // Given
            val pt = pendingTransfer("some-org")
            val org = Organization(id = 5L, name = "some-org")
            val orgAdminUser = User(id = 4L, loginId = "org-admin", name = "조직관리자")
            val adminRole = Role(id = RoleType.ORG_ADMIN.roleType)
            val orgUser = OrganizationUser(id = 501L, user = orgAdminUser, organization = org, role = adminRole)
            val managerRole = Role(id = RoleType.MANAGER.roleType)

            every {
                projectTransferRepository.findByIdAndAcceptedAndRequestedAfter(50L, false, any())
            } returns Optional.of(pt)
            every { userRepository.findById(4L) } returns Optional.of(orgAdminUser)
            every { organizationRepository.findByName("some-org") } returns Optional.of(org)
            every { organizationUserRepository.findByOrganizationIdAndUserId(5L, 4L) } returns Optional.of(orgUser)
            every { projectRepository.save(any()) } returns project
            every { projectUserRepository.findByProjectIdAndUserId(10L, 1L) } returns Optional.empty()
            every { userRepository.findByLoginId("some-org") } returns Optional.empty()
            every { roleRepository.findById(RoleType.MANAGER.roleType) } returns Optional.of(managerRole)
            every { projectTransferRepository.delete(any()) } returns Unit

            // When & Then (예외가 발생하지 않아야 함)
            projectService.acceptTransfer(50L, "correct-key", 4L)

            pt.accepted shouldBe true
            // yona acceptTransfer()의 "project.organization = newOwnerOrg"(조직에게 이관) 대응 (P1-73).
            project.organization shouldBe org
        }

        // yona ProjectApp.acceptTransfer()의 "project.organization = newOwnerOrg 또는 null" 대응 (P1-73).
        it("조직 소속 프로젝트를 개인에게 이관하면 organization이 명시적으로 null로 지워져야 한다") {
            val orgOwnedProject = Project(
                id = 11L, name = "org-owned-project", owner = "some-org", vcs = "GIT",
                organization = Organization(id = 5L, name = "some-org")
            )
            val pt = ProjectTransfer(
                id = 51L, project = orgOwnedProject, sender = sender, destination = "intended-owner",
                confirmKey = "correct-key", newProjectName = "org-owned-project", requested = Instant.now()
            )
            val destUser = User(id = 2L, loginId = "intended-owner", name = "받는사람")
            val managerRole = Role(id = RoleType.MANAGER.roleType)

            every {
                projectTransferRepository.findByIdAndAcceptedAndRequestedAfter(51L, false, any())
            } returns Optional.of(pt)
            every { userRepository.findById(2L) } returns Optional.of(destUser)
            every { organizationRepository.findByName("intended-owner") } returns Optional.empty()
            every { projectRepository.save(any()) } returns orgOwnedProject
            every { projectUserRepository.findByProjectIdAndUserId(11L, 1L) } returns Optional.empty()
            every { userRepository.findByLoginId("intended-owner") } returns Optional.of(destUser)
            every { projectUserRepository.findByProjectIdAndUserId(11L, 2L) } returns Optional.empty()
            every { roleRepository.findById(RoleType.MANAGER.roleType) } returns Optional.of(managerRole)
            every { projectUserRepository.save(any()) } returns mockk()
            every { projectTransferRepository.delete(any()) } returns Unit

            projectService.acceptTransfer(51L, "correct-key", 2L)

            orgOwnedProject.organization shouldBe null
        }

        // yona Project.recordRenameOrTransferHistoryIfLastChangePassed24HoursFrom() 대응 (P1-76).
        it("이전 완료 시 예전 owner/name이 기록돼야 한다(previousOwnerLoginId/previousName)") {
            val pt = pendingTransfer("intended-owner")
            val destUser = User(id = 2L, loginId = "intended-owner", name = "받는사람")
            val managerRole = Role(id = RoleType.MANAGER.roleType)

            every {
                projectTransferRepository.findByIdAndAcceptedAndRequestedAfter(50L, false, any())
            } returns Optional.of(pt)
            every { userRepository.findById(2L) } returns Optional.of(destUser)
            every { organizationRepository.findByName("intended-owner") } returns Optional.empty()
            every { projectRepository.save(any()) } returns project
            every { projectUserRepository.findByProjectIdAndUserId(10L, 1L) } returns Optional.empty()
            every { userRepository.findByLoginId("intended-owner") } returns Optional.of(destUser)
            every { projectUserRepository.findByProjectIdAndUserId(10L, 2L) } returns Optional.empty()
            every { roleRepository.findById(RoleType.MANAGER.roleType) } returns Optional.of(managerRole)
            every { projectUserRepository.save(any()) } returns mockk()
            every { projectTransferRepository.delete(any()) } returns Unit

            projectService.acceptTransfer(50L, "correct-key", 2L)

            project.previousOwnerLoginId shouldBe "sender"
            project.previousName shouldBe "yona-project"
            project.previousNameChangedTime shouldNotBe null
        }

        it("24시간 이내에 이미 예전 위치가 기록됐으면 다시 덮어쓰지 않아야 한다") {
            val recentChange = Instant.now().minusSeconds(3600) // 1시간 전
            val projectWithRecentHistory = Project(
                id = 12L, name = "yona-project", owner = "sender", vcs = "GIT",
                previousOwnerLoginId = "very-old-owner", previousName = "very-old-name",
                previousNameChangedTime = recentChange
            )
            val pt = ProjectTransfer(
                id = 52L, project = projectWithRecentHistory, sender = sender, destination = "intended-owner",
                confirmKey = "correct-key", newProjectName = "yona-project", requested = Instant.now()
            )
            val destUser = User(id = 2L, loginId = "intended-owner", name = "받는사람")
            val managerRole = Role(id = RoleType.MANAGER.roleType)

            every {
                projectTransferRepository.findByIdAndAcceptedAndRequestedAfter(52L, false, any())
            } returns Optional.of(pt)
            every { userRepository.findById(2L) } returns Optional.of(destUser)
            every { organizationRepository.findByName("intended-owner") } returns Optional.empty()
            every { projectRepository.save(any()) } returns projectWithRecentHistory
            every { projectUserRepository.findByProjectIdAndUserId(12L, 1L) } returns Optional.empty()
            every { userRepository.findByLoginId("intended-owner") } returns Optional.of(destUser)
            every { projectUserRepository.findByProjectIdAndUserId(12L, 2L) } returns Optional.empty()
            every { roleRepository.findById(RoleType.MANAGER.roleType) } returns Optional.of(managerRole)
            every { projectUserRepository.save(any()) } returns mockk()
            every { projectTransferRepository.delete(any()) } returns Unit

            projectService.acceptTransfer(52L, "correct-key", 2L)

            // 24시간이 안 지났으므로 예전 기록이 "sender/yona-project"로 갱신되지 않고 그대로 유지돼야 한다.
            projectWithRecentHistory.previousOwnerLoginId shouldBe "very-old-owner"
            projectWithRecentHistory.previousName shouldBe "very-old-name"
            projectWithRecentHistory.previousNameChangedTime shouldBe recentChange
        }

        it("confirmKey가 일치하지 않으면 인가 검사 전에 예외가 발생해야 한다") {
            // Given
            val pt = pendingTransfer("intended-owner")

            every {
                projectTransferRepository.findByIdAndAcceptedAndRequestedAfter(50L, false, any())
            } returns Optional.of(pt)

            // When & Then
            shouldThrow<IllegalArgumentException> {
                projectService.acceptTransfer(50L, "wrong-key", 2L)
            }
        }

        // 아래부터는 각 it마다 독립된 고유 id의 project/sender/pt를 사용한다 — 이 describe 상단의
        // 공유 `project`/`sender`는 여러 it에 걸쳐 실제로 상태가 누적 변이되므로(SingleInstance),
        // 새 테스트가 기존 테스트의 실행 순서/상태에 영향을 주거나 받지 않도록 격리한다.

        it("송신자가 MANAGER였다면 이관 후 MEMBER로 강등되어야 한다") {
            val sender2 = User(id = 301L, loginId = "sender-301", name = "보내는사람301")
            val proj = Project(id = 501L, name = "demote-proj", owner = "sender-301", vcs = "GIT")
            val pt = ProjectTransfer(
                id = 601L, project = proj, sender = sender2, destination = "dest-401",
                confirmKey = "key", newProjectName = "demote-proj", requested = Instant.now()
            )
            val destUser = User(id = 401L, loginId = "dest-401", name = "받는사람401")
            val managerRole = Role(id = RoleType.MANAGER.roleType)
            val memberRole = Role(id = RoleType.MEMBER.roleType)
            val senderProjectUser = ProjectUser(id = 801L, user = sender2, project = proj, role = managerRole)

            every { projectTransferRepository.findByIdAndAcceptedAndRequestedAfter(601L, false, any()) } returns Optional.of(pt)
            every { userRepository.findById(401L) } returns Optional.of(destUser)
            every { organizationRepository.findByName("dest-401") } returns Optional.empty()
            every { projectRepository.save(any()) } returns proj
            every { projectUserRepository.findByProjectIdAndUserId(501L, 301L) } returns Optional.of(senderProjectUser)
            every { roleRepository.findById(RoleType.MEMBER.roleType) } returns Optional.of(memberRole)
            every { projectUserRepository.save(any()) } returns mockk()
            every { userRepository.findByLoginId("dest-401") } returns Optional.of(destUser)
            every { projectUserRepository.findByProjectIdAndUserId(501L, 401L) } returns Optional.empty()
            every { roleRepository.findById(RoleType.MANAGER.roleType) } returns Optional.of(managerRole)
            every { projectTransferRepository.delete(any()) } returns Unit

            projectService.acceptTransfer(601L, "key", 401L)

            senderProjectUser.role shouldBe memberRole
            verify(exactly = 1) { projectUserRepository.save(senderProjectUser) }
        }

        it("강등에 필요한 MEMBER 역할을 찾을 수 없으면 예외가 발생해야 한다") {
            val sender2 = User(id = 302L, loginId = "sender-302", name = "보내는사람302")
            val proj = Project(id = 502L, name = "demote-fail-proj", owner = "sender-302", vcs = "GIT")
            val pt = ProjectTransfer(
                id = 602L, project = proj, sender = sender2, destination = "dest-402",
                confirmKey = "key", newProjectName = "demote-fail-proj", requested = Instant.now()
            )
            val destUser = User(id = 402L, loginId = "dest-402", name = "받는사람402")
            val managerRole = Role(id = RoleType.MANAGER.roleType)
            val senderProjectUser = ProjectUser(id = 802L, user = sender2, project = proj, role = managerRole)

            every { projectTransferRepository.findByIdAndAcceptedAndRequestedAfter(602L, false, any()) } returns Optional.of(pt)
            every { userRepository.findById(402L) } returns Optional.of(destUser)
            every { organizationRepository.findByName("dest-402") } returns Optional.empty()
            every { projectRepository.save(any()) } returns proj
            every { projectUserRepository.findByProjectIdAndUserId(502L, 302L) } returns Optional.of(senderProjectUser)
            every { roleRepository.findById(RoleType.MEMBER.roleType) } returns Optional.empty()

            shouldThrow<IllegalStateException> {
                projectService.acceptTransfer(602L, "key", 402L)
            }
        }

        it("송신자가 MANAGER가 아니었다면 강등 처리를 하지 않아야 한다") {
            val sender2 = User(id = 303L, loginId = "sender-303", name = "보내는사람303")
            val proj = Project(id = 503L, name = "no-demote-proj", owner = "sender-303", vcs = "GIT")
            val pt = ProjectTransfer(
                id = 603L, project = proj, sender = sender2, destination = "dest-403",
                confirmKey = "key", newProjectName = "no-demote-proj", requested = Instant.now()
            )
            val destUser = User(id = 403L, loginId = "dest-403", name = "받는사람403")
            val managerRole = Role(id = RoleType.MANAGER.roleType)
            val memberRole = Role(id = RoleType.MEMBER.roleType)
            val senderProjectUser = ProjectUser(id = 803L, user = sender2, project = proj, role = memberRole)

            every { projectTransferRepository.findByIdAndAcceptedAndRequestedAfter(603L, false, any()) } returns Optional.of(pt)
            every { userRepository.findById(403L) } returns Optional.of(destUser)
            every { organizationRepository.findByName("dest-403") } returns Optional.empty()
            every { projectRepository.save(any()) } returns proj
            every { projectUserRepository.findByProjectIdAndUserId(503L, 303L) } returns Optional.of(senderProjectUser)
            every { userRepository.findByLoginId("dest-403") } returns Optional.of(destUser)
            every { projectUserRepository.findByProjectIdAndUserId(503L, 403L) } returns Optional.empty()
            every { roleRepository.findById(RoleType.MANAGER.roleType) } returns Optional.of(managerRole)
            every { projectUserRepository.save(any()) } returns mockk()
            every { projectTransferRepository.delete(any()) } returns Unit
            // roleRepository는 스펙 전역에서 공유되는 mock이라 이전 it들의 호출 이력이 누적돼 있다.
            // exactly=0 단언이 오염되지 않도록 스텁(answers)은 남기고 호출 이력만 초기화한다.
            clearMocks(roleRepository, answers = false)

            projectService.acceptTransfer(603L, "key", 403L)

            senderProjectUser.role shouldBe memberRole
            verify(exactly = 0) { roleRepository.findById(RoleType.MEMBER.roleType) }
        }

        it("이관 목적지 사용자가 이미 프로젝트 멤버라면 새로 만들지 않고 기존 멤버의 역할만 MANAGER로 갱신해야 한다") {
            val sender2 = User(id = 304L, loginId = "sender-304", name = "보내는사람304")
            val proj = Project(id = 504L, name = "existing-member-proj", owner = "sender-304", vcs = "GIT")
            val pt = ProjectTransfer(
                id = 604L, project = proj, sender = sender2, destination = "dest-404",
                confirmKey = "key", newProjectName = "existing-member-proj", requested = Instant.now()
            )
            val destUser = User(id = 404L, loginId = "dest-404", name = "받는사람404")
            val managerRole = Role(id = RoleType.MANAGER.roleType)
            val existingMemberRole = Role(id = RoleType.MEMBER.roleType)
            val existingProjectUser = ProjectUser(id = 804L, user = destUser, project = proj, role = existingMemberRole)

            every { projectTransferRepository.findByIdAndAcceptedAndRequestedAfter(604L, false, any()) } returns Optional.of(pt)
            every { userRepository.findById(404L) } returns Optional.of(destUser)
            every { organizationRepository.findByName("dest-404") } returns Optional.empty()
            every { projectRepository.save(any()) } returns proj
            every { projectUserRepository.findByProjectIdAndUserId(504L, 304L) } returns Optional.empty()
            every { userRepository.findByLoginId("dest-404") } returns Optional.of(destUser)
            every { projectUserRepository.findByProjectIdAndUserId(504L, 404L) } returns Optional.of(existingProjectUser)
            every { roleRepository.findById(RoleType.MANAGER.roleType) } returns Optional.of(managerRole)
            every { projectUserRepository.save(any()) } returns mockk()
            every { projectTransferRepository.delete(any()) } returns Unit

            projectService.acceptTransfer(604L, "key", 404L)

            existingProjectUser.role shouldBe managerRole
            verify(exactly = 1) { projectUserRepository.save(existingProjectUser) }
        }

        it("이관 목적지 사용자에게 부여할 MANAGER 역할을 찾을 수 없으면 예외가 발생해야 한다") {
            val sender2 = User(id = 305L, loginId = "sender-305", name = "보내는사람305")
            val proj = Project(id = 505L, name = "no-manager-role-proj", owner = "sender-305", vcs = "GIT")
            val pt = ProjectTransfer(
                id = 605L, project = proj, sender = sender2, destination = "dest-405",
                confirmKey = "key", newProjectName = "no-manager-role-proj", requested = Instant.now()
            )
            val destUser = User(id = 405L, loginId = "dest-405", name = "받는사람405")

            every { projectTransferRepository.findByIdAndAcceptedAndRequestedAfter(605L, false, any()) } returns Optional.of(pt)
            every { userRepository.findById(405L) } returns Optional.of(destUser)
            every { organizationRepository.findByName("dest-405") } returns Optional.empty()
            every { projectRepository.save(any()) } returns proj
            every { projectUserRepository.findByProjectIdAndUserId(505L, 305L) } returns Optional.empty()
            every { userRepository.findByLoginId("dest-405") } returns Optional.of(destUser)
            every { projectUserRepository.findByProjectIdAndUserId(505L, 405L) } returns Optional.empty()
            every { roleRepository.findById(RoleType.MANAGER.roleType) } returns Optional.empty()

            shouldThrow<IllegalStateException> {
                projectService.acceptTransfer(605L, "key", 405L)
            }
        }

        // yona 물리 저장소 폴더 이동 대응 — 실제 파일시스템에 GIT bare 저장소 디렉터리를 만들어
        // sourceDir.exists()==true 분기(File.renameTo 실행 경로)를 검증한다.
        it("GIT 저장소 폴더가 실재하면 이관 시 물리적으로 이동돼야 한다") {
            val owner = "phys-git-owner"
            val name = "phys-git-repo"
            val destOwner = "phys-git-dest-owner"
            val sender2 = User(id = 306L, loginId = owner, name = "물리깃")
            val proj = Project(id = 506L, name = name, owner = owner, vcs = "GIT")
            val pt = ProjectTransfer(
                id = 606L, project = proj, sender = sender2, destination = destOwner,
                confirmKey = "key", newProjectName = name, requested = Instant.now()
            )
            val destUser = User(id = 406L, loginId = destOwner, name = "물리깃수신")
            val managerRole = Role(id = RoleType.MANAGER.roleType)

            val sourceDir = File("/tmp/yona/git/$owner/$name.git")
            val targetDir = File("/tmp/yona/git/$destOwner/$name.git")
            sourceDir.deleteRecursively()
            targetDir.deleteRecursively()
            try {
                sourceDir.mkdirs()
                File(sourceDir, "HEAD").writeText("ref: refs/heads/main")

                every { projectTransferRepository.findByIdAndAcceptedAndRequestedAfter(606L, false, any()) } returns Optional.of(pt)
                every { userRepository.findById(406L) } returns Optional.of(destUser)
                every { organizationRepository.findByName(destOwner) } returns Optional.empty()
                every { projectRepository.save(any()) } returns proj
                every { projectUserRepository.findByProjectIdAndUserId(506L, 306L) } returns Optional.empty()
                every { userRepository.findByLoginId(destOwner) } returns Optional.of(destUser)
                every { projectUserRepository.findByProjectIdAndUserId(506L, 406L) } returns Optional.empty()
                every { roleRepository.findById(RoleType.MANAGER.roleType) } returns Optional.of(managerRole)
                every { projectUserRepository.save(any()) } returns mockk()
                every { projectTransferRepository.delete(any()) } returns Unit

                projectService.acceptTransfer(606L, "key", 406L)

                sourceDir.exists() shouldBe false
                targetDir.exists() shouldBe true
                File(targetDir, "HEAD").exists() shouldBe true
            } finally {
                sourceDir.deleteRecursively()
                targetDir.deleteRecursively()
            }
        }

        it("SVN(SUBVERSION) 저장소 폴더가 실재하면 이관 시 svn 기본 경로에서 이동돼야 한다") {
            val owner = "phys-svn-owner"
            val name = "phys-svn-repo"
            val destOwner = "phys-svn-dest-owner"
            val sender2 = User(id = 307L, loginId = owner, name = "물리svn")
            val proj = Project(id = 507L, name = name, owner = owner, vcs = "SUBVERSION")
            val pt = ProjectTransfer(
                id = 607L, project = proj, sender = sender2, destination = destOwner,
                confirmKey = "key", newProjectName = name, requested = Instant.now()
            )
            val destUser = User(id = 407L, loginId = destOwner, name = "물리svn수신")
            val managerRole = Role(id = RoleType.MANAGER.roleType)

            // 코디네이터 push 전 리뷰(2026-09-08, P3-12 2라운드) — SvnRepository.getDirectory()는
            // ".git" 접미사 없는 "$owner/$name" 경로를 쓰므로(git 전용 접미사 버그 수정 이후) 테스트
            // 픽스처도 실제 물리 경로와 일치시킨다(수정 전에는 이 테스트 자체가 버그와 동일한 ".git"
            // 접미사를 픽스처에 붙여, 버그가 있어도 통과하는 거짓 양성 테스트였다).
            val sourceDir = File("/tmp/yona/svn/$owner/$name")
            val targetDir = File("/tmp/yona/svn/$destOwner/$name")
            sourceDir.deleteRecursively()
            targetDir.deleteRecursively()
            try {
                sourceDir.mkdirs()

                every { projectTransferRepository.findByIdAndAcceptedAndRequestedAfter(607L, false, any()) } returns Optional.of(pt)
                every { userRepository.findById(407L) } returns Optional.of(destUser)
                every { organizationRepository.findByName(destOwner) } returns Optional.empty()
                every { projectRepository.save(any()) } returns proj
                every { projectUserRepository.findByProjectIdAndUserId(507L, 307L) } returns Optional.empty()
                every { userRepository.findByLoginId(destOwner) } returns Optional.of(destUser)
                every { projectUserRepository.findByProjectIdAndUserId(507L, 407L) } returns Optional.empty()
                every { roleRepository.findById(RoleType.MANAGER.roleType) } returns Optional.of(managerRole)
                every { projectUserRepository.save(any()) } returns mockk()
                every { projectTransferRepository.delete(any()) } returns Unit

                projectService.acceptTransfer(607L, "key", 407L)

                sourceDir.exists() shouldBe false
                targetDir.exists() shouldBe true
            } finally {
                sourceDir.deleteRecursively()
                targetDir.deleteRecursively()
            }
        }

        // yona-wiki P3-12 2라운드 회귀 테스트 — 위 SVN 테스트와 동일한 결함(".git" 접미사 하드코딩)이
        // Mercurial 프로젝트에도 그대로 적용됐었다. HgRepository.getDirectory()도 접미사 없는
        // "$owner/$name" 경로를 쓰므로 동일하게 검증한다.
        it("Mercurial(MERCURIAL) 저장소 폴더가 실재하면 이관 시 hg 기본 경로에서 이동돼야 한다") {
            val owner = "phys-hg-owner"
            val name = "phys-hg-repo"
            val destOwner = "phys-hg-dest-owner"
            val sender2 = User(id = 309L, loginId = owner, name = "물리hg")
            val proj = Project(id = 509L, name = name, owner = owner, vcs = "MERCURIAL")
            val pt = ProjectTransfer(
                id = 609L, project = proj, sender = sender2, destination = destOwner,
                confirmKey = "key", newProjectName = name, requested = Instant.now()
            )
            val destUser = User(id = 409L, loginId = destOwner, name = "물리hg수신")
            val managerRole = Role(id = RoleType.MANAGER.roleType)

            val sourceDir = File("/tmp/yona/hg/$owner/$name")
            val targetDir = File("/tmp/yona/hg/$destOwner/$name")
            sourceDir.deleteRecursively()
            targetDir.deleteRecursively()
            try {
                sourceDir.mkdirs()

                every { projectTransferRepository.findByIdAndAcceptedAndRequestedAfter(609L, false, any()) } returns Optional.of(pt)
                every { userRepository.findById(409L) } returns Optional.of(destUser)
                every { organizationRepository.findByName(destOwner) } returns Optional.empty()
                every { projectRepository.save(any()) } returns proj
                every { projectUserRepository.findByProjectIdAndUserId(509L, 309L) } returns Optional.empty()
                every { userRepository.findByLoginId(destOwner) } returns Optional.of(destUser)
                every { projectUserRepository.findByProjectIdAndUserId(509L, 409L) } returns Optional.empty()
                every { roleRepository.findById(RoleType.MANAGER.roleType) } returns Optional.of(managerRole)
                every { projectUserRepository.save(any()) } returns mockk()
                every { projectTransferRepository.delete(any()) } returns Unit

                projectService.acceptTransfer(609L, "key", 409L)

                sourceDir.exists() shouldBe false
                targetDir.exists() shouldBe true
            } finally {
                sourceDir.deleteRecursively()
                targetDir.deleteRecursively()
            }
        }

        // yona isAuthorizedToAcceptTransfer()의 "조직 소속이 아예 아닌 사용자" 분기(orgUser==null) 대응.
        it("이관 목적지가 조직이고 수락자가 그 조직의 소속원이 전혀 아니면 권한 없음 예외가 발생해야 한다") {
            val sender2 = User(id = 308L, loginId = "sender-308", name = "보내는사람308")
            val proj = Project(id = 508L, name = "org-no-member-proj", owner = "sender-308", vcs = "GIT")
            val pt = ProjectTransfer(
                id = 608L, project = proj, sender = sender2, destination = "org-no-member",
                confirmKey = "key", newProjectName = "org-no-member-proj", requested = Instant.now()
            )
            val outsider = User(id = 408L, loginId = "outsider", name = "외부인")
            val org = Organization(id = 708L, name = "org-no-member")

            every { projectTransferRepository.findByIdAndAcceptedAndRequestedAfter(608L, false, any()) } returns Optional.of(pt)
            every { userRepository.findById(408L) } returns Optional.of(outsider)
            every { organizationRepository.findByName("org-no-member") } returns Optional.of(org)
            every { organizationUserRepository.findByOrganizationIdAndUserId(708L, 408L) } returns Optional.empty()

            shouldThrow<IllegalArgumentException> {
                projectService.acceptTransfer(608L, "key", 408L)
            }
        }

        // yona recordRenameOrTransferHistoryIfLastChangePassed24HoursFrom()의 "24시간 초과 -> 재기록"
        // 분기(lastChanged.isBefore(now-24h)==true) 대응 — 기존 테스트는 null(최초)과 24시간 이내
        // 두 경우만 다뤄, 24시간을 실제로 넘긴 경우의 재기록 분기는 아직 검증되지 않았었다.
        it("previousNameChangedTime이 24시간을 초과했으면 예전 위치가 다시 갱신돼야 한다") {
            val oldChange = Instant.now().minusSeconds(25 * 3600) // 25시간 전
            val sender2 = User(id = 309L, loginId = "sender-309", name = "보내는사람309")
            val proj = Project(
                id = 509L, name = "old-history-proj", owner = "sender-309", vcs = "GIT",
                previousOwnerLoginId = "ancient-owner", previousName = "ancient-name",
                previousNameChangedTime = oldChange
            )
            val pt = ProjectTransfer(
                id = 609L, project = proj, sender = sender2, destination = "dest-409",
                confirmKey = "key", newProjectName = "old-history-proj", requested = Instant.now()
            )
            val destUser = User(id = 409L, loginId = "dest-409", name = "받는사람409")
            val managerRole = Role(id = RoleType.MANAGER.roleType)

            every { projectTransferRepository.findByIdAndAcceptedAndRequestedAfter(609L, false, any()) } returns Optional.of(pt)
            every { userRepository.findById(409L) } returns Optional.of(destUser)
            every { organizationRepository.findByName("dest-409") } returns Optional.empty()
            every { projectRepository.save(any()) } returns proj
            every { projectUserRepository.findByProjectIdAndUserId(509L, 309L) } returns Optional.empty()
            every { userRepository.findByLoginId("dest-409") } returns Optional.of(destUser)
            every { projectUserRepository.findByProjectIdAndUserId(509L, 409L) } returns Optional.empty()
            every { roleRepository.findById(RoleType.MANAGER.roleType) } returns Optional.of(managerRole)
            every { projectUserRepository.save(any()) } returns mockk()
            every { projectTransferRepository.delete(any()) } returns Unit

            projectService.acceptTransfer(609L, "key", 409L)

            proj.previousOwnerLoginId shouldBe "sender-309"
            proj.previousName shouldBe "old-history-proj"
            proj.previousNameChangedTime shouldNotBe oldChange
        }

        it("존재하지 않거나 만료된 이관 요청이면 예외가 발생해야 한다") {
            every {
                projectTransferRepository.findByIdAndAcceptedAndRequestedAfter(777001L, false, any())
            } returns Optional.empty()

            shouldThrow<IllegalArgumentException> {
                projectService.acceptTransfer(777001L, "any-key", 1L)
            }
        }

        it("수락자(acceptor) 사용자를 찾을 수 없으면 예외가 발생해야 한다") {
            val sender2 = User(id = 310L, loginId = "sender-310", name = "보내는사람310")
            val proj = Project(id = 510L, name = "no-acceptor-proj", owner = "sender-310", vcs = "GIT")
            val pt = ProjectTransfer(
                id = 610L, project = proj, sender = sender2, destination = "dest-410",
                confirmKey = "key", newProjectName = "no-acceptor-proj", requested = Instant.now()
            )
            every { projectTransferRepository.findByIdAndAcceptedAndRequestedAfter(610L, false, any()) } returns Optional.of(pt)
            every { userRepository.findById(410L) } returns Optional.empty()

            shouldThrow<IllegalArgumentException> {
                projectService.acceptTransfer(610L, "key", 410L)
            }
        }

        // val originalOwner = project.owner ?: "" — 이관 전 프로젝트의 owner가 null인 경우(엘비스 분기).
        it("이관 전 프로젝트의 owner가 null이었으면 예전 위치가 빈 문자열로 기록돼야 한다") {
            val sender2 = User(id = 311L, loginId = "sender-311", name = "보내는사람311")
            val proj = Project(id = 511L, name = "null-owner-proj", owner = null, vcs = "GIT")
            val pt = ProjectTransfer(
                id = 611L, project = proj, sender = sender2, destination = "dest-411",
                confirmKey = "key", newProjectName = "null-owner-proj", requested = Instant.now()
            )
            val destUser = User(id = 411L, loginId = "dest-411", name = "받는사람411")
            val managerRole = Role(id = RoleType.MANAGER.roleType)

            every { projectTransferRepository.findByIdAndAcceptedAndRequestedAfter(611L, false, any()) } returns Optional.of(pt)
            every { userRepository.findById(411L) } returns Optional.of(destUser)
            every { organizationRepository.findByName("dest-411") } returns Optional.empty()
            every { projectRepository.save(any()) } returns proj
            every { projectUserRepository.findByProjectIdAndUserId(511L, 311L) } returns Optional.empty()
            every { userRepository.findByLoginId("dest-411") } returns Optional.of(destUser)
            every { projectUserRepository.findByProjectIdAndUserId(511L, 411L) } returns Optional.empty()
            every { roleRepository.findById(RoleType.MANAGER.roleType) } returns Optional.of(managerRole)
            every { projectUserRepository.save(any()) } returns mockk()
            every { projectTransferRepository.delete(any()) } returns Unit

            projectService.acceptTransfer(611L, "key", 411L)

            proj.owner shouldBe "dest-411"
            proj.previousOwnerLoginId shouldBe ""
        }

        // baseDir 판정용 vcs?.uppercase() 체인 — vcs가 null(엘비스 분기)인 경우 기본 GIT 경로로 처리돼야 한다.
        it("vcs가 null인 프로젝트를 이관해도 기본 GIT 경로 기준으로 정상 처리돼야 한다") {
            val sender2 = User(id = 312L, loginId = "sender-312", name = "보내는사람312")
            val proj = Project(id = 512L, name = "vcs-null-proj", owner = "sender-312", vcs = null)
            val pt = ProjectTransfer(
                id = 612L, project = proj, sender = sender2, destination = "dest-412",
                confirmKey = "key", newProjectName = "vcs-null-proj", requested = Instant.now()
            )
            val destUser = User(id = 412L, loginId = "dest-412", name = "받는사람412")
            val managerRole = Role(id = RoleType.MANAGER.roleType)

            every { projectTransferRepository.findByIdAndAcceptedAndRequestedAfter(612L, false, any()) } returns Optional.of(pt)
            every { userRepository.findById(412L) } returns Optional.of(destUser)
            every { organizationRepository.findByName("dest-412") } returns Optional.empty()
            every { projectRepository.save(any()) } returns proj
            every { projectUserRepository.findByProjectIdAndUserId(512L, 312L) } returns Optional.empty()
            every { userRepository.findByLoginId("dest-412") } returns Optional.of(destUser)
            every { projectUserRepository.findByProjectIdAndUserId(512L, 412L) } returns Optional.empty()
            every { roleRepository.findById(RoleType.MANAGER.roleType) } returns Optional.of(managerRole)
            every { projectUserRepository.save(any()) } returns mockk()
            every { projectTransferRepository.delete(any()) } returns Unit

            projectService.acceptTransfer(612L, "key", 412L)

            proj.owner shouldBe "dest-412"
        }

        // vcs가 축약형 "SVN"인 경우도 svn 기본 경로로 판정돼야 한다(OR의 두 번째 항이 true).
        it("vcs가 SVN(축약형)인 프로젝트를 이관하면 svn 기본 경로 기준으로 판단돼야 한다") {
            val sender2 = User(id = 313L, loginId = "sender-313", name = "보내는사람313")
            val proj = Project(id = 513L, name = "vcs-svn-abbrev-proj", owner = "sender-313", vcs = "SVN")
            val pt = ProjectTransfer(
                id = 613L, project = proj, sender = sender2, destination = "dest-413",
                confirmKey = "key", newProjectName = "vcs-svn-abbrev-proj", requested = Instant.now()
            )
            val destUser = User(id = 413L, loginId = "dest-413", name = "받는사람413")
            val managerRole = Role(id = RoleType.MANAGER.roleType)

            every { projectTransferRepository.findByIdAndAcceptedAndRequestedAfter(613L, false, any()) } returns Optional.of(pt)
            every { userRepository.findById(413L) } returns Optional.of(destUser)
            every { organizationRepository.findByName("dest-413") } returns Optional.empty()
            every { projectRepository.save(any()) } returns proj
            every { projectUserRepository.findByProjectIdAndUserId(513L, 313L) } returns Optional.empty()
            every { userRepository.findByLoginId("dest-413") } returns Optional.of(destUser)
            every { projectUserRepository.findByProjectIdAndUserId(513L, 413L) } returns Optional.empty()
            every { roleRepository.findById(RoleType.MANAGER.roleType) } returns Optional.of(managerRole)
            every { projectUserRepository.save(any()) } returns mockk()
            every { projectTransferRepository.delete(any()) } returns Unit

            projectService.acceptTransfer(613L, "key", 413L)

            proj.owner shouldBe "dest-413"
        }

        // senderProjectUser.role.id는 Role.id: Long?(nullable)라, id가 null인 방어적 케이스도
        // 강등 판정(role.id == MANAGER.roleType)에서 false로 안전하게 처리돼야 한다.
        it("송신자의 역할 id가 null이면 강등 판정에서 false로 처리돼 강등되지 않아야 한다") {
            val sender2 = User(id = 314L, loginId = "sender-314", name = "보내는사람314")
            val proj = Project(id = 514L, name = "role-id-null-proj", owner = "sender-314", vcs = "GIT")
            val pt = ProjectTransfer(
                id = 614L, project = proj, sender = sender2, destination = "dest-414",
                confirmKey = "key", newProjectName = "role-id-null-proj", requested = Instant.now()
            )
            val destUser = User(id = 414L, loginId = "dest-414", name = "받는사람414")
            val managerRole = Role(id = RoleType.MANAGER.roleType)
            val roleWithNullId = Role(id = null)
            val senderProjectUser = ProjectUser(id = 805L, user = sender2, project = proj, role = roleWithNullId)

            every { projectTransferRepository.findByIdAndAcceptedAndRequestedAfter(614L, false, any()) } returns Optional.of(pt)
            every { userRepository.findById(414L) } returns Optional.of(destUser)
            every { organizationRepository.findByName("dest-414") } returns Optional.empty()
            every { projectRepository.save(any()) } returns proj
            every { projectUserRepository.findByProjectIdAndUserId(514L, 314L) } returns Optional.of(senderProjectUser)
            every { userRepository.findByLoginId("dest-414") } returns Optional.of(destUser)
            every { projectUserRepository.findByProjectIdAndUserId(514L, 414L) } returns Optional.empty()
            every { roleRepository.findById(RoleType.MANAGER.roleType) } returns Optional.of(managerRole)
            every { projectUserRepository.save(any()) } returns mockk()
            every { projectTransferRepository.delete(any()) } returns Unit

            projectService.acceptTransfer(614L, "key", 414L)

            senderProjectUser.role shouldBe roleWithNullId
        }

        // isAuthorizedToAcceptTransfer()의 orgUser.role.id == ORG_ADMIN.roleType — id가 null인
        // 방어적 케이스도 권한 없음(false)으로 안전하게 처리돼야 한다.
        it("조직 이관 수락자의 역할 id가 null이면 권한 없음으로 처리돼야 한다") {
            val sender2 = User(id = 315L, loginId = "sender-315", name = "보내는사람315")
            val proj = Project(id = 515L, name = "org-role-id-null-proj", owner = "sender-315", vcs = "GIT")
            val pt = ProjectTransfer(
                id = 615L, project = proj, sender = sender2, destination = "org-role-id-null",
                confirmKey = "key", newProjectName = "org-role-id-null-proj", requested = Instant.now()
            )
            val member = User(id = 415L, loginId = "org-member-null-role", name = "조직원")
            val org = Organization(id = 709L, name = "org-role-id-null")
            val roleWithNullId = Role(id = null)
            val orgUser = OrganizationUser(id = 900L, user = member, organization = org, role = roleWithNullId)

            every { projectTransferRepository.findByIdAndAcceptedAndRequestedAfter(615L, false, any()) } returns Optional.of(pt)
            every { userRepository.findById(415L) } returns Optional.of(member)
            every { organizationRepository.findByName("org-role-id-null") } returns Optional.of(org)
            every { organizationUserRepository.findByOrganizationIdAndUserId(709L, 415L) } returns Optional.of(orgUser)

            shouldThrow<IllegalArgumentException> {
                projectService.acceptTransfer(615L, "key", 415L)
            }
        }
    }

    // yona Project.findByOwnerAndProjectName()/findProjectsByOwner() 대응 — 단순 위임 메서드지만
    // 메서드 커버리지(95%) 목표를 위해 이 스펙에서도 최소 한 번씩은 실행돼야 한다.
    describe("ProjectServiceImpl.findByOwnerAndName / findProjectsByOwner") {
        it("findByOwnerAndName: 프로젝트가 있으면 그 프로젝트를 반환해야 한다") {
            val project = Project(id = 9960L, name = "p", owner = "owner-a")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner-a", "p") } returns Optional.of(project)

            projectService.findByOwnerAndName("owner-a", "p") shouldBe project
        }

        it("findByOwnerAndName: 프로젝트가 없으면 null을 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner-b", "none") } returns Optional.empty()

            projectService.findByOwnerAndName("owner-b", "none") shouldBe null
        }

        it("findProjectsByOwner: owner가 소유한 프로젝트 목록을 그대로 반환해야 한다") {
            val projects = listOf(Project(id = 9961L, name = "p1", owner = "owner-c"))
            every { projectRepository.findByOwner("owner-c") } returns projects

            projectService.findProjectsByOwner("owner-c") shouldBe projects
        }
    }

    // yona Project.newProjectName(loginId, projectName) 대응 (P1-72).
    describe("ProjectServiceImpl.requestNewTransfer") {
        val sender = User(id = 1L, loginId = "sender", name = "보내는사람")
        val project = Project(id = 10L, name = "yona-project", owner = "sender", vcs = "GIT")

        it("목적지에 동명 프로젝트가 없으면 이름 변경 없이 그대로 이관 요청되어야 한다") {
            every { projectRepository.findById(10L) } returns Optional.of(project)
            every { userRepository.findById(1L) } returns Optional.of(sender)
            every { userRepository.findByLoginId("new-owner") } returns Optional.empty()
            every { projectRepository.findByOwner("new-owner") } returns emptyList()
            every { projectRepository.findByOwnerAndName("new-owner", "yona-project") } returns Optional.empty()
            every {
                projectTransferRepository.findByProjectAndSenderAndDestination(project, sender, "new-owner")
            } returns Optional.empty()
            every { projectTransferRepository.save(any()) } answers { firstArg() }

            val pt = projectService.requestNewTransfer(10L, 1L, "new-owner")

            pt.newProjectName shouldBe "yona-project"
        }

        it("목적지에 동명 프로젝트가 있으면 충돌이 없을 때까지 뒤에 -1, -2...를 붙여야 한다") {
            every { projectRepository.findById(10L) } returns Optional.of(project)
            every { userRepository.findById(1L) } returns Optional.of(sender)
            every { userRepository.findByLoginId("new-owner") } returns Optional.empty()
            every { projectRepository.findByOwner("new-owner") } returns emptyList()
            every { projectRepository.findByOwnerAndName("new-owner", "yona-project") } returns
                Optional.of(Project(id = 99L, name = "yona-project", owner = "new-owner"))
            every { projectRepository.findByOwnerAndName("new-owner", "yona-project-1") } returns
                Optional.of(Project(id = 98L, name = "yona-project-1", owner = "new-owner"))
            every { projectRepository.findByOwnerAndName("new-owner", "yona-project-2") } returns Optional.empty()
            every {
                projectTransferRepository.findByProjectAndSenderAndDestination(project, sender, "new-owner")
            } returns Optional.empty()
            every { projectTransferRepository.save(any()) } answers { firstArg() }

            val pt = projectService.requestNewTransfer(10L, 1L, "new-owner")

            pt.newProjectName shouldBe "yona-project-2"
        }

        it("프로젝트를 찾을 수 없으면 예외가 발생해야 한다") {
            every { projectRepository.findById(9980L) } returns Optional.empty()

            shouldThrow<IllegalArgumentException> {
                projectService.requestNewTransfer(9980L, 1L, "new-owner")
            }
        }

        it("보내는 사람(sender)을 찾을 수 없으면 예외가 발생해야 한다") {
            every { projectRepository.findById(10L) } returns Optional.of(project)
            every { userRepository.findById(9981L) } returns Optional.empty()

            shouldThrow<IllegalArgumentException> {
                projectService.requestNewTransfer(10L, 9981L, "new-owner")
            }
        }

        // 동일한 (project, sender, destination) 조합의 이관 요청이 이미 존재하면 새로 만들지 않고
        // 기존 요청의 requested/confirmKey만 갱신해야 한다(existing.isPresent==true 분기).
        it("동일 목적지로의 이관 요청이 이미 존재하면 새로 만들지 않고 기존 요청을 갱신해야 한다") {
            val existingPt = ProjectTransfer(
                id = 700L, project = project, sender = sender, destination = "new-owner",
                confirmKey = "old-key", newProjectName = "yona-project",
                requested = Instant.now().minusSeconds(3600)
            )
            every { projectRepository.findById(10L) } returns Optional.of(project)
            every { userRepository.findById(1L) } returns Optional.of(sender)
            every { userRepository.findByLoginId("new-owner") } returns Optional.empty()
            every { projectRepository.findByOwner("new-owner") } returns emptyList()
            every { projectRepository.findByOwnerAndName("new-owner", "yona-project") } returns Optional.empty()
            every {
                projectTransferRepository.findByProjectAndSenderAndDestination(project, sender, "new-owner")
            } returns Optional.of(existingPt)
            every { projectTransferRepository.save(any()) } answers { firstArg() }

            val result = projectService.requestNewTransfer(10L, 1L, "new-owner")

            result shouldBe existingPt
            existingPt.confirmKey shouldNotBe "old-key"
            verify(exactly = 1) { projectTransferRepository.save(existingPt) }
        }
    }

    describe("ProjectServiceImpl.attachLabel/detachLabel (P1-13)") {
        val project = Project(id = 1L, name = "yona-project", owner = "owner", vcs = "GIT")

        it("같은 category+name의 라벨이 없으면 새로 생성하여 프로젝트에 붙여야 한다") {
            every { projectRepository.findById(1L) } returns Optional.of(project)
            every { labelRepository.findByCategoryAndName("os", "linux") } returns Optional.empty()
            every { labelRepository.save(any()) } answers { (it.invocation.args[0] as Label).apply { id = 100L } }
            every { projectRepository.save(any()) } returns project

            val result = projectService.attachLabel(1L, "os", "linux")

            result.isCreated shouldBe true
            result.isAttached shouldBe true
            result.label.name shouldBe "linux"
            project.labels.any { it.id == 100L } shouldBe true
        }

        it("category가 없으면 기본값 'Label'로 처리해야 한다") {
            val freshProject = Project(id = 2L, name = "p2", owner = "owner", vcs = "GIT")
            every { projectRepository.findById(2L) } returns Optional.of(freshProject)
            every { labelRepository.findByCategoryAndName("Label", "urgent") } returns Optional.empty()
            every { labelRepository.save(any()) } answers { (it.invocation.args[0] as Label).apply { id = 101L } }
            every { projectRepository.save(any()) } returns freshProject

            val result = projectService.attachLabel(2L, null, "urgent")

            result.label.category shouldBe "Label"
        }

        it("이미 존재하는 라벨이지만 이 프로젝트엔 아직 없으면 새로 만들지 않고 붙이기만 해야 한다") {
            val freshProject = Project(id = 3L, name = "p3", owner = "owner", vcs = "GIT")
            val existingLabel = Label(id = 200L, category = "os", name = "linux")
            every { projectRepository.findById(3L) } returns Optional.of(freshProject)
            every { labelRepository.findByCategoryAndName("os", "linux") } returns Optional.of(existingLabel)
            every { projectRepository.save(any()) } returns freshProject

            val result = projectService.attachLabel(3L, "os", "linux")

            result.isCreated shouldBe false
            result.isAttached shouldBe true
        }

        it("이미 이 프로젝트에 붙어있는 라벨이면 isAttached=false를 반환해야 한다") {
            val existingLabel = Label(id = 200L, category = "os", name = "linux")
            val projectWithLabel = Project(id = 4L, name = "p4", owner = "owner", vcs = "GIT")
            projectWithLabel.labels.add(existingLabel)
            every { projectRepository.findById(4L) } returns Optional.of(projectWithLabel)
            every { labelRepository.findByCategoryAndName("os", "linux") } returns Optional.of(existingLabel)

            val result = projectService.attachLabel(4L, "os", "linux")

            result.isCreated shouldBe false
            result.isAttached shouldBe false
        }

        it("존재하지 않는 라벨 id로 detach를 시도하면 false를 반환해야 한다(404 대응)") {
            every { projectRepository.findById(1L) } returns Optional.of(project)
            every { labelRepository.findById(999L) } returns Optional.empty()

            val result = projectService.detachLabel(1L, 999L)

            result shouldBe false
        }

        it("detach 후 다른 프로젝트가 더 이상 없으면 라벨 자체를 삭제해야 한다") {
            val label = Label(id = 200L, category = "os", name = "linux")
            val projectWithLabel = Project(id = 5L, name = "p5", owner = "owner", vcs = "GIT")
            projectWithLabel.labels.add(label)
            every { projectRepository.findById(5L) } returns Optional.of(projectWithLabel)
            every { labelRepository.findById(200L) } returns Optional.of(label)
            every { projectRepository.save(any()) } returns projectWithLabel
            every { projectRepository.countByLabelsId(200L) } returns 0L
            every { labelRepository.delete(label) } returns Unit

            val result = projectService.detachLabel(5L, 200L)

            result shouldBe true
            projectWithLabel.labels.any { it.id == 200L } shouldBe false
        }

        it("detach 후에도 다른 프로젝트가 그 라벨을 쓰고 있으면 라벨을 삭제하지 않아야 한다") {
            val label = Label(id = 201L, category = "os", name = "mac")
            val projectWithLabel = Project(id = 6L, name = "p6", owner = "owner", vcs = "GIT")
            projectWithLabel.labels.add(label)
            every { projectRepository.findById(6L) } returns Optional.of(projectWithLabel)
            every { labelRepository.findById(201L) } returns Optional.of(label)
            every { projectRepository.save(any()) } returns projectWithLabel
            every { projectRepository.countByLabelsId(201L) } returns 1L

            val result = projectService.detachLabel(6L, 201L)

            result shouldBe true
            verify(exactly = 0) { labelRepository.delete(label) }
        }

        it("attachLabel: 프로젝트를 찾을 수 없으면 예외가 발생해야 한다") {
            every { projectRepository.findById(9970L) } returns Optional.empty()

            shouldThrow<IllegalArgumentException> {
                projectService.attachLabel(9970L, "os", "linux")
            }
        }

        it("detachLabel: 프로젝트를 찾을 수 없으면 예외가 발생해야 한다") {
            every { projectRepository.findById(9971L) } returns Optional.empty()

            shouldThrow<IllegalArgumentException> {
                projectService.detachLabel(9971L, 1L)
            }
        }
    }

    // yona Project.getLabels() 대응 — 존재하지 않는 프로젝트 조회 시 예외, 정상 조회 시 라벨 집합 반환.
    describe("ProjectServiceImpl.getProjectLabels") {
        it("프로젝트를 찾을 수 없으면 예외가 발생해야 한다") {
            every { projectRepository.findById(9972L) } returns Optional.empty()

            shouldThrow<IllegalArgumentException> {
                projectService.getProjectLabels(9972L)
            }
        }

        it("프로젝트가 존재하면 그 프로젝트에 붙은 라벨 집합을 반환해야 한다") {
            val label = Label(id = 300L, category = "os", name = "linux")
            val project = Project(id = 9973L, name = "p", owner = "owner")
            project.labels.add(label)
            every { projectRepository.findById(9973L) } returns Optional.of(project)

            val result = projectService.getProjectLabels(9973L)

            result shouldBe setOf(label)
        }
    }

    // yona ProjectApp.settingProject()의 이름 변경(개명) 분기 대응 (P1-144). 소유자는 그대로 두고
    // 이름만 바꾸는 경로 자체가 yona에 없었다 — validateWhenUpdate()의 projectNameChangeable() 중복
    // 검사, recordRenameOrTransferHistoryIfLastChangePassed24HoursFrom(), repository.renameTo(),
    // FavoriteProject.updateFavoriteProject() 네 가지를 전부 그대로 재현한다.
    describe("ProjectServiceImpl.updateProject - 개명 (P1-144)") {
        val playRepository = mockk<PlayRepository>()

        beforeTest {
            clearMocks(playRepository, answers = false)
        }

        fun baseParam(name: String? = null) = UpdateProjectParam(
            name = name,
            overview = "설명",
            projectScope = ProjectScope.PRIVATE,
            isCodeAccessibleMemberOnly = false,
            isUsingReviewerCount = false,
            defaultReviewerCount = 1,
            defaultBranch = null,
            isCodeEnabled = true,
            isIssueEnabled = true,
            isPullRequestEnabled = true,
            isReviewEnabled = true,
            isMilestoneEnabled = true,
            isBoardEnabled = true
        )

        it("이름이 바뀌지 않으면 저장소 rename을 호출하지 않아야 한다") {
            val project = Project(id = 20L, name = "old-name", owner = "owner1", vcs = "GIT")
            every { projectRepository.findById(20L) } returns Optional.of(project)
            every { projectRepository.save(any()) } returns project

            projectService.updateProject(20L, baseParam(name = "old-name"))

            verify(exactly = 0) { repositoryService.getRepository(any()) }
        }

        it("같은 소유자 내 이미 존재하는 이름으로 바꾸려 하면 예외가 발생하고 아무것도 바뀌지 않아야 한다") {
            val project = Project(id = 21L, name = "old-name", owner = "owner1", vcs = "GIT")
            every { projectRepository.findById(21L) } returns Optional.of(project)
            every {
                projectRepository.existsByOwnerIgnoreCaseAndNameIgnoreCaseAndIdNot("owner1", "taken-name", 21L)
            } returns true

            shouldThrow<IllegalArgumentException> {
                projectService.updateProject(21L, baseParam(name = "taken-name"))
            }

            project.name shouldBe "old-name"
            verify(exactly = 0) { repositoryService.getRepository(any()) }
        }

        it("저장소 rename에 실패하면 예외가 발생하고 이름이 바뀌지 않아야 한다") {
            val project = Project(id = 22L, name = "old-name", owner = "owner1", vcs = "GIT")
            every { projectRepository.findById(22L) } returns Optional.of(project)
            every {
                projectRepository.existsByOwnerIgnoreCaseAndNameIgnoreCaseAndIdNot("owner1", "new-name", 22L)
            } returns false
            every { repositoryService.getRepository(project) } returns playRepository
            every { playRepository.renameTo("new-name") } returns false

            shouldThrow<IllegalStateException> {
                projectService.updateProject(22L, baseParam(name = "new-name"))
            }

            project.name shouldBe "old-name"
        }

        it("이름을 바꾸면 저장소도 rename되고, 개명 이력과 즐겨찾기 owner/projectName이 함께 갱신돼야 한다") {
            val project = Project(id = 23L, name = "old-name", owner = "owner1", vcs = "GIT")
            val favorite = FavoriteProject(id = 900L, user = mockk(relaxed = true), project = project, owner = "owner1", projectName = "old-name")

            every { projectRepository.findById(23L) } returns Optional.of(project)
            every {
                projectRepository.existsByOwnerIgnoreCaseAndNameIgnoreCaseAndIdNot("owner1", "new-name", 23L)
            } returns false
            every { repositoryService.getRepository(project) } returns playRepository
            every { playRepository.renameTo("new-name") } returns true
            every { projectRepository.save(any()) } returns project
            every { favoriteProjectRepository.findByProjectId(23L) } returns listOf(favorite)
            every { favoriteProjectRepository.save(any()) } returns favorite

            projectService.updateProject(23L, baseParam(name = "new-name"))

            project.name shouldBe "new-name"
            project.previousName shouldBe "old-name"
            project.previousOwnerLoginId shouldBe "owner1"
            project.previousNameChangedTime shouldNotBe null
            favorite.owner shouldBe "owner1"
            favorite.projectName shouldBe "new-name"
            verify(exactly = 1) { playRepository.renameTo("new-name") }
        }

        it("최근 24시간 내 이미 개명 이력이 있으면 previousName을 다시 덮어쓰지 않아야 한다") {
            val recentChange = Instant.now().minusSeconds(3600) // 1시간 전
            val project = Project(
                id = 24L, name = "old-name", owner = "owner1", vcs = "GIT",
                previousName = "very-old-name", previousOwnerLoginId = "owner1",
                previousNameChangedTime = recentChange
            )

            every { projectRepository.findById(24L) } returns Optional.of(project)
            every {
                projectRepository.existsByOwnerIgnoreCaseAndNameIgnoreCaseAndIdNot("owner1", "new-name", 24L)
            } returns false
            every { repositoryService.getRepository(project) } returns playRepository
            every { playRepository.renameTo("new-name") } returns true
            every { projectRepository.save(any()) } returns project
            every { favoriteProjectRepository.findByProjectId(24L) } returns emptyList()

            projectService.updateProject(24L, baseParam(name = "new-name"))

            // yona isRenamedOrTransferredIn24Hours()가 false를 반환하는 경우 — 예전 위치 포인터를
            // 그대로 보존해 사용자가 "가장 먼저" 있었던 위치로 계속 폴백 조회할 수 있게 한다.
            project.previousName shouldBe "very-old-name"
            project.previousNameChangedTime shouldBe recentChange
        }

        it("프로젝트를 찾을 수 없으면 예외가 발생해야 한다") {
            every { projectRepository.findById(25L) } returns Optional.empty()

            shouldThrow<IllegalArgumentException> {
                projectService.updateProject(25L, baseParam(name = "whatever"))
            }
        }

        // param.name이 아예 null(변경 요청 없음)이면 "param.name != null" 분기 자체가 false가 되어
        // 개명 관련 로직 전체(중복검사/rename/이력기록/즐겨찾기 갱신)를 건너뛰어야 한다.
        it("param.name이 null이면 개명 로직 전체를 건너뛰어야 한다") {
            val project = Project(id = 26L, name = "keep-name", owner = "owner1", vcs = "GIT")
            every { projectRepository.findById(26L) } returns Optional.of(project)
            every { projectRepository.save(any()) } returns project
            // projectRepository/repositoryService는 스펙 전역 공유 mock이라 이전 it들의 호출 이력이
            // 누적돼 있다. exactly=0 단언이 오염되지 않도록 스텁(answers)은 남기고 호출 이력만 초기화한다.
            clearMocks(projectRepository, repositoryService, answers = false)

            projectService.updateProject(26L, baseParam(name = null))

            project.name shouldBe "keep-name"
            verify(exactly = 0) { projectRepository.existsByOwnerIgnoreCaseAndNameIgnoreCaseAndIdNot(any(), any(), any()) }
            verify(exactly = 0) { repositoryService.getRepository(any()) }
        }

        // project.owner ?: "" 엘비스 분기 — owner가 null인 프로젝트의 개명 시도.
        it("프로젝트 owner가 null이어도 이름 변경 시 빈 문자열 owner로 중복 검사가 수행돼야 한다") {
            val project = Project(id = 27L, name = "old-name", owner = null, vcs = "GIT")
            // 즐겨찾기 동기화 루프(project.owner ?: "")의 owner==null 분기까지 실제로 태우기 위해
            // 빈 리스트가 아니라 실제 즐겨찾기 1건을 포함시킨다.
            val favorite = FavoriteProject(id = 901L, user = mockk(relaxed = true), project = project, owner = "old-name-owner", projectName = "old-name")
            every { projectRepository.findById(27L) } returns Optional.of(project)
            every {
                projectRepository.existsByOwnerIgnoreCaseAndNameIgnoreCaseAndIdNot("", "new-name", 27L)
            } returns false
            every { repositoryService.getRepository(project) } returns playRepository
            every { playRepository.renameTo("new-name") } returns true
            every { projectRepository.save(any()) } returns project
            every { favoriteProjectRepository.findByProjectId(27L) } returns listOf(favorite)
            every { favoriteProjectRepository.save(any()) } returns favorite

            projectService.updateProject(27L, baseParam(name = "new-name"))

            project.name shouldBe "new-name"
            favorite.owner shouldBe "" // project.owner가 null이라 빈 문자열로 채워져야 한다
            verify(exactly = 1) { projectRepository.existsByOwnerIgnoreCaseAndNameIgnoreCaseAndIdNot("", "new-name", 27L) }
        }

        it("defaultBranch가 지정되면 저장소의 기본 브랜치를 설정해야 한다") {
            val project = Project(id = 28L, name = "same-name", owner = "owner1", vcs = "GIT")
            every { projectRepository.findById(28L) } returns Optional.of(project)
            every { repositoryService.getRepository(project) } returns playRepository
            every { playRepository.setDefaultBranch("refs/heads/develop") } returns Unit
            every { projectRepository.save(any()) } returns project

            projectService.updateProject(28L, baseParam(name = "same-name").copy(defaultBranch = "develop"))

            verify(exactly = 1) { playRepository.setDefaultBranch("refs/heads/develop") }
        }

        it("기본 브랜치 설정 중 예외가 발생해도 무시하고 저장은 계속돼야 한다") {
            val project = Project(id = 29L, name = "same-name2", owner = "owner1", vcs = "GIT")
            every { projectRepository.findById(29L) } returns Optional.of(project)
            every { repositoryService.getRepository(project) } returns playRepository
            every { playRepository.setDefaultBranch("refs/heads/broken") } throws RuntimeException("boom")
            every { projectRepository.save(any()) } returns project

            val result = projectService.updateProject(29L, baseParam(name = "same-name2").copy(defaultBranch = "broken"))

            result shouldBe project
            verify(exactly = 1) { projectRepository.save(project) }
        }

        it("defaultBranch가 공백 문자열이면 기본 브랜치 설정을 건너뛰어야 한다") {
            val project = Project(id = 30L, name = "same-name3", owner = "owner1", vcs = "GIT")
            every { projectRepository.findById(30L) } returns Optional.of(project)
            every { projectRepository.save(any()) } returns project
            // repositoryService는 스펙 전역 공유 mock이라 이전 it들의 호출 이력이 누적돼 있다.
            // exactly=0 단언이 오염되지 않도록 스텁(answers)은 남기고 호출 이력만 초기화한다.
            clearMocks(repositoryService, answers = false)

            projectService.updateProject(30L, baseParam(name = "same-name3").copy(defaultBranch = "   "))

            verify(exactly = 0) { repositoryService.getRepository(any()) }
        }
    }

    // yona Project.delete() 대응 (P0-19) — 계단식 삭제 전수 이식. 프로젝트 삭제 시 연관된 모든
    // 리소스(이전요청/리뷰스레드/PR/이슈/라벨카테고리/담당자/웹훅/게시글/멤버)가 함께 정리되고,
    // fork 자식 프로젝트는 삭제되지 않고 원본 연결만 끊어져야 한다.
    describe("ProjectServiceImpl.deleteProject") {
        beforeTest {
            clearMocks(
                projectRepository, projectUserRepository, projectTransferRepository,
                issueRepository, issueService, issueLabelCategoryRepository, issueLabelService,
                assigneeRepository, webhookRepository, webhookThreadRepository,
                postingRepository, postingService, commentThreadRepository,
                pullRequestRepository, pullRequestEventRepository, pullRequestCommitRepository,
                answers = false
            )
        }

        it("연관 데이터를 모두 정리한 뒤 프로젝트 자체를 삭제해야 한다") {
            val project = Project(id = 1L, name = "will-delete", owner = "owner")

            val transfer = mockk<ProjectTransfer>(relaxed = true)
            every { projectTransferRepository.findByProjectId(1L) } returns listOf(transfer)
            every { projectTransferRepository.deleteAll(listOf(transfer)) } returns Unit

            val thread = mockk<CommentThread>(relaxed = true)
            every { commentThreadRepository.findByProject(project) } returns listOf(thread)
            every { commentThreadRepository.deleteAll(listOf(thread)) } returns Unit

            val pr = mockk<PullRequest>(relaxed = true)
            every { pr.id } returns 77L
            every { pullRequestRepository.findByFromProject(project) } returns listOf(pr)
            every { pullRequestRepository.findByToProject(project) } returns emptyList()
            // P2-37: PR 단위 CommentThread 정리 — 이 PR엔 project 단위 정리(위 findByProject)로
            // 이미 지워지지 않은 잔여 스레드가 없다고 가정.
            every { commentThreadRepository.findByPullRequest(pr) } returns emptyList()
            every { commentThreadRepository.deleteAll(emptyList()) } returns Unit
            val prEvent = mockk<PullRequestEvent>(relaxed = true)
            every { pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(pr) } returns listOf(prEvent)
            every { pullRequestEventRepository.deleteAll(listOf(prEvent)) } returns Unit
            val prCommit = mockk<PullRequestCommit>(relaxed = true)
            every { pullRequestCommitRepository.findByPullRequest(pr) } returns listOf(prCommit)
            every { pullRequestCommitRepository.deleteAll(listOf(prCommit)) } returns Unit
            every { pullRequestRepository.delete(pr) } returns Unit

            val issue = mockk<Issue>(relaxed = true)
            every { issueRepository.findByProject(project) } returns listOf(issue)
            every { issueService.deleteIssueCascade(issue) } returns Unit

            val category = mockk<IssueLabelCategory>(relaxed = true)
            every { category.id } returns 30L
            every { issueLabelCategoryRepository.findByProject(project) } returns listOf(category)
            every { issueLabelService.deleteCategory(30L) } returns Unit

            val assignee = mockk<Assignee>(relaxed = true)
            every { assigneeRepository.findByProjectId(1L) } returns listOf(assignee)
            every { assigneeRepository.deleteAll(listOf(assignee)) } returns Unit

            val webhook = mockk<Webhook>(relaxed = true)
            every { webhook.id } returns 40L
            every { webhookRepository.findByProjectId(1L) } returns listOf(webhook)
            val webhookThread = mockk<WebhookThread>(relaxed = true)
            every { webhookThreadRepository.findByWebhookId(40L) } returns listOf(webhookThread)
            every { webhookThreadRepository.deleteAll(listOf(webhookThread)) } returns Unit
            every { webhookRepository.delete(webhook) } returns Unit

            val posting = mockk<Posting>(relaxed = true)
            every { postingRepository.findByProject(project) } returns listOf(posting)
            every { postingService.deletePostingCascade(posting) } returns Unit

            val projectUser = mockk<ProjectUser>(relaxed = true)
            every { projectUserRepository.findByProjectId(1L) } returns listOf(projectUser)
            every { projectUserRepository.deleteAll(listOf(projectUser)) } returns Unit

            every { projectRepository.findById(1L) } returns Optional.of(project)
            every { projectRepository.delete(project) } returns Unit

            // TASK-0421(버그9) — 물리 git 저장소도 함께 삭제돼야 한다(changeVCS()와 동일한 패턴).
            val repo = mockk<PlayRepository>(relaxed = true)
            every { repositoryService.getRepository(project) } returns repo

            projectService.deleteProject(1L)

            verify(exactly = 1) { repo.delete() }

            verify(exactly = 1) { projectTransferRepository.deleteAll(listOf(transfer)) }
            verify(exactly = 1) { commentThreadRepository.deleteAll(listOf(thread)) }
            verify(exactly = 1) { pullRequestEventRepository.deleteAll(listOf(prEvent)) }
            verify(exactly = 1) { pullRequestCommitRepository.deleteAll(listOf(prCommit)) }
            verify(exactly = 1) { pullRequestRepository.delete(pr) }
            verify(exactly = 1) { issueService.deleteIssueCascade(issue) }
            verify(exactly = 1) { issueLabelService.deleteCategory(30L) }
            verify(exactly = 1) { assigneeRepository.deleteAll(listOf(assignee)) }
            verify(exactly = 1) { webhookThreadRepository.deleteAll(listOf(webhookThread)) }
            verify(exactly = 1) { webhookRepository.delete(webhook) }
            verify(exactly = 1) { postingService.deletePostingCascade(posting) }
            verify(exactly = 1) { projectUserRepository.deleteAll(listOf(projectUser)) }
            verify(exactly = 1) { projectRepository.delete(project) }
            // yona models/resource/ResourcePersistAdapter.java postDelete() 대응 (P1-147).
            verify(exactly = 1) { watchService.deleteAll(ResourceType.PULL_REQUEST, "77") }
            verify(exactly = 1) { watchService.deleteAll(ResourceType.PROJECT, "1") }
        }

        it("이 프로젝트를 fork한 자식 프로젝트는 삭제하지 않고, 그 fork의 PR만 정리한 뒤 원본 연결을 끊어야 한다") {
            val fork = Project(id = 2L, name = "fork-of-1", owner = "forker")
            val project = Project(id = 1L, name = "original", owner = "owner", forkingProjects = mutableListOf(fork))

            every { projectTransferRepository.findByProjectId(1L) } returns emptyList()
            every { projectTransferRepository.deleteAll(emptyList()) } returns Unit
            every { commentThreadRepository.findByProject(project) } returns emptyList()
            every { commentThreadRepository.deleteAll(emptyList()) } returns Unit
            every { pullRequestRepository.findByFromProject(project) } returns emptyList()
            every { pullRequestRepository.findByToProject(project) } returns emptyList()

            val forkPr = mockk<PullRequest>(relaxed = true)
            every { pullRequestRepository.findByFromProject(fork) } returns listOf(forkPr)
            every { pullRequestRepository.findByToProject(fork) } returns emptyList()
            // P2-37: fork가 제3 프로젝트로 보낸 PR에 달린 CommentThread(thread.project가 그 제3
            // 프로젝트)도 PR 단위로 정리돼야 한다.
            val forkThread = mockk<CommentThread>(relaxed = true)
            every { commentThreadRepository.findByPullRequest(forkPr) } returns listOf(forkThread)
            every { commentThreadRepository.deleteAll(listOf(forkThread)) } returns Unit
            every { pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(forkPr) } returns emptyList()
            every { pullRequestEventRepository.deleteAll(emptyList<PullRequestEvent>()) } returns Unit
            every { pullRequestCommitRepository.findByPullRequest(forkPr) } returns emptyList()
            every { pullRequestCommitRepository.deleteAll(emptyList<PullRequestCommit>()) } returns Unit
            every { pullRequestRepository.delete(forkPr) } returns Unit
            every { projectRepository.save(fork) } returns fork

            every { issueRepository.findByProject(project) } returns emptyList()
            every { issueLabelCategoryRepository.findByProject(project) } returns emptyList()
            every { assigneeRepository.findByProjectId(1L) } returns emptyList()
            every { assigneeRepository.deleteAll(emptyList()) } returns Unit
            every { webhookRepository.findByProjectId(1L) } returns emptyList()
            every { postingRepository.findByProject(project) } returns emptyList()
            every { projectUserRepository.findByProjectId(1L) } returns emptyList()
            every { projectUserRepository.deleteAll(emptyList()) } returns Unit

            every { projectRepository.findById(1L) } returns Optional.of(project)
            every { projectRepository.delete(project) } returns Unit

            // TASK-0421(버그9) — 물리 git 저장소도 함께 삭제돼야 한다(fork 자식의 저장소는 건드리지
            // 않는다 — 이 프로젝트 것만 조회되도록 project로만 스텁한다).
            every { repositoryService.getRepository(project) } returns mockk<PlayRepository>(relaxed = true)

            projectService.deleteProject(1L)

            verify(exactly = 1) { pullRequestRepository.delete(forkPr) }
            verify(exactly = 1) { commentThreadRepository.deleteAll(listOf(forkThread)) }
            verify(exactly = 1) { projectRepository.save(fork) }
            fork.originalProject shouldBe null
            verify(exactly = 0) { projectRepository.delete(fork) }
            verify(exactly = 1) { projectRepository.delete(project) }
        }

        it("프로젝트를 찾을 수 없으면 예외가 발생해야 한다") {
            every { projectRepository.findById(9999L) } returns Optional.empty()

            shouldThrow<IllegalArgumentException> {
                projectService.deleteProject(9999L)
            }
        }
    }

    // yona Project.create() 대응 — 이름 중복 검사, 생성일/사이트URL 세팅, MANAGER 멤버 자동 등록.
    describe("ProjectServiceImpl.createProject") {
        it("이미 동일한 owner/name의 프로젝트가 있으면 예외가 발생해야 한다") {
            val creator = User(id = 900L, loginId = "creator", name = "생성자")
            val newProject = Project(name = "dup-project", owner = "creator")
            every { projectRepository.findByOwnerAndName("creator", "dup-project") } returns
                Optional.of(Project(id = 1000L, name = "dup-project", owner = "creator"))

            shouldThrow<IllegalArgumentException> {
                projectService.createProject(newProject, creator)
            }

            verify(exactly = 0) { projectRepository.save(any()) }
        }

        it("새 프로젝트를 생성하면 생성일/사이트URL이 채워지고 생성자가 MANAGER로 등록돼야 한다") {
            val creator = User(id = 901L, loginId = "creator2", name = "생성자2")
            val newProject = Project(name = "new-project", owner = "creator2")
            val managerRole = Role(id = RoleType.MANAGER.roleType)
            every { projectRepository.findByOwnerAndName("creator2", "new-project") } returns Optional.empty()
            every { projectRepository.save(newProject) } returns newProject
            every { roleRepository.findById(RoleType.MANAGER.roleType) } returns Optional.of(managerRole)
            every { projectUserRepository.save(any()) } returns mockk()
            every { repositoryService.getRepository(newProject) } returns mockk<PlayRepository>(relaxed = true)

            val result = projectService.createProject(newProject, creator)

            result shouldBe newProject
            result.createdDate shouldNotBe null
            result.siteurl shouldBe "http://localhost:9000/new-project"
            result.projectUsers.size shouldBe 1
            result.projectUsers.first().role shouldBe managerRole
            result.projectUsers.first().user shouldBe creator
            verify(exactly = 1) { projectUserRepository.save(any()) }
        }

        // roleRepository.findById(MANAGER).ifPresent {} — MANAGER 역할이 없으면 람다 자체가
        // 실행되지 않아 멤버 등록 없이 프로젝트만 만들어져야 한다(ifPresent의 absent 분기).
        it("MANAGER 역할을 찾지 못하면 멤버 등록 없이 프로젝트만 생성돼야 한다") {
            val creator = User(id = 902L, loginId = "creator3", name = "생성자3")
            val newProject = Project(name = "no-manager-role-project", owner = "creator3")
            every { projectRepository.findByOwnerAndName("creator3", "no-manager-role-project") } returns Optional.empty()
            every { projectRepository.save(newProject) } returns newProject
            every { roleRepository.findById(RoleType.MANAGER.roleType) } returns Optional.empty()
            every { repositoryService.getRepository(newProject) } returns mockk<PlayRepository>(relaxed = true)
            // projectUserRepository는 스펙 전역 공유 mock이라 이전 it들의 호출 이력이 누적돼 있다.
            // exactly=0 단언이 오염되지 않도록 스텁(answers)은 남기고 호출 이력만 초기화한다.
            clearMocks(projectUserRepository, answers = false)

            val result = projectService.createProject(newProject, creator)

            result shouldBe newProject
            result.projectUsers.size shouldBe 0
            verify(exactly = 0) { projectUserRepository.save(any()) }
        }

        // yona models/Project.java:62 @ExConstraints.Restricted({".", "..", ".git"}) 대응 (P1-145).
        // RestrictedValidator.isValid()가 ignoreCase=false(기본값)일 때 오히려 equalsIgnoreCase로 비교하는
        // legacy 버그를 그대로 재현 — 즉 대소문자 무관하게 ".", "..", ".git"과 일치하면 거부된다.
        it("프로젝트명이 예약 패턴(.,..,.git)과 대소문자 무관하게 일치하면 예외가 발생해야 한다") {
            val creator = User(id = 904L, loginId = "creator5", name = "생성자5")
            // projectRepository는 스펙 전역 공유 mock이라 이전 it들의 save() 호출 이력이 누적돼 있다.
            // exactly=0 단언이 오염되지 않도록 스텁(answers)은 남기고 호출 이력만 초기화한다.
            clearMocks(projectRepository, answers = false)

            listOf(".", "..", ".git", ".GIT", ".Git").forEach { restrictedName ->
                val newProject = Project(name = restrictedName, owner = "creator5")

                shouldThrow<IllegalArgumentException> {
                    projectService.createProject(newProject, creator)
                }
            }

            verify(exactly = 0) { projectRepository.save(any()) }
        }

        it("프로젝트명이 예약 패턴을 포함만 하고 정확히 일치하지 않으면 정상 생성돼야 한다") {
            val creator = User(id = 905L, loginId = "creator6", name = "생성자6")
            val newProject = Project(name = "my.git-project", owner = "creator6")
            val managerRole = Role(id = RoleType.MANAGER.roleType)
            every { projectRepository.findByOwnerAndName("creator6", "my.git-project") } returns Optional.empty()
            every { projectRepository.save(newProject) } returns newProject
            every { roleRepository.findById(RoleType.MANAGER.roleType) } returns Optional.of(managerRole)
            every { projectUserRepository.save(any()) } returns mockk()
            every { repositoryService.getRepository(newProject) } returns mockk<PlayRepository>(relaxed = true)

            val result = projectService.createProject(newProject, creator)

            result shouldBe newProject
        }

        // yona ProjectApp.java:191 "RepositoryService.createRepository(project)" 대응 (P0-26).
        // yona의 웹 폼 생성 경로(ProjectViewController.newProject → 이 메서드)는 DB 행만 만들고
        // 물리 bare git/svn 저장소를 생성하지 않아, 이후 README 커밋 등 저장소 쓰기 작업이 전부
        // 조용히 실패하는 회귀가 있었다(BareCommit의 catch(Exception)가 예외를 삼킴).
        it("새 프로젝트를 생성하면 물리 저장소도 함께 생성돼야 한다") {
            val creator = User(id = 906L, loginId = "creator7", name = "생성자7")
            val newProject = Project(name = "repo-created-project", owner = "creator7", vcs = "GIT")
            val managerRole = Role(id = RoleType.MANAGER.roleType)
            val playRepository = mockk<PlayRepository>(relaxed = true)
            every { projectRepository.findByOwnerAndName("creator7", "repo-created-project") } returns Optional.empty()
            every { projectRepository.save(newProject) } returns newProject
            every { roleRepository.findById(RoleType.MANAGER.roleType) } returns Optional.of(managerRole)
            every { projectUserRepository.save(any()) } returns mockk()
            every { repositoryService.getRepository(newProject) } returns playRepository

            projectService.createProject(newProject, creator)

            verify(exactly = 1) { playRepository.create() }
        }

        // exists(project.owner ?: "", project.name) — owner가 null인 프로젝트 생성 시도(엘비스 분기).
        it("owner가 null인 프로젝트를 생성하면 빈 문자열 owner로 중복 검사가 수행돼야 한다") {
            val creator = User(id = 903L, loginId = "creator4", name = "생성자4")
            val newProject = Project(name = "no-owner-project", owner = null)
            val managerRole = Role(id = RoleType.MANAGER.roleType)
            every { projectRepository.findByOwnerAndName("", "no-owner-project") } returns Optional.empty()
            every { projectRepository.save(newProject) } returns newProject
            every { roleRepository.findById(RoleType.MANAGER.roleType) } returns Optional.of(managerRole)
            every { projectUserRepository.save(any()) } returns mockk()
            every { repositoryService.getRepository(newProject) } returns mockk<PlayRepository>(relaxed = true)

            val result = projectService.createProject(newProject, creator)

            result shouldBe newProject
            verify(exactly = 1) { projectRepository.findByOwnerAndName("", "no-owner-project") }
        }
    }

    // yona Project.isMember() 대응 — 사이트 관리자/프로젝트 owner/일반 멤버 세 갈래 판정 경로.
    describe("ProjectServiceImpl.isMember") {
        it("프로젝트가 존재하지 않으면 false를 반환해야 한다") {
            every { projectRepository.findById(9990L) } returns Optional.empty()

            projectService.isMember(9990L, "someone") shouldBe false
        }

        it("사이트 관리자(SITE_ADMIN)이면 프로젝트 멤버가 아니어도 true를 반환해야 한다") {
            val project = Project(id = 9991L, name = "p", owner = "owner-x")
            val siteAdmin = User(id = 950L, loginId = "admin", name = "관리자", state = UserState.SITE_ADMIN)
            every { projectRepository.findById(9991L) } returns Optional.of(project)
            every { userRepository.findByLoginId("admin") } returns Optional.of(siteAdmin)

            projectService.isMember(9991L, "admin") shouldBe true
        }

        it("사이트 관리자가 아닌 일반 유저라도 프로젝트 owner와 loginId가 같으면 true를 반환해야 한다") {
            val project = Project(id = 9992L, name = "p", owner = "owner-y")
            val normalUser = User(id = 951L, loginId = "owner-y", name = "일반유저")
            every { projectRepository.findById(9992L) } returns Optional.of(project)
            every { userRepository.findByLoginId("owner-y") } returns Optional.of(normalUser)

            projectService.isMember(9992L, "owner-y") shouldBe true
        }

        it("사용자를 찾지 못했더라도 프로젝트 owner와 loginId가 일치하면 true를 반환해야 한다") {
            val project = Project(id = 9993L, name = "p", owner = "owner-z")
            every { projectRepository.findById(9993L) } returns Optional.of(project)
            every { userRepository.findByLoginId("owner-z") } returns Optional.empty()

            projectService.isMember(9993L, "owner-z") shouldBe true
        }

        it("owner도 아니고 사이트관리자도 아니면 프로젝트 멤버 여부(existsBy...)로 최종 판정해야 한다") {
            val project = Project(id = 9994L, name = "p", owner = "owner-w")
            val memberUser = User(id = 952L, loginId = "member-user", name = "멤버")
            every { projectRepository.findById(9994L) } returns Optional.of(project)
            every { userRepository.findByLoginId("member-user") } returns Optional.of(memberUser)
            every { projectUserRepository.existsByProjectIdAndUserLoginId(9994L, "member-user") } returns true

            projectService.isMember(9994L, "member-user") shouldBe true
        }

        it("owner도 멤버도 아니면 false를 반환해야 한다") {
            val project = Project(id = 9995L, name = "p", owner = "owner-v")
            every { projectRepository.findById(9995L) } returns Optional.of(project)
            every { userRepository.findByLoginId("stranger") } returns Optional.empty()
            every { projectUserRepository.existsByProjectIdAndUserLoginId(9995L, "stranger") } returns false

            projectService.isMember(9995L, "stranger") shouldBe false
        }
    }

    // yona Project.fork() 대응 — 자식 프로젝트 엔티티 생성/멤버 등록과, 물리 Bare 저장소의
    // 하드링크(Hard Link) 기반 무복사 복제(cloneHardLinkedRepository)까지 실제 파일시스템으로 검증한다.
    describe("ProjectServiceImpl.forkProject / cloneHardLinkedRepository (실제 파일시스템)") {
        val gitBase = File("/tmp/yona/git")
        val svnBase = File("/tmp/yona/svn")
        val hgBase = File("/tmp/yona/hg")

        it("원본 프로젝트가 없으면 예외가 발생해야 한다") {
            every { projectRepository.findById(9001L) } returns Optional.empty()

            shouldThrow<IllegalArgumentException> {
                projectService.forkProject(9001L, 1L, "", "")
            }
        }

        it("포크할 사용자가 없으면 예외가 발생해야 한다") {
            val original = Project(id = 9002L, name = "orig", owner = "owner1", vcs = "GIT")
            every { projectRepository.findById(9002L) } returns Optional.of(original)
            every { userRepository.findById(77L) } returns Optional.empty()

            shouldThrow<IllegalArgumentException> {
                projectService.forkProject(9002L, 77L, "", "")
            }
        }

        // TASK-0418 — 실서버 재현: 목적지 미지정으로 fork하면 destOwner가 forker의 loginId로
        // 대체되는데, forker 본인이 이미 그 프로젝트의 owner라면(자기 자신에게 fork) destOwner/destName이
        // 원본과 완전히 같아져 사전 검증에 걸려야 한다. 파일시스템 하드링크를 시도하기도 전에
        // IllegalArgumentException으로 거절되고, projectRepository.save/projectUserRepository.save가
        // 전혀 호출되지 않아야 한다(DB에 중복 행이 남지 않음을 보장).
        it("목적지 프로젝트(owner+name)가 이미 존재하면 파일시스템 작업 전에 IllegalArgumentException으로 거절해야 한다") {
            val original = Project(id = 9010L, name = "self-fork-repo", owner = "self-fork-owner", vcs = "GIT")
            val forker = User(id = 12L, loginId = "self-fork-owner")
            every { projectRepository.findById(9010L) } returns Optional.of(original)
            every { userRepository.findById(12L) } returns Optional.of(forker)
            every { projectRepository.findByOwnerAndName("self-fork-owner", "self-fork-repo") } returns Optional.of(original)
            // 이 describe 블록의 다른 테스트들이 이미 projectRepository.save/projectUserRepository.save를
            // 호출해뒀을 수 있어(mockk 인스턴스가 파일 전체에서 공유됨) 아래 verify(exactly=0)이
            // "이번 테스트에서" 호출되지 않았는지를 보려면 먼저 기록을 비워야 한다(스텁 자체는
            // 유지 — answers=false).
            clearMocks(projectRepository, projectUserRepository, answers = false)

            shouldThrow<IllegalArgumentException> {
                // 목적지 미지정 -> destOwner=forker.loginId("self-fork-owner"), destName=원본 이름
                // 그대로라 원본과 완전히 동일한 좌표로 fork를 시도하는 상황과 같다.
                projectService.forkProject(9010L, 12L, "", "")
            }

            verify(exactly = 0) { projectRepository.save(any()) }
            verify(exactly = 0) { projectUserRepository.save(any()) }
        }

        it("destinationOwner/destinationName이 빈 값이면 forker의 loginId와 원본 프로젝트명으로 대체돼야 한다 (소스 저장소 없음)") {
            val original = Project(id = 9003L, name = "orig-noexist", owner = "owner-noexist-abcxyz", vcs = "GIT")
            val forker = User(id = 5L, loginId = "forker-login")
            val managerRole = Role(id = RoleType.MANAGER.roleType)
            every { projectRepository.findById(9003L) } returns Optional.of(original)
            every { userRepository.findById(5L) } returns Optional.of(forker)
            every { projectRepository.findByOwnerAndName("forker-login", "orig-noexist") } returns Optional.empty()
            every { projectRepository.save(any()) } answers { firstArg() }
            every { roleRepository.findById(RoleType.MANAGER.roleType) } returns Optional.of(managerRole)
            every { projectUserRepository.save(any()) } returns mockk()

            val result = projectService.forkProject(9003L, 5L, "", "")

            result.owner shouldBe "forker-login"
            result.name shouldBe "orig-noexist"
            result.originalProject shouldBe original
            // 원본 소스 저장소가 실재하지 않으므로 하드링크 복제는 수행되지 않아야 한다.
            File(gitBase, "forker-login/orig-noexist.git").exists() shouldBe false
        }

        it("MANAGER 역할을 찾지 못하면 예외가 발생해야 한다") {
            val original = Project(id = 9004L, name = "orig2", owner = "owner2", vcs = "SVN")
            val forker = User(id = 6L, loginId = "forker2")
            every { projectRepository.findById(9004L) } returns Optional.of(original)
            every { userRepository.findById(6L) } returns Optional.of(forker)
            every { projectRepository.findByOwnerAndName("dest-owner2", "dest-name2") } returns Optional.empty()
            every { projectRepository.save(any()) } answers { firstArg() }
            every { roleRepository.findById(RoleType.MANAGER.roleType) } returns Optional.empty()

            shouldThrow<IllegalStateException> {
                projectService.forkProject(9004L, 6L, "dest-owner2", "dest-name2")
            }
        }

        it("원본 저장소가 실재하면 하드링크 방식으로 무복사 복제해야 한다 (GIT, 중첩 디렉터리 포함)") {
            val owner = "fork-src-owner"
            val name = "fork-src-repo"
            val destOwner = "fork-dst-owner"
            val destName = "fork-dst-repo"
            val sourceDir = File(gitBase, "$owner/$name.git")
            val targetDir = File(gitBase, "$destOwner/$destName.git")
            sourceDir.deleteRecursively()
            targetDir.deleteRecursively()
            try {
                // 중첩 디렉터리 + 파일 구조로 cloneHardLinkedRepository의 isDirectory 참/거짓 분기를 모두 태운다.
                File(sourceDir, "objects/pack").mkdirs()
                File(sourceDir, "HEAD").writeText("ref: refs/heads/main")
                File(sourceDir, "objects/info.txt").writeText("info")

                val original = Project(id = 9005L, name = name, owner = owner, vcs = "GIT")
                val forker = User(id = 7L, loginId = "forker3")
                val managerRole = Role(id = RoleType.MANAGER.roleType)
                every { projectRepository.findById(9005L) } returns Optional.of(original)
                every { userRepository.findById(7L) } returns Optional.of(forker)
                every { projectRepository.findByOwnerAndName(destOwner, destName) } returns Optional.empty()
                every { projectRepository.save(any()) } answers { firstArg() }
                every { roleRepository.findById(RoleType.MANAGER.roleType) } returns Optional.of(managerRole)
                every { projectUserRepository.save(any()) } returns mockk()

                projectService.forkProject(9005L, 7L, destOwner, destName)

                targetDir.exists() shouldBe true
                File(targetDir, "HEAD").exists() shouldBe true
                File(targetDir, "objects/info.txt").exists() shouldBe true
                File(targetDir, "objects/pack").isDirectory shouldBe true
            } finally {
                sourceDir.deleteRecursively()
                targetDir.deleteRecursively()
            }
        }

        it("대상 디렉터리가 이미 존재하면 다시 만들지 않고 그 안에 하드링크를 생성해야 한다 (SVN 경로)") {
            val owner = "fork-src-owner2"
            val name = "fork-src-repo2"
            val destOwner = "fork-dst-owner2"
            val destName = "fork-dst-repo2"
            // 코디네이터 push 전 리뷰(2026-09-08, P3-12 2라운드) — SvnRepository.getDirectory()는
            // ".git" 접미사 없는 경로를 쓰므로(git 전용 접미사 버그 수정 이후) 픽스처도 일치시킨다.
            val sourceDir = File(svnBase, "$owner/$name")
            val targetDir = File(svnBase, "$destOwner/$destName")
            sourceDir.deleteRecursively()
            targetDir.deleteRecursively()
            try {
                sourceDir.mkdirs()
                File(sourceDir, "config").writeText("config-content")
                targetDir.mkdirs() // target.exists()==true 분기를 태우기 위해 미리 생성

                val original = Project(id = 9006L, name = name, owner = owner, vcs = "SUBVERSION")
                val forker = User(id = 8L, loginId = "forker4")
                val managerRole = Role(id = RoleType.MANAGER.roleType)
                every { projectRepository.findById(9006L) } returns Optional.of(original)
                every { userRepository.findById(8L) } returns Optional.of(forker)
                every { projectRepository.findByOwnerAndName(destOwner, destName) } returns Optional.empty()
                every { projectRepository.save(any()) } answers { firstArg() }
                every { roleRepository.findById(RoleType.MANAGER.roleType) } returns Optional.of(managerRole)
                every { projectUserRepository.save(any()) } returns mockk()

                projectService.forkProject(9006L, 8L, destOwner, destName)

                File(targetDir, "config").exists() shouldBe true
            } finally {
                sourceDir.deleteRecursively()
                targetDir.deleteRecursively()
            }
        }

        // yona-wiki P3-12 2라운드 회귀 테스트 — 위 SVN 테스트와 동일한 결함이 Mercurial 포크에도
        // 적용됐었다. HgRepository.getDirectory()도 접미사 없는 경로를 쓴다.
        it("Mercurial(MERCURIAL) 저장소 폴더가 실재하면 포크 시 hg 기본 경로에서 하드링크 복제돼야 한다") {
            val owner = "fork-src-owner-hg"
            val name = "fork-src-repo-hg"
            val destOwner = "fork-dst-owner-hg"
            val destName = "fork-dst-repo-hg"
            val sourceDir = File(hgBase, "$owner/$name")
            val targetDir = File(hgBase, "$destOwner/$destName")
            sourceDir.deleteRecursively()
            targetDir.deleteRecursively()
            try {
                sourceDir.mkdirs()
                File(sourceDir, ".hg").mkdirs()
                File(sourceDir, ".hg/requires").writeText("dotencode\n")

                val original = Project(id = 9020L, name = name, owner = owner, vcs = "MERCURIAL")
                val forker = User(id = 20L, loginId = "forker-hg")
                val managerRole = Role(id = RoleType.MANAGER.roleType)
                every { projectRepository.findById(9020L) } returns Optional.of(original)
                every { userRepository.findById(20L) } returns Optional.of(forker)
                every { projectRepository.findByOwnerAndName(destOwner, destName) } returns Optional.empty()
                every { projectRepository.save(any()) } answers { firstArg() }
                every { roleRepository.findById(RoleType.MANAGER.roleType) } returns Optional.of(managerRole)
                every { projectUserRepository.save(any()) } returns mockk()

                projectService.forkProject(9020L, 20L, destOwner, destName)

                File(targetDir, ".hg/requires").exists() shouldBe true
            } finally {
                sourceDir.deleteRecursively()
                targetDir.deleteRecursively()
            }
        }

        it("원본 저장소 경로가 디렉터리가 아니라 일반 파일이면 listFiles()가 null이라 아무 것도 복제하지 않아야 한다") {
            val owner = "fork-src-owner3"
            val name = "fork-src-file3"
            val destOwner = "fork-dst-owner3"
            val destName = "fork-dst-repo3"
            val sourceDir = File(gitBase, "$owner/$name.git") // 일부러 디렉터리가 아닌 일반 파일로 생성
            val targetDir = File(gitBase, "$destOwner/$destName.git")
            sourceDir.deleteRecursively()
            targetDir.deleteRecursively()
            try {
                sourceDir.parentFile.mkdirs()
                sourceDir.writeText("this-is-a-plain-file-not-a-directory")

                val original = Project(id = 9007L, name = name, owner = owner, vcs = "GIT")
                val forker = User(id = 9L, loginId = "forker5")
                val managerRole = Role(id = RoleType.MANAGER.roleType)
                every { projectRepository.findById(9007L) } returns Optional.of(original)
                every { userRepository.findById(9L) } returns Optional.of(forker)
                every { projectRepository.findByOwnerAndName(destOwner, destName) } returns Optional.empty()
                every { projectRepository.save(any()) } answers { firstArg() }
                every { roleRepository.findById(RoleType.MANAGER.roleType) } returns Optional.of(managerRole)
                every { projectUserRepository.save(any()) } returns mockk()

                projectService.forkProject(9007L, 9L, destOwner, destName)

                targetDir.exists() shouldBe true // mkdirs는 호출됨
                (targetDir.listFiles()?.size ?: -1) shouldBe 0 // listFiles()==null이라 복제된 파일이 없어야 함
            } finally {
                sourceDir.deleteRecursively()
                targetDir.deleteRecursively()
            }
        }

        // baseDir 판정용 vcs?.uppercase() 체인 — vcs가 null(엘비스 분기)이면 기본 GIT 경로로 처리돼야 한다.
        it("원본 프로젝트의 vcs가 null이어도 기본 GIT 경로 기준으로 정상 처리돼야 한다") {
            val original = Project(id = 9008L, name = "vcs-null-fork-src", owner = "vcs-null-owner", vcs = null)
            val forker = User(id = 10L, loginId = "forker6")
            val managerRole = Role(id = RoleType.MANAGER.roleType)
            every { projectRepository.findById(9008L) } returns Optional.of(original)
            every { userRepository.findById(10L) } returns Optional.of(forker)
            every { projectRepository.findByOwnerAndName("vcs-null-fork-dest", "vcs-null-fork-dest-repo") } returns Optional.empty()
            every { projectRepository.save(any()) } answers { firstArg() }
            every { roleRepository.findById(RoleType.MANAGER.roleType) } returns Optional.of(managerRole)
            every { projectUserRepository.save(any()) } returns mockk()

            val result = projectService.forkProject(9008L, 10L, "vcs-null-fork-dest", "vcs-null-fork-dest-repo")

            result.owner shouldBe "vcs-null-fork-dest"
        }

        // vcs가 축약형 "SVN"인 경우도 svn 기본 경로로 판정돼야 한다(OR의 두 번째 항이 true).
        it("원본 프로젝트의 vcs가 SVN(축약형)이면 svn 기본 경로 기준으로 판단돼야 한다") {
            val original = Project(id = 9009L, name = "vcs-svn-abbrev-fork-src", owner = "vcs-svn-abbrev-owner", vcs = "SVN")
            val forker = User(id = 11L, loginId = "forker7")
            val managerRole = Role(id = RoleType.MANAGER.roleType)
            every { projectRepository.findById(9009L) } returns Optional.of(original)
            every { userRepository.findById(11L) } returns Optional.of(forker)
            every { projectRepository.findByOwnerAndName("vcs-svn-abbrev-fork-dest", "vcs-svn-abbrev-fork-dest-repo") } returns Optional.empty()
            every { projectRepository.save(any()) } answers { firstArg() }
            every { roleRepository.findById(RoleType.MANAGER.roleType) } returns Optional.of(managerRole)
            every { projectUserRepository.save(any()) } returns mockk()

            val result = projectService.forkProject(9009L, 11L, "vcs-svn-abbrev-fork-dest", "vcs-svn-abbrev-fork-dest-repo")

            result.owner shouldBe "vcs-svn-abbrev-fork-dest"
        }
    }

    // 크로스플랫폼/운영 경로 하드코딩 버그 수정 — acceptTransfer/forkProject 두 곳에만
    // "/tmp/yona/git", "/tmp/yona/svn"이 리터럴로 박혀 있어, yona.git.base-dir/yona.svn.base-dir을
    // 다른 값으로 설정해도 이 두 기능만 계속 /tmp/yona를 참조하던 문제를 검증한다.
    describe("ProjectServiceImpl 설정 가능한 base-dir 지원 (하드코딩 버그 수정)") {
        val customGitBase = Files.createTempDirectory("yona-custom-git").toFile()
        val customSvnBase = Files.createTempDirectory("yona-custom-svn").toFile()
        val customBaseDirProjectService = ProjectServiceImpl(
            projectRepository, projectUserRepository, repositoryService, userRepository,
            projectTransferRepository, roleRepository, organizationRepository, organizationUserRepository,
            labelRepository, issueRepository, issueService, issueLabelCategoryRepository, issueLabelService,
            assigneeRepository, webhookRepository, webhookThreadRepository, postingRepository, postingService,
            commentThreadRepository, pullRequestRepository, pullRequestEventRepository, pullRequestCommitRepository,
            favoriteProjectRepository, watchService,
            customGitBase.absolutePath, customSvnBase.absolutePath, "/tmp/yona/hg"
        )

        it("포크 시 하드코딩된 /tmp/yona/git이 아니라 주입된 gitBaseDir 설정을 따라야 한다") {
            val owner = "custom-base-fork-owner"
            val name = "custom-base-fork-repo"
            val destOwner = "custom-base-fork-dest"
            val destName = "custom-base-fork-dest-repo"
            val sourceDir = File(customGitBase, "$owner/$name.git")
            val targetDir = File(customGitBase, "$destOwner/$destName.git")
            sourceDir.mkdirs()
            File(sourceDir, "HEAD").writeText("ref: refs/heads/main")

            val original = Project(id = 9100L, name = name, owner = owner, vcs = "GIT")
            val forker = User(id = 100L, loginId = "custom-forker")
            val managerRole = Role(id = RoleType.MANAGER.roleType)
            every { projectRepository.findById(9100L) } returns Optional.of(original)
            every { userRepository.findById(100L) } returns Optional.of(forker)
            every { projectRepository.findByOwnerAndName(destOwner, destName) } returns Optional.empty()
            every { projectRepository.save(any()) } answers { firstArg() }
            every { roleRepository.findById(RoleType.MANAGER.roleType) } returns Optional.of(managerRole)
            every { projectUserRepository.save(any()) } returns mockk()

            customBaseDirProjectService.forkProject(9100L, 100L, destOwner, destName)

            targetDir.exists() shouldBe true
            File(targetDir, "HEAD").exists() shouldBe true
            // 예전 하드코딩 경로(/tmp/yona/git)에는 생성되지 않아야 한다.
            File("/tmp/yona/git/$destOwner/$destName.git").exists() shouldBe false
        }

        it("프로젝트 이관 시 하드코딩된 /tmp/yona/svn이 아니라 주입된 svnBaseDir 설정을 따라야 한다") {
            val owner = "custom-base-transfer-owner"
            val name = "custom-base-transfer-repo"
            val destOwner = "custom-base-transfer-dest"
            val sender2 = User(id = 101L, loginId = owner, name = "커스텀이관")
            val proj = Project(id = 9101L, name = name, owner = owner, vcs = "SUBVERSION")
            val pt = ProjectTransfer(
                id = 9101L, project = proj, sender = sender2, destination = destOwner,
                confirmKey = "key", newProjectName = name, requested = Instant.now()
            )
            val destUser = User(id = 102L, loginId = destOwner, name = "커스텀이관수신")
            val managerRole = Role(id = RoleType.MANAGER.roleType)

            // 코디네이터 push 전 리뷰(2026-09-08, P3-12 2라운드) — SvnRepository.getDirectory()는
            // ".git" 접미사 없는 경로를 쓰므로(git 전용 접미사 버그 수정 이후) 픽스처도 일치시킨다.
            val sourceDir = File(customSvnBase, "$owner/$name")
            val targetDir = File(customSvnBase, "$destOwner/$name")
            sourceDir.mkdirs()

            every { projectTransferRepository.findByIdAndAcceptedAndRequestedAfter(9101L, false, any()) } returns Optional.of(pt)
            every { userRepository.findById(102L) } returns Optional.of(destUser)
            every { organizationRepository.findByName(destOwner) } returns Optional.empty()
            every { projectRepository.save(any()) } returns proj
            every { projectUserRepository.findByProjectIdAndUserId(9101L, 101L) } returns Optional.empty()
            every { userRepository.findByLoginId(destOwner) } returns Optional.of(destUser)
            every { projectUserRepository.findByProjectIdAndUserId(9101L, 102L) } returns Optional.empty()
            every { roleRepository.findById(RoleType.MANAGER.roleType) } returns Optional.of(managerRole)
            every { projectUserRepository.save(any()) } returns mockk()
            every { projectTransferRepository.delete(any()) } returns Unit

            customBaseDirProjectService.acceptTransfer(9101L, "key", 102L)

            sourceDir.exists() shouldBe false
            targetDir.exists() shouldBe true
            File("/tmp/yona/svn/$destOwner/$name").exists() shouldBe false
        }
    }

    // yona Project.java의 VCS 전환(Git<->SVN) 기능 대응 — 기존 저장소 삭제 후 다음 VCS로 재생성,
    // fork 자식과의 연결은 모두 끊는다. yona-wiki P3-12(Mercurial 지원) 1라운드로 2지선다 토글이
    // GIT->SUBVERSION->MERCURIAL->GIT 3종 순환(nextVcsInCycle)으로 확장됨.
    describe("ProjectServiceImpl.changeVCS") {
        it("프로젝트를 찾을 수 없으면 예외가 발생해야 한다") {
            every { projectRepository.findById(9500L) } returns Optional.empty()

            shouldThrow<IllegalArgumentException> {
                projectService.changeVCS(9500L)
            }
        }

        it("GIT 프로젝트는 SUBVERSION으로 전환되고, fork 자식들의 원본 연결이 모두 끊어져야 한다") {
            val fork1 = Project(id = 9561L, name = "fork1", owner = "forker1")
            val fork2 = Project(id = 9562L, name = "fork2", owner = "forker2")
            val project = Project(
                id = 9560L, name = "vcs-switch", owner = "owner", vcs = "GIT",
                forkingProjects = mutableListOf(fork1, fork2)
            )
            val vcsPlayRepository = mockk<PlayRepository>()
            every { projectRepository.findById(9560L) } returns Optional.of(project)
            every { projectRepository.save(fork1) } returns fork1
            every { projectRepository.save(fork2) } returns fork2
            every { projectRepository.save(project) } returns project
            every { repositoryService.getRepository(project) } returns vcsPlayRepository
            every { vcsPlayRepository.delete() } returns Unit
            every { vcsPlayRepository.create() } returns Unit

            val result = projectService.changeVCS(9560L)

            result.vcs shouldBe "SUBVERSION"
            fork1.originalProject shouldBe null
            fork2.originalProject shouldBe null
            project.forkingProjects.size shouldBe 0
            verify(exactly = 1) { vcsPlayRepository.delete() }
            verify(exactly = 1) { vcsPlayRepository.create() }
            verify(exactly = 1) { projectRepository.save(fork1) }
            verify(exactly = 1) { projectRepository.save(fork2) }
        }

        it("vcs가 null이면 기본값 GIT으로 취급해 SUBVERSION으로 전환돼야 한다") {
            val project = Project(id = 9563L, name = "vcs-null", owner = "owner", vcs = null)
            val vcsPlayRepository = mockk<PlayRepository>()
            every { projectRepository.findById(9563L) } returns Optional.of(project)
            every { repositoryService.getRepository(project) } returns vcsPlayRepository
            every { vcsPlayRepository.delete() } returns Unit
            every { vcsPlayRepository.create() } returns Unit
            every { projectRepository.save(project) } returns project

            val result = projectService.changeVCS(9563L)

            result.vcs shouldBe "SUBVERSION"
        }

        it("SUBVERSION 프로젝트는 MERCURIAL로 전환돼야 한다") {
            val project = Project(id = 9564L, name = "vcs-svn", owner = "owner", vcs = "SUBVERSION")
            val vcsPlayRepository = mockk<PlayRepository>()
            every { projectRepository.findById(9564L) } returns Optional.of(project)
            every { repositoryService.getRepository(project) } returns vcsPlayRepository
            every { vcsPlayRepository.delete() } returns Unit
            every { vcsPlayRepository.create() } returns Unit
            every { projectRepository.save(project) } returns project

            val result = projectService.changeVCS(9564L)

            result.vcs shouldBe "MERCURIAL"
        }

        it("MERCURIAL 프로젝트는 다시 GIT으로 전환돼야 한다(순환)") {
            val project = Project(id = 9566L, name = "vcs-hg", owner = "owner", vcs = "MERCURIAL")
            val vcsPlayRepository = mockk<PlayRepository>()
            every { projectRepository.findById(9566L) } returns Optional.of(project)
            every { repositoryService.getRepository(project) } returns vcsPlayRepository
            every { vcsPlayRepository.delete() } returns Unit
            every { vcsPlayRepository.create() } returns Unit
            every { projectRepository.save(project) } returns project

            val result = projectService.changeVCS(9566L)

            result.vcs shouldBe "GIT"
        }

        it("기존 저장소 삭제 중 예외가 발생해도 무시하고 새 VCS로 저장소를 생성해야 한다") {
            val project = Project(id = 9565L, name = "vcs-delete-fail", owner = "owner", vcs = "GIT")
            val vcsPlayRepository = mockk<PlayRepository>()
            every { projectRepository.findById(9565L) } returns Optional.of(project)
            every { repositoryService.getRepository(project) } returns vcsPlayRepository
            every { vcsPlayRepository.delete() } throws RuntimeException("delete failed")
            every { vcsPlayRepository.create() } returns Unit
            every { projectRepository.save(project) } returns project

            val result = projectService.changeVCS(9565L)

            result.vcs shouldBe "SUBVERSION"
            verify(exactly = 1) { vcsPlayRepository.create() }
        }
    }
})
