package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.pullrequest.CodeCommentThread
import com.github.yonaprojects.yona.domain.pullrequest.CodeReviewService
import com.github.yonaprojects.yona.domain.pullrequest.CommentThreadRepository
import com.github.yonaprojects.yona.domain.pullrequest.NonRangedCodeCommentThread
import com.github.yonaprojects.yona.domain.pullrequest.PullRequest
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestEventRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestMergeResult
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestService
import com.github.yonaprojects.yona.domain.vcs.PlayRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestCommitRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.vcs.PushedBranchRepository
import com.github.yonaprojects.yona.domain.watch.WatchService
import com.github.yonaprojects.yona.domain.attachment.AttachmentRepository
import org.springframework.context.MessageSource
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.data.web.PageableHandlerMethodArgumentResolver
import java.util.Optional
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import io.mockk.clearMocks
import io.mockk.slot
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationUser
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestEvent
import com.github.yonaprojects.yona.domain.enumeration.EventType
import java.time.Instant
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestTimelineItem
import com.github.yonaprojects.yona.domain.pullrequest.CommentThread
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestCommit
import com.github.yonaprojects.yona.domain.pullrequest.MergePreviewResult
import com.github.yonaprojects.yona.domain.vcs.PushedBranch
import com.github.yonaprojects.yona.domain.attachment.Attachment
import org.springframework.data.jpa.domain.Specification
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import io.kotest.matchers.string.shouldContain
import io.mockk.verify

class PullRequestViewControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val pullRequestService = mockk<PullRequestService>()
    val pullRequestRepository = mockk<PullRequestRepository>()
    val repositoryService = mockk<RepositoryService>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val commentThreadRepository = mockk<CommentThreadRepository>()
    val pullRequestEventRepository = mockk<PullRequestEventRepository>()
    val pullRequestCommitRepository = mockk<PullRequestCommitRepository>()
    val issueRepository = mockk<IssueRepository>()
    val codeReviewService = mockk<CodeReviewService>()
    every { codeReviewService.isThreadOutdated(any()) } returns false
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    every { organizationUserRepository.findByOrganizationIdAndUserId(any(), any()) } returns Optional.empty()
    val userRepositoryForAccessControl = mockk<UserRepository>()
    val organizationRepositoryForAccessControl = mockk<OrganizationRepository>()
    val issueRepositoryForAccessControl = mockk<IssueRepository>()
    val postingRepositoryForAccessControl = mockk<PostingRepository>()
    val reviewCommentRepositoryForAccessControl = mockk<ReviewCommentRepository>()
    val commitCommentRepositoryForAccessControl = mockk<CommitCommentRepository>()
    val milestoneRepositoryForAccessControl = mockk<MilestoneRepository>()
    val accessControl = AccessControl(
        projectUserRepository, organizationUserRepository,
        userRepositoryForAccessControl, organizationRepositoryForAccessControl,
        issueRepositoryForAccessControl, postingRepositoryForAccessControl,
        reviewCommentRepositoryForAccessControl, commitCommentRepositoryForAccessControl,
        milestoneRepositoryForAccessControl
    )

    val pushedBranchRepository = mockk<PushedBranchRepository>()
    val watchService = mockk<WatchService>()
    val messageSource = mockk<MessageSource>()
    val attachmentRepository = mockk<AttachmentRepository>()
    every { pushedBranchRepository.findByProjectAndPushedDateAfter(any(), any()) } returns emptyList()
    every { pushedBranchRepository.findByOriginalProjectAndOwnerAndPushedDateAfter(any(), any(), any()) } returns emptyList()
    every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()
    every { watchService.isWatching(any(), any(), any()) } returns false
    every { messageSource.getMessage(any(), any(), any()) } returns ""

    val pullRequestViewController = PullRequestViewController(
        projectRepository,
        pullRequestService,
        pullRequestRepository,
        repositoryService,
        projectUserRepository,
        userRepository,
        commentThreadRepository,
        pullRequestEventRepository,
        pullRequestCommitRepository,
        issueRepository,
        accessControl,
        codeReviewService,
        pushedBranchRepository,
        watchService,
        messageSource,
        attachmentRepository
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(pullRequestViewController)
        .setCustomArgumentResolvers(PageableHandlerMethodArgumentResolver())
        .build()

    beforeTest {
        clearMocks(
            projectRepository,
            pullRequestService,
            pullRequestRepository,
            repositoryService,
            projectUserRepository,
            userRepository,
            commentThreadRepository,
            pullRequestEventRepository,
            pullRequestCommitRepository,
            issueRepository
        )
        every { pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(any()) } returns emptyList()
        every {
            pullRequestRepository.findAll(any<Specification<PullRequest>>(), any<Pageable>())
        } returns PageImpl(emptyList())
        every {
            pullRequestRepository.count(any<Specification<PullRequest>>())
        } returns 0L
        every { pullRequestRepository.findDistinctContributorsByToProject(any()) } returns emptyList()
    }

    describe("PullRequestViewController 템플릿 연동 테스트") {
        val project = Project(id = 1L, name = "TestProj", owner = "owner", projectScope = ProjectScope.PRIVATE)
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val pullRequest = PullRequest(
            id = 50L,
            title = "PR 테스트 제목",
            body = "PR 본문",
            toProject = project,
            fromProject = project,
            toBranch = "master",
            fromBranch = "feature",
            contributor = user,
            state = State.OPEN,
            number = 1L
        )

        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")
        val pageRequest = PageRequest.of(0, 20)

        describe("GET /{owner}/{projectName}/pulls") {
            it("비공개 프로젝트일 때 멤버라면 200 OK와 pullrequest/list 뷰를 반환해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 900L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every {
                    pullRequestRepository.findAll(any<Specification<PullRequest>>(), any<Pageable>())
                } returns PageImpl(listOf(pullRequest), pageRequest, 1)

                mockMvc.perform(get("/owner/TestProj/pulls").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("pullrequest/list"))
                    .andExpect(model().attributeExists("project", "prPage", "state"))
            }

            it("프로젝트 멤버가 아닐 경우 403 Forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns false

                mockMvc.perform(get("/owner/TestProj/pulls").principal(userAuth))
                    .andExpect(view().name("error/forbidden"))
            }

            // yona models/PullRequest.java:66 ITEMS_PER_PAGE 대응 (P1-105) — PR 목록은 항상 고정 15.
            it("페이지 크기는 항상 15로 고정되어야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 900L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                val pageableSlot = slot<Pageable>()
                every {
                    pullRequestRepository.findAll(any<Specification<PullRequest>>(), capture(pageableSlot))
                } returns PageImpl(listOf(pullRequest), pageRequest, 1)

                mockMvc.perform(get("/owner/TestProj/pulls").principal(userAuth))
                    .andExpect(status().isOk)

                pageableSlot.captured.pageSize shouldBe 15
            }

            // yona AccessControl.isAllowedIfGroupMember() 대응 (P1-57) — checkMemberAccess() 공용 헬퍼
            it("직접 멤버가 아니어도 프로젝트가 속한 조직의 멤버라면 200 OK를 반환해야 한다") {
                val groupOrg = Organization(id = 1L, name = "org")
                groupOrg.organizationUsers.add(
                    OrganizationUser(
                        id = 1L, user = user, organization = groupOrg,
                        role = Role(id = RoleType.ORG_MEMBER.roleType)
                    )
                )
                val groupProject = Project(id = 12L, name = "group-project", owner = "owner", projectScope = ProjectScope.PROTECTED, organization = groupOrg)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "group-project") } returns Optional.of(groupProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(12L, 10L) } returns false
                every {
                    pullRequestRepository.findAll(any<Specification<PullRequest>>(), any<Pageable>())
                } returns PageImpl(emptyList(), pageRequest, 0)

                mockMvc.perform(get("/owner/group-project/pulls").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("pullrequest/list"))
            }
        }

        describe("GET /{owner}/{projectName}/closedPullRequests") {
            it("멤버라면 CLOSED와 MERGED 상태를 모두 포함한 목록을 pullrequest/list 뷰로 반환해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 901L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every {
                    pullRequestRepository.findAll(any<Specification<PullRequest>>(), any<Pageable>())
                } returns PageImpl(listOf(pullRequest), pageRequest, 1)

                mockMvc.perform(get("/owner/TestProj/closedPullRequests").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("pullrequest/list"))
                    .andExpect(model().attributeExists("project", "prPage"))
                    .andExpect(model().attribute("state", "closed"))
            }

            it("프로젝트 멤버가 아닐 경우 403 Forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns false

                mockMvc.perform(get("/owner/TestProj/closedPullRequests").principal(userAuth))
                    .andExpect(view().name("error/forbidden"))
            }
        }

        describe("GET /{owner}/{projectName}/sentPullRequests") {
            it("멤버라면 이 프로젝트가 출발지(fromProject)인 PR 목록을 pullrequest/list 뷰로 반환해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 902L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every {
                    pullRequestRepository.findAll(any<Specification<PullRequest>>(), any<Pageable>())
                } returns PageImpl(listOf(pullRequest), pageRequest, 1)

                mockMvc.perform(get("/owner/TestProj/sentPullRequests").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("pullrequest/list"))
                    .andExpect(model().attributeExists("project", "prPage"))
                    .andExpect(model().attribute("state", "sent"))
            }
        }

        describe("GET /{owner}/{projectName}/pull/{number}") {
            it("멤버라면 200 OK와 pullrequest/view 뷰를 반환해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 903L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest

                val mergeResult = PullRequestMergeResult(pullRequest = pullRequest)
                every { pullRequestService.attemptMerge(50L) } returns mergeResult
                every { commentThreadRepository.findByPullRequest(pullRequest) } returns emptyList()
                every { pullRequestCommitRepository.findByPullRequest(pullRequest) } returns emptyList()

                mockMvc.perform(get("/owner/TestProj/pull/1").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("pullrequest/view"))
                    .andExpect(model().attributeExists("project", "pr", "mergeResult"))
            }

            // yona git/view.scala.html conversation 탭 + partial_pull_request_event.scala.html 대응
            // (P2-39, P1-106 범위 정정) — 대화 탭은 PullRequestEvent만 시간순으로 보여주고 댓글
            // 스레드는 렌더링하지 않는다(legacy 원본 범위, 사용자 확인 완료로 되돌림). 렌더링 대상이
            // 아닌 이벤트 타입(NEW_PULL_REQUEST)은 제외된다.
            it("timeline 모델 속성에 이벤트만 시간순으로 담기고 댓글스레드/NEW_PULL_REQUEST는 제외되어야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 905L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { pullRequestService.attemptMerge(50L) } returns PullRequestMergeResult(pullRequest = pullRequest)
                every { pullRequestCommitRepository.findByPullRequest(pullRequest) } returns emptyList()

                val stateEvent = PullRequestEvent(
                    id = 1L, pullRequest = pullRequest, senderLoginId = "testuser",
                    eventType = EventType.PULL_REQUEST_STATE_CHANGED,
                    oldValue = "OPEN", newValue = "CLOSED",
                    created = Instant.parse("2026-01-02T00:00:00Z")
                )
                val newPrEvent = PullRequestEvent(
                    id = 2L, pullRequest = pullRequest, senderLoginId = "testuser",
                    eventType = EventType.NEW_PULL_REQUEST,
                    oldValue = null, newValue = "PR 본문",
                    created = Instant.parse("2026-01-03T00:00:00Z")
                )
                every { pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(pullRequest) } returns listOf(stateEvent, newPrEvent)
                every { commentThreadRepository.findByPullRequest(pullRequest) } returns emptyList()

                val result = mockMvc.perform(get("/owner/TestProj/pull/1").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                val timeline = result.modelAndView!!.model["timeline"] as List<*>
                timeline.size shouldBe 1
                val eventIds = timeline.map { (it as PullRequestTimelineItem).event.id }
                eventIds shouldBe listOf(1L)
                // openThreadCount(addCommonPrAttributes)를 위해 commentThreadRepository는 호출되지만,
                // 댓글스레드 자체가 timeline 모델 속성에 별도 항목으로 섞여 들어가지는 않아야 한다.
                result.modelAndView!!.model.containsKey("commentThreads") shouldBe false
            }
        }

        describe("GET /{owner}/{projectName}/pull/{number}/changes") {
            it("멤버라면 200 OK와 pullrequest/view 뷰를 반환하고 diffs 모델을 주입해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 904L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest

                val mergeResult = PullRequestMergeResult(pullRequest = pullRequest)
                every { pullRequestService.attemptMerge(50L) } returns mergeResult
                every { pullRequestService.getDiff(pullRequest) } returns emptyList()
                every { commentThreadRepository.findByPullRequest(pullRequest) } returns emptyList()
                every { pullRequestCommitRepository.findByPullRequest(pullRequest) } returns emptyList()

                mockMvc.perform(get("/owner/TestProj/pull/1/changes").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("pullrequest/view"))
                    .andExpect(model().attributeExists("project", "pr", "diffs", "mergeResult"))
            }

            // yona PullRequest.java:1063-1103 getCodeCommentThreadsForChanges() 대응 (P1-114). [GL-models_PullRequest-100]
            // commitId 없이 조회하는 "전체 변경사항"에서는 커밋 단위 스레드(isCommitComment)와
            // outdated 스레드를 제외하고, NonRangedCodeCommentThread는 필터링 없이 그대로 포함해야 한다.
            it("commentThreads는 커밋단위/outdated 스레드를 제외하고 non-ranged 스레드는 그대로 포함해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 906L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { pullRequestService.attemptMerge(50L) } returns PullRequestMergeResult(pullRequest = pullRequest)
                every { pullRequestService.getDiff(pullRequest) } returns emptyList()

                val outdatedThread = CodeCommentThread(
                    id = 801L, pullRequest = pullRequest, prevCommitId = "prev1", commitId = "commit1"
                )
                val validThread = CodeCommentThread(
                    id = 802L, pullRequest = pullRequest, prevCommitId = "prev2", commitId = "commit2"
                )
                val commitCommentThread = CodeCommentThread(
                    id = 803L, pullRequest = pullRequest, prevCommitId = "", commitId = "commit3"
                )
                val nonRangedThread = NonRangedCodeCommentThread(
                    id = 804L, pullRequest = pullRequest, prevCommitId = "prev4", commitId = "commit4"
                )
                every { commentThreadRepository.findByPullRequest(pullRequest) } returns
                    listOf(outdatedThread, validThread, commitCommentThread, nonRangedThread)
                every { pullRequestCommitRepository.findByPullRequest(pullRequest) } returns emptyList()
                every { codeReviewService.isThreadOutdated(801L) } returns true
                every { codeReviewService.isThreadOutdated(802L) } returns false

                val result = mockMvc.perform(get("/owner/TestProj/pull/1/changes").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                val commentThreads = result.modelAndView!!.model["commentThreads"] as List<*>
                val ids = commentThreads.map { (it as CommentThread).id }
                ids shouldBe listOf(802L, 804L)
            }
        }

        describe("GET /{owner}/{projectName}/pullRequest/{number}/changes/{commitId}") {
            it("멤버라면 200 OK와 특정 커밋 변경 사항을 반환해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 905L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest

                val mergeResult = PullRequestMergeResult(pullRequest = pullRequest)
                every { pullRequestService.attemptMerge(50L) } returns mergeResult
                every { pullRequestService.getDiff(pullRequest, "abcdefg") } returns emptyList()
                every { commentThreadRepository.findByPullRequest(pullRequest) } returns emptyList()
                every { pullRequestCommitRepository.findByPullRequest(pullRequest) } returns emptyList()

                mockMvc.perform(get("/owner/TestProj/pullRequest/1/changes/abcdefg").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("pullrequest/view"))
                    .andExpect(model().attributeExists("project", "pr", "diffs", "commitId"))
            }

            // yona PullRequest.java:1074-1077 대응 (P1-114). commitId를 지정해 조회할 때는 outdated
            // 여부와 무관하게 그 커밋에 달린 스레드만 노출해야 한다(다른 커밋 스레드는 제외).
            it("commitId 지정 조회 시 해당 커밋의 스레드만 commentThreads에 포함해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 907L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { pullRequestService.attemptMerge(50L) } returns PullRequestMergeResult(pullRequest = pullRequest)
                every { pullRequestService.getDiff(pullRequest, "abcdefg") } returns emptyList()

                val matchingThread = CodeCommentThread(
                    id = 811L, pullRequest = pullRequest, prevCommitId = "", commitId = "abcdefg"
                )
                val otherThread = CodeCommentThread(
                    id = 812L, pullRequest = pullRequest, prevCommitId = "", commitId = "zzzzzzz"
                )
                val matchingNonRanged = NonRangedCodeCommentThread(
                    id = 813L, pullRequest = pullRequest, prevCommitId = "", commitId = "abcdefg"
                )
                every { commentThreadRepository.findByPullRequest(pullRequest) } returns
                    listOf(matchingThread, otherThread, matchingNonRanged)
                every { pullRequestCommitRepository.findByPullRequest(pullRequest) } returns emptyList()

                val result = mockMvc.perform(get("/owner/TestProj/pullRequest/1/changes/abcdefg").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                val commentThreads = result.modelAndView!!.model["commentThreads"] as List<*>
                val ids = commentThreads.map { (it as CommentThread).id }
                ids shouldBe listOf(811L, 813L)
            }
        }

        describe("GET /{owner}/{projectName}/pull/new") {
            it("멤버라면 200 OK와 pullrequest/create 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                
                val playRepo = mockk<PlayRepository>()
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.getRefNames() } returns listOf("refs/heads/master", "refs/heads/feature")
                every { playRepo.getDefaultBranch() } returns "refs/heads/master"

                mockMvc.perform(get("/owner/TestProj/pull/new").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("pullrequest/create"))
                    .andExpect(model().attributeExists("project", "branches", "defaultBranch"))
            }
        }

        describe("GET /{owner}/{projectName}/pull/{number}/edit") {
            it("PR 작성자(contributor)라면 200 OK와 pullrequest/edit 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.empty()

                mockMvc.perform(get("/owner/TestProj/pull/1/edit").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("pullrequest/edit"))
                    .andExpect(model().attributeExists("project", "pr", "currentUser"))
            }

            it("프로젝트 매니저라면 작성자가 아니어도 200 OK와 pullrequest/edit 뷰를 반환해야 한다") {
                val managerUser = User(id = 20L, loginId = "manager", name = "매니저")
                val managerAuth = UsernamePasswordAuthenticationToken("manager", "password")
                val managerRole = Role(id = RoleType.MANAGER.roleType)
                val projectManagerUser = ProjectUser(id = 101L, user = managerUser, project = project, role = managerRole)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("manager") } returns Optional.of(managerUser)
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { projectUserRepository.findByProjectIdAndUserId(1L, 20L) } returns Optional.of(projectManagerUser)

                mockMvc.perform(get("/owner/TestProj/pull/1/edit").principal(managerAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("pullrequest/edit"))
            }

            it("작성자도 매니저도 아니라면 403 Forbidden 뷰를 반환해야 한다") {
                val otherUser = User(id = 30L, loginId = "other", name = "다른유저")
                val otherAuth = UsernamePasswordAuthenticationToken("other", "password")
                val memberRole = Role(id = RoleType.MEMBER.roleType)
                val projectMemberUser = ProjectUser(id = 102L, user = otherUser, project = project, role = memberRole)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("other") } returns Optional.of(otherUser)
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { projectUserRepository.findByProjectIdAndUserId(1L, 30L) } returns Optional.of(projectMemberUser)

                mockMvc.perform(get("/owner/TestProj/pull/1/edit").principal(otherAuth))
                    .andExpect(view().name("error/forbidden"))
            }

            it("로그인하지 않았다면 403 Forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest

                mockMvc.perform(get("/owner/TestProj/pull/1/edit"))
                    .andExpect(view().name("error/forbidden"))
            }
        }

        describe("GET /{owner}/{projectName}/pulls — 추가 분기 (404/상태 파라미터/renderList)") {
            it("프로젝트를 찾을 수 없으면 404 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuchProj") } returns Optional.empty()

                mockMvc.perform(get("/owner/NoSuchProj/pulls").principal(userAuth))
                    .andExpect(view().name("error/404"))
            }

            it("state=closed 쿼리면 CLOSED 상태만으로 조회해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 950L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true

                mockMvc.perform(get("/owner/TestProj/pulls").param("state", "closed").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("state", "closed"))
            }

            it("state=all 쿼리면 상태 필터 없이 전체를 조회해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 951L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true

                mockMvc.perform(get("/owner/TestProj/pulls").param("state", "all").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("state", "all"))
            }

            // renderList: filter ?: "" — filter 쿼리파라미터가 non-null인 경우(비어있지 않은 값)의 분기.
            it("filter 쿼리파라미터가 있으면 모델의 filter 속성에 그대로 반영되어야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 955L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true

                mockMvc.perform(get("/owner/TestProj/pulls").param("filter", "검색어").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("filter", "검색어"))
            }

            // renderList: project.isForkedFromOrigin=true 분기 — acceptedCount/sentCount 및 defaultBranchFor의
            // "target=originalProject" 분기(포크는 원본 저장소 기준으로 기본 브랜치를 계산)를 함께 검증한다.
            it("포크 프로젝트라면 acceptedCount/sentCount 모델 속성과 원본 저장소 기준 기본 브랜치를 노출해야 한다") {
                val originProject = Project(id = 200L, name = "origin", owner = "origin-owner", projectScope = ProjectScope.PUBLIC)
                val forkProject = Project(id = 201L, name = "fork", owner = "owner", projectScope = ProjectScope.PUBLIC, originalProject = originProject)
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 952L, user = memberUser, project = forkProject, role = Role(id = RoleType.MEMBER.roleType)))

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "fork") } returns Optional.of(forkProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(201L, 10L) } returns true
                val pushed = PushedBranch(id = 1L, name = "refs/heads/feature", project = forkProject)
                every { pushedBranchRepository.findByProjectAndPushedDateAfter(forkProject, any()) } returns listOf(pushed)
                val playRepo = mockk<PlayRepository>()
                every { repositoryService.getRepository(originProject) } returns playRepo
                every { playRepo.getDefaultBranch() } returns "refs/heads/master"

                val result = mockMvc.perform(get("/owner/fork/pulls").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                result.modelAndView!!.model.containsKey("acceptedCount") shouldBe true
                result.modelAndView!!.model.containsKey("sentCount") shouldBe true
                result.modelAndView!!.model["defaultBranch"] shouldBe "master"
                val pushedBranches = result.modelAndView!!.model["pushedBranches"] as List<*>
                pushedBranches.size shouldBe 1
            }

            // renderList: isForkedFromOrigin=false, 익명 사용자 → pushedBranches는 빈 리스트(else 분기).
            it("공개 프로젝트를 익명 사용자가 조회하면 pushedBranches가 빈 리스트여야 한다") {
                val publicProject = Project(id = 202L, name = "pub", owner = "owner", projectScope = ProjectScope.PUBLIC)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "pub") } returns Optional.of(publicProject)
                val playRepo = mockk<PlayRepository>()
                every { repositoryService.getRepository(publicProject) } returns playRepo
                every { playRepo.getDefaultBranch() } returns "refs/heads/master"

                val result = mockMvc.perform(get("/owner/pub/pulls"))
                    .andExpect(status().isOk)
                    .andReturn()

                val pushedBranches = result.modelAndView!!.model["pushedBranches"] as List<*>
                pushedBranches shouldBe emptyList<Any>()
            }

            // renderList: isForkedFromOrigin=false, 로그인 사용자이지만 PR 리소스 생성 권한이 없음(비멤버,
            // 공개 프로젝트에서 PULL_REQUEST는 비회원 생성 허용 리소스 타입이 아님) → else 분기.
            it("공개 프로젝트의 비멤버 로그인 사용자는 PR 생성 권한이 없어 pushedBranches가 빈 리스트여야 한다") {
                val publicProject = Project(id = 203L, name = "pub2", owner = "owner", projectScope = ProjectScope.PUBLIC)
                val outsider = User(id = 40L, loginId = "outsider", name = "외부인")
                val outsiderAuth = UsernamePasswordAuthenticationToken("outsider", "password")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "pub2") } returns Optional.of(publicProject)
                every { userRepository.findByLoginId("outsider") } returns Optional.of(outsider)
                val playRepo = mockk<PlayRepository>()
                every { repositoryService.getRepository(publicProject) } returns playRepo
                every { playRepo.getDefaultBranch() } returns "refs/heads/master"

                val result = mockMvc.perform(get("/owner/pub2/pulls").principal(outsiderAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                val pushedBranches = result.modelAndView!!.model["pushedBranches"] as List<*>
                pushedBranches shouldBe emptyList<Any>()
            }

            // renderList: isForkedFromOrigin=false, 로그인 멤버(PR 생성 권한 있음) → 원본 프로젝트 기준으로
            // 자신이 소유한 fork들의 최근 push 브랜치를 노출(findByOriginalProjectAndOwnerAndPushedDateAfter).
            // 또한 PushedBranch.project=null인 항목을 섞어 defaultBranchFor(null)의 "master" 기본값 분기(project
            // null 조기 반환)도 함께 검증한다.
            it("멤버가 조회하면 자신이 소유한 fork의 최근 push 브랜치를 findByOriginalProjectAndOwnerAndPushedDateAfter로 조회해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 953L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                val pushedNullProject = PushedBranch(id = 2L, name = "refs/heads/orphan", project = null)
                every { pushedBranchRepository.findByOriginalProjectAndOwnerAndPushedDateAfter(project, "testuser", any()) } returns listOf(pushedNullProject)
                val playRepo = mockk<PlayRepository>()
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.getDefaultBranch() } returns "refs/heads/master"

                val result = mockMvc.perform(get("/owner/TestProj/pulls").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                val defaultBranches = result.modelAndView!!.model["pushedBranchDefaultBranches"] as Map<*, *>
                defaultBranches[2L] shouldBe "master"
            }

            // pushedBranches.associate { (it.id ?: 0L) ... } — PushedBranch.id가 null인 경우 키가 0L로
            // 대체되는 분기.
            it("PushedBranch.id가 null이면 pushedBranchDefaultBranches의 키가 0L이어야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 991L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                val pushedNoId = PushedBranch(id = null, name = "refs/heads/noid", project = project)
                every { pushedBranchRepository.findByOriginalProjectAndOwnerAndPushedDateAfter(project, "testuser", any()) } returns listOf(pushedNoId)
                val playRepo = mockk<PlayRepository>()
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.getDefaultBranch() } returns "refs/heads/master"

                val result = mockMvc.perform(get("/owner/TestProj/pulls").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                val defaultBranches = result.modelAndView!!.model["pushedBranchDefaultBranches"] as Map<*, *>
                defaultBranches.containsKey(0L) shouldBe true
            }

            // defaultBranchFor: 저장소 조회 중 예외가 발생하면 "master"로 안전하게 대체해야 한다.
            it("defaultBranchFor는 저장소 조회 중 예외가 발생하면 master로 대체해야 한다") {
                val brokenProject = Project(id = 204L, name = "broken", owner = "owner", projectScope = ProjectScope.PUBLIC)
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 954L, user = memberUser, project = brokenProject, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "broken") } returns Optional.of(brokenProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(204L, 10L) } returns true
                every { repositoryService.getRepository(brokenProject) } throws RuntimeException("boom")

                val result = mockMvc.perform(get("/owner/broken/pulls").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                result.modelAndView!!.model["defaultBranch"] shouldBe "master"
            }
        }

        describe("GET /{owner}/{projectName}/closedPullRequests — 추가 분기") {
            it("프로젝트를 찾을 수 없으면 404 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuchProj") } returns Optional.empty()

                mockMvc.perform(get("/owner/NoSuchProj/closedPullRequests").principal(userAuth))
                    .andExpect(view().name("error/404"))
            }

            // authentication?.let{}의 "인증 없음(익명)" 분기 — 비공개 프로젝트를 익명으로 조회하면 403이어야 한다.
            it("익명 사용자가 비공개 프로젝트를 조회하면 403 Forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)

                mockMvc.perform(get("/owner/TestProj/closedPullRequests"))
                    .andExpect(view().name("error/forbidden"))
            }
        }

        describe("GET /{owner}/{projectName}/sentPullRequests — 추가 분기") {
            it("프로젝트를 찾을 수 없으면 404 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuchProj") } returns Optional.empty()

                mockMvc.perform(get("/owner/NoSuchProj/sentPullRequests").principal(userAuth))
                    .andExpect(view().name("error/404"))
            }

            it("프로젝트 멤버가 아닐 경우 403 Forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns false

                mockMvc.perform(get("/owner/TestProj/sentPullRequests").principal(userAuth))
                    .andExpect(view().name("error/forbidden"))
            }

            // authentication?.let{}의 "인증 없음(익명)" 분기 — 비공개 프로젝트를 익명으로 조회하면 403이어야 한다.
            it("익명 사용자가 비공개 프로젝트를 조회하면 403 Forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)

                mockMvc.perform(get("/owner/TestProj/sentPullRequests"))
                    .andExpect(view().name("error/forbidden"))
            }
        }

        describe("GET /{owner}/{projectName}/pull/{number} — 추가 분기 (404/forbidden/notfound, addCommonPrAttributes, getReferredIssues, 첨부파일)") {
            it("프로젝트를 찾을 수 없으면 404 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuchProj") } returns Optional.empty()

                mockMvc.perform(get("/owner/NoSuchProj/pull/1").principal(userAuth))
                    .andExpect(view().name("error/404"))
            }

            it("읽기 권한이 없으면 403 Forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)

                mockMvc.perform(get("/owner/TestProj/pull/1"))
                    .andExpect(view().name("error/forbidden"))
            }

            it("PR을 찾을 수 없으면 error/notfound 뷰를 반환해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 960L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { pullRequestService.getPullRequest(1L, 99L) } returns null

                mockMvc.perform(get("/owner/TestProj/pull/99").principal(userAuth))
                    .andExpect(view().name("error/notfound"))
            }

            it("attemptMerge가 예외를 던지면 mergeResult 모델 속성은 null이어야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 961L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { pullRequestService.attemptMerge(50L) } throws RuntimeException("merge check 실패")
                every { commentThreadRepository.findByPullRequest(pullRequest) } returns emptyList()
                every { pullRequestCommitRepository.findByPullRequest(pullRequest) } returns emptyList()

                val result = mockMvc.perform(get("/owner/TestProj/pull/1").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                result.modelAndView!!.model["mergeResult"] shouldBe null
            }

            // yona git/view.scala.html의 AttachmentApp.getFileList() 대응 — 첨부파일이 있으면 attachmentsJson에
            // id/mimeType/size가 null인 항목도 안전하게(빈 문자열/"0") 직렬화되어야 한다.
            it("첨부파일이 있으면 attachmentsJson에 각 필드를 안전하게 채워야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 962L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { pullRequestService.attemptMerge(50L) } returns PullRequestMergeResult(pullRequest = pullRequest)
                every { commentThreadRepository.findByPullRequest(pullRequest) } returns emptyList()
                every { pullRequestCommitRepository.findByPullRequest(pullRequest) } returns emptyList()

                val fullAttachment = Attachment(
                    id = 700L, name = "파일\"이름\".txt", hash = "h1",
                    containerType = ResourceType.PULL_REQUEST, containerId = "50",
                    mimeType = "text/plain", size = 1024L
                )
                val bareAttachment = Attachment(
                    id = null, name = "이름없음", hash = "h2",
                    containerType = ResourceType.PULL_REQUEST, containerId = "50",
                    mimeType = null, size = null
                )
                every {
                    attachmentRepository.findByContainerTypeAndContainerId(ResourceType.PULL_REQUEST, "50")
                } returns listOf(fullAttachment, bareAttachment)

                val result = mockMvc.perform(get("/owner/TestProj/pull/1").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                val json = result.modelAndView!!.model["attachmentsJson"] as String
                json shouldContain "\"id\":\"700\""
                json shouldContain "\"size\":1024"
                json shouldContain "\"id\":\"\""
                json shouldContain "\"mimeType\":\"\""
                json shouldContain "\"size\":0"
            }

            it("리뷰어 수가 부족하면 isAcceptable=false, 리뷰어 부족 메시지를 사용해야 한다") {
                val strictProject = Project(id = 210L, name = "strict", owner = "owner", projectScope = ProjectScope.PRIVATE, isUsingReviewerCount = true, defaultReviewerCount = 2)
                val pr = PullRequest(
                    id = 70L, title = "t", toProject = strictProject, fromProject = strictProject,
                    toBranch = "master", fromBranch = "feature", contributor = user, state = State.OPEN, number = 5L
                )
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 963L, user = memberUser, project = strictProject, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "strict") } returns Optional.of(strictProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { pullRequestService.getPullRequest(210L, 5L) } returns pr
                every { pullRequestService.attemptMerge(70L) } returns PullRequestMergeResult(pullRequest = pr)
                every { commentThreadRepository.findByPullRequest(pr) } returns emptyList()
                every { pullRequestCommitRepository.findByPullRequest(pr) } returns emptyList()

                val result = mockMvc.perform(get("/owner/strict/pull/5").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                result.modelAndView!!.model["isAcceptable"] shouldBe false
                verify(atLeast = 1) { messageSource.getMessage("pullRequest.is.not.safe", null, any()) }
            }

            it("충돌 상태(isConflict)면 isAcceptable=false, 충돌 메시지를 사용해야 한다") {
                val pr = PullRequest(
                    id = 71L, title = "t", toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature", contributor = user, state = State.OPEN,
                    number = 6L, isConflict = true
                )
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 964L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { pullRequestService.getPullRequest(1L, 6L) } returns pr
                every { pullRequestService.attemptMerge(71L) } returns PullRequestMergeResult(pullRequest = pr)
                every { commentThreadRepository.findByPullRequest(pr) } returns emptyList()
                every { pullRequestCommitRepository.findByPullRequest(pr) } returns emptyList()

                val result = mockMvc.perform(get("/owner/TestProj/pull/6").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                result.modelAndView!!.model["isAcceptable"] shouldBe false
                verify(atLeast = 1) { messageSource.getMessage("pullRequest.is.not.safe", null, any()) }
            }

            it("병합 중(isMerging)이면 isAcceptable=false, 병합중 메시지를 사용해야 한다") {
                val pr = PullRequest(
                    id = 72L, title = "t", toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature", contributor = user, state = State.OPEN,
                    number = 7L, isMerging = true
                )
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 965L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { pullRequestService.getPullRequest(1L, 7L) } returns pr
                every { pullRequestService.attemptMerge(72L) } returns PullRequestMergeResult(pullRequest = pr)
                every { commentThreadRepository.findByPullRequest(pr) } returns emptyList()
                every { pullRequestCommitRepository.findByPullRequest(pr) } returns emptyList()

                val result = mockMvc.perform(get("/owner/TestProj/pull/7").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                result.modelAndView!!.model["isAcceptable"] shouldBe false
                verify(atLeast = 1) { messageSource.getMessage("pullRequest.is.merging", null, any()) }
            }

            it("닫힌 PR이지만 충돌/병합중/리뷰어부족 사유가 없으면 disabledAcceptReason은 null이어야 한다") {
                val pr = PullRequest(
                    id = 73L, title = "t", toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature", contributor = user, state = State.CLOSED, number = 8L
                )
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 966L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { pullRequestService.getPullRequest(1L, 8L) } returns pr
                every { pullRequestService.attemptMerge(73L) } returns PullRequestMergeResult(pullRequest = pr)
                every { commentThreadRepository.findByPullRequest(pr) } returns emptyList()
                every { pullRequestCommitRepository.findByPullRequest(pr) } returns emptyList()

                val result = mockMvc.perform(get("/owner/TestProj/pull/8").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                result.modelAndView!!.model["isAcceptable"] shouldBe false
                result.modelAndView!!.model["disabledAcceptReason"] shouldBe null
            }

            it("watch 중이라면 isWatching=true여야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 967L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { pullRequestService.attemptMerge(50L) } returns PullRequestMergeResult(pullRequest = pullRequest)
                every { commentThreadRepository.findByPullRequest(pullRequest) } returns emptyList()
                every { pullRequestCommitRepository.findByPullRequest(pullRequest) } returns emptyList()
                every { watchService.isWatching(memberUser, ResourceType.PULL_REQUEST, "50") } returns true

                val result = mockMvc.perform(get("/owner/TestProj/pull/1").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                result.modelAndView!!.model["isWatching"] shouldBe true
            }

            // addCommonPrAttributes: isWatching = loginUser?.let{...} ?: false — 비로그인(익명) 사용자는
            // watchService를 호출하지 않고 곧바로 false여야 한다.
            it("익명 사용자가 공개 프로젝트의 PR을 보면 isWatching=false여야 한다") {
                val publicProject = Project(id = 250L, name = "pub3", owner = "owner", projectScope = ProjectScope.PUBLIC)
                val pr = PullRequest(
                    id = 79L, title = "t", toProject = publicProject, fromProject = publicProject,
                    toBranch = "master", fromBranch = "feature", contributor = user, state = State.OPEN, number = 14L
                )
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "pub3") } returns Optional.of(publicProject)
                every { pullRequestService.getPullRequest(250L, 14L) } returns pr
                every { pullRequestService.attemptMerge(79L) } returns PullRequestMergeResult(pullRequest = pr)
                every { commentThreadRepository.findByPullRequest(pr) } returns emptyList()
                every { pullRequestCommitRepository.findByPullRequest(pr) } returns emptyList()

                val result = mockMvc.perform(get("/owner/pub3/pull/14"))
                    .andExpect(status().isOk)
                    .andReturn()

                result.modelAndView!!.model["isWatching"] shouldBe false
            }

            // meetsReviewerCount: 리뷰어 강제 프로젝트에서 실제 리뷰어 수가 최소 인원 이상이면 true여야 한다.
            it("리뷰어 강제 프로젝트에서 리뷰어 수가 충분하면 isAcceptable=true여야 한다") {
                val strictProject = Project(id = 211L, name = "strict2", owner = "owner", projectScope = ProjectScope.PRIVATE, isUsingReviewerCount = true, defaultReviewerCount = 1)
                val reviewer = User(id = 60L, loginId = "reviewer", name = "리뷰어")
                val pr = PullRequest(
                    id = 80L, title = "t", toProject = strictProject, fromProject = strictProject,
                    toBranch = "master", fromBranch = "feature", contributor = user, state = State.OPEN, number = 15L
                )
                pr.reviewers.add(reviewer)
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 992L, user = memberUser, project = strictProject, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "strict2") } returns Optional.of(strictProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { pullRequestService.getPullRequest(211L, 15L) } returns pr
                every { pullRequestService.attemptMerge(80L) } returns PullRequestMergeResult(pullRequest = pr)
                every { commentThreadRepository.findByPullRequest(pr) } returns emptyList()
                every { pullRequestCommitRepository.findByPullRequest(pr) } returns emptyList()

                val result = mockMvc.perform(get("/owner/strict2/pull/15").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                result.modelAndView!!.model["isAcceptable"] shouldBe true
                result.modelAndView!!.model["disabledAcceptReason"] shouldBe null
            }

            // getReferredIssues: toLongOrNull()이 오버플로로 null을 반환하는 경우 — 정규식 그룹은 항상
            // 숫자만 캡처하지만(#(\d+)), 자릿수가 Long 범위를 넘으면 toLongOrNull()이 null이 되어 해당
            // 매치는 조용히 무시되어야 한다.
            it("이슈 번호가 Long 범위를 넘으면 조용히 무시되어야 한다") {
                val pr = PullRequest(
                    id = 81L, title = "closes #99999999999999999999", body = null, toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature", contributor = user, state = State.OPEN, number = 16L
                )
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 993L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { pullRequestService.getPullRequest(1L, 16L) } returns pr
                every { pullRequestService.attemptMerge(81L) } returns PullRequestMergeResult(pullRequest = pr)
                every { commentThreadRepository.findByPullRequest(pr) } returns emptyList()
                every { pullRequestCommitRepository.findByPullRequest(pr) } returns emptyList()

                val result = mockMvc.perform(get("/owner/TestProj/pull/16").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                val referred = result.modelAndView!!.model["referredIssues"] as List<*>
                referred shouldBe emptyList<Any>()
                verify(exactly = 0) { issueRepository.findByProjectAndNumber(any(), any()) }
            }

            it("열린 스레드만 openThreadCount에 카운트되어야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 968L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { pullRequestService.attemptMerge(50L) } returns PullRequestMergeResult(pullRequest = pullRequest)
                every { pullRequestCommitRepository.findByPullRequest(pullRequest) } returns emptyList()
                val openThread = NonRangedCodeCommentThread(id = 900L, pullRequest = pullRequest, prevCommitId = "", commitId = "c1", state = CommentThread.ThreadState.OPEN)
                val closedThread = NonRangedCodeCommentThread(id = 901L, pullRequest = pullRequest, prevCommitId = "", commitId = "c2", state = CommentThread.ThreadState.CLOSED)
                every { commentThreadRepository.findByPullRequest(pullRequest) } returns listOf(openThread, closedThread)

                val result = mockMvc.perform(get("/owner/TestProj/pull/1").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                result.modelAndView!!.model["openThreadCount"] shouldBe 1
            }

            it("병합된 PR이고 원본 브랜치가 남아있으면 canDeleteBranch=true, canRestoreBranch=false여야 한다") {
                val mergedPr = PullRequest(
                    id = 74L, title = "t", toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature", contributor = user, state = State.MERGED, number = 9L
                )
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 969L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { pullRequestService.getPullRequest(1L, 9L) } returns mergedPr
                every { pullRequestService.attemptMerge(74L) } returns PullRequestMergeResult(pullRequest = mergedPr)
                every { commentThreadRepository.findByPullRequest(mergedPr) } returns emptyList()
                every { pullRequestCommitRepository.findByPullRequest(mergedPr) } returns emptyList()
                val playRepo = mockk<PlayRepository>()
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.getRefNames() } returns listOf("refs/heads/master", "refs/heads/feature")

                val result = mockMvc.perform(get("/owner/TestProj/pull/9").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                result.modelAndView!!.model["canDeleteBranch"] shouldBe true
                result.modelAndView!!.model["canRestoreBranch"] shouldBe false
            }

            it("병합된 PR이고 원본 브랜치가 삭제됐으면 canDeleteBranch=false, canRestoreBranch=true여야 한다") {
                val mergedPr = PullRequest(
                    id = 75L, title = "t", toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature", contributor = user, state = State.MERGED, number = 10L
                )
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 970L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { pullRequestService.getPullRequest(1L, 10L) } returns mergedPr
                every { pullRequestService.attemptMerge(75L) } returns PullRequestMergeResult(pullRequest = mergedPr)
                every { commentThreadRepository.findByPullRequest(mergedPr) } returns emptyList()
                every { pullRequestCommitRepository.findByPullRequest(mergedPr) } returns emptyList()
                val playRepo = mockk<PlayRepository>()
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.getRefNames() } returns listOf("refs/heads/master")

                val result = mockMvc.perform(get("/owner/TestProj/pull/10").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                result.modelAndView!!.model["canDeleteBranch"] shouldBe false
                result.modelAndView!!.model["canRestoreBranch"] shouldBe true
            }

            it("병합된 PR의 브랜치 조회 중 예외가 발생하면 canDeleteBranch/canRestoreBranch 모두 false여야 한다") {
                val mergedPr = PullRequest(
                    id = 76L, title = "t", toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature", contributor = user, state = State.MERGED, number = 11L
                )
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 971L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { pullRequestService.getPullRequest(1L, 11L) } returns mergedPr
                every { pullRequestService.attemptMerge(76L) } returns PullRequestMergeResult(pullRequest = mergedPr)
                every { commentThreadRepository.findByPullRequest(mergedPr) } returns emptyList()
                every { pullRequestCommitRepository.findByPullRequest(mergedPr) } returns emptyList()
                every { repositoryService.getRepository(project) } throws RuntimeException("repo 없음")

                val result = mockMvc.perform(get("/owner/TestProj/pull/11").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                result.modelAndView!!.model["canDeleteBranch"] shouldBe false
                result.modelAndView!!.model["canRestoreBranch"] shouldBe false
            }

            // getReferredIssues: 제목·본문·커밋 메시지에서 이슈 번호를 모두 추출하고, 존재하지 않는
            // 이슈 번호는 결과에서 제외해야 한다.
            it("제목/본문/커밋 메시지에서 참조된 이슈를 모두 모으고 존재하지 않는 이슈는 제외해야 한다") {
                val pr = PullRequest(
                    id = 77L, title = "closes #5", body = "resolved #6 논의", toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature", contributor = user, state = State.OPEN, number = 12L
                )
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 972L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { pullRequestService.getPullRequest(1L, 12L) } returns pr
                every { pullRequestService.attemptMerge(77L) } returns PullRequestMergeResult(pullRequest = pr)
                every { commentThreadRepository.findByPullRequest(pr) } returns emptyList()
                val commit = PullRequestCommit(id = 1L, pullRequest = pr, commitId = "abc", commitMessage = "resolves #7 반영")
                every { pullRequestCommitRepository.findByPullRequest(pr) } returns listOf(commit)

                val issue5 = Issue(id = 5L, title = "이슈5", project = project, number = 5L)
                val issue7 = Issue(id = 7L, title = "이슈7", project = project, number = 7L)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue5
                every { issueRepository.findByProjectAndNumber(project, 6L) } returns null
                every { issueRepository.findByProjectAndNumber(project, 7L) } returns issue7

                val result = mockMvc.perform(get("/owner/TestProj/pull/12").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                val referred = result.modelAndView!!.model["referredIssues"] as List<*>
                val ids = referred.map { (it as Issue).id }.toSet()
                ids shouldBe setOf(5L, 7L)
            }

            // closePattern의 "fix[e[s|d]]?" 부분이 대괄호 중첩 오사용으로 fix/fixes/fixed를 전혀
            // 매치하지 못하던 실버그를 커버리지 감사 중 발견해 "fix(?:es|ed)?"로 수정(TASK-0270,
            // 사용자 지시로 기능은 유지). 이 회귀 테스트가 없으면 다시 깨져도 알아챌 수 없다.
            it("fix/fixes/fixed 키워드도 close/resolve와 동일하게 이슈 번호를 인식해야 한다") {
                val pr = PullRequest(
                    id = 79L, title = "fix #20", body = "이 PR은 fixes #21 문제를 해결합니다", toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature", contributor = user, state = State.OPEN, number = 14L
                )
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 974L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { pullRequestService.getPullRequest(1L, 14L) } returns pr
                every { pullRequestService.attemptMerge(79L) } returns PullRequestMergeResult(pullRequest = pr)
                every { commentThreadRepository.findByPullRequest(pr) } returns emptyList()
                val commit = PullRequestCommit(id = 2L, pullRequest = pr, commitId = "def", commitMessage = "fixed #22")
                every { pullRequestCommitRepository.findByPullRequest(pr) } returns listOf(commit)

                val issue20 = Issue(id = 20L, title = "이슈20", project = project, number = 20L)
                val issue21 = Issue(id = 21L, title = "이슈21", project = project, number = 21L)
                val issue22 = Issue(id = 22L, title = "이슈22", project = project, number = 22L)
                every { issueRepository.findByProjectAndNumber(project, 20L) } returns issue20
                every { issueRepository.findByProjectAndNumber(project, 21L) } returns issue21
                every { issueRepository.findByProjectAndNumber(project, 22L) } returns issue22

                val result = mockMvc.perform(get("/owner/TestProj/pull/14").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                val referred = result.modelAndView!!.model["referredIssues"] as List<*>
                val ids = referred.map { (it as Issue).id }.toSet()
                ids shouldBe setOf(20L, 21L, 22L)
            }

            it("본문이 null이고 이슈 참조가 전혀 없으면 referredIssues는 빈 리스트이고 issueRepository는 호출되지 않아야 한다") {
                val pr = PullRequest(
                    id = 78L, title = "그냥 제목", body = null, toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature", contributor = user, state = State.OPEN, number = 13L
                )
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 973L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { pullRequestService.getPullRequest(1L, 13L) } returns pr
                every { pullRequestService.attemptMerge(78L) } returns PullRequestMergeResult(pullRequest = pr)
                every { commentThreadRepository.findByPullRequest(pr) } returns emptyList()
                every { pullRequestCommitRepository.findByPullRequest(pr) } returns emptyList()

                val result = mockMvc.perform(get("/owner/TestProj/pull/13").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                val referred = result.modelAndView!!.model["referredIssues"] as List<*>
                referred shouldBe emptyList<Any>()
                verify(exactly = 0) { issueRepository.findByProjectAndNumber(any(), any()) }
            }
        }

        describe("GET .../pull/{number}/changes — 추가 분기 (404/forbidden/notfound, 예외 처리, OR 분기)") {
            it("프로젝트를 찾을 수 없으면 404 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuchProj") } returns Optional.empty()

                mockMvc.perform(get("/owner/NoSuchProj/pull/1/changes").principal(userAuth))
                    .andExpect(view().name("error/404"))
            }

            it("읽기 권한이 없으면 403 Forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)

                mockMvc.perform(get("/owner/TestProj/pull/1/changes"))
                    .andExpect(view().name("error/forbidden"))
            }

            it("PR을 찾을 수 없으면 error/notfound 뷰를 반환해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 980L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { pullRequestService.getPullRequest(1L, 98L) } returns null

                mockMvc.perform(get("/owner/TestProj/pull/98/changes").principal(userAuth))
                    .andExpect(view().name("error/notfound"))
            }

            it("attemptMerge/getDiff가 예외를 던지면 mergeResult=null, diffs=빈 리스트여야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 981L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { pullRequestService.attemptMerge(50L) } throws RuntimeException("merge 실패")
                every { pullRequestService.getDiff(pullRequest) } throws RuntimeException("diff 실패")
                every { commentThreadRepository.findByPullRequest(pullRequest) } returns emptyList()
                every { pullRequestCommitRepository.findByPullRequest(pullRequest) } returns emptyList()

                val result = mockMvc.perform(get("/owner/TestProj/pull/1/changes").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                result.modelAndView!!.model["mergeResult"] shouldBe null
                (result.modelAndView!!.model["diffs"] as List<*>) shouldBe emptyList<Any>()
            }

            // buildCommentThreadsForChanges: commitId 지정 시 매칭되지 않는 NonRangedCodeCommentThread는
            // 제외되어야 한다(OR 조건의 우측이 false인 경로).
            it("commitId 지정 조회 시 매칭되지 않는 non-ranged 스레드는 제외되어야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 982L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { pullRequestService.attemptMerge(50L) } returns PullRequestMergeResult(pullRequest = pullRequest)
                every { pullRequestService.getDiff(pullRequest, "abcdefg") } returns emptyList()
                every { pullRequestCommitRepository.findByPullRequest(pullRequest) } returns emptyList()

                val mismatchedNonRanged = NonRangedCodeCommentThread(id = 820L, pullRequest = pullRequest, prevCommitId = "", commitId = "zzzzzzz")
                every { commentThreadRepository.findByPullRequest(pullRequest) } returns listOf(mismatchedNonRanged)

                val result = mockMvc.perform(get("/owner/TestProj/pullRequest/1/changes/abcdefg").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                val commentThreads = result.modelAndView!!.model["commentThreads"] as List<*>
                commentThreads shouldBe emptyList<Any>()
            }
        }

        describe("GET /{owner}/{projectName}/pull/new — 추가 분기 (404/forbidden/그룹멤버, 브랜치 없음, cross-fork resolveAssociatedProject)") {
            it("프로젝트를 찾을 수 없으면 404 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuchProj") } returns Optional.empty()

                mockMvc.perform(get("/owner/NoSuchProj/pull/new").principal(userAuth))
                    .andExpect(view().name("error/404"))
            }

            it("로그인하지 않았다면 403 Forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)

                mockMvc.perform(get("/owner/TestProj/pull/new"))
                    .andExpect(view().name("error/forbidden"))
            }

            it("멤버도 그룹멤버도 아니면 403 Forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns false

                mockMvc.perform(get("/owner/TestProj/pull/new").principal(userAuth))
                    .andExpect(view().name("error/forbidden"))
            }

            it("직접 멤버가 아니어도 조직 그룹멤버라면 200 OK를 반환해야 한다") {
                val groupOrg = Organization(id = 2L, name = "org2")
                groupOrg.organizationUsers.add(
                    OrganizationUser(id = 2L, user = user, organization = groupOrg, role = Role(id = RoleType.ORG_MEMBER.roleType))
                )
                val groupProject = Project(id = 220L, name = "group-proj-new", owner = "owner", projectScope = ProjectScope.PROTECTED, organization = groupOrg)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "group-proj-new") } returns Optional.of(groupProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(220L, 10L) } returns false
                val playRepo = mockk<PlayRepository>()
                every { repositoryService.getRepository(groupProject) } returns playRepo
                every { playRepo.getRefNames() } returns listOf("refs/heads/master")

                mockMvc.perform(get("/owner/group-proj-new/pull/new").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("pullrequest/create"))
            }

            it("fromBranches가 비어있으면 error/badrequest 뷰를 from 저장소 메시지로 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                val playRepo = mockk<PlayRepository>()
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.getRefNames() } returns emptyList()

                val result = mockMvc.perform(get("/owner/TestProj/pull/new").principal(userAuth))
                    .andExpect(view().name("error/badrequest"))
                    .andReturn()

                result.modelAndView!!.model["messageKey"] shouldBe "error.pullRequest.empty.from.repository"
            }

            it("toBranches가 비어있으면 error/badrequest 뷰를 to 저장소 메시지로 반환해야 한다") {
                val originProject = Project(id = 221L, name = "origin2", owner = "origin2-owner", projectScope = ProjectScope.PUBLIC, isCodeEnabled = true, isPullRequestEnabled = true)
                val forkProject = Project(id = 222L, name = "fork2", owner = "owner", projectScope = ProjectScope.PUBLIC, originalProject = originProject)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "fork2") } returns Optional.of(forkProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(222L, 10L) } returns true
                val fromRepo = mockk<PlayRepository>()
                every { repositoryService.getRepository(forkProject) } returns fromRepo
                every { fromRepo.getRefNames() } returns listOf("refs/heads/feature")
                val toRepo = mockk<PlayRepository>()
                every { repositoryService.getRepository(originProject) } returns toRepo
                every { toRepo.getRefNames() } returns emptyList()

                val result = mockMvc.perform(get("/owner/fork2/pull/new").principal(userAuth))
                    .andExpect(view().name("error/badrequest"))
                    .andReturn()

                result.modelAndView!!.model["messageKey"] shouldBe "error.pullRequest.empty.to.repository"
            }

            // resolveAssociatedProject: 원본의 코드 기능이 꺼져 있으면 toProject 기본값이 자기 자신이어야 한다.
            it("원본의 코드 기능이 꺼져 있으면 toProject 기본값은 fork 자기 자신이어야 한다") {
                val originProject = Project(id = 223L, name = "origin3", owner = "origin3-owner", projectScope = ProjectScope.PUBLIC, isCodeEnabled = false, isPullRequestEnabled = true)
                val forkProject = Project(id = 224L, name = "fork3", owner = "owner", projectScope = ProjectScope.PUBLIC, originalProject = originProject)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "fork3") } returns Optional.of(forkProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(224L, 10L) } returns true
                val playRepo = mockk<PlayRepository>()
                every { repositoryService.getRepository(forkProject) } returns playRepo
                every { playRepo.getRefNames() } returns listOf("refs/heads/master")

                val result = mockMvc.perform(get("/owner/fork3/pull/new").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                (result.modelAndView!!.model["toProject"] as Project).id shouldBe 224L
            }

            it("원본의 PR 기능이 꺼져 있으면 toProject 기본값은 fork 자기 자신이어야 한다") {
                val originProject = Project(id = 225L, name = "origin4", owner = "origin4-owner", projectScope = ProjectScope.PUBLIC, isCodeEnabled = true, isPullRequestEnabled = false)
                val forkProject = Project(id = 226L, name = "fork4", owner = "owner", projectScope = ProjectScope.PUBLIC, originalProject = originProject)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "fork4") } returns Optional.of(forkProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(226L, 10L) } returns true
                val playRepo = mockk<PlayRepository>()
                every { repositoryService.getRepository(forkProject) } returns playRepo
                every { playRepo.getRefNames() } returns listOf("refs/heads/master")

                val result = mockMvc.perform(get("/owner/fork4/pull/new").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                (result.modelAndView!!.model["toProject"] as Project).id shouldBe 226L
            }

            // resolveAssociatedProject의 projectId "찾음"/"못 찾음" 분기를 한 요청에서 함께 검증한다: fromProjectId는
            // association 목록에 있는 원본을 가리켜 명시적으로 전환되고, toProjectId는 목록에 없는 값이라 무시되어
            // (isForkedFromOrigin 기본 로직에 따른) 원본이 그대로 유지된다.
            it("fromProjectId가 association 목록의 원본이면 전환되고, toProjectId가 목록에 없으면 무시되어야 한다") {
                val originProject = Project(id = 227L, name = "origin5", owner = "origin5-owner", projectScope = ProjectScope.PUBLIC, isCodeEnabled = true, isPullRequestEnabled = true)
                val forkProject = Project(id = 228L, name = "fork5", owner = "owner", projectScope = ProjectScope.PUBLIC, originalProject = originProject)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "fork5") } returns Optional.of(forkProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(228L, 10L) } returns true
                every { projectUserRepository.existsByProjectIdAndUserId(227L, 10L) } returns false
                val forkRepo = mockk<PlayRepository>()
                every { repositoryService.getRepository(forkProject) } returns forkRepo
                every { forkRepo.getRefNames() } returns listOf("refs/heads/master")
                val originRepo = mockk<PlayRepository>()
                every { repositoryService.getRepository(originProject) } returns originRepo
                every { originRepo.getRefNames() } returns listOf("refs/heads/master")

                val result = mockMvc.perform(
                    get("/owner/fork5/pull/new")
                        .param("fromProjectId", "227")
                        .param("toProjectId", "999999")
                        .principal(userAuth)
                ).andExpect(status().isOk).andReturn()

                (result.modelAndView!!.model["fromProject"] as Project).id shouldBe 227L
                (result.modelAndView!!.model["toProject"] as Project).id shouldBe 227L
            }

            // prefillFromBranch/prefillToBranch: 쿼리파라미터가 빈 문자열이면 목록의 첫 브랜치로 대체돼야 한다.
            it("fromBranch/toBranch 쿼리파라미터가 빈 문자열이면 첫 브랜치로 대체돼야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                val playRepo = mockk<PlayRepository>()
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.getRefNames() } returns listOf("refs/heads/master", "refs/heads/feature")

                val result = mockMvc.perform(
                    get("/owner/TestProj/pull/new").param("fromBranch", "").param("toBranch", "").principal(userAuth)
                ).andExpect(status().isOk).andReturn()

                result.modelAndView!!.model["prefillFromBranch"] shouldBe "master"
                result.modelAndView!!.model["prefillToBranch"] shouldBe "master"
            }

            it("fromBranch/toBranch 쿼리파라미터가 명시되면 그대로 prefill 값이 되어야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                val playRepo = mockk<PlayRepository>()
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.getRefNames() } returns listOf("refs/heads/master", "refs/heads/feature")

                val result = mockMvc.perform(
                    get("/owner/TestProj/pull/new").param("fromBranch", "feature").param("toBranch", "feature").principal(userAuth)
                ).andExpect(status().isOk).andReturn()

                result.modelAndView!!.model["prefillFromBranch"] shouldBe "feature"
                result.modelAndView!!.model["prefillToBranch"] shouldBe "feature"
            }

            it("memberAssociationProjects는 사용자가 속한 프로젝트만 필터링해야 한다") {
                val originProject = Project(id = 229L, name = "origin6", owner = "origin6-owner", projectScope = ProjectScope.PUBLIC, isCodeEnabled = true, isPullRequestEnabled = true)
                val forkProject = Project(id = 230L, name = "fork6", owner = "owner", projectScope = ProjectScope.PUBLIC, originalProject = originProject)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "fork6") } returns Optional.of(forkProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(230L, 10L) } returns true
                every { projectUserRepository.existsByProjectIdAndUserId(229L, 10L) } returns false
                val forkRepo = mockk<PlayRepository>()
                every { repositoryService.getRepository(forkProject) } returns forkRepo
                every { forkRepo.getRefNames() } returns listOf("refs/heads/master")
                val originRepo = mockk<PlayRepository>()
                every { repositoryService.getRepository(originProject) } returns originRepo
                every { originRepo.getRefNames() } returns listOf("refs/heads/master")

                val result = mockMvc.perform(get("/owner/fork6/pull/new").principal(userAuth)).andExpect(status().isOk).andReturn()

                val memberAssoc = result.modelAndView!!.model["memberAssociationProjects"] as List<*>
                val ids = memberAssoc.map { (it as Project).id }
                ids shouldBe listOf(230L)
            }
        }

        describe("GET .../pull/mergeResult — 추가 분기 (404/forbidden/그룹멤버, 브랜치 미지정, 프리뷰 예외, cross-fork)") {
            it("프로젝트를 찾을 수 없으면 404 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuchProj") } returns Optional.empty()

                mockMvc.perform(get("/owner/NoSuchProj/pull/mergeResult").principal(userAuth))
                    .andExpect(view().name("error/404"))
            }

            it("로그인하지 않았다면 403 Forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)

                mockMvc.perform(get("/owner/TestProj/pull/mergeResult"))
                    .andExpect(view().name("error/forbidden"))
            }

            it("멤버도 그룹멤버도 아니면 403 Forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns false

                mockMvc.perform(get("/owner/TestProj/pull/mergeResult").principal(userAuth))
                    .andExpect(view().name("error/forbidden"))
            }

            it("직접 멤버가 아니어도 조직 그룹멤버라면 200 OK를 반환해야 한다") {
                val groupOrg = Organization(id = 3L, name = "org3")
                groupOrg.organizationUsers.add(
                    OrganizationUser(id = 3L, user = user, organization = groupOrg, role = Role(id = RoleType.ORG_MEMBER.roleType))
                )
                val groupProject = Project(id = 240L, name = "group-proj-mr", owner = "owner", projectScope = ProjectScope.PROTECTED, organization = groupOrg)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "group-proj-mr") } returns Optional.of(groupProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(240L, 10L) } returns false
                val playRepo = mockk<PlayRepository>()
                every { repositoryService.getRepository(groupProject) } returns playRepo
                every { playRepo.getRefNames() } returns listOf("refs/heads/master")
                every { pullRequestService.previewMerge(groupProject, groupProject, "master", "master") } returns
                    MergePreviewResult(commits = emptyList(), conflict = false, suggestedTitle = null, suggestedBody = null)

                mockMvc.perform(get("/owner/group-proj-mr/pull/mergeResult").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("pullrequest/partial_merge_result :: mergeResult"))
            }

            it("fromBranches가 비어있어 resolvedFromBranch가 없으면 변경 없음 상태로 렌더링해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                val playRepo = mockk<PlayRepository>()
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.getRefNames() } returns emptyList()

                val result = mockMvc.perform(get("/owner/TestProj/pull/mergeResult").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                result.modelAndView!!.model["commits"] shouldBe emptyList<Any>()
                result.modelAndView!!.model["conflict"] shouldBe null
                verify(exactly = 0) { pullRequestService.previewMerge(any(), any(), any(), any()) }
            }

            it("toBranches가 비어있어 resolvedToBranch가 없으면 변경 없음 상태로 렌더링해야 한다") {
                val originProject = Project(id = 241L, name = "origin7", owner = "origin7-owner", projectScope = ProjectScope.PUBLIC, isCodeEnabled = true, isPullRequestEnabled = true)
                val forkProject = Project(id = 242L, name = "fork7", owner = "owner", projectScope = ProjectScope.PUBLIC, originalProject = originProject)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "fork7") } returns Optional.of(forkProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(242L, 10L) } returns true
                val fromRepo = mockk<PlayRepository>()
                every { repositoryService.getRepository(forkProject) } returns fromRepo
                every { fromRepo.getRefNames() } returns listOf("refs/heads/feature")
                val toRepo = mockk<PlayRepository>()
                every { repositoryService.getRepository(originProject) } returns toRepo
                every { toRepo.getRefNames() } returns emptyList()

                val result = mockMvc.perform(get("/owner/fork7/pull/mergeResult").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                result.modelAndView!!.model["commits"] shouldBe emptyList<Any>()
                result.modelAndView!!.model["conflict"] shouldBe null
            }

            it("previewMerge가 성공하면 커밋/충돌/제목/본문이 모델에 반영되어야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                val playRepo = mockk<PlayRepository>()
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.getRefNames() } returns listOf("refs/heads/master", "refs/heads/feature")
                every { pullRequestService.previewMerge(project, project, "feature", "master") } returns
                    MergePreviewResult(commits = emptyList(), conflict = true, suggestedTitle = "제안 제목", suggestedBody = "제안 본문")

                val result = mockMvc.perform(
                    get("/owner/TestProj/pull/mergeResult")
                        .param("fromBranch", "feature")
                        .param("toBranch", "master")
                        .principal(userAuth)
                ).andExpect(status().isOk).andReturn()

                result.modelAndView!!.model["pullRequestTitle"] shouldBe "제안 제목"
                result.modelAndView!!.model["pullRequestBody"] shouldBe "제안 본문"
                result.modelAndView!!.model["conflict"] shouldBe true
            }

            it("previewMerge가 예외를 던지면 안내용 빈 모델로 완화되어야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                val playRepo = mockk<PlayRepository>()
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.getRefNames() } returns listOf("refs/heads/master", "refs/heads/feature")
                every { pullRequestService.previewMerge(project, project, "feature", "master") } throws RuntimeException("merge 실패")

                val result = mockMvc.perform(
                    get("/owner/TestProj/pull/mergeResult")
                        .param("fromBranch", "feature")
                        .param("toBranch", "master")
                        .principal(userAuth)
                ).andExpect(status().isOk).andReturn()

                result.modelAndView!!.model["pullRequestTitle"] shouldBe null
                result.modelAndView!!.model["commits"] shouldBe emptyList<Any>()
            }

            it("fromBranch/toBranch가 빈 문자열이면 브랜치 목록의 첫 번째 값으로 대체해 previewMerge를 호출해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                val playRepo = mockk<PlayRepository>()
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.getRefNames() } returns listOf("refs/heads/master", "refs/heads/feature")
                every { pullRequestService.previewMerge(project, project, "master", "master") } returns
                    MergePreviewResult(commits = emptyList(), conflict = false, suggestedTitle = null, suggestedBody = null)

                mockMvc.perform(
                    get("/owner/TestProj/pull/mergeResult")
                        .param("fromBranch", "")
                        .param("toBranch", "")
                        .principal(userAuth)
                ).andExpect(status().isOk)

                verify(exactly = 1) { pullRequestService.previewMerge(project, project, "master", "master") }
            }

            // cross-fork: fromProjectId/toProjectId로 서로 다른 연관 프로젝트를 지정하면 resolveAssociatedProject가
            // 그 프로젝트들을 previewMerge에 전달해야 한다.
            it("fromProjectId/toProjectId를 지정하면 해당 프로젝트들로 previewMerge를 호출해야 한다") {
                val originProject = Project(id = 243L, name = "origin8", owner = "origin8-owner", projectScope = ProjectScope.PUBLIC, isCodeEnabled = true, isPullRequestEnabled = true)
                val forkProject = Project(id = 244L, name = "fork8", owner = "owner", projectScope = ProjectScope.PUBLIC, originalProject = originProject)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "fork8") } returns Optional.of(forkProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(244L, 10L) } returns true
                val forkRepo = mockk<PlayRepository>()
                every { repositoryService.getRepository(forkProject) } returns forkRepo
                every { forkRepo.getRefNames() } returns listOf("refs/heads/feature")
                val originRepo = mockk<PlayRepository>()
                every { repositoryService.getRepository(originProject) } returns originRepo
                every { originRepo.getRefNames() } returns listOf("refs/heads/master")
                every { pullRequestService.previewMerge(forkProject, originProject, "feature", "master") } returns
                    MergePreviewResult(commits = emptyList(), conflict = false, suggestedTitle = null, suggestedBody = null)

                mockMvc.perform(
                    get("/owner/fork8/pull/mergeResult")
                        .param("fromProjectId", "244")
                        .param("toProjectId", "243")
                        .param("fromBranch", "feature")
                        .param("toBranch", "master")
                        .principal(userAuth)
                ).andExpect(status().isOk)

                verify(exactly = 1) { pullRequestService.previewMerge(forkProject, originProject, "feature", "master") }
            }
        }

        describe("GET /{owner}/{projectName}/pull/{number}/edit — 추가 분기") {
            it("프로젝트를 찾을 수 없으면 404 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuchProj") } returns Optional.empty()

                mockMvc.perform(get("/owner/NoSuchProj/pull/1/edit").principal(userAuth))
                    .andExpect(view().name("error/404"))
            }

            it("PR을 찾을 수 없으면 error/notfound 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { pullRequestService.getPullRequest(1L, 97L) } returns null

                mockMvc.perform(get("/owner/TestProj/pull/97/edit").principal(userAuth))
                    .andExpect(view().name("error/notfound"))
            }

            // isManagerOrContributor: 프로젝트에 소속되지 않아 ProjectUser 레코드 자체가 없는(Optional.empty)
            // 사용자는 컨트리뷰터도 매니저도 아니므로 403이어야 한다(orElse(false) 분기).
            it("프로젝트 소속조차 아닌 사용자는 403 Forbidden 뷰를 반환해야 한다") {
                val strangerUser = User(id = 50L, loginId = "stranger", name = "낯선사람")
                val strangerAuth = UsernamePasswordAuthenticationToken("stranger", "password")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("stranger") } returns Optional.of(strangerUser)
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { projectUserRepository.findByProjectIdAndUserId(1L, 50L) } returns Optional.empty()

                mockMvc.perform(get("/owner/TestProj/pull/1/edit").principal(strangerAuth))
                    .andExpect(view().name("error/forbidden"))
            }
        }
    }

        describe("Coverage addition for PullRequestViewController") {
            it("should handle null states, null filter, null contributorId in listPullRequests") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                val project = Project(id = 1L, name = "TestProj", owner = "owner", projectScope = ProjectScope.PRIVATE)
                memberUser.projectUsers.add(ProjectUser(id = 900L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                
                io.mockk.every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                io.mockk.every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                io.mockk.every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                io.mockk.every {
                    pullRequestRepository.findAll(any<Specification<PullRequest>>(), any<Pageable>())
                } returns PageImpl(emptyList<PullRequest>(), PageRequest.of(0, 20), 0)
                
                // state=all (states=null), filter=null, contributorId=null
                mockMvc.perform(MockMvcRequestBuilders.get("/owner/TestProj/pulls")
                    .param("state", "all")
                    .principal(UsernamePasswordAuthenticationToken("testuser", "password")))
                    .andExpect(MockMvcResultMatchers.status().isOk)
            }
            
            it("should handle getReferredIssues with empty body") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                val project = Project(id = 1L, name = "TestProj", owner = "owner", projectScope = ProjectScope.PRIVATE)
                memberUser.projectUsers.add(ProjectUser(id = 900L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                
                val prWithEmptyBody = PullRequest(
                    id = 50L, title = "PR tests", body = null, toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature", contributor = memberUser, state = State.OPEN, number = 1L
                )
                
                io.mockk.every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                io.mockk.every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                io.mockk.every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                io.mockk.every { pullRequestService.getPullRequest(1L, 1L) } returns prWithEmptyBody
                io.mockk.every { pullRequestService.attemptMerge(50L) } returns PullRequestMergeResult(pullRequest = prWithEmptyBody)
                io.mockk.every { commentThreadRepository.findByPullRequest(prWithEmptyBody) } returns emptyList()
                io.mockk.every { pullRequestCommitRepository.findByPullRequest(prWithEmptyBody) } returns emptyList()
                
                mockMvc.perform(MockMvcRequestBuilders.get("/owner/TestProj/pull/1")
                    .principal(UsernamePasswordAuthenticationToken("testuser", "password")))
                    .andExpect(MockMvcResultMatchers.status().isOk)
            }
            
            it("should handle viewPullRequest\$lambda\$5(Attachment) with null fields") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                val project = Project(id = 1L, name = "TestProj", owner = "owner", projectScope = ProjectScope.PRIVATE)
                memberUser.projectUsers.add(ProjectUser(id = 900L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                val pr = PullRequest(
                    id = 50L, title = "PR test", body = "body", toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature", contributor = memberUser, state = State.OPEN, number = 1L
                )
                
                io.mockk.every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                io.mockk.every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                io.mockk.every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                io.mockk.every { pullRequestService.getPullRequest(1L, 1L) } returns pr
                io.mockk.every { pullRequestService.attemptMerge(50L) } returns PullRequestMergeResult(pullRequest = pr)
                io.mockk.every { commentThreadRepository.findByPullRequest(pr) } returns emptyList()
                io.mockk.every { pullRequestCommitRepository.findByPullRequest(pr) } returns emptyList()
                
                val attachment = Attachment(
                    id = null, mimeType = null, name = "test.txt", size = null, containerType = ResourceType.PULL_REQUEST, containerId = "50"
                )
                io.mockk.every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.PULL_REQUEST, "50") } returns listOf(attachment)
                
                mockMvc.perform(MockMvcRequestBuilders.get("/owner/TestProj/pull/1")
                    .principal(UsernamePasswordAuthenticationToken("testuser", "password")))
                    .andExpect(MockMvcResultMatchers.status().isOk)
            }
            
            it("should return false when isManagerOrContributor is called for non-manager") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                val project = Project(id = 1L, name = "TestProj", owner = "owner", projectScope = ProjectScope.PRIVATE)
                val memberRole = Role(id = RoleType.MEMBER.roleType)
                memberUser.projectUsers.add(ProjectUser(id = 900L, user = memberUser, project = project, role = memberRole))
                
                val pr = PullRequest(
                    id = 50L, title = "PR test", body = "body", toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature", 
                    contributor = User(id = 20L, loginId = "other", name = "other"), 
                    state = State.OPEN, number = 1L
                )
                
                io.mockk.every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                io.mockk.every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                io.mockk.every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                io.mockk.every { pullRequestService.getPullRequest(1L, 1L) } returns pr
                io.mockk.every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(ProjectUser(id = 900L, user = memberUser, project = project, role = memberRole))
                
                mockMvc.perform(MockMvcRequestBuilders.get("/owner/TestProj/pull/1/edit")
                    .principal(UsernamePasswordAuthenticationToken("testuser", "password")))
                    .andExpect(MockMvcResultMatchers.status().isOk)
                    .andExpect(MockMvcResultMatchers.view().name("error/forbidden"))
            }
            
        }
    
})
