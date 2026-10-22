package dev.yona.domain.pullrequest

import dev.yona.domain.enumeration.State
import org.springframework.data.jpa.repository.JpaRepository

/** 풀 리퀘스트 저장소이다. */
interface PullRequestRepository : JpaRepository<PullRequest, Long> {

    /** 받는 프로젝트 기준으로 지정한 상태의 풀 리퀘스트 개수를 센다 (v1.6 `PullRequest.countOpenedPullRequests`). */
    fun countByToProjectIdAndState(projectId: Long, state: State): Long
}
