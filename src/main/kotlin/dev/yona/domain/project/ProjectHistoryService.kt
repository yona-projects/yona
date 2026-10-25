package dev.yona.domain.project

import dev.yona.domain.board.PostingRepository
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.pullrequest.PullRequestRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserLookup
import dev.yona.domain.user.UserRepository
import dev.yona.repository.CommitHistoryService
import dev.yona.security.AvatarService
import dev.yona.util.SiteUrls
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Date

/**
 * 프로젝트 홈 이력 탭의 한 줄이다. v1.6 `models.History`이다.
 *
 * @property who 누가. 사용자를 찾으면 사용자 이름, 못 찾으면 커밋 작성자 이름이다
 * @property userPageUrl 사용자 페이지 주소. 사용자를 찾지 못하면 `null`이다
 * @property userAvatarUrl 사용자 아바타 주소. 사용자를 찾지 못하면 `null`이다
 * @property what 종류. `commit`, `issue`, `post`, `pullrequest`
 * @property shortTitle `#번호` 또는 7자리 커밋 번호
 * @property how 내용. 제목 또는 커밋 메시지 첫 줄
 * @property url 컨텍스트 경로를 포함한 화면 주소
 */
data class ProjectHistoryItem(
    val who: String?, val userPageUrl: String?, val userAvatarUrl: String?, val whenDate: Date?, val what: String, val shortTitle: String, val how: String?, val url: String
)

/**
 * 프로젝트 홈의 이력 탭을 만든다. v1.6 `ProjectApp.getProjectHistory`, `History.makeHistory`(`app/models/History.java`)에 해당한다.
 *
 * 최근 커밋, 이슈, 게시글, 받은 풀 리퀘스트를 각 10개씩 모아 시각 내림차순으로 정렬한다(같은 시각은 이 순서를 유지한다).
 * 주소의 소유자는 프로젝트의 실제 소유자가 아니라 호출한 쪽이 요청 주소에서 가져온 값이다.
 */
@Service
class ProjectHistoryService(
    private val commits: CommitHistoryService,
    private val issues: IssueRepository,
    private val postings: PostingRepository,
    private val pullRequests: PullRequestRepository,
    private val users: UserRepository,
    private val userLookup: UserLookup,
    private val avatars: AvatarService,
    private val siteUrls: SiteUrls
) {

    @Transactional(readOnly = true)
    fun history(owner: String, project: Project): List<ProjectHistoryItem> {
        val projectId = requireNotNull(project.id)
        val newest = { column: String -> PageRequest.of(0, LIMIT, Sort.by(Sort.Order.desc(column), Sort.Order.desc("id"))) }
        val base = "/$owner/${project.name}"
        val items = mutableListOf<ProjectHistoryItem>()

        val recentCommits = try {
            commits.history(project, 0, LIMIT, null, null).orEmpty()
        } catch (e: CommitHistoryService.NoHeadException) {
            emptyList()
        }
        for (commit in recentCommits) {
            val user = userLookup.findByEmail(commit.authorEmail)
            items += item(user, commit.authorName, commit.commitTime, "commit", commit.shortId, commit.shortMessage, "$base/commit/${commit.id}")
        }
        for (issue in issues.findByProjectId(projectId, newest("createdDate"))) {
            items += item(issue.authorLoginId?.let { users.findByLoginId(it) }, issue.authorName, issue.createdDate, "issue", "#${issue.number}", issue.title, "$base/issue/${issue.number}")
        }
        for (posting in postings.findByProjectId(projectId, newest("createdDate"))) {
            items += item(posting.authorLoginId?.let { users.findByLoginId(it) }, posting.authorName, posting.createdDate, "post", "#${posting.number}", posting.title, "$base/post/${posting.number}")
        }
        for (pull in pullRequests.findByToProjectId(projectId, newest("created"))) {
            items += item(pull.contributor?.loginId?.let { users.findByLoginId(it) }, pull.contributor?.loginId, pull.created, "pullrequest", "#${pull.number}", pull.title, "$base/pullRequest/${pull.number}")
        }
        return items.sortedByDescending { it.whenDate }
    }

    /** 사용자를 찾았으면 사용자 정보(v1.6 `setUserPageUrl`)를, 못 찾았으면 [fallbackName]을 쓴다. */
    private fun item(user: User?, fallbackName: String?, whenDate: Date?, what: String, shortTitle: String, how: String?, url: String) = ProjectHistoryItem(
        who = user?.name ?: fallbackName, userPageUrl = user?.let { siteUrls.path("/${it.loginId}") }, userAvatarUrl = user?.let { avatars.avatarUrl(it) },
        whenDate = whenDate, what = what, shortTitle = shortTitle, how = how, url = siteUrls.path(url)
    )

    private companion object {
        const val LIMIT = 10
    }
}
