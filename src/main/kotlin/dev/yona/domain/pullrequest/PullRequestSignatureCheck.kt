package dev.yona.domain.pullrequest

import dev.yona.domain.enumeration.PullRequestCommitState
import dev.yona.domain.gpgkey.GpgVerificationService
import dev.yona.repository.CommitHistoryService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 풀 리퀘스트의 커밋 가운데 Verified 서명이 아닌 것의 수를 센다(추가 개발 항목 X-4, `decisions.md` D-65). 받는 브랜치에 서명 커밋을 요구하는 규칙 묶음이 없으면
 * 저장소를 읽지 않고 0이다. 서버가 병합할 때 만드는 병합 커밋은 대상이 아니라 풀 리퀘스트가 가진 커밋만 본다. 저장소에서 읽을 수 없는 커밋은 Verified가 아니므로 센다.
 */
@Service
class PullRequestSignatureCheck(
    private val commits: PullRequestCommitRepository,
    private val history: CommitHistoryService,
    private val verification: GpgVerificationService
) {

    @Transactional(readOnly = true)
    fun unverifiedCount(pullRequest: PullRequest): Int {
        if (pullRequest.rulesets.none { it.requireSignedCommits == true }) return 0
        val project = pullRequest.toProject ?: return 0
        val ids = commits.findByPullRequestIdAndStateOrderByCreatedDesc(requireNotNull(pullRequest.id), PullRequestCommitState.CURRENT).mapNotNull { it.commitId }
        val infos = history.commits(project, ids)
        return ids.count { id -> infos[id]?.let { !verification.verifyCommit(it.signature, it.committerEmail).verified } ?: true }
    }
}
