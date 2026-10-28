package dev.yona.domain.pullrequest

import dev.yona.domain.enumeration.State
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.IssueService
import dev.yona.domain.user.User
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 풀 리퀘스트를 병합하면 제목, 본문, 커밋 메시지에서 `closes #번호`처럼 가리킨 이슈를 닫는다. v1.6에 없는 추가 항목이다
 * (로드맵 X-11, `decisions.md` A-188). 규칙은 [PullRequestIssueReference]를 본다.
 *
 * 받는 프로젝트(`toProject`)의 이슈만 닫는다. 이미 닫혔거나 초안이거나 없는 번호는 건드리지 않는다. 닫는 사람은 병합한 사용자이며
 * 수동으로 닫을 때와 같이 타임라인 이벤트와 알림을 남긴다. 이슈를 닫다가 실패해도 이미 끝난 병합에는 영향을 주지 않는다.
 */
@Component
class PullRequestIssueCloser(
    private val issues: IssueRepository,
    private val issueService: IssueService,
    private val commits: PullRequestCommitRepository
) {
    private val log = LoggerFactory.getLogger(PullRequestIssueCloser::class.java)

    /** [pullRequest]가 가리킨 이슈를 [actor]가 닫은 것으로 처리하고, 닫은 이슈 번호를 돌려준다. */
    fun closeReferenced(pullRequest: PullRequest, actor: User): List<Long> {
        val project = pullRequest.toProject ?: return emptyList()
        val projectId = project.id ?: return emptyList()
        val texts = listOf(pullRequest.title, pullRequest.body) + commits.findByPullRequestId(pullRequest.id ?: return emptyList()).map { it.commitMessage }
        val closed = mutableListOf<Long>()
        for (number in PullRequestIssueReference.closingNumbers(texts).sorted()) {
            try {
                val issue = issues.findByProjectIdAndNumber(projectId, number) ?: continue
                if (issue.state != State.OPEN) continue
                issueService.toNextState(issue, actor, reopenParent = false)
                closed += number
            } catch (e: Exception) {
                log.error("Failed to close issue #{} referred from pull request {}", number, pullRequest.id, e)
            }
        }
        return closed
    }
}
