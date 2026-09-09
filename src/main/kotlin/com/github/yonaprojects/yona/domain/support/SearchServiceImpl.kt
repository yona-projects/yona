package com.github.yonaprojects.yona.domain.support

import com.github.yonaprojects.yona.domain.enumeration.SearchType
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.issue.IssueCommentRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.board.PostingCommentRepository
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class SearchServiceImpl(
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val issueRepository: IssueRepository,
    private val postingRepository: PostingRepository,
    private val milestoneRepository: MilestoneRepository,
    private val issueCommentRepository: IssueCommentRepository,
    private val postingCommentRepository: PostingCommentRepository,
    private val reviewCommentRepository: ReviewCommentRepository,
    // `yona search prs` 대응.
    private val pullRequestRepository: PullRequestRepository,
    // yona controllers/Application.java의 HIDE_PROJECT_LISTING 대응.
    @Value("\${yona.application.hide-project-listing:false}")
    private val hideProjectListing: Boolean = false
) : SearchService {

    // yona Search.projectsEL() 대응. HIDE_PROJECT_LISTING이 켜져 있으면 PUBLIC 프로젝트를
    // 검색 대상에서 제외한다 — 익명은 결과 없음, 로그인 사용자는 자신이 멤버이거나 소속 조직이
    // PROTECTED로 공개한 프로젝트만 남는다.
    private fun getAllowedProjectIds(user: User?): List<Long> {
        return if (user == null || user.id == null) {
            if (hideProjectListing) emptyList() else projectRepository.findPublicProjectIds()
        } else {
            if (hideProjectListing) {
                projectRepository.findAllowedProjectIdsForUserExcludingPublic(user.id!!)
            } else {
                projectRepository.findAllowedProjectIdsForUser(user.id!!)
            }
        }
    }

    override fun searchInAll(keyword: String, searchType: SearchType, user: User?, pageable: Pageable): SearchResult {
        val allowedProjectIds = getAllowedProjectIds(user)
        if (allowedProjectIds.isEmpty()) {
            return SearchResult(keyword = keyword, searchType = searchType)
        }

        val processedKeyword = "%${keyword.lowercase()}%"
        val result = getSearchResultCounts(keyword, allowedProjectIds, user?.id)
        result.searchType = searchType
        result.updateSearchType()

        when (result.searchType) {
            SearchType.ISSUE -> result.issues = issueRepository.searchIssues(allowedProjectIds, processedKeyword, user?.id, pageable)
            SearchType.USER -> result.users = userRepository.searchUsers(processedKeyword, pageable)
            SearchType.PROJECT -> result.projects = projectRepository.searchProjects(allowedProjectIds, processedKeyword, pageable)
            SearchType.POST -> result.posts = postingRepository.searchPostings(allowedProjectIds, processedKeyword, user?.id, pageable)
            SearchType.MILESTONE -> result.milestones = milestoneRepository.searchMilestones(allowedProjectIds, processedKeyword, pageable)
            SearchType.ISSUE_COMMENT -> result.issueComments = issueCommentRepository.searchIssueComments(allowedProjectIds, processedKeyword, user?.id, pageable)
            SearchType.POST_COMMENT -> result.postComments = postingCommentRepository.searchPostingComments(allowedProjectIds, processedKeyword, user?.id, pageable)
            SearchType.REVIEW -> result.reviews = reviewCommentRepository.searchReviewComments(allowedProjectIds, processedKeyword, user?.id, pageable)
            SearchType.PULL_REQUEST -> result.pullRequests = pullRequestRepository.searchPullRequests(allowedProjectIds, processedKeyword, user?.id, pageable)
            else -> {}
        }

        return result
    }

    override fun searchInAProject(keyword: String, searchType: SearchType, user: User?, project: Project, pageable: Pageable): SearchResult {
        // 프로젝트 단일 검색 카운트 및 조회
        val result = SearchResult(keyword = keyword, searchType = searchType)
        val processedKeyword = "%${keyword.lowercase()}%"
        
        result.usersCount = userRepository.countSearchUsers(processedKeyword) // 유저는 전역 검색
        result.issuesCount = issueRepository.countSearchIssuesInProject(project, processedKeyword)
        result.postsCount = postingRepository.countSearchPostingsInProject(project, processedKeyword)
        result.milestonesCount = milestoneRepository.countSearchMilestonesInProject(project, processedKeyword)
        result.issueCommentsCount = issueCommentRepository.countSearchIssueCommentsInProject(project, processedKeyword)
        result.postCommentsCount = postingCommentRepository.countSearchPostingCommentsInProject(project, processedKeyword)
        result.reviewsCount = reviewCommentRepository.countSearchReviewCommentsInProject(project, processedKeyword)
        result.pullRequestsCount = pullRequestRepository.countSearchPullRequestsInProject(project, processedKeyword)

        result.updateSearchType()

        when (result.searchType) {
            SearchType.ISSUE -> result.issues = issueRepository.searchIssuesInProject(project, processedKeyword, pageable)
            SearchType.USER -> result.users = userRepository.searchUsers(processedKeyword, pageable)
            SearchType.POST -> result.posts = postingRepository.searchPostingsInProject(project, processedKeyword, pageable)
            SearchType.MILESTONE -> result.milestones = milestoneRepository.searchMilestonesInProject(project, processedKeyword, pageable)
            SearchType.ISSUE_COMMENT -> result.issueComments = issueCommentRepository.searchIssueCommentsInProject(project, processedKeyword, pageable)
            SearchType.POST_COMMENT -> result.postComments = postingCommentRepository.searchPostingCommentsInProject(project, processedKeyword, pageable)
            SearchType.REVIEW -> result.reviews = reviewCommentRepository.searchReviewCommentsInProject(project, processedKeyword, pageable)
            SearchType.PULL_REQUEST -> result.pullRequests = pullRequestRepository.searchPullRequestsInProject(project, processedKeyword, pageable)
            else -> {}
        }

        return result
    }

    override fun searchInAGroup(keyword: String, searchType: SearchType, user: User?, organization: Organization, pageable: Pageable): SearchResult {
        val allowedProjectIds = getAllowedProjectIds(user)
        val groupProjectIds = projectRepository.findAllById(allowedProjectIds)
            .filter { it.organization?.id == organization.id }
            .map { it.id!! }

        if (groupProjectIds.isEmpty()) {
            return SearchResult(keyword = keyword, searchType = searchType)
        }

        val processedKeyword = "%${keyword.lowercase()}%"
        val result = getSearchResultCounts(keyword, groupProjectIds, user?.id)
        result.searchType = searchType
        result.updateSearchType()

        when (result.searchType) {
            SearchType.ISSUE -> result.issues = issueRepository.searchIssues(groupProjectIds, processedKeyword, user?.id, pageable)
            SearchType.USER -> result.users = userRepository.searchUsers(processedKeyword, pageable)
            SearchType.PROJECT -> result.projects = projectRepository.searchProjects(groupProjectIds, processedKeyword, pageable)
            SearchType.POST -> result.posts = postingRepository.searchPostings(groupProjectIds, processedKeyword, user?.id, pageable)
            SearchType.MILESTONE -> result.milestones = milestoneRepository.searchMilestones(groupProjectIds, processedKeyword, pageable)
            SearchType.ISSUE_COMMENT -> result.issueComments = issueCommentRepository.searchIssueComments(groupProjectIds, processedKeyword, user?.id, pageable)
            SearchType.POST_COMMENT -> result.postComments = postingCommentRepository.searchPostingComments(groupProjectIds, processedKeyword, user?.id, pageable)
            SearchType.REVIEW -> result.reviews = reviewCommentRepository.searchReviewComments(groupProjectIds, processedKeyword, user?.id, pageable)
            SearchType.PULL_REQUEST -> result.pullRequests = pullRequestRepository.searchPullRequests(groupProjectIds, processedKeyword, user?.id, pageable)
            else -> {}
        }

        return result
    }

    private fun getSearchResultCounts(keyword: String, projectIds: List<Long>, userId: Long?): SearchResult {
        val processedKeyword = "%${keyword.lowercase()}%"
        return SearchResult(
            keyword = keyword,
            usersCount = userRepository.countSearchUsers(processedKeyword),
            projectsCount = projectRepository.countSearchProjects(projectIds, processedKeyword),
            issuesCount = issueRepository.countSearchIssues(projectIds, processedKeyword, userId),
            postsCount = postingRepository.countSearchPostings(projectIds, processedKeyword, userId),
            milestonesCount = milestoneRepository.countSearchMilestones(projectIds, processedKeyword),
            issueCommentsCount = issueCommentRepository.countSearchIssueComments(projectIds, processedKeyword, userId),
            postCommentsCount = postingCommentRepository.countSearchPostingComments(projectIds, processedKeyword, userId),
            reviewsCount = reviewCommentRepository.countSearchReviewComments(projectIds, processedKeyword, userId),
            pullRequestsCount = pullRequestRepository.countSearchPullRequests(projectIds, processedKeyword, userId)
        )
    }
}
