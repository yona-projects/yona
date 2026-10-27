package dev.yona.domain.issue

import dev.yona.domain.misc.RecentIssue
import dev.yona.domain.misc.RecentIssueRepository
import dev.yona.domain.user.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Date

/**
 * 사용자가 최근 본 이슈를 기록한다. v1.6 `User.visits(Issue)`와 `RecentIssue.addVisitIssueHistory`
 * (`app/models/RecentIssue.java:47-140`)에 해당한다. 같은 이슈의 이전 기록은 지우고 새로 남기며 사용자당 100개를 넘으면
 * 가장 오래된 것부터 지운다.
 *
 * v1.6은 이 작업을 비동기로 실행했다. v2는 요청 트랜잭션 안에서 처리한다([decisions.md] A-15).
 */
@Service
class RecentIssueService(private val recentIssues: RecentIssueRepository, private val siteUrls: dev.yona.util.SiteUrls) {

    /**
     * 이슈나 게시글의 주소이다. v1.6은 리버스 라우트(`routes.IssueApp.issue`)로 주소를 만들어 컨텍스트 경로를 앞에 붙이고 경로 조각을 퍼센트 인코딩한다.
     */
    private fun routeUrl(project: dev.yona.domain.project.Project, kind: String, number: Long?): String =
        siteUrls.path("/" + listOf(project.owner.orEmpty(), project.name.orEmpty()).joinToString("/") { dev.yona.util.UrlPaths.encode(it) } + "/$kind/$number")

    /** [user]가 [issue]를 봤음을 기록한다. 익명이면 아무것도 하지 않는다. */
    @Transactional
    fun visits(user: User?, issue: Issue) {
        val userId = user?.id ?: return
        recentIssues.findByUserIdAndIssueId(userId, requireNotNull(issue.id)).forEach { recentIssues.delete(it) }
        recentIssues.flush()
        val project = requireNotNull(issue.project)
        recentIssues.save(
            RecentIssue(user = user, issue = issue, title = issue.title, url = routeUrl(project, "issue", issue.number), createdDate = Date())
        )
        recentIssues.findByUserIdOrderByIdAsc(userId).let { all -> all.take(maxOf(0, all.size - MAX_RECENT_ISSUES)).forEach { recentIssues.delete(it) } }
    }

    /**
     * [user]가 [posting]을 봤음을 기록한다 (v1.6 `User.visits(Posting)`, `RecentIssue.addVisitPostingHistory`). 이슈와 같은 목록에 남고
     * 게시글 번호(`postingId`)와 주소로 구분한다.
     */
    @Transactional
    fun visits(user: User?, posting: dev.yona.domain.board.Posting) {
        val userId = user?.id ?: return
        val postingId = requireNotNull(posting.id)
        recentIssues.findByUserIdAndPostingId(userId, postingId).forEach { recentIssues.delete(it) }
        recentIssues.flush()
        val project = requireNotNull(posting.project)
        recentIssues.save(
            RecentIssue(user = user, postingId = postingId, title = posting.title, url = routeUrl(project, "post", posting.number), createdDate = Date())
        )
        recentIssues.findByUserIdOrderByIdAsc(userId).let { all -> all.take(maxOf(0, all.size - MAX_RECENT_ISSUES)).forEach { recentIssues.delete(it) } }
    }

    companion object {
        /** 사용자당 보관하는 최근 본 이슈의 최대 개수이다 (v1.6 `RecentIssue.MAX_RECENT_LIST_PER_USER`). */
        const val MAX_RECENT_ISSUES = 100
    }
}
