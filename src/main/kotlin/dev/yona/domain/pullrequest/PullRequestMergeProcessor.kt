package dev.yona.domain.pullrequest

import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.PullRequestCommitState
import dev.yona.domain.enumeration.State
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Date

/**
 * 풀 리퀘스트를 받는 브랜치와 병합해 보고 결과를 풀 리퀘스트에 반영한다. v1.6 `PullRequestMergingActor`와 `PullRequestActor.processPullRequestMerging`
 * (`app/actors/PullRequestActor.java`, `PullRequestMergingActor.java`), `PullRequestMergeResult`에 해당한다.
 *
 * 처리 순서:
 * 1. 병합 중(`isMerging`) 표시를 켠다.
 * 2. 보내는 브랜치를 가져와 병합한다. 충돌하지 않으면 병합 커밋과 병합 기준 커밋 번호(`mergedCommitIdFrom`, `mergedCommitIdTo`)를 저장한다.
 * 3. 가져올 커밋이 있으면 새 커밋을 `CURRENT`로 저장하고 더는 포함되지 않는 것은 `PRIOR`로 바꾼다. 새 커밋이 있으면 (새 풀 리퀘스트가 아닐 때) 커밋
 *    변경 알림을 내보내고 타임라인에 새 커밋 이벤트를 쌓고 리뷰어를 비운다.
 * 4. 가져올 커밋이 없으면 이미 반영된 것으로 보고 `MERGED`로 바꾼다. 이때 v1.6은 상태를 먼저 바꾼 뒤 "상태가 MERGED가 아니면" 이벤트를 쌓는 조건을
 *    걸어 이벤트가 쌓이지 않는다([refactoring-backlog.md] R-42). 같게 재현한다.
 * 5. 충돌이 새로 생기거나 풀리면 병합 상태 알림과 타임라인 이벤트를 쌓는다.
 * 6. 병합 중 표시를 끈다.
 *
 * 처리 중 예외가 나면 로그를 남기고 병합 중 표시를 끈다(v1.6은 표시를 그대로 두어 화면이 계속 "병합 중"이었다, R-42-2).
 */
