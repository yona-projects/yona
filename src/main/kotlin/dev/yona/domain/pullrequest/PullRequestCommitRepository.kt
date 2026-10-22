package dev.yona.domain.pullrequest

import org.springframework.data.jpa.repository.JpaRepository

/** 풀 리퀘스트에 포함된 커밋 저장소이다. */
interface PullRequestCommitRepository : JpaRepository<PullRequestCommit, Long> {

    /** 풀 리퀘스트의 커밋을 모두 조회한다(이전 푸시의 것 포함). */
    fun findByPullRequestId(pullRequestId: Long): List<PullRequestCommit>

    /** 풀 리퀘스트의 현재 커밋을 최근 것이 앞이 되게 조회한다 (v1.6 `PullRequestCommit.getCurrentCommits`). */
    fun findByPullRequestIdAndStateOrderByCreatedDesc(pullRequestId: Long, state: dev.yona.domain.enumeration.PullRequestCommitState): List<PullRequestCommit>
}
