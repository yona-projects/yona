package com.github.yonaprojects.yona.domain.support

import com.github.yonaprojects.yona.domain.enumeration.SearchType
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.milestone.Milestone
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.issue.IssueCommentRepository
import com.github.yonaprojects.yona.domain.board.PostingCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

class SearchServiceSpec : DescribeSpec({
    val userRepository = mockk<UserRepository>()
    val projectRepository = mockk<ProjectRepository>()
    val issueRepository = mockk<IssueRepository>()
    val postingRepository = mockk<PostingRepository>()
    val milestoneRepository = mockk<MilestoneRepository>()
    val issueCommentRepository = mockk<IssueCommentRepository>()
    val postingCommentRepository = mockk<PostingCommentRepository>()
    val reviewCommentRepository = mockk<ReviewCommentRepository>()
    val pullRequestRepository = mockk<PullRequestRepository>()

    val searchService = SearchServiceImpl(
        userRepository,
        projectRepository,
        issueRepository,
        postingRepository,
        milestoneRepository,
        issueCommentRepository,
        postingCommentRepository,
        reviewCommentRepository,
        pullRequestRepository
    )

    describe("SearchService 비즈니스 로직 테스트") {
        val loginUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val pageable = PageRequest.of(0, 20)

        it("전역 검색 시 사용자가 접근 가능한 프로젝트의 이슈들을 검색해야 한다") {
            every { projectRepository.findAllowedProjectIdsForUser(10L) } returns listOf(1L, 2L)
            
            // counts mock
            every { userRepository.countSearchUsers("%test%") } returns 0
            every { projectRepository.countSearchProjects(listOf(1L, 2L), "%test%") } returns 0
            every { issueRepository.countSearchIssues(listOf(1L, 2L), "%test%", 10L) } returns 1
            every { postingRepository.countSearchPostings(listOf(1L, 2L), "%test%", 10L) } returns 0
            every { milestoneRepository.countSearchMilestones(listOf(1L, 2L), "%test%") } returns 0
            every { issueCommentRepository.countSearchIssueComments(listOf(1L, 2L), "%test%", 10L) } returns 0
            every { postingCommentRepository.countSearchPostingComments(listOf(1L, 2L), "%test%", 10L) } returns 0
            every { reviewCommentRepository.countSearchReviewComments(listOf(1L, 2L), "%test%", 10L) } returns 0
            every { pullRequestRepository.countSearchPullRequests(listOf(1L, 2L), "%test%", 10L) } returns 0

            // search result mock
            val expectedIssues: Page<Issue> = PageImpl(emptyList())
            every { issueRepository.searchIssues(listOf(1L, 2L), "%test%", 10L, pageable) } returns expectedIssues

            val result = searchService.searchInAll("test", SearchType.AUTO, loginUser, pageable)

            result.issuesCount shouldBe 1
            result.searchType shouldBe SearchType.ISSUE
        }

        it("단일 프로젝트 검색 시 해당 프로젝트 내부의 게시글을 검색해야 한다") {
            val project = Project(id = 1L, name = "TestProj", owner = "owner")

            // counts mock
            every { userRepository.countSearchUsers("%board%") } returns 0
            every { issueRepository.countSearchIssuesInProject(project, "%board%") } returns 0
            every { postingRepository.countSearchPostingsInProject(project, "%board%") } returns 2
            every { milestoneRepository.countSearchMilestonesInProject(project, "%board%") } returns 0
            every { issueCommentRepository.countSearchIssueCommentsInProject(project, "%board%") } returns 0
            every { postingCommentRepository.countSearchPostingCommentsInProject(project, "%board%") } returns 0
            every { reviewCommentRepository.countSearchReviewCommentsInProject(project, "%board%") } returns 0
            every { pullRequestRepository.countSearchPullRequestsInProject(project, "%board%") } returns 0

            // search result mock
            val expectedPosts: Page<Posting> = PageImpl(emptyList())
            every { postingRepository.searchPostingsInProject(project, "%board%", pageable) } returns expectedPosts

            val result = searchService.searchInAProject("board", SearchType.POST, loginUser, project, pageable)

            result.postsCount shouldBe 2
            result.searchType shouldBe SearchType.POST
        }

        it("전역 검색 시 프로젝트 검색 타입일 경우 프로젝트 리포지토리를 호출해야 한다") {
            every { projectRepository.findAllowedProjectIdsForUser(10L) } returns listOf(1L, 2L)
            every { userRepository.countSearchUsers("%test%") } returns 0
            every { projectRepository.countSearchProjects(listOf(1L, 2L), "%test%") } returns 3
            every { issueRepository.countSearchIssues(listOf(1L, 2L), "%test%", 10L) } returns 0
            every { postingRepository.countSearchPostings(listOf(1L, 2L), "%test%", 10L) } returns 0
            every { milestoneRepository.countSearchMilestones(listOf(1L, 2L), "%test%") } returns 0
            every { issueCommentRepository.countSearchIssueComments(listOf(1L, 2L), "%test%", 10L) } returns 0
            every { postingCommentRepository.countSearchPostingComments(listOf(1L, 2L), "%test%", 10L) } returns 0
            every { reviewCommentRepository.countSearchReviewComments(listOf(1L, 2L), "%test%", 10L) } returns 0
            every { pullRequestRepository.countSearchPullRequests(listOf(1L, 2L), "%test%", 10L) } returns 0

            val expectedProjects: Page<Project> = PageImpl(emptyList())
            every { projectRepository.searchProjects(listOf(1L, 2L), "%test%", pageable) } returns expectedProjects

            val result = searchService.searchInAll("test", SearchType.PROJECT, loginUser, pageable)

            result.projectsCount shouldBe 3
            result.searchType shouldBe SearchType.PROJECT
        }

        it("전역 검색 시 사용자 검색 타입일 경우 사용자 리포지토리를 호출해야 한다") {
            every { projectRepository.findAllowedProjectIdsForUser(10L) } returns listOf(1L, 2L)
            every { userRepository.countSearchUsers("%test%") } returns 5
            every { projectRepository.countSearchProjects(listOf(1L, 2L), "%test%") } returns 0
            every { issueRepository.countSearchIssues(listOf(1L, 2L), "%test%", 10L) } returns 0
            every { postingRepository.countSearchPostings(listOf(1L, 2L), "%test%", 10L) } returns 0
            every { milestoneRepository.countSearchMilestones(listOf(1L, 2L), "%test%") } returns 0
            every { issueCommentRepository.countSearchIssueComments(listOf(1L, 2L), "%test%", 10L) } returns 0
            every { postingCommentRepository.countSearchPostingComments(listOf(1L, 2L), "%test%", 10L) } returns 0
            every { reviewCommentRepository.countSearchReviewComments(listOf(1L, 2L), "%test%", 10L) } returns 0
            every { pullRequestRepository.countSearchPullRequests(listOf(1L, 2L), "%test%", 10L) } returns 0

            val expectedUsers: Page<User> = PageImpl(emptyList())
            every { userRepository.searchUsers("%test%", pageable) } returns expectedUsers

            val result = searchService.searchInAll("test", SearchType.USER, loginUser, pageable)

            result.usersCount shouldBe 5
            result.searchType shouldBe SearchType.USER
        }

        it("전역 검색 시 마일스톤 검색 타입일 경우 마일스톤 리포지토리를 호출해야 한다") {
            every { projectRepository.findAllowedProjectIdsForUser(10L) } returns listOf(1L, 2L)
            every { userRepository.countSearchUsers("%test%") } returns 0
            every { projectRepository.countSearchProjects(listOf(1L, 2L), "%test%") } returns 0
            every { issueRepository.countSearchIssues(listOf(1L, 2L), "%test%", 10L) } returns 0
            every { postingRepository.countSearchPostings(listOf(1L, 2L), "%test%", 10L) } returns 0
            every { milestoneRepository.countSearchMilestones(listOf(1L, 2L), "%test%") } returns 1
            every { issueCommentRepository.countSearchIssueComments(listOf(1L, 2L), "%test%", 10L) } returns 0
            every { postingCommentRepository.countSearchPostingComments(listOf(1L, 2L), "%test%", 10L) } returns 0
            every { reviewCommentRepository.countSearchReviewComments(listOf(1L, 2L), "%test%", 10L) } returns 0
            every { pullRequestRepository.countSearchPullRequests(listOf(1L, 2L), "%test%", 10L) } returns 0

            val expectedMilestones: Page<Milestone> = PageImpl(emptyList())
            every { milestoneRepository.searchMilestones(listOf(1L, 2L), "%test%", pageable) } returns expectedMilestones

            val result = searchService.searchInAll("test", SearchType.MILESTONE, loginUser, pageable)

            result.milestonesCount shouldBe 1
            result.searchType shouldBe SearchType.MILESTONE
        }

        // yona-wiki P3-02 Step8.6 항목3(2026-09-01, 우선순위 3위) — `yona search prs` 대응.
        it("전역 검색 시 PR 검색 타입일 경우 PR 리포지토리를 호출해야 한다") {
            every { projectRepository.findAllowedProjectIdsForUser(10L) } returns listOf(1L, 2L)
            every { userRepository.countSearchUsers("%pr1%") } returns 0
            every { projectRepository.countSearchProjects(listOf(1L, 2L), "%pr1%") } returns 0
            every { issueRepository.countSearchIssues(listOf(1L, 2L), "%pr1%", 10L) } returns 0
            every { postingRepository.countSearchPostings(listOf(1L, 2L), "%pr1%", 10L) } returns 0
            every { milestoneRepository.countSearchMilestones(listOf(1L, 2L), "%pr1%") } returns 0
            every { issueCommentRepository.countSearchIssueComments(listOf(1L, 2L), "%pr1%", 10L) } returns 0
            every { postingCommentRepository.countSearchPostingComments(listOf(1L, 2L), "%pr1%", 10L) } returns 0
            every { reviewCommentRepository.countSearchReviewComments(listOf(1L, 2L), "%pr1%", 10L) } returns 0
            every { pullRequestRepository.countSearchPullRequests(listOf(1L, 2L), "%pr1%", 10L) } returns 1

            val expectedPullRequests: Page<com.github.yonaprojects.yona.domain.pullrequest.PullRequest> = PageImpl(emptyList())
            every { pullRequestRepository.searchPullRequests(listOf(1L, 2L), "%pr1%", 10L, pageable) } returns expectedPullRequests

            val result = searchService.searchInAll("pr1", SearchType.PULL_REQUEST, loginUser, pageable)

            result.pullRequestsCount shouldBe 1
            result.searchType shouldBe SearchType.PULL_REQUEST
        }
    }

    // yona Search.projectsEL() 대응 (P0-23) — HIDE_PROJECT_LISTING이 켜져 있을 때 PUBLIC 프로젝트가
    // 검색 대상에서 제외되는지 검증한다.
    describe("HIDE_PROJECT_LISTING=true일 때") {
        val hiddenSearchService = SearchServiceImpl(
            userRepository,
            projectRepository,
            issueRepository,
            postingRepository,
            milestoneRepository,
            issueCommentRepository,
            postingCommentRepository,
            reviewCommentRepository,
            pullRequestRepository,
            hideProjectListing = true
        )
        val pageable = PageRequest.of(0, 20)

        it("익명 사용자는 검색 결과가 비어 있어야 한다(허용 프로젝트 조회 자체를 안 함)") {
            val result = hiddenSearchService.searchInAll("test", SearchType.AUTO, null, pageable)

            result.issuesCount shouldBe 0
            result.projectsCount shouldBe 0
        }

        it("로그인 사용자는 findAllowedProjectIdsForUserExcludingPublic으로 PUBLIC을 제외한 목록만 조회해야 한다") {
            val loginUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
            every { projectRepository.findAllowedProjectIdsForUserExcludingPublic(10L) } returns listOf(3L)
            every { userRepository.countSearchUsers("%test%") } returns 0
            every { projectRepository.countSearchProjects(listOf(3L), "%test%") } returns 0
            every { issueRepository.countSearchIssues(listOf(3L), "%test%", 10L) } returns 1
            every { postingRepository.countSearchPostings(listOf(3L), "%test%", 10L) } returns 0
            every { milestoneRepository.countSearchMilestones(listOf(3L), "%test%") } returns 0
            every { issueCommentRepository.countSearchIssueComments(listOf(3L), "%test%", 10L) } returns 0
            every { postingCommentRepository.countSearchPostingComments(listOf(3L), "%test%", 10L) } returns 0
            every { reviewCommentRepository.countSearchReviewComments(listOf(3L), "%test%", 10L) } returns 0
            every { pullRequestRepository.countSearchPullRequests(listOf(3L), "%test%", 10L) } returns 0
            val expectedIssues: Page<Issue> = PageImpl(emptyList())
            every { issueRepository.searchIssues(listOf(3L), "%test%", 10L, pageable) } returns expectedIssues

            val result = hiddenSearchService.searchInAll("test", SearchType.AUTO, loginUser, pageable)

            result.issuesCount shouldBe 1
        }
    }

    // searchInAll의 나머지 분기(빈 결과, 익명/준영속 사용자, 나머지 SearchType, else 분기)를 보강한다.
    describe("searchInAll 추가 분기 커버리지") {
        val loginUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val pageable = PageRequest.of(0, 20)

        it("허용된 프로젝트가 없으면 리포지토리 조회 없이 빈 SearchResult를 반환해야 한다") {
            every { projectRepository.findAllowedProjectIdsForUser(10L) } returns emptyList()

            val result = searchService.searchInAll("empty1", SearchType.AUTO, loginUser, pageable)

            result.keyword shouldBe "empty1"
            result.searchType shouldBe SearchType.AUTO
            result.issuesCount shouldBe 0
        }

        it("익명 사용자이고 HIDE_PROJECT_LISTING이 꺼져 있으면 공개 프로젝트 ID로 검색해야 한다") {
            every { projectRepository.findPublicProjectIds() } returns listOf(21L, 22L)
            every { userRepository.countSearchUsers("%anon1%") } returns 0
            every { projectRepository.countSearchProjects(listOf(21L, 22L), "%anon1%") } returns 0
            every { issueRepository.countSearchIssues(listOf(21L, 22L), "%anon1%", null) } returns 1
            every { postingRepository.countSearchPostings(listOf(21L, 22L), "%anon1%", null) } returns 0
            every { milestoneRepository.countSearchMilestones(listOf(21L, 22L), "%anon1%") } returns 0
            every { issueCommentRepository.countSearchIssueComments(listOf(21L, 22L), "%anon1%", null) } returns 0
            every { postingCommentRepository.countSearchPostingComments(listOf(21L, 22L), "%anon1%", null) } returns 0
            every { reviewCommentRepository.countSearchReviewComments(listOf(21L, 22L), "%anon1%", null) } returns 0
            every { pullRequestRepository.countSearchPullRequests(listOf(21L, 22L), "%anon1%", null) } returns 0
            every { issueRepository.searchIssues(listOf(21L, 22L), "%anon1%", null, pageable) } returns Page.empty()

            val result = searchService.searchInAll("anon1", SearchType.AUTO, null, pageable)

            result.issuesCount shouldBe 1
            result.searchType shouldBe SearchType.ISSUE
        }

        it("user.id가 null인 준영속 사용자는 익명처럼 취급되어 공개 프로젝트 ID로 조회해야 한다") {
            val transientUser = User(id = null, loginId = "ghost", name = "유령유저")
            every { projectRepository.findPublicProjectIds() } returns listOf(23L)
            every { userRepository.countSearchUsers("%ghost1%") } returns 0
            every { projectRepository.countSearchProjects(listOf(23L), "%ghost1%") } returns 0
            every { issueRepository.countSearchIssues(listOf(23L), "%ghost1%", null) } returns 0
            every { postingRepository.countSearchPostings(listOf(23L), "%ghost1%", null) } returns 0
            every { milestoneRepository.countSearchMilestones(listOf(23L), "%ghost1%") } returns 0
            every { issueCommentRepository.countSearchIssueComments(listOf(23L), "%ghost1%", null) } returns 0
            every { postingCommentRepository.countSearchPostingComments(listOf(23L), "%ghost1%", null) } returns 0
            every { reviewCommentRepository.countSearchReviewComments(listOf(23L), "%ghost1%", null) } returns 1
            every { pullRequestRepository.countSearchPullRequests(listOf(23L), "%ghost1%", null) } returns 0
            every { reviewCommentRepository.searchReviewComments(listOf(23L), "%ghost1%", null, pageable) } returns Page.empty()

            val result = searchService.searchInAll("ghost1", SearchType.AUTO, transientUser, pageable)

            result.reviewsCount shouldBe 1
            result.searchType shouldBe SearchType.REVIEW
        }

        it("전역 검색 시 게시글 검색 타입일 경우 게시글 리포지토리를 호출해야 한다") {
            every { projectRepository.findAllowedProjectIdsForUser(10L) } returns listOf(1L, 2L)
            every { userRepository.countSearchUsers("%postkw%") } returns 0
            every { projectRepository.countSearchProjects(listOf(1L, 2L), "%postkw%") } returns 0
            every { issueRepository.countSearchIssues(listOf(1L, 2L), "%postkw%", 10L) } returns 0
            every { postingRepository.countSearchPostings(listOf(1L, 2L), "%postkw%", 10L) } returns 4
            every { milestoneRepository.countSearchMilestones(listOf(1L, 2L), "%postkw%") } returns 0
            every { issueCommentRepository.countSearchIssueComments(listOf(1L, 2L), "%postkw%", 10L) } returns 0
            every { postingCommentRepository.countSearchPostingComments(listOf(1L, 2L), "%postkw%", 10L) } returns 0
            every { reviewCommentRepository.countSearchReviewComments(listOf(1L, 2L), "%postkw%", 10L) } returns 0
            every { pullRequestRepository.countSearchPullRequests(listOf(1L, 2L), "%postkw%", 10L) } returns 0
            every { postingRepository.searchPostings(listOf(1L, 2L), "%postkw%", 10L, pageable) } returns Page.empty()

            val result = searchService.searchInAll("postkw", SearchType.AUTO, loginUser, pageable)

            result.postsCount shouldBe 4
            result.searchType shouldBe SearchType.POST
        }

        it("전역 검색 시 이슈 댓글 검색 타입일 경우 이슈 댓글 리포지토리를 호출해야 한다") {
            every { projectRepository.findAllowedProjectIdsForUser(10L) } returns listOf(1L, 2L)
            every { userRepository.countSearchUsers("%icmt%") } returns 0
            every { projectRepository.countSearchProjects(listOf(1L, 2L), "%icmt%") } returns 0
            every { issueRepository.countSearchIssues(listOf(1L, 2L), "%icmt%", 10L) } returns 0
            every { postingRepository.countSearchPostings(listOf(1L, 2L), "%icmt%", 10L) } returns 0
            every { milestoneRepository.countSearchMilestones(listOf(1L, 2L), "%icmt%") } returns 0
            every { issueCommentRepository.countSearchIssueComments(listOf(1L, 2L), "%icmt%", 10L) } returns 2
            every { postingCommentRepository.countSearchPostingComments(listOf(1L, 2L), "%icmt%", 10L) } returns 0
            every { reviewCommentRepository.countSearchReviewComments(listOf(1L, 2L), "%icmt%", 10L) } returns 0
            every { pullRequestRepository.countSearchPullRequests(listOf(1L, 2L), "%icmt%", 10L) } returns 0
            every { issueCommentRepository.searchIssueComments(listOf(1L, 2L), "%icmt%", 10L, pageable) } returns Page.empty()

            val result = searchService.searchInAll("icmt", SearchType.AUTO, loginUser, pageable)

            result.issueCommentsCount shouldBe 2
            result.searchType shouldBe SearchType.ISSUE_COMMENT
        }

        it("전역 검색 시 게시글 댓글 검색 타입일 경우 게시글 댓글 리포지토리를 호출해야 한다") {
            every { projectRepository.findAllowedProjectIdsForUser(10L) } returns listOf(1L, 2L)
            every { userRepository.countSearchUsers("%pcmt%") } returns 0
            every { projectRepository.countSearchProjects(listOf(1L, 2L), "%pcmt%") } returns 0
            every { issueRepository.countSearchIssues(listOf(1L, 2L), "%pcmt%", 10L) } returns 0
            every { postingRepository.countSearchPostings(listOf(1L, 2L), "%pcmt%", 10L) } returns 0
            every { milestoneRepository.countSearchMilestones(listOf(1L, 2L), "%pcmt%") } returns 0
            every { issueCommentRepository.countSearchIssueComments(listOf(1L, 2L), "%pcmt%", 10L) } returns 0
            every { postingCommentRepository.countSearchPostingComments(listOf(1L, 2L), "%pcmt%", 10L) } returns 3
            every { reviewCommentRepository.countSearchReviewComments(listOf(1L, 2L), "%pcmt%", 10L) } returns 0
            every { pullRequestRepository.countSearchPullRequests(listOf(1L, 2L), "%pcmt%", 10L) } returns 0
            every { postingCommentRepository.searchPostingComments(listOf(1L, 2L), "%pcmt%", 10L, pageable) } returns Page.empty()

            val result = searchService.searchInAll("pcmt", SearchType.AUTO, loginUser, pageable)

            result.postCommentsCount shouldBe 3
            result.searchType shouldBe SearchType.POST_COMMENT
        }

        it("전역 검색 시 리뷰 검색 타입일 경우 리뷰 리포지토리를 호출해야 한다") {
            every { projectRepository.findAllowedProjectIdsForUser(10L) } returns listOf(1L, 2L)
            every { userRepository.countSearchUsers("%rvw%") } returns 0
            every { projectRepository.countSearchProjects(listOf(1L, 2L), "%rvw%") } returns 0
            every { issueRepository.countSearchIssues(listOf(1L, 2L), "%rvw%", 10L) } returns 0
            every { postingRepository.countSearchPostings(listOf(1L, 2L), "%rvw%", 10L) } returns 0
            every { milestoneRepository.countSearchMilestones(listOf(1L, 2L), "%rvw%") } returns 0
            every { issueCommentRepository.countSearchIssueComments(listOf(1L, 2L), "%rvw%", 10L) } returns 0
            every { postingCommentRepository.countSearchPostingComments(listOf(1L, 2L), "%rvw%", 10L) } returns 0
            every { reviewCommentRepository.countSearchReviewComments(listOf(1L, 2L), "%rvw%", 10L) } returns 5
            every { pullRequestRepository.countSearchPullRequests(listOf(1L, 2L), "%rvw%", 10L) } returns 0
            every { reviewCommentRepository.searchReviewComments(listOf(1L, 2L), "%rvw%", 10L, pageable) } returns Page.empty()

            val result = searchService.searchInAll("rvw", SearchType.AUTO, loginUser, pageable)

            result.reviewsCount shouldBe 5
            result.searchType shouldBe SearchType.REVIEW
        }

        it("검색 타입을 NA로 명시하면 when절의 else 분기를 타서 결과 목록을 채우지 않아야 한다") {
            every { projectRepository.findAllowedProjectIdsForUser(10L) } returns listOf(1L, 2L)
            every { userRepository.countSearchUsers("%naval%") } returns 0
            every { projectRepository.countSearchProjects(listOf(1L, 2L), "%naval%") } returns 0
            every { issueRepository.countSearchIssues(listOf(1L, 2L), "%naval%", 10L) } returns 0
            every { postingRepository.countSearchPostings(listOf(1L, 2L), "%naval%", 10L) } returns 0
            every { milestoneRepository.countSearchMilestones(listOf(1L, 2L), "%naval%") } returns 0
            every { issueCommentRepository.countSearchIssueComments(listOf(1L, 2L), "%naval%", 10L) } returns 0
            every { postingCommentRepository.countSearchPostingComments(listOf(1L, 2L), "%naval%", 10L) } returns 0
            every { reviewCommentRepository.countSearchReviewComments(listOf(1L, 2L), "%naval%", 10L) } returns 0
            every { pullRequestRepository.countSearchPullRequests(listOf(1L, 2L), "%naval%", 10L) } returns 0

            val result = searchService.searchInAll("naval", SearchType.NA, loginUser, pageable)

            result.searchType shouldBe SearchType.NA
            result.issues.totalElements shouldBe 0
        }

        // when절 각 case 안의 `user?.id` 세이프콜은 케이스별로 별도 분기를 만든다 — 익명(user=null) 검색이
        // POST/ISSUE_COMMENT/POST_COMMENT/REVIEW 타입으로 귀결되는 경우를 각각 별도로 검증해야
        // 해당 4개 라인의 널(user=null) 분기가 커버된다(위의 ghost1 테스트는 user 참조 자체는 null이
        // 아니라서 이 분기를 타지 않는다).
        it("익명 사용자의 전역 검색이 게시글 타입으로 귀결되면 user?.id의 null 분기를 타야 한다") {
            every { projectRepository.findPublicProjectIds() } returns listOf(31L)
            every { userRepository.countSearchUsers("%apostkw%") } returns 0
            every { projectRepository.countSearchProjects(listOf(31L), "%apostkw%") } returns 0
            every { issueRepository.countSearchIssues(listOf(31L), "%apostkw%", null) } returns 0
            every { postingRepository.countSearchPostings(listOf(31L), "%apostkw%", null) } returns 1
            every { milestoneRepository.countSearchMilestones(listOf(31L), "%apostkw%") } returns 0
            every { issueCommentRepository.countSearchIssueComments(listOf(31L), "%apostkw%", null) } returns 0
            every { postingCommentRepository.countSearchPostingComments(listOf(31L), "%apostkw%", null) } returns 0
            every { reviewCommentRepository.countSearchReviewComments(listOf(31L), "%apostkw%", null) } returns 0
            every { pullRequestRepository.countSearchPullRequests(listOf(31L), "%apostkw%", null) } returns 0
            every { postingRepository.searchPostings(listOf(31L), "%apostkw%", null, pageable) } returns Page.empty()

            val result = searchService.searchInAll("apostkw", SearchType.AUTO, null, pageable)

            result.postsCount shouldBe 1
            result.searchType shouldBe SearchType.POST
        }

        it("익명 사용자의 전역 검색이 이슈 댓글 타입으로 귀결되면 user?.id의 null 분기를 타야 한다") {
            every { projectRepository.findPublicProjectIds() } returns listOf(32L)
            every { userRepository.countSearchUsers("%aicmt%") } returns 0
            every { projectRepository.countSearchProjects(listOf(32L), "%aicmt%") } returns 0
            every { issueRepository.countSearchIssues(listOf(32L), "%aicmt%", null) } returns 0
            every { postingRepository.countSearchPostings(listOf(32L), "%aicmt%", null) } returns 0
            every { milestoneRepository.countSearchMilestones(listOf(32L), "%aicmt%") } returns 0
            every { issueCommentRepository.countSearchIssueComments(listOf(32L), "%aicmt%", null) } returns 1
            every { postingCommentRepository.countSearchPostingComments(listOf(32L), "%aicmt%", null) } returns 0
            every { reviewCommentRepository.countSearchReviewComments(listOf(32L), "%aicmt%", null) } returns 0
            every { pullRequestRepository.countSearchPullRequests(listOf(32L), "%aicmt%", null) } returns 0
            every { issueCommentRepository.searchIssueComments(listOf(32L), "%aicmt%", null, pageable) } returns Page.empty()

            val result = searchService.searchInAll("aicmt", SearchType.AUTO, null, pageable)

            result.issueCommentsCount shouldBe 1
            result.searchType shouldBe SearchType.ISSUE_COMMENT
        }

        it("익명 사용자의 전역 검색이 게시글 댓글 타입으로 귀결되면 user?.id의 null 분기를 타야 한다") {
            every { projectRepository.findPublicProjectIds() } returns listOf(33L)
            every { userRepository.countSearchUsers("%apcmt%") } returns 0
            every { projectRepository.countSearchProjects(listOf(33L), "%apcmt%") } returns 0
            every { issueRepository.countSearchIssues(listOf(33L), "%apcmt%", null) } returns 0
            every { postingRepository.countSearchPostings(listOf(33L), "%apcmt%", null) } returns 0
            every { milestoneRepository.countSearchMilestones(listOf(33L), "%apcmt%") } returns 0
            every { issueCommentRepository.countSearchIssueComments(listOf(33L), "%apcmt%", null) } returns 0
            every { postingCommentRepository.countSearchPostingComments(listOf(33L), "%apcmt%", null) } returns 1
            every { reviewCommentRepository.countSearchReviewComments(listOf(33L), "%apcmt%", null) } returns 0
            every { pullRequestRepository.countSearchPullRequests(listOf(33L), "%apcmt%", null) } returns 0
            every { postingCommentRepository.searchPostingComments(listOf(33L), "%apcmt%", null, pageable) } returns Page.empty()

            val result = searchService.searchInAll("apcmt", SearchType.AUTO, null, pageable)

            result.postCommentsCount shouldBe 1
            result.searchType shouldBe SearchType.POST_COMMENT
        }

        it("익명 사용자의 전역 검색이 리뷰 타입으로 귀결되면 user?.id의 null 분기를 타야 한다") {
            every { projectRepository.findPublicProjectIds() } returns listOf(34L)
            every { userRepository.countSearchUsers("%arvw%") } returns 0
            every { projectRepository.countSearchProjects(listOf(34L), "%arvw%") } returns 0
            every { issueRepository.countSearchIssues(listOf(34L), "%arvw%", null) } returns 0
            every { postingRepository.countSearchPostings(listOf(34L), "%arvw%", null) } returns 0
            every { milestoneRepository.countSearchMilestones(listOf(34L), "%arvw%") } returns 0
            every { issueCommentRepository.countSearchIssueComments(listOf(34L), "%arvw%", null) } returns 0
            every { postingCommentRepository.countSearchPostingComments(listOf(34L), "%arvw%", null) } returns 0
            every { reviewCommentRepository.countSearchReviewComments(listOf(34L), "%arvw%", null) } returns 1
            every { pullRequestRepository.countSearchPullRequests(listOf(34L), "%arvw%", null) } returns 0
            every { reviewCommentRepository.searchReviewComments(listOf(34L), "%arvw%", null, pageable) } returns Page.empty()

            val result = searchService.searchInAll("arvw", SearchType.AUTO, null, pageable)

            result.reviewsCount shouldBe 1
            result.searchType shouldBe SearchType.REVIEW
        }
    }

    // searchInAProject의 나머지 SearchType 분기, PROJECT 미지원(else) 분기, AUTO 기본값 폴백을 보강한다.
    describe("searchInAProject 추가 분기 커버리지") {
        val loginUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val pageable = PageRequest.of(0, 20)

        it("프로젝트 내부 검색 시 이슈 검색 타입일 경우 이슈 리포지토리를 호출해야 한다") {
            val project = Project(id = 101L, name = "P101", owner = "owner")
            every { userRepository.countSearchUsers("%pissue%") } returns 0
            every { issueRepository.countSearchIssuesInProject(project, "%pissue%") } returns 1
            every { postingRepository.countSearchPostingsInProject(project, "%pissue%") } returns 0
            every { milestoneRepository.countSearchMilestonesInProject(project, "%pissue%") } returns 0
            every { issueCommentRepository.countSearchIssueCommentsInProject(project, "%pissue%") } returns 0
            every { postingCommentRepository.countSearchPostingCommentsInProject(project, "%pissue%") } returns 0
            every { reviewCommentRepository.countSearchReviewCommentsInProject(project, "%pissue%") } returns 0
            every { pullRequestRepository.countSearchPullRequestsInProject(project, "%pissue%") } returns 0
            every { issueRepository.searchIssuesInProject(project, "%pissue%", pageable) } returns Page.empty()

            val result = searchService.searchInAProject("pissue", SearchType.AUTO, loginUser, project, pageable)

            result.issuesCount shouldBe 1
            result.searchType shouldBe SearchType.ISSUE
        }

        it("프로젝트 내부 검색 시 사용자 검색 타입일 경우 사용자 리포지토리를 호출해야 한다") {
            val project = Project(id = 102L, name = "P102", owner = "owner")
            every { userRepository.countSearchUsers("%puser%") } returns 6
            every { issueRepository.countSearchIssuesInProject(project, "%puser%") } returns 0
            every { postingRepository.countSearchPostingsInProject(project, "%puser%") } returns 0
            every { milestoneRepository.countSearchMilestonesInProject(project, "%puser%") } returns 0
            every { issueCommentRepository.countSearchIssueCommentsInProject(project, "%puser%") } returns 0
            every { postingCommentRepository.countSearchPostingCommentsInProject(project, "%puser%") } returns 0
            every { reviewCommentRepository.countSearchReviewCommentsInProject(project, "%puser%") } returns 0
            every { pullRequestRepository.countSearchPullRequestsInProject(project, "%puser%") } returns 0
            every { userRepository.searchUsers("%puser%", pageable) } returns Page.empty()

            val result = searchService.searchInAProject("puser", SearchType.AUTO, loginUser, project, pageable)

            result.usersCount shouldBe 6
            result.searchType shouldBe SearchType.USER
        }

        it("프로젝트 내부 검색 시 마일스톤 검색 타입일 경우 마일스톤 리포지토리를 호출해야 한다") {
            val project = Project(id = 103L, name = "P103", owner = "owner")
            every { userRepository.countSearchUsers("%pmile%") } returns 0
            every { issueRepository.countSearchIssuesInProject(project, "%pmile%") } returns 0
            every { postingRepository.countSearchPostingsInProject(project, "%pmile%") } returns 0
            every { milestoneRepository.countSearchMilestonesInProject(project, "%pmile%") } returns 7
            every { issueCommentRepository.countSearchIssueCommentsInProject(project, "%pmile%") } returns 0
            every { postingCommentRepository.countSearchPostingCommentsInProject(project, "%pmile%") } returns 0
            every { reviewCommentRepository.countSearchReviewCommentsInProject(project, "%pmile%") } returns 0
            every { pullRequestRepository.countSearchPullRequestsInProject(project, "%pmile%") } returns 0
            every { milestoneRepository.searchMilestonesInProject(project, "%pmile%", pageable) } returns Page.empty()

            val result = searchService.searchInAProject("pmile", SearchType.AUTO, loginUser, project, pageable)

            result.milestonesCount shouldBe 7
            result.searchType shouldBe SearchType.MILESTONE
        }

        it("프로젝트 내부 검색 시 이슈 댓글 검색 타입일 경우 이슈 댓글 리포지토리를 호출해야 한다") {
            val project = Project(id = 104L, name = "P104", owner = "owner")
            every { userRepository.countSearchUsers("%picmt%") } returns 0
            every { issueRepository.countSearchIssuesInProject(project, "%picmt%") } returns 0
            every { postingRepository.countSearchPostingsInProject(project, "%picmt%") } returns 0
            every { milestoneRepository.countSearchMilestonesInProject(project, "%picmt%") } returns 0
            every { issueCommentRepository.countSearchIssueCommentsInProject(project, "%picmt%") } returns 8
            every { postingCommentRepository.countSearchPostingCommentsInProject(project, "%picmt%") } returns 0
            every { reviewCommentRepository.countSearchReviewCommentsInProject(project, "%picmt%") } returns 0
            every { pullRequestRepository.countSearchPullRequestsInProject(project, "%picmt%") } returns 0
            every { issueCommentRepository.searchIssueCommentsInProject(project, "%picmt%", pageable) } returns Page.empty()

            val result = searchService.searchInAProject("picmt", SearchType.AUTO, loginUser, project, pageable)

            result.issueCommentsCount shouldBe 8
            result.searchType shouldBe SearchType.ISSUE_COMMENT
        }

        it("프로젝트 내부 검색 시 게시글 댓글 검색 타입일 경우 게시글 댓글 리포지토리를 호출해야 한다") {
            val project = Project(id = 105L, name = "P105", owner = "owner")
            every { userRepository.countSearchUsers("%ppcmt%") } returns 0
            every { issueRepository.countSearchIssuesInProject(project, "%ppcmt%") } returns 0
            every { postingRepository.countSearchPostingsInProject(project, "%ppcmt%") } returns 0
            every { milestoneRepository.countSearchMilestonesInProject(project, "%ppcmt%") } returns 0
            every { issueCommentRepository.countSearchIssueCommentsInProject(project, "%ppcmt%") } returns 0
            every { postingCommentRepository.countSearchPostingCommentsInProject(project, "%ppcmt%") } returns 9
            every { reviewCommentRepository.countSearchReviewCommentsInProject(project, "%ppcmt%") } returns 0
            every { pullRequestRepository.countSearchPullRequestsInProject(project, "%ppcmt%") } returns 0
            every { postingCommentRepository.searchPostingCommentsInProject(project, "%ppcmt%", pageable) } returns Page.empty()

            val result = searchService.searchInAProject("ppcmt", SearchType.AUTO, loginUser, project, pageable)

            result.postCommentsCount shouldBe 9
            result.searchType shouldBe SearchType.POST_COMMENT
        }

        it("프로젝트 내부 검색 시 리뷰 검색 타입일 경우 리뷰 리포지토리를 호출해야 한다") {
            val project = Project(id = 106L, name = "P106", owner = "owner")
            every { userRepository.countSearchUsers("%prvw%") } returns 0
            every { issueRepository.countSearchIssuesInProject(project, "%prvw%") } returns 0
            every { postingRepository.countSearchPostingsInProject(project, "%prvw%") } returns 0
            every { milestoneRepository.countSearchMilestonesInProject(project, "%prvw%") } returns 0
            every { issueCommentRepository.countSearchIssueCommentsInProject(project, "%prvw%") } returns 0
            every { postingCommentRepository.countSearchPostingCommentsInProject(project, "%prvw%") } returns 0
            every { reviewCommentRepository.countSearchReviewCommentsInProject(project, "%prvw%") } returns 10
            every { pullRequestRepository.countSearchPullRequestsInProject(project, "%prvw%") } returns 0
            every { reviewCommentRepository.searchReviewCommentsInProject(project, "%prvw%", pageable) } returns Page.empty()

            val result = searchService.searchInAProject("prvw", SearchType.AUTO, loginUser, project, pageable)

            result.reviewsCount shouldBe 10
            result.searchType shouldBe SearchType.REVIEW
        }

        // yona-wiki P3-02 Step8.6 항목3(2026-09-01, 우선순위 3위) — `yona search prs` 대응.
        it("프로젝트 내부 검색 시 PR 검색 타입일 경우 PR 리포지토리를 호출해야 한다") {
            val project = Project(id = 109L, name = "P109", owner = "owner")
            every { userRepository.countSearchUsers("%pprreq%") } returns 0
            every { issueRepository.countSearchIssuesInProject(project, "%pprreq%") } returns 0
            every { postingRepository.countSearchPostingsInProject(project, "%pprreq%") } returns 0
            every { milestoneRepository.countSearchMilestonesInProject(project, "%pprreq%") } returns 0
            every { issueCommentRepository.countSearchIssueCommentsInProject(project, "%pprreq%") } returns 0
            every { postingCommentRepository.countSearchPostingCommentsInProject(project, "%pprreq%") } returns 0
            every { reviewCommentRepository.countSearchReviewCommentsInProject(project, "%pprreq%") } returns 0
            every { pullRequestRepository.countSearchPullRequestsInProject(project, "%pprreq%") } returns 11
            every { pullRequestRepository.searchPullRequestsInProject(project, "%pprreq%", pageable) } returns Page.empty()

            val result = searchService.searchInAProject("pprreq", SearchType.AUTO, loginUser, project, pageable)

            result.pullRequestsCount shouldBe 11
            result.searchType shouldBe SearchType.PULL_REQUEST
        }

        it("PROJECT 타입을 명시하면 프로젝트 내부 검색은 지원하지 않으므로 when절의 else 분기를 타야 한다") {
            val project = Project(id = 107L, name = "P107", owner = "owner")
            every { userRepository.countSearchUsers("%pproj%") } returns 0
            every { issueRepository.countSearchIssuesInProject(project, "%pproj%") } returns 0
            every { postingRepository.countSearchPostingsInProject(project, "%pproj%") } returns 0
            every { milestoneRepository.countSearchMilestonesInProject(project, "%pproj%") } returns 0
            every { issueCommentRepository.countSearchIssueCommentsInProject(project, "%pproj%") } returns 0
            every { postingCommentRepository.countSearchPostingCommentsInProject(project, "%pproj%") } returns 0
            every { reviewCommentRepository.countSearchReviewCommentsInProject(project, "%pproj%") } returns 0
            every { pullRequestRepository.countSearchPullRequestsInProject(project, "%pproj%") } returns 0

            val result = searchService.searchInAProject("pproj", SearchType.PROJECT, loginUser, project, pageable)

            result.searchType shouldBe SearchType.PROJECT
            result.projectsCount shouldBe 0
            result.projects.totalElements shouldBe 0
        }

        it("모든 카운트가 0이면 AUTO는 기본값인 이슈 타입으로 폴백해야 한다") {
            val project = Project(id = 108L, name = "P108", owner = "owner")
            every { userRepository.countSearchUsers("%pall0%") } returns 0
            every { issueRepository.countSearchIssuesInProject(project, "%pall0%") } returns 0
            every { postingRepository.countSearchPostingsInProject(project, "%pall0%") } returns 0
            every { milestoneRepository.countSearchMilestonesInProject(project, "%pall0%") } returns 0
            every { issueCommentRepository.countSearchIssueCommentsInProject(project, "%pall0%") } returns 0
            every { postingCommentRepository.countSearchPostingCommentsInProject(project, "%pall0%") } returns 0
            every { reviewCommentRepository.countSearchReviewCommentsInProject(project, "%pall0%") } returns 0
            every { pullRequestRepository.countSearchPullRequestsInProject(project, "%pall0%") } returns 0
            every { issueRepository.searchIssuesInProject(project, "%pall0%", pageable) } returns Page.empty()

            val result = searchService.searchInAProject("pall0", SearchType.AUTO, loginUser, project, pageable)

            result.searchType shouldBe SearchType.ISSUE
        }
    }

    // searchInAGroup은 기존 커버리지가 전혀 없었다(branch 0/25, line 0/20) — 조직 소속 프로젝트 필터링,
    // 필터링 결과가 비는 경우, 모든 SearchType 분기, else 분기를 신규로 검증한다.
    describe("searchInAGroup 신규 테스트") {
        val loginUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val pageable = PageRequest.of(0, 20)
        val org = Organization(id = 200L, name = "테스트조직")
        val otherOrg = Organization(id = 300L, name = "다른조직")

        it("허용된 프로젝트 중 해당 조직 소속 프로젝트만 필터링해 이슈를 검색해야 한다") {
            val p1 = Project(id = 1L, name = "P1", owner = "o1", organization = org)
            val p2 = Project(id = 2L, name = "P2", owner = "o2", organization = otherOrg)
            val p3 = Project(id = 3L, name = "P3", owner = "o3", organization = org)
            every { projectRepository.findAllowedProjectIdsForUser(10L) } returns listOf(1L, 2L, 3L)
            every { projectRepository.findAllById(listOf(1L, 2L, 3L)) } returns listOf(p1, p2, p3)

            every { userRepository.countSearchUsers("%gissue%") } returns 0
            every { projectRepository.countSearchProjects(listOf(1L, 3L), "%gissue%") } returns 0
            every { issueRepository.countSearchIssues(listOf(1L, 3L), "%gissue%", 10L) } returns 1
            every { postingRepository.countSearchPostings(listOf(1L, 3L), "%gissue%", 10L) } returns 0
            every { milestoneRepository.countSearchMilestones(listOf(1L, 3L), "%gissue%") } returns 0
            every { issueCommentRepository.countSearchIssueComments(listOf(1L, 3L), "%gissue%", 10L) } returns 0
            every { postingCommentRepository.countSearchPostingComments(listOf(1L, 3L), "%gissue%", 10L) } returns 0
            every { reviewCommentRepository.countSearchReviewComments(listOf(1L, 3L), "%gissue%", 10L) } returns 0
            every { pullRequestRepository.countSearchPullRequests(listOf(1L, 3L), "%gissue%", 10L) } returns 0
            every { issueRepository.searchIssues(listOf(1L, 3L), "%gissue%", 10L, pageable) } returns Page.empty()

            val result = searchService.searchInAGroup("gissue", SearchType.AUTO, loginUser, org, pageable)

            result.issuesCount shouldBe 1
            result.searchType shouldBe SearchType.ISSUE
        }

        it("허용된 프로젝트 중 해당 조직 소속 프로젝트가 하나도 없으면 빈 SearchResult를 반환해야 한다") {
            val p4 = Project(id = 4L, name = "P4", owner = "o4", organization = otherOrg)
            every { projectRepository.findAllowedProjectIdsForUser(10L) } returns listOf(4L)
            every { projectRepository.findAllById(listOf(4L)) } returns listOf(p4)

            val result = searchService.searchInAGroup("gempty", SearchType.AUTO, loginUser, org, pageable)

            result.keyword shouldBe "gempty"
            result.searchType shouldBe SearchType.AUTO
            result.issuesCount shouldBe 0
        }

        it("그룹 검색 시 사용자 검색 타입일 경우 사용자 리포지토리를 호출해야 한다") {
            val p5 = Project(id = 5L, name = "P5", owner = "o5", organization = org)
            every { projectRepository.findAllowedProjectIdsForUser(10L) } returns listOf(5L)
            every { projectRepository.findAllById(listOf(5L)) } returns listOf(p5)

            every { userRepository.countSearchUsers("%guser%") } returns 2
            every { projectRepository.countSearchProjects(listOf(5L), "%guser%") } returns 0
            every { issueRepository.countSearchIssues(listOf(5L), "%guser%", 10L) } returns 0
            every { postingRepository.countSearchPostings(listOf(5L), "%guser%", 10L) } returns 0
            every { milestoneRepository.countSearchMilestones(listOf(5L), "%guser%") } returns 0
            every { issueCommentRepository.countSearchIssueComments(listOf(5L), "%guser%", 10L) } returns 0
            every { postingCommentRepository.countSearchPostingComments(listOf(5L), "%guser%", 10L) } returns 0
            every { reviewCommentRepository.countSearchReviewComments(listOf(5L), "%guser%", 10L) } returns 0
            every { pullRequestRepository.countSearchPullRequests(listOf(5L), "%guser%", 10L) } returns 0
            every { userRepository.searchUsers("%guser%", pageable) } returns Page.empty()

            val result = searchService.searchInAGroup("guser", SearchType.AUTO, loginUser, org, pageable)

            result.usersCount shouldBe 2
            result.searchType shouldBe SearchType.USER
        }

        it("그룹 검색 시 프로젝트 검색 타입일 경우 프로젝트 리포지토리를 호출해야 한다") {
            val p6 = Project(id = 6L, name = "P6", owner = "o6", organization = org)
            every { projectRepository.findAllowedProjectIdsForUser(10L) } returns listOf(6L)
            every { projectRepository.findAllById(listOf(6L)) } returns listOf(p6)

            every { userRepository.countSearchUsers("%gproj%") } returns 0
            every { projectRepository.countSearchProjects(listOf(6L), "%gproj%") } returns 3
            every { issueRepository.countSearchIssues(listOf(6L), "%gproj%", 10L) } returns 0
            every { postingRepository.countSearchPostings(listOf(6L), "%gproj%", 10L) } returns 0
            every { milestoneRepository.countSearchMilestones(listOf(6L), "%gproj%") } returns 0
            every { issueCommentRepository.countSearchIssueComments(listOf(6L), "%gproj%", 10L) } returns 0
            every { postingCommentRepository.countSearchPostingComments(listOf(6L), "%gproj%", 10L) } returns 0
            every { reviewCommentRepository.countSearchReviewComments(listOf(6L), "%gproj%", 10L) } returns 0
            every { pullRequestRepository.countSearchPullRequests(listOf(6L), "%gproj%", 10L) } returns 0
            every { projectRepository.searchProjects(listOf(6L), "%gproj%", pageable) } returns Page.empty()

            val result = searchService.searchInAGroup("gproj", SearchType.AUTO, loginUser, org, pageable)

            result.projectsCount shouldBe 3
            result.searchType shouldBe SearchType.PROJECT
        }

        it("그룹 검색 시 게시글 검색 타입일 경우 게시글 리포지토리를 호출해야 한다") {
            val p7 = Project(id = 7L, name = "P7", owner = "o7", organization = org)
            every { projectRepository.findAllowedProjectIdsForUser(10L) } returns listOf(7L)
            every { projectRepository.findAllById(listOf(7L)) } returns listOf(p7)

            every { userRepository.countSearchUsers("%gpost%") } returns 0
            every { projectRepository.countSearchProjects(listOf(7L), "%gpost%") } returns 0
            every { issueRepository.countSearchIssues(listOf(7L), "%gpost%", 10L) } returns 0
            every { postingRepository.countSearchPostings(listOf(7L), "%gpost%", 10L) } returns 4
            every { milestoneRepository.countSearchMilestones(listOf(7L), "%gpost%") } returns 0
            every { issueCommentRepository.countSearchIssueComments(listOf(7L), "%gpost%", 10L) } returns 0
            every { postingCommentRepository.countSearchPostingComments(listOf(7L), "%gpost%", 10L) } returns 0
            every { reviewCommentRepository.countSearchReviewComments(listOf(7L), "%gpost%", 10L) } returns 0
            every { pullRequestRepository.countSearchPullRequests(listOf(7L), "%gpost%", 10L) } returns 0
            every { postingRepository.searchPostings(listOf(7L), "%gpost%", 10L, pageable) } returns Page.empty()

            val result = searchService.searchInAGroup("gpost", SearchType.AUTO, loginUser, org, pageable)

            result.postsCount shouldBe 4
            result.searchType shouldBe SearchType.POST
        }

        it("그룹 검색 시 마일스톤 검색 타입일 경우 마일스톤 리포지토리를 호출해야 한다") {
            val p8 = Project(id = 8L, name = "P8", owner = "o8", organization = org)
            every { projectRepository.findAllowedProjectIdsForUser(10L) } returns listOf(8L)
            every { projectRepository.findAllById(listOf(8L)) } returns listOf(p8)

            every { userRepository.countSearchUsers("%gmile%") } returns 0
            every { projectRepository.countSearchProjects(listOf(8L), "%gmile%") } returns 0
            every { issueRepository.countSearchIssues(listOf(8L), "%gmile%", 10L) } returns 0
            every { postingRepository.countSearchPostings(listOf(8L), "%gmile%", 10L) } returns 0
            every { milestoneRepository.countSearchMilestones(listOf(8L), "%gmile%") } returns 5
            every { issueCommentRepository.countSearchIssueComments(listOf(8L), "%gmile%", 10L) } returns 0
            every { postingCommentRepository.countSearchPostingComments(listOf(8L), "%gmile%", 10L) } returns 0
            every { reviewCommentRepository.countSearchReviewComments(listOf(8L), "%gmile%", 10L) } returns 0
            every { pullRequestRepository.countSearchPullRequests(listOf(8L), "%gmile%", 10L) } returns 0
            every { milestoneRepository.searchMilestones(listOf(8L), "%gmile%", pageable) } returns Page.empty()

            val result = searchService.searchInAGroup("gmile", SearchType.AUTO, loginUser, org, pageable)

            result.milestonesCount shouldBe 5
            result.searchType shouldBe SearchType.MILESTONE
        }

        it("그룹 검색 시 이슈 댓글 검색 타입일 경우 이슈 댓글 리포지토리를 호출해야 한다") {
            val p9 = Project(id = 9L, name = "P9", owner = "o9", organization = org)
            every { projectRepository.findAllowedProjectIdsForUser(10L) } returns listOf(9L)
            every { projectRepository.findAllById(listOf(9L)) } returns listOf(p9)

            every { userRepository.countSearchUsers("%gicmt%") } returns 0
            every { projectRepository.countSearchProjects(listOf(9L), "%gicmt%") } returns 0
            every { issueRepository.countSearchIssues(listOf(9L), "%gicmt%", 10L) } returns 0
            every { postingRepository.countSearchPostings(listOf(9L), "%gicmt%", 10L) } returns 0
            every { milestoneRepository.countSearchMilestones(listOf(9L), "%gicmt%") } returns 0
            every { issueCommentRepository.countSearchIssueComments(listOf(9L), "%gicmt%", 10L) } returns 6
            every { postingCommentRepository.countSearchPostingComments(listOf(9L), "%gicmt%", 10L) } returns 0
            every { reviewCommentRepository.countSearchReviewComments(listOf(9L), "%gicmt%", 10L) } returns 0
            every { pullRequestRepository.countSearchPullRequests(listOf(9L), "%gicmt%", 10L) } returns 0
            every { issueCommentRepository.searchIssueComments(listOf(9L), "%gicmt%", 10L, pageable) } returns Page.empty()

            val result = searchService.searchInAGroup("gicmt", SearchType.AUTO, loginUser, org, pageable)

            result.issueCommentsCount shouldBe 6
            result.searchType shouldBe SearchType.ISSUE_COMMENT
        }

        it("그룹 검색 시 게시글 댓글 검색 타입일 경우 게시글 댓글 리포지토리를 호출해야 한다") {
            val p10 = Project(id = 10L, name = "P10", owner = "o10", organization = org)
            every { projectRepository.findAllowedProjectIdsForUser(10L) } returns listOf(10L)
            every { projectRepository.findAllById(listOf(10L)) } returns listOf(p10)

            every { userRepository.countSearchUsers("%gpcmt%") } returns 0
            every { projectRepository.countSearchProjects(listOf(10L), "%gpcmt%") } returns 0
            every { issueRepository.countSearchIssues(listOf(10L), "%gpcmt%", 10L) } returns 0
            every { postingRepository.countSearchPostings(listOf(10L), "%gpcmt%", 10L) } returns 0
            every { milestoneRepository.countSearchMilestones(listOf(10L), "%gpcmt%") } returns 0
            every { issueCommentRepository.countSearchIssueComments(listOf(10L), "%gpcmt%", 10L) } returns 0
            every { postingCommentRepository.countSearchPostingComments(listOf(10L), "%gpcmt%", 10L) } returns 7
            every { reviewCommentRepository.countSearchReviewComments(listOf(10L), "%gpcmt%", 10L) } returns 0
            every { pullRequestRepository.countSearchPullRequests(listOf(10L), "%gpcmt%", 10L) } returns 0
            every { postingCommentRepository.searchPostingComments(listOf(10L), "%gpcmt%", 10L, pageable) } returns Page.empty()

            val result = searchService.searchInAGroup("gpcmt", SearchType.AUTO, loginUser, org, pageable)

            result.postCommentsCount shouldBe 7
            result.searchType shouldBe SearchType.POST_COMMENT
        }

        it("그룹 검색 시 리뷰 검색 타입일 경우 리뷰 리포지토리를 호출해야 한다") {
            val p11 = Project(id = 11L, name = "P11", owner = "o11", organization = org)
            every { projectRepository.findAllowedProjectIdsForUser(10L) } returns listOf(11L)
            every { projectRepository.findAllById(listOf(11L)) } returns listOf(p11)

            every { userRepository.countSearchUsers("%grvw%") } returns 0
            every { projectRepository.countSearchProjects(listOf(11L), "%grvw%") } returns 0
            every { issueRepository.countSearchIssues(listOf(11L), "%grvw%", 10L) } returns 0
            every { postingRepository.countSearchPostings(listOf(11L), "%grvw%", 10L) } returns 0
            every { milestoneRepository.countSearchMilestones(listOf(11L), "%grvw%") } returns 0
            every { issueCommentRepository.countSearchIssueComments(listOf(11L), "%grvw%", 10L) } returns 0
            every { postingCommentRepository.countSearchPostingComments(listOf(11L), "%grvw%", 10L) } returns 0
            every { reviewCommentRepository.countSearchReviewComments(listOf(11L), "%grvw%", 10L) } returns 8
            every { pullRequestRepository.countSearchPullRequests(listOf(11L), "%grvw%", 10L) } returns 0
            every { reviewCommentRepository.searchReviewComments(listOf(11L), "%grvw%", 10L, pageable) } returns Page.empty()

            val result = searchService.searchInAGroup("grvw", SearchType.AUTO, loginUser, org, pageable)

            result.reviewsCount shouldBe 8
            result.searchType shouldBe SearchType.REVIEW
        }

        // yona-wiki P3-02 Step8.6 항목3(2026-09-01, 우선순위 3위) — `yona search prs` 대응.
        it("그룹 검색 시 PR 검색 타입일 경우 PR 리포지토리를 호출해야 한다") {
            val p13 = Project(id = 13L, name = "P13", owner = "o13", organization = org)
            every { projectRepository.findAllowedProjectIdsForUser(10L) } returns listOf(13L)
            every { projectRepository.findAllById(listOf(13L)) } returns listOf(p13)

            every { userRepository.countSearchUsers("%gpr%") } returns 0
            every { projectRepository.countSearchProjects(listOf(13L), "%gpr%") } returns 0
            every { issueRepository.countSearchIssues(listOf(13L), "%gpr%", 10L) } returns 0
            every { postingRepository.countSearchPostings(listOf(13L), "%gpr%", 10L) } returns 0
            every { milestoneRepository.countSearchMilestones(listOf(13L), "%gpr%") } returns 0
            every { issueCommentRepository.countSearchIssueComments(listOf(13L), "%gpr%", 10L) } returns 0
            every { postingCommentRepository.countSearchPostingComments(listOf(13L), "%gpr%", 10L) } returns 0
            every { reviewCommentRepository.countSearchReviewComments(listOf(13L), "%gpr%", 10L) } returns 0
            every { pullRequestRepository.countSearchPullRequests(listOf(13L), "%gpr%", 10L) } returns 9
            every { pullRequestRepository.searchPullRequests(listOf(13L), "%gpr%", 10L, pageable) } returns Page.empty()

            val result = searchService.searchInAGroup("gpr", SearchType.AUTO, loginUser, org, pageable)

            result.pullRequestsCount shouldBe 9
            result.searchType shouldBe SearchType.PULL_REQUEST
        }

        it("검색 타입을 NA로 명시하면 when절의 else 분기를 타서 결과 목록을 채우지 않아야 한다") {
            val p12 = Project(id = 12L, name = "P12", owner = "o12", organization = org)
            every { projectRepository.findAllowedProjectIdsForUser(10L) } returns listOf(12L)
            every { projectRepository.findAllById(listOf(12L)) } returns listOf(p12)

            every { userRepository.countSearchUsers("%gnaval%") } returns 0
            every { projectRepository.countSearchProjects(listOf(12L), "%gnaval%") } returns 0
            every { issueRepository.countSearchIssues(listOf(12L), "%gnaval%", 10L) } returns 0
            every { postingRepository.countSearchPostings(listOf(12L), "%gnaval%", 10L) } returns 0
            every { milestoneRepository.countSearchMilestones(listOf(12L), "%gnaval%") } returns 0
            every { issueCommentRepository.countSearchIssueComments(listOf(12L), "%gnaval%", 10L) } returns 0
            every { postingCommentRepository.countSearchPostingComments(listOf(12L), "%gnaval%", 10L) } returns 0
            every { reviewCommentRepository.countSearchReviewComments(listOf(12L), "%gnaval%", 10L) } returns 0
            every { pullRequestRepository.countSearchPullRequests(listOf(12L), "%gnaval%", 10L) } returns 0

            val result = searchService.searchInAGroup("gnaval", SearchType.NA, loginUser, org, pageable)

            result.searchType shouldBe SearchType.NA
            result.issues.totalElements shouldBe 0
        }

        it("허용된 프로젝트 중 organization이 null인 프로젝트는 필터링에서 제외되어야 한다") {
            val orphanProject = Project(id = 50L, name = "P50", owner = "o50", organization = null)
            every { projectRepository.findAllowedProjectIdsForUser(10L) } returns listOf(50L)
            every { projectRepository.findAllById(listOf(50L)) } returns listOf(orphanProject)

            val result = searchService.searchInAGroup("gnullorg", SearchType.AUTO, loginUser, org, pageable)

            result.keyword shouldBe "gnullorg"
            result.searchType shouldBe SearchType.AUTO
            result.issuesCount shouldBe 0
        }

        // when절 각 case 안의 `user?.id` 세이프콜은 케이스별로 별도 분기를 만든다 — 익명(user=null) 검색이
        // ISSUE/POST/ISSUE_COMMENT/POST_COMMENT/REVIEW 타입으로 귀결되는 경우를 각각 검증해야
        // 해당 라인들의 널(user=null) 분기가 커버된다. getAllowedProjectIds가 findPublicProjectIds를
        // 타도록 익명 사용자로 호출한다(HIDE_PROJECT_LISTING 꺼짐).
        it("익명 사용자의 그룹 검색이 이슈 타입으로 귀결되면 user?.id의 null 분기를 타야 한다") {
            val p41 = Project(id = 41L, name = "P41", owner = "o41", organization = org)
            every { projectRepository.findPublicProjectIds() } returns listOf(41L)
            every { projectRepository.findAllById(listOf(41L)) } returns listOf(p41)

            every { userRepository.countSearchUsers("%agissue%") } returns 0
            every { projectRepository.countSearchProjects(listOf(41L), "%agissue%") } returns 0
            every { issueRepository.countSearchIssues(listOf(41L), "%agissue%", null) } returns 1
            every { postingRepository.countSearchPostings(listOf(41L), "%agissue%", null) } returns 0
            every { milestoneRepository.countSearchMilestones(listOf(41L), "%agissue%") } returns 0
            every { issueCommentRepository.countSearchIssueComments(listOf(41L), "%agissue%", null) } returns 0
            every { postingCommentRepository.countSearchPostingComments(listOf(41L), "%agissue%", null) } returns 0
            every { reviewCommentRepository.countSearchReviewComments(listOf(41L), "%agissue%", null) } returns 0
            every { pullRequestRepository.countSearchPullRequests(listOf(41L), "%agissue%", null) } returns 0
            every { issueRepository.searchIssues(listOf(41L), "%agissue%", null, pageable) } returns Page.empty()

            val result = searchService.searchInAGroup("agissue", SearchType.AUTO, null, org, pageable)

            result.issuesCount shouldBe 1
            result.searchType shouldBe SearchType.ISSUE
        }

        it("익명 사용자의 그룹 검색이 게시글 타입으로 귀결되면 user?.id의 null 분기를 타야 한다") {
            val p42 = Project(id = 42L, name = "P42", owner = "o42", organization = org)
            every { projectRepository.findPublicProjectIds() } returns listOf(42L)
            every { projectRepository.findAllById(listOf(42L)) } returns listOf(p42)

            every { userRepository.countSearchUsers("%agpost%") } returns 0
            every { projectRepository.countSearchProjects(listOf(42L), "%agpost%") } returns 0
            every { issueRepository.countSearchIssues(listOf(42L), "%agpost%", null) } returns 0
            every { postingRepository.countSearchPostings(listOf(42L), "%agpost%", null) } returns 1
            every { milestoneRepository.countSearchMilestones(listOf(42L), "%agpost%") } returns 0
            every { issueCommentRepository.countSearchIssueComments(listOf(42L), "%agpost%", null) } returns 0
            every { postingCommentRepository.countSearchPostingComments(listOf(42L), "%agpost%", null) } returns 0
            every { reviewCommentRepository.countSearchReviewComments(listOf(42L), "%agpost%", null) } returns 0
            every { pullRequestRepository.countSearchPullRequests(listOf(42L), "%agpost%", null) } returns 0
            every { postingRepository.searchPostings(listOf(42L), "%agpost%", null, pageable) } returns Page.empty()

            val result = searchService.searchInAGroup("agpost", SearchType.AUTO, null, org, pageable)

            result.postsCount shouldBe 1
            result.searchType shouldBe SearchType.POST
        }

        it("익명 사용자의 그룹 검색이 이슈 댓글 타입으로 귀결되면 user?.id의 null 분기를 타야 한다") {
            val p43 = Project(id = 43L, name = "P43", owner = "o43", organization = org)
            every { projectRepository.findPublicProjectIds() } returns listOf(43L)
            every { projectRepository.findAllById(listOf(43L)) } returns listOf(p43)

            every { userRepository.countSearchUsers("%agicmt%") } returns 0
            every { projectRepository.countSearchProjects(listOf(43L), "%agicmt%") } returns 0
            every { issueRepository.countSearchIssues(listOf(43L), "%agicmt%", null) } returns 0
            every { postingRepository.countSearchPostings(listOf(43L), "%agicmt%", null) } returns 0
            every { milestoneRepository.countSearchMilestones(listOf(43L), "%agicmt%") } returns 0
            every { issueCommentRepository.countSearchIssueComments(listOf(43L), "%agicmt%", null) } returns 1
            every { postingCommentRepository.countSearchPostingComments(listOf(43L), "%agicmt%", null) } returns 0
            every { reviewCommentRepository.countSearchReviewComments(listOf(43L), "%agicmt%", null) } returns 0
            every { pullRequestRepository.countSearchPullRequests(listOf(43L), "%agicmt%", null) } returns 0
            every { issueCommentRepository.searchIssueComments(listOf(43L), "%agicmt%", null, pageable) } returns Page.empty()

            val result = searchService.searchInAGroup("agicmt", SearchType.AUTO, null, org, pageable)

            result.issueCommentsCount shouldBe 1
            result.searchType shouldBe SearchType.ISSUE_COMMENT
        }

        it("익명 사용자의 그룹 검색이 게시글 댓글 타입으로 귀결되면 user?.id의 null 분기를 타야 한다") {
            val p44 = Project(id = 44L, name = "P44", owner = "o44", organization = org)
            every { projectRepository.findPublicProjectIds() } returns listOf(44L)
            every { projectRepository.findAllById(listOf(44L)) } returns listOf(p44)

            every { userRepository.countSearchUsers("%agpcmt%") } returns 0
            every { projectRepository.countSearchProjects(listOf(44L), "%agpcmt%") } returns 0
            every { issueRepository.countSearchIssues(listOf(44L), "%agpcmt%", null) } returns 0
            every { postingRepository.countSearchPostings(listOf(44L), "%agpcmt%", null) } returns 0
            every { milestoneRepository.countSearchMilestones(listOf(44L), "%agpcmt%") } returns 0
            every { issueCommentRepository.countSearchIssueComments(listOf(44L), "%agpcmt%", null) } returns 0
            every { postingCommentRepository.countSearchPostingComments(listOf(44L), "%agpcmt%", null) } returns 1
            every { reviewCommentRepository.countSearchReviewComments(listOf(44L), "%agpcmt%", null) } returns 0
            every { pullRequestRepository.countSearchPullRequests(listOf(44L), "%agpcmt%", null) } returns 0
            every { postingCommentRepository.searchPostingComments(listOf(44L), "%agpcmt%", null, pageable) } returns Page.empty()

            val result = searchService.searchInAGroup("agpcmt", SearchType.AUTO, null, org, pageable)

            result.postCommentsCount shouldBe 1
            result.searchType shouldBe SearchType.POST_COMMENT
        }

        it("익명 사용자의 그룹 검색이 리뷰 타입으로 귀결되면 user?.id의 null 분기를 타야 한다") {
            val p45 = Project(id = 45L, name = "P45", owner = "o45", organization = org)
            every { projectRepository.findPublicProjectIds() } returns listOf(45L)
            every { projectRepository.findAllById(listOf(45L)) } returns listOf(p45)

            every { userRepository.countSearchUsers("%agrvw%") } returns 0
            every { projectRepository.countSearchProjects(listOf(45L), "%agrvw%") } returns 0
            every { issueRepository.countSearchIssues(listOf(45L), "%agrvw%", null) } returns 0
            every { postingRepository.countSearchPostings(listOf(45L), "%agrvw%", null) } returns 0
            every { milestoneRepository.countSearchMilestones(listOf(45L), "%agrvw%") } returns 0
            every { issueCommentRepository.countSearchIssueComments(listOf(45L), "%agrvw%", null) } returns 0
            every { postingCommentRepository.countSearchPostingComments(listOf(45L), "%agrvw%", null) } returns 0
            every { reviewCommentRepository.countSearchReviewComments(listOf(45L), "%agrvw%", null) } returns 1
            every { pullRequestRepository.countSearchPullRequests(listOf(45L), "%agrvw%", null) } returns 0
            every { reviewCommentRepository.searchReviewComments(listOf(45L), "%agrvw%", null, pageable) } returns Page.empty()

            val result = searchService.searchInAGroup("agrvw", SearchType.AUTO, null, org, pageable)

            result.reviewsCount shouldBe 1
            result.searchType shouldBe SearchType.REVIEW
        }
    }
})