@Service
class PullRequestMergeProcessor(
    private val pullRequests: PullRequestRepository,
    private val commits: PullRequestCommitRepository,
    private val merger: PullRequestMerger,
    private val events: PullRequestEventService,
    private val users: UserRepository,
    private val publisher: ApplicationEventPublisher,
    private val reviewService: PullRequestReviewService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 병합 중 표시를 켠다 (v1.6 `startMerge` 뒤 `update`). 별도 트랜잭션에서 커밋해 두어 검사가 오래 걸려도 화면이 병합 중임을 보여 준다. */
    @Transactional
    fun start(pullRequestId: Long) {
        pullRequests.findById(pullRequestId).orElse(null)?.let { it.isMerging = true; pullRequests.save(it) }
    }

    /**
     * 병합 검사를 처리한다.
     *
     * @param newPullRequest 방금 만든 풀 리퀘스트이면 `true`. 새 커밋 알림을 내보내지 않는다
     */
    @Transactional
    fun process(pullRequestId: Long, senderId: Long, newPullRequest: Boolean) {
        val pullRequest = pullRequests.findById(pullRequestId).orElse(null) ?: return
        val sender = users.findById(senderId).orElse(null) ?: return
        try {
            handle(pullRequest, sender, newPullRequest)
        } catch (e: Exception) {
            log.error("Failed to check merging from {}", pullRequest.id, e)
            // 병합 중 표시를 꺼서 화면이 계속 "병합 중"으로 남지 않게 한다. v1.6은 그대로 두었다(R-42-2)
            pullRequest.isMerging = false
            pullRequests.save(pullRequest)
        }
    }

    private fun handle(pullRequest: PullRequest, sender: User, newPullRequest: Boolean) {
        val oldMergeCommitId = pullRequest.mergedCommitIdTo
        val wasConflict = pullRequest.isConflict ?: false

        val update = merger.update(pullRequest)
        pullRequest.isConflict = update.attempt.conflict
        // 추가 항목(X-28, D-41 7번): 리베이스 병합이 가능한지도 이 검사에서 함께 계산해 둔다. 계산에 실패하면 할 수 있는 것으로 둔다
        pullRequest.rebaseBlocked = try {
            if (update.attempt.commits.isEmpty()) null else merger.rebaseBlocked(pullRequest)
        } catch (e: Exception) {
            log.warn("Failed to check the rebase availability of {}", pullRequest.id, e)
            null
        }
        update.mergeCommitId?.let {
            pullRequest.mergedCommitIdFrom = update.attempt.leftParent.name
            pullRequest.mergedCommitIdTo = it.name
        }
        pullRequests.save(pullRequest)

        if (update.attempt.commits.isNotEmpty()) {
            val newCommits = saveCommits(pullRequest, update.attempt.commits)
            if (newCommits.isNotEmpty()) {
                if (!newPullRequest) publisher.publishEvent(PullRequestChangedActivity(pullRequest, EventType.PULL_REQUEST_COMMIT_CHANGED, null, null, sender))
                events.addCommits(sender, pullRequest, newCommits, commitEventOldValue(oldMergeCommitId, pullRequest.mergedCommitIdTo))
                // v1.6은 새 커밋이 들어오면 리뷰어를 모두 비운다. 추가 항목(X-12, D-35 5번)으로 프로젝트가 끌 수 있고(`isDismissingStaleApprovals`), 켜져 있으면
                // 현재 승인들을 사유와 함께 해제 처리한 뒤 보정 전의 옛 승인 집합도 비운다
                if (pullRequest.toProject?.isDismissingStaleApprovals != false) {
                    reviewService.dismissStaleApprovals(pullRequest)
                    pullRequest.reviewers.clear()
                }
            }
        } else {
            pullRequest.isConflict = false
            pullRequest.state = State.MERGED
            pullRequest.receiver = sender
            // v1.6은 위에서 상태를 MERGED로 바꾼 뒤 "상태가 MERGED가 아니면" 이벤트를 쌓으므로 이 분기는 실행되지 않는다.
        }

        val conflict = pullRequest.isConflict == true
        if (!wasConflict && conflict) {
            publishMerge(pullRequest, State.CONFLICT, sender)
        }
        if (wasConflict && !conflict) {
            publishMerge(pullRequest, State.RESOLVED, sender)
        }

        pullRequest.isMerging = false
        pullRequests.save(pullRequest)
    }

    private fun publishMerge(pullRequest: PullRequest, state: State, sender: User) {
        publisher.publishEvent(PullRequestChangedActivity(pullRequest, EventType.PULL_REQUEST_MERGED, null, state.state, sender))
        events.addMerge(sender, EventType.PULL_REQUEST_MERGED, state, pullRequest)
    }

    /**
     * 가져올 커밋 중 아직 `CURRENT`로 없는 것을 새로 저장하고, 더는 가져오지 않는 커밋은 `PRIOR`로 바꾼다 (v1.6 `saveCommits`).
     * 이미 `PRIOR`인 커밋이 다시 나타나도 되살리지 않고 새 행을 만든다.
     */
    private fun saveCommits(pullRequest: PullRequest, gitCommits: List<dev.yona.repository.CommitInfo>): List<PullRequestCommit> {
        val existing = commits.findByPullRequestId(requireNotNull(pullRequest.id))
        val currentIds = existing.filter { it.state == PullRequestCommitState.CURRENT }.mapNotNull { it.commitId }.toSet()
        val created = gitCommits.filter { it.id !in currentIds }.map {
            PullRequestCommit(
                pullRequest = pullRequest, commitId = it.id, commitShortId = it.shortId, commitMessage = it.message, authorEmail = it.authorEmail,
                authorDate = it.authorDate, created = Date(), state = PullRequestCommitState.CURRENT
            )
        }.map { commits.save(it) }
        val keep = gitCommits.map { it.id }.toSet()
        existing.filter { it.commitId !in keep }.forEach { it.state = PullRequestCommitState.PRIOR; commits.save(it) }
        return created
    }

    private fun commitEventOldValue(oldMergeCommitId: String?, newMergeCommitId: String?): String? =
        if (oldMergeCommitId == null) null else oldMergeCommitId + PullRequestEventService.DELIMITER + newMergeCommitId
}
