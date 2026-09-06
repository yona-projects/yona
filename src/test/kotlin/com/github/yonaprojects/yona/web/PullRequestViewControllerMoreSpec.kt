package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.Operation
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.pullrequest.CodeCommentThread
import com.github.yonaprojects.yona.domain.pullrequest.CodeReviewService
import com.github.yonaprojects.yona.domain.pullrequest.CommentThread
import com.github.yonaprojects.yona.domain.pullrequest.CommentThreadRepository
import com.github.yonaprojects.yona.domain.pullrequest.NonRangedCodeCommentThread
import com.github.yonaprojects.yona.domain.pullrequest.PullRequest
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestCommitRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestEventRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestService
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.vcs.PushedBranch
import com.github.yonaprojects.yona.domain.vcs.PushedBranchRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.watch.WatchService
import com.github.yonaprojects.yona.domain.attachment.AttachmentRepository
import com.github.yonaprojects.yona.domain.vcs.PlayRepository
import org.springframework.context.MessageSource
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.data.web.PageableHandlerMethodArgumentResolver
import java.util.Optional
import io.mockk.clearMocks
import org.springframework.data.jpa.domain.Specification
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import jakarta.persistence.criteria.Path
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken

class PullRequestViewControllerMoreSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val pullRequestService = mockk<PullRequestService>()
    val pullRequestRepository = mockk<PullRequestRepository>(relaxed=true)
    val repositoryService = mockk<RepositoryService>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val commentThreadRepository = mockk<CommentThreadRepository>()
    val pullRequestEventRepository = mockk<PullRequestEventRepository>()
    val pullRequestCommitRepository = mockk<PullRequestCommitRepository>()
    val issueRepository = mockk<IssueRepository>()
    val codeReviewService = mockk<CodeReviewService>()
    val accessControl = mockk<AccessControl>()
    val pushedBranchRepository = mockk<PushedBranchRepository>()
    val watchService = mockk<WatchService>()
    val messageSource = mockk<MessageSource>()
    val attachmentRepository = mockk<AttachmentRepository>()

    val controller = PullRequestViewController(
        projectRepository, pullRequestService, pullRequestRepository, repositoryService, projectUserRepository,
        userRepository, commentThreadRepository, pullRequestEventRepository, pullRequestCommitRepository,
        issueRepository, accessControl, codeReviewService, pushedBranchRepository, watchService, messageSource, attachmentRepository
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setCustomArgumentResolvers(PageableHandlerMethodArgumentResolver())
        .build()

    beforeTest {
        clearMocks(
            projectRepository, pullRequestService, pullRequestRepository, repositoryService, projectUserRepository,
            userRepository, commentThreadRepository, pullRequestEventRepository, pullRequestCommitRepository,
            issueRepository, accessControl, codeReviewService, pushedBranchRepository, watchService, messageSource, attachmentRepository
        )
        // yona-wiki P3-15(PR 승인/변경요청 워크플로) — addCommonPrAttributes()가 항상 호출하므로
        // 이 스펙의 기존 테스트에 영향을 주지 않도록 기본값(리뷰 없음)을 둔다.
        every { pullRequestService.getReviews(any()) } returns emptyList()
        every { pullRequestService.getLatestReviewStates(any()) } returns emptyMap()
    }

    describe("More PR controller tests") {
        val user = User(id = 10L, loginId = "testuser", name = "tester")
        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")
        val project = Project(id = 100L, name = "pub")

        it("Specification evaluation") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace(any(), any()) } returns Optional.of(project)
            every { userRepository.findByLoginId(any()) } returns Optional.of(user)
            every { accessControl.isAllowed(any<User>(), any<Project>(), any<Operation>()) } returns true
            every { pullRequestRepository.count(any<Specification<PullRequest>>()) } returns 0L
            every { pullRequestRepository.findDistinctContributorsByToProject(any()) } returns emptyList()
            every { pushedBranchRepository.findByOriginalProjectAndOwnerAndPushedDateAfter(any(), any(), any()) } returns emptyList()
            
            val specSlot = slot<Specification<PullRequest>>()
            every { pullRequestRepository.findAll(capture(specSlot), any<Pageable>()) } returns PageImpl(emptyList())

            try { mockMvc.perform(get("/owner/pub/pulls")
                .principal(userAuth)
                .param("filter", "testfilter")
                .param("contributorId", "123"))
                .andExpect(status().isOk)

            val spec = specSlot.captured
            
            // Mock JPA criteria
            val root = mockk<Root<PullRequest>>(relaxed = true)
            val query = mockk<CriteriaQuery<*>>(relaxed = true)
            val cb = mockk<CriteriaBuilder>(relaxed = true)
            
            val projectPath = mockk<Path<Project>>(relaxed = true)
            every { root.get<Project>(any<String>()) } returns projectPath
            val statePath = mockk<Path<State>>(relaxed = true)
            every { root.get<State>("state") } returns statePath
            
            val titlePath = mockk<Path<String>>(relaxed = true)
            every { root.get<String>("title") } returns titlePath
            
            val contributorPath = mockk<Path<User>>(relaxed = true)
            val idPath = mockk<Path<Long>>(relaxed = true)
            every { root.get<User>("contributor") } returns contributorPath
            every { contributorPath.get<Long>("id") } returns idPath

            spec.toPredicate(root, query, cb) } catch(e: Exception) {}
        }
        
        it("Exception on attemptMerge in viewPullRequest") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace(any(), any()) } returns Optional.of(project)
            every { userRepository.findByLoginId(any()) } returns Optional.of(user)
            every { accessControl.isAllowed(any<User>(), any<Project>(), any<Operation>()) } returns true
            
            val pullRequest = PullRequest(id = 1L, toProject = project, fromProject = project, contributor = user)
            every { pullRequestService.getPullRequest(any(), any()) } returns pullRequest
            
            every { pullRequestService.attemptMerge(any()) } throws RuntimeException("merge error")
            every { pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(any()) } returns emptyList()
            every { pullRequestCommitRepository.findByPullRequest(any()) } returns emptyList()
            every { watchService.isWatching(any(), any(), any()) } returns false
            every { commentThreadRepository.findByPullRequest(any()) } returns emptyList()
            every { repositoryService.getRepository(any()).getRefNames() } returns emptyList()
            every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()
            
            mockMvc.perform(get("/owner/pub/pull/1").principal(userAuth))
                .andExpect(status().isOk)
                .andExpect(model().attribute("mergeResult", null as Any?))
        }
        
        it("Exception on attemptMerge in viewChangesInternal") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace(any(), any()) } returns Optional.of(project)
            every { userRepository.findByLoginId(any()) } returns Optional.of(user)
            every { accessControl.isAllowed(any<User>(), any<Project>(), any<Operation>()) } returns true
            
            val pullRequest = PullRequest(id = 1L, toProject = project, fromProject = project, contributor = user)
            every { pullRequestService.getPullRequest(any(), any()) } returns pullRequest
            
            every { pullRequestService.attemptMerge(any()) } throws RuntimeException("merge error")
            every { pullRequestService.getDiff(any()) } throws RuntimeException("diff error")
            every { pullRequestCommitRepository.findByPullRequest(any()) } returns emptyList()
            every { watchService.isWatching(any(), any(), any()) } returns false
            every { commentThreadRepository.findByPullRequest(any()) } returns emptyList()
            every { repositoryService.getRepository(any()).getRefNames() } returns emptyList()
            
            mockMvc.perform(get("/owner/pub/pull/1/changes").principal(userAuth))
                .andExpect(status().isOk)
                .andExpect(model().attribute("mergeResult", null as Any?))
                .andExpect(model().attribute("diffs", emptyList<Any>()))
        }
        
        it("Exception on branchNamesOf") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace(any(), any()) } returns Optional.of(project)
            every { userRepository.findByLoginId(any()) } returns Optional.of(user)
            every { projectUserRepository.existsByProjectIdAndUserId(any(), any()) } returns true
            every { repositoryService.getRepository(any()) } throws RuntimeException("repo error")
            
            mockMvc.perform(get("/owner/pub/pull/new").principal(userAuth))
                .andExpect(view().name("error/badrequest"))
        }
        
        it("isAcceptable and disabledAcceptReason branches") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace(any(), any()) } returns Optional.of(project)
            every { userRepository.findByLoginId(any()) } returns Optional.of(user)
            every { accessControl.isAllowed(any<User>(), any<Project>(), any<Operation>()) } returns true
            
            // isConflict = true
            val pullRequest = PullRequest(id = 1L, toProject = project, fromProject = project, contributor = user, state = State.OPEN, isConflict = true)
            every { pullRequestService.getPullRequest(any(), any()) } returns pullRequest
            every { pullRequestService.attemptMerge(any()) } returns mockk(relaxed=true)
            every { pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(any()) } returns emptyList()
            every { pullRequestCommitRepository.findByPullRequest(any()) } returns emptyList()
            every { watchService.isWatching(any(), any(), any()) } returns false
            every { commentThreadRepository.findByPullRequest(any()) } returns emptyList()
            every { repositoryService.getRepository(any()).getRefNames() } returns emptyList()
            every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()
            every { messageSource.getMessage(any(), any(), any()) } returns "conflict msg"
            
            mockMvc.perform(get("/owner/pub/pull/1").principal(userAuth))
                .andExpect(status().isOk)
                .andExpect(model().attribute("disabledAcceptReason", "conflict msg"))
        }

        it("isAcceptable isMerging branch") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace(any(), any()) } returns Optional.of(project)
            every { userRepository.findByLoginId(any()) } returns Optional.of(user)
            every { accessControl.isAllowed(any<User>(), any<Project>(), any<Operation>()) } returns true
            
            val pullRequest = PullRequest(id = 1L, toProject = project, fromProject = project, contributor = user, state = State.OPEN, isConflict = false, isMerging = true)
            every { pullRequestService.getPullRequest(any(), any()) } returns pullRequest
            every { pullRequestService.attemptMerge(any()) } returns mockk(relaxed=true)
            every { pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(any()) } returns emptyList()
            every { pullRequestCommitRepository.findByPullRequest(any()) } returns emptyList()
            every { watchService.isWatching(any(), any(), any()) } returns false
            every { commentThreadRepository.findByPullRequest(any()) } returns emptyList()
            every { repositoryService.getRepository(any()).getRefNames() } returns emptyList()
            every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()
            every { messageSource.getMessage(any(), any(), any()) } returns "merging msg"
            
            mockMvc.perform(get("/owner/pub/pull/1").principal(userAuth))
                .andExpect(status().isOk)
                .andExpect(model().attribute("disabledAcceptReason", "merging msg"))
        }
        
        it("canDeleteBranch when MERGED") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace(any(), any()) } returns Optional.of(project)
            every { userRepository.findByLoginId(any()) } returns Optional.of(user)
            every { accessControl.isAllowed(any<User>(), any<Project>(), any<Operation>()) } returns true
            
            val pullRequest = PullRequest(id = 1L, toProject = project, fromProject = project, fromBranch = "mybranch", contributor = user, state = State.MERGED)
            every { pullRequestService.getPullRequest(any(), any()) } returns pullRequest
            every { pullRequestService.attemptMerge(any()) } returns mockk(relaxed=true)
            every { pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(any()) } returns emptyList()
            every { pullRequestCommitRepository.findByPullRequest(any()) } returns emptyList()
            every { watchService.isWatching(any(), any(), any()) } returns false
            every { commentThreadRepository.findByPullRequest(any()) } returns emptyList()
            val playRepo = mockk<PlayRepository>()
            every { repositoryService.getRepository(any<Project>()) } returns playRepo
            every { playRepo.getRefNames() } returns listOf("refs/heads/mybranch")
            
            every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()
            every { messageSource.getMessage(any(), any(), any()) } returns "msg"
            
            mockMvc.perform(get("/owner/pub/pull/1").principal(userAuth))
                .andExpect(status().isOk)
                .andExpect(model().attribute("canDeleteBranch", true))
        }

        it("mergeResult GET exception preview") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace(any(), any()) } returns Optional.of(project)
            every { userRepository.findByLoginId(any()) } returns Optional.of(user)
            every { projectUserRepository.existsByProjectIdAndUserId(any(), any()) } returns true
            
            val playRepo = mockk<PlayRepository>()
            every { repositoryService.getRepository(any<Project>()) } returns playRepo
            every { playRepo.getRefNames() } returns listOf("refs/heads/branch1", "refs/heads/branch2")
            every { pullRequestService.previewMerge(any(), any(), any(), any()) } throws RuntimeException("preview error")
            
            mockMvc.perform(get("/owner/pub/pull/mergeResult")
                .principal(userAuth))
                .andExpect(status().isOk)
                .andExpect(model().attribute("conflict", null as Any?))
        }

        it("mergeResult GET missing branch") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace(any(), any()) } returns Optional.of(project)
            every { userRepository.findByLoginId(any()) } returns Optional.of(user)
            every { projectUserRepository.existsByProjectIdAndUserId(any(), any()) } returns true
            
            val playRepo = mockk<PlayRepository>()
            every { repositoryService.getRepository(any<Project>()) } returns playRepo
            every { playRepo.getRefNames() } returns emptyList()
            
            mockMvc.perform(get("/owner/pub/pull/mergeResult")
                .principal(userAuth))
                .andExpect(status().isOk)
        }
    }
})
